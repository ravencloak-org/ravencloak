package service

import (
	"context"
	"crypto/rand"
	"fmt"
	"io"
	"net/netip"
	"os"
	"strings"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
	"github.com/rs/zerolog/log"
	"github.com/slackhq/nebula/cert"
	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/attribute"
	"golang.org/x/crypto/curve25519"
)

type NebulaService struct {
	cfg    *config.Config
	caCert cert.Certificate
	caKey  []byte
}

type GeneratedCert struct {
	CertificatePEM string
	PrivateKeyPEM  string
	IPAddress      string
	ExpiresAt      time.Time
}

func NewNebulaService(cfg *config.Config) (*NebulaService, error) {
	caCertPEM, err := os.ReadFile(cfg.NebulaCACertPath)
	if err != nil {
		return nil, fmt.Errorf("read CA cert: %w", err)
	}

	caKeyPEM, err := os.ReadFile(cfg.NebulaCAKeyPath)
	if err != nil {
		return nil, fmt.Errorf("read CA key: %w", err)
	}

	caCert, _, err := cert.UnmarshalCertificateFromPEM(caCertPEM)
	if err != nil {
		return nil, fmt.Errorf("parse CA cert: %w", err)
	}

	caKey, _, _, err := cert.UnmarshalSigningPrivateKeyFromPEM(caKeyPEM)
	if err != nil {
		return nil, fmt.Errorf("parse CA key: %w", err)
	}

	if !caCert.IsCA() {
		return nil, fmt.Errorf("loaded certificate is not a CA")
	}

	if caCert.Expired(time.Now()) {
		return nil, fmt.Errorf("CA certificate is expired")
	}

	log.Info().
		Str("name", caCert.Name()).
		Time("expires", caCert.NotAfter()).
		Msg("CA certificate loaded")

	return &NebulaService{
		cfg:    cfg,
		caCert: caCert,
		caKey:  caKey,
	}, nil
}

// GenerateCertificate creates a new Nebula certificate signed by the CA.
func (s *NebulaService) GenerateCertificate(ctx context.Context, name, ip string, groups []string) (*GeneratedCert, error) {
	_, span := otel.Tracer("nebula-sidecar").Start(ctx, "nebula.generate_certificate")
	defer span.End()
	span.SetAttributes(
		attribute.String("nebula.node_name", name),
		attribute.String("nebula.ip", ip),
		attribute.String("nebula.groups", strings.Join(groups, ",")),
	)
	prefix, err := netip.ParsePrefix(ip + "/24")
	if err != nil {
		return nil, fmt.Errorf("parse IP prefix: %w", err)
	}

	// Generate key pair (X25519)
	pub, priv, err := generateX25519Keypair()
	if err != nil {
		return nil, fmt.Errorf("generate keypair: %w", err)
	}

	now := time.Now()
	expiresAt := now.Add(time.Duration(s.cfg.CertValidityDays) * 24 * time.Hour)

	// Cap expiry to CA expiry
	if expiresAt.After(s.caCert.NotAfter()) {
		expiresAt = s.caCert.NotAfter()
	}

	tbs := &cert.TBSCertificate{
		Version:   cert.Version1,
		Curve:     cert.Curve_CURVE25519,
		Name:      name,
		Networks:  []netip.Prefix{prefix},
		Groups:    groups,
		IsCA:      false,
		NotBefore: now,
		NotAfter:  expiresAt,
		PublicKey: pub,
	}

	signedCert, err := tbs.Sign(s.caCert, cert.Curve_CURVE25519, s.caKey)
	if err != nil {
		return nil, fmt.Errorf("sign certificate: %w", err)
	}

	certPEM, err := signedCert.MarshalPEM()
	if err != nil {
		return nil, fmt.Errorf("marshal cert PEM: %w", err)
	}

	privKeyPEM := cert.MarshalPrivateKeyToPEM(cert.Curve_CURVE25519, priv)

	return &GeneratedCert{
		CertificatePEM: string(certPEM),
		PrivateKeyPEM:  string(privKeyPEM),
		IPAddress:      ip,
		ExpiresAt:      expiresAt,
	}, nil
}

// GetCACertPEM returns the PEM-encoded CA certificate.
func (s *NebulaService) GetCACertPEM() (string, error) {
	pem, err := s.caCert.MarshalPEM()
	if err != nil {
		return "", fmt.Errorf("marshal CA cert PEM: %w", err)
	}
	return string(pem), nil
}

// GenerateConfig produces a ready-to-use Nebula config.yaml.
func (s *NebulaService) GenerateConfig(certPEM, keyPEM, nodeType string) string {
	lighthouseIP := s.cfg.NebulaLighthouseIP
	externalIP := s.cfg.NebulaLighthouseExternalIP
	port := s.cfg.NebulaLighthousePort

	caCertPEM, _ := s.caCert.MarshalPEM()

	// Determine listen port: lighthouse uses fixed port, others use 0 (random)
	listenPort := 0
	amLighthouse := false
	if nodeType == "lighthouse" {
		listenPort = port
		amLighthouse = true
	}

	return fmt.Sprintf(`pki:
  ca: |
%s
  cert: |
%s
  key: |
%s

static_host_map:
  "%s":
    - "%s:%d"

lighthouse:
  am_lighthouse: %t
  interval: 60
  hosts:
    - "%s"

listen:
  host: 0.0.0.0
  port: %d

punchy:
  punch: true

tun:
  dev: nebula
  drop_local_broadcast: false
  drop_multicast: false
  tx_queue: 500
  mtu: 1300

logging:
  level: info
  format: json

firewall:
  conntrack:
    tcp_timeout: 12m
    udp_timeout: 3m
    default_timeout: 10m
  outbound:
    - port: any
      proto: any
      host: any
  inbound:
    - port: any
      proto: any
      host: any
`,
		indentPEM(string(caCertPEM), "    "),
		indentPEM(certPEM, "    "),
		indentPEM(keyPEM, "    "),
		lighthouseIP,
		externalIP, port,
		amLighthouse,
		lighthouseIP,
		listenPort,
	)
}

// indentPEM adds indentation to each line of a PEM block.
func indentPEM(pem, indent string) string {
	var result string
	for i, line := range splitLines(pem) {
		if i > 0 {
			result += "\n"
		}
		if line != "" {
			result += indent + line
		}
	}
	return result
}

func splitLines(s string) []string {
	var lines []string
	start := 0
	for i := 0; i < len(s); i++ {
		if s[i] == '\n' {
			lines = append(lines, s[start:i])
			start = i + 1
		}
	}
	if start < len(s) {
		lines = append(lines, s[start:])
	}
	return lines
}

// generateX25519Keypair generates a Curve25519 keypair for Nebula node certs.
// Uses raw random bytes + X25519 scalar multiplication (matching Nebula's approach).
func generateX25519Keypair() ([]byte, []byte, error) {
	privKey := make([]byte, 32)
	if _, err := io.ReadFull(rand.Reader, privKey); err != nil {
		return nil, nil, fmt.Errorf("generate random key: %w", err)
	}

	pubKey, err := curve25519.X25519(privKey, curve25519.Basepoint)
	if err != nil {
		return nil, nil, fmt.Errorf("compute public key: %w", err)
	}

	return pubKey, privKey, nil
}
