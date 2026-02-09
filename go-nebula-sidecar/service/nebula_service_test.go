package service

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"net/netip"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
	nebulacert "github.com/slackhq/nebula/cert"
)

// createTestCA generates a temporary CA cert and key for testing.
func createTestCA(t *testing.T) (string, string) {
	t.Helper()

	pubKey, privKey, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatalf("generate CA key: %v", err)
	}

	tbs := &nebulacert.TBSCertificate{
		Version:   nebulacert.Version1,
		Curve:     nebulacert.Curve_CURVE25519,
		Name:      "test-ca",
		IsCA:      true,
		NotBefore: time.Now().Add(-1 * time.Hour),
		NotAfter:  time.Now().Add(365 * 24 * time.Hour),
		PublicKey: pubKey,
		Networks:  []netip.Prefix{netip.MustParsePrefix("192.168.100.0/24")},
		Groups:    []string{"developer", "admin", "ec2-uat", "ec2-prod"},
	}

	caCert, err := tbs.Sign(nil, nebulacert.Curve_CURVE25519, privKey)
	if err != nil {
		t.Fatalf("sign CA cert: %v", err)
	}

	certPEM, err := caCert.MarshalPEM()
	if err != nil {
		t.Fatalf("marshal CA cert PEM: %v", err)
	}

	keyPEM := nebulacert.MarshalSigningPrivateKeyToPEM(nebulacert.Curve_CURVE25519, privKey)

	tmpDir := t.TempDir()
	certPath := filepath.Join(tmpDir, "ca.crt")
	keyPath := filepath.Join(tmpDir, "ca.key")

	if err := os.WriteFile(certPath, certPEM, 0600); err != nil {
		t.Fatalf("write CA cert: %v", err)
	}
	if err := os.WriteFile(keyPath, keyPEM, 0600); err != nil {
		t.Fatalf("write CA key: %v", err)
	}

	return certPath, keyPath
}

func TestNewNebulaService(t *testing.T) {
	certPath, keyPath := createTestCA(t)

	cfg := &config.Config{
		NebulaCACertPath:           certPath,
		NebulaCAKeyPath:            keyPath,
		CertValidityDays:           365,
		NebulaLighthouseIP:         "192.168.100.1",
		NebulaLighthouseExternalIP: "1.2.3.4",
		NebulaLighthousePort:       4242,
	}

	svc, err := NewNebulaService(cfg)
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if svc == nil {
		t.Fatal("expected non-nil service")
	}
}

func TestNewNebulaService_MissingCertFile(t *testing.T) {
	cfg := &config.Config{
		NebulaCACertPath: "/nonexistent/ca.crt",
		NebulaCAKeyPath:  "/nonexistent/ca.key",
	}

	_, err := NewNebulaService(cfg)
	if err == nil {
		t.Fatal("expected error for missing cert file")
	}
}

func TestGenerateCertificate(t *testing.T) {
	certPath, keyPath := createTestCA(t)

	cfg := &config.Config{
		NebulaCACertPath:           certPath,
		NebulaCAKeyPath:            keyPath,
		CertValidityDays:           365,
		NebulaLighthouseIP:         "192.168.100.1",
		NebulaLighthouseExternalIP: "1.2.3.4",
		NebulaLighthousePort:       4242,
	}

	svc, err := NewNebulaService(cfg)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}

	gen, err := svc.GenerateCertificate(context.Background(), "test-host", "192.168.100.42", []string{"developer"})
	if err != nil {
		t.Fatalf("generate cert: %v", err)
	}

	// Verify PEM content
	if !strings.Contains(gen.CertificatePEM, "NEBULA CERTIFICATE") {
		t.Error("certificate PEM should contain NEBULA CERTIFICATE banner")
	}
	if !strings.Contains(gen.PrivateKeyPEM, "NEBULA X25519 PRIVATE KEY") {
		t.Error("private key PEM should contain NEBULA X25519 PRIVATE KEY banner")
	}

	if gen.IPAddress != "192.168.100.42" {
		t.Errorf("expected IP 192.168.100.42, got %s", gen.IPAddress)
	}

	// Verify the generated cert can be parsed back
	parsedCert, _, err := nebulacert.UnmarshalCertificateFromPEM([]byte(gen.CertificatePEM))
	if err != nil {
		t.Fatalf("parse generated cert: %v", err)
	}

	if parsedCert.Name() != "test-host" {
		t.Errorf("expected name test-host, got %s", parsedCert.Name())
	}

	networks := parsedCert.Networks()
	if len(networks) != 1 {
		t.Fatalf("expected 1 network, got %d", len(networks))
	}
	if networks[0].Addr().String() != "192.168.100.42" {
		t.Errorf("expected IP 192.168.100.42, got %s", networks[0].Addr().String())
	}

	groups := parsedCert.Groups()
	if len(groups) != 1 || groups[0] != "developer" {
		t.Errorf("expected groups [developer], got %v", groups)
	}

	if parsedCert.Expired(time.Now()) {
		t.Error("newly generated cert should not be expired")
	}

	// Verify expiry is ~365 days from now
	expectedExpiry := time.Now().Add(365 * 24 * time.Hour)
	diff := gen.ExpiresAt.Sub(expectedExpiry)
	if diff > time.Minute || diff < -time.Minute {
		t.Errorf("expiry should be ~365 days from now, diff: %v", diff)
	}
}

func TestGenerateCertificate_MultipleGroups(t *testing.T) {
	certPath, keyPath := createTestCA(t)

	cfg := &config.Config{
		NebulaCACertPath:           certPath,
		NebulaCAKeyPath:            keyPath,
		CertValidityDays:           365,
		NebulaLighthouseIP:         "192.168.100.1",
		NebulaLighthouseExternalIP: "1.2.3.4",
		NebulaLighthousePort:       4242,
	}

	svc, err := NewNebulaService(cfg)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}

	gen, err := svc.GenerateCertificate(context.Background(), "ec2-host", "192.168.100.10", []string{"ec2-uat", "admin"})
	if err != nil {
		t.Fatalf("generate cert: %v", err)
	}

	parsedCert, _, err := nebulacert.UnmarshalCertificateFromPEM([]byte(gen.CertificatePEM))
	if err != nil {
		t.Fatalf("parse cert: %v", err)
	}

	groups := parsedCert.Groups()
	if len(groups) != 2 {
		t.Errorf("expected 2 groups, got %d: %v", len(groups), groups)
	}
}

func TestGetCACertPEM(t *testing.T) {
	certPath, keyPath := createTestCA(t)

	cfg := &config.Config{
		NebulaCACertPath:           certPath,
		NebulaCAKeyPath:            keyPath,
		CertValidityDays:           365,
		NebulaLighthouseIP:         "192.168.100.1",
		NebulaLighthouseExternalIP: "1.2.3.4",
		NebulaLighthousePort:       4242,
	}

	svc, err := NewNebulaService(cfg)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}

	pem, err := svc.GetCACertPEM()
	if err != nil {
		t.Fatalf("get CA cert PEM: %v", err)
	}

	if !strings.Contains(pem, "NEBULA CERTIFICATE") {
		t.Error("CA cert PEM should contain NEBULA CERTIFICATE banner")
	}
}

func TestGenerateConfig_Laptop(t *testing.T) {
	certPath, keyPath := createTestCA(t)

	cfg := &config.Config{
		NebulaCACertPath:           certPath,
		NebulaCAKeyPath:            keyPath,
		CertValidityDays:           365,
		NebulaLighthouseIP:         "192.168.100.1",
		NebulaLighthouseExternalIP: "5.6.7.8",
		NebulaLighthousePort:       4242,
	}

	svc, err := NewNebulaService(cfg)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}

	configYAML := svc.GenerateConfig("CERT_PEM_HERE", "KEY_PEM_HERE", "laptop")

	// Check key sections exist
	checks := []string{
		"pki:",
		"static_host_map:",
		`"192.168.100.1"`,
		`"5.6.7.8:4242"`,
		"am_lighthouse: false",
		"port: 0",
		"punch: true",
		"mtu: 1300",
		"firewall:",
	}

	for _, check := range checks {
		if !strings.Contains(configYAML, check) {
			t.Errorf("config should contain %q", check)
		}
	}
}

func TestGenerateConfig_Lighthouse(t *testing.T) {
	certPath, keyPath := createTestCA(t)

	cfg := &config.Config{
		NebulaCACertPath:           certPath,
		NebulaCAKeyPath:            keyPath,
		CertValidityDays:           365,
		NebulaLighthouseIP:         "192.168.100.1",
		NebulaLighthouseExternalIP: "5.6.7.8",
		NebulaLighthousePort:       4242,
	}

	svc, err := NewNebulaService(cfg)
	if err != nil {
		t.Fatalf("create service: %v", err)
	}

	configYAML := svc.GenerateConfig("CERT_PEM_HERE", "KEY_PEM_HERE", "lighthouse")

	if !strings.Contains(configYAML, "am_lighthouse: true") {
		t.Error("lighthouse config should have am_lighthouse: true")
	}
	if !strings.Contains(configYAML, "port: 4242") {
		t.Error("lighthouse config should use port 4242")
	}
}

func TestGenerateX25519Keypair(t *testing.T) {
	pub, priv, err := generateX25519Keypair()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if len(pub) != 32 {
		t.Errorf("expected 32-byte public key, got %d", len(pub))
	}
	if len(priv) != 32 {
		t.Errorf("expected 32-byte private key, got %d", len(priv))
	}

	// Verify uniqueness
	pub2, priv2, err := generateX25519Keypair()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if string(pub) == string(pub2) {
		t.Error("two keypairs should not be identical")
	}
	if string(priv) == string(priv2) {
		t.Error("two keypairs should not be identical")
	}
}

func TestSplitLines(t *testing.T) {
	tests := []struct {
		input    string
		expected int
	}{
		{"a\nb\nc", 3},
		{"single", 1},
		{"a\n", 1},
		{"", 0},
	}

	for _, tt := range tests {
		lines := splitLines(tt.input)
		if len(lines) != tt.expected {
			t.Errorf("splitLines(%q): expected %d lines, got %d", tt.input, tt.expected, len(lines))
		}
	}
}

func TestIndentPEM(t *testing.T) {
	input := "-----BEGIN TEST-----\ndata\n-----END TEST-----"
	result := indentPEM(input, "    ")

	lines := strings.Split(result, "\n")
	for _, line := range lines {
		if line != "" && !strings.HasPrefix(line, "    ") {
			t.Errorf("line should be indented: %q", line)
		}
	}
}
