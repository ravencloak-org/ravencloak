package api

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strconv"
	"sync"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/db"
	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/service"
	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

type Handler struct {
	cfg        *config.Config
	pool       *pgxpool.Pool
	repo       *db.Repository
	nebulaSvc  *service.NebulaService
	allocSvc   *service.AllocationService

	// CRL cache
	crlMu        sync.RWMutex
	crlCache     []db.RevokedCertEntry
	crlCacheTime time.Time
}

func NewHandler(cfg *config.Config, pool *pgxpool.Pool, repo *db.Repository, nebulaSvc *service.NebulaService, allocSvc *service.AllocationService) *Handler {
	return &Handler{
		cfg:       cfg,
		pool:      pool,
		repo:      repo,
		nebulaSvc: nebulaSvc,
		allocSvc:  allocSvc,
	}
}

func (h *Handler) HealthCheck(c *gin.Context) {
	resp := HealthResponse{
		Status:      "healthy",
		Database:    "connected",
		AuthBackend: "reachable",
	}

	// Check database
	ctx, cancel := context.WithTimeout(c.Request.Context(), 2*time.Second)
	defer cancel()
	if err := h.pool.Ping(ctx); err != nil {
		resp.Status = "degraded"
		resp.Database = "disconnected"
	}

	// Check auth backend
	authURL := fmt.Sprintf("%s/health", h.cfg.AuthBackendURL)
	client := &http.Client{Timeout: 2 * time.Second, Transport: otelhttp.NewTransport(http.DefaultTransport)}
	authReq, _ := http.NewRequestWithContext(c.Request.Context(), http.MethodGet, authURL, nil)
	authResp, err := client.Do(authReq)
	if err != nil || (authResp != nil && authResp.StatusCode >= 500) {
		resp.Status = "degraded"
		resp.AuthBackend = "unreachable"
	}
	if authResp != nil {
		authResp.Body.Close()
	}

	status := http.StatusOK
	if resp.Status == "degraded" {
		status = http.StatusServiceUnavailable
	}
	c.JSON(status, resp)
}

// GenerateCert handles POST /api/nebula/generate-cert
func (h *Handler) GenerateCert(c *gin.Context) {
	var req GenerateCertRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, http.StatusBadRequest, "invalid request body")
		return
	}

	if !isValidNodeType(req.NodeType) {
		respondError(c, http.StatusBadRequest, "nodeType must be 'laptop' or 'ec2'")
		return
	}

	if !isValidNodeName(req.NodeName) {
		respondError(c, http.StatusBadRequest, "nodeName must be alphanumeric with dashes/underscores/dots")
		return
	}

	userID, _ := c.Get("userID")
	uid := userID.(string)

	// Check for existing cert
	exists, err := h.repo.CheckUserNodeExists(c.Request.Context(), uid, req.NodeType, req.NodeName)
	if err != nil {
		log.Error().Err(err).Msg("check existing cert")
		respondError(c, http.StatusInternalServerError, "internal error")
		return
	}
	if exists {
		respondError(c, http.StatusConflict, "certificate already exists for this node")
		return
	}

	// Allocate IP
	ip, err := h.allocSvc.AllocateIP(c.Request.Context(), uid, req.NodeType, "")
	if err != nil {
		log.Error().Err(err).Msg("allocate IP")
		respondError(c, http.StatusInternalServerError, "IP allocation failed")
		return
	}

	// Determine groups
	groups := []string{"developer"}
	if req.NodeType == "ec2" {
		groups = []string{"ec2"}
	}

	// Generate certificate
	gen, err := h.nebulaSvc.GenerateCertificate(c.Request.Context(), req.NodeName, ip, groups)
	if err != nil {
		log.Error().Err(err).Msg("generate certificate")
		respondError(c, http.StatusInternalServerError, "certificate generation failed")
		return
	}

	// Store in database
	var deviceInfo json.RawMessage
	if req.DeviceInfo != "" {
		deviceInfo, _ = json.Marshal(map[string]string{"description": req.DeviceInfo})
	}

	cert := &db.NebulaCertificate{
		UserID:         uid,
		NodeType:       req.NodeType,
		NodeName:       req.NodeName,
		CertificatePEM: gen.CertificatePEM,
		PrivateKeyPEM:  gen.PrivateKeyPEM,
		IPAddress:      ip,
		Groups:         groups,
		DeviceInfo:     deviceInfo,
		ExpiresAt:      gen.ExpiresAt,
	}

	if err := h.repo.CreateCertificate(c.Request.Context(), cert); err != nil {
		log.Error().Err(err).Msg("store certificate")
		respondError(c, http.StatusInternalServerError, "failed to store certificate")
		return
	}

	log.Info().Str("userID", uid).Str("nodeName", req.NodeName).Str("ip", ip).Msg("certificate generated")

	// Generate config
	configYAML := h.nebulaSvc.GenerateConfig(gen.CertificatePEM, gen.PrivateKeyPEM, req.NodeType)

	c.JSON(http.StatusOK, GenerateCertResponse{
		Certificate:    gen.CertificatePEM,
		PrivateKey:     gen.PrivateKeyPEM,
		Config:         configYAML,
		LighthouseIP:   h.cfg.NebulaLighthouseIP,
		LighthousePort: h.cfg.NebulaLighthousePort,
		StaticHostMap: map[string][]string{
			h.cfg.NebulaLighthouseIP: {fmt.Sprintf("%s:%d", h.cfg.NebulaLighthouseExternalIP, h.cfg.NebulaLighthousePort)},
		},
		IP:        ip,
		ExpiresAt: gen.ExpiresAt.Format(time.RFC3339),
	})
}

// GenerateEC2Cert handles POST /api/nebula/generate-ec2-cert
func (h *Handler) GenerateEC2Cert(c *gin.Context) {
	var req GenerateEC2CertRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, http.StatusBadRequest, "invalid request body")
		return
	}

	if !isValidEnvironment(req.Environment) {
		respondError(c, http.StatusBadRequest, "environment must be one of: uat, prod, staging, dev")
		return
	}

	if !isValidNodeName(req.InstanceName) {
		respondError(c, http.StatusBadRequest, "instanceName must be alphanumeric with dashes/underscores/dots")
		return
	}

	userID := "system"
	if req.UserID != "" {
		userID = req.UserID
	}

	// Check for existing
	exists, err := h.repo.CheckUserNodeExists(c.Request.Context(), userID, "ec2", req.InstanceName)
	if err != nil {
		log.Error().Err(err).Msg("check existing cert")
		respondError(c, http.StatusInternalServerError, "internal error")
		return
	}
	if exists {
		respondError(c, http.StatusConflict, "certificate already exists for this instance")
		return
	}

	// Allocate IP
	ip, err := h.allocSvc.AllocateIP(c.Request.Context(), userID, "ec2", req.Environment)
	if err != nil {
		log.Error().Err(err).Msg("allocate IP")
		respondError(c, http.StatusInternalServerError, "IP allocation failed")
		return
	}

	groups := []string{"ec2", "ec2-" + req.Environment}

	gen, err := h.nebulaSvc.GenerateCertificate(c.Request.Context(), req.InstanceName, ip, groups)
	if err != nil {
		log.Error().Err(err).Msg("generate certificate")
		respondError(c, http.StatusInternalServerError, "certificate generation failed")
		return
	}

	env := req.Environment
	cert := &db.NebulaCertificate{
		UserID:         userID,
		NodeType:       "ec2",
		NodeName:       req.InstanceName,
		CertificatePEM: gen.CertificatePEM,
		PrivateKeyPEM:  gen.PrivateKeyPEM,
		IPAddress:      ip,
		Environment:    &env,
		Groups:         groups,
		ExpiresAt:      gen.ExpiresAt,
	}

	if err := h.repo.CreateCertificate(c.Request.Context(), cert); err != nil {
		log.Error().Err(err).Msg("store certificate")
		respondError(c, http.StatusInternalServerError, "failed to store certificate")
		return
	}

	log.Info().Str("instance", req.InstanceName).Str("env", req.Environment).Str("ip", ip).Msg("EC2 certificate generated")

	configYAML := h.nebulaSvc.GenerateConfig(gen.CertificatePEM, gen.PrivateKeyPEM, "ec2")

	c.JSON(http.StatusOK, GenerateCertResponse{
		Certificate:    gen.CertificatePEM,
		PrivateKey:     gen.PrivateKeyPEM,
		Config:         configYAML,
		LighthouseIP:   h.cfg.NebulaLighthouseIP,
		LighthousePort: h.cfg.NebulaLighthousePort,
		StaticHostMap: map[string][]string{
			h.cfg.NebulaLighthouseIP: {fmt.Sprintf("%s:%d", h.cfg.NebulaLighthouseExternalIP, h.cfg.NebulaLighthousePort)},
		},
		IP:        ip,
		ExpiresAt: gen.ExpiresAt.Format(time.RFC3339),
	})
}

// ListCerts handles GET /api/nebula/list-certs
func (h *Handler) ListCerts(c *gin.Context) {
	userID, _ := c.Get("userID")
	uid := userID.(string)

	var nodeType *string
	if nt := c.Query("nodeType"); nt != "" {
		nodeType = &nt
	}

	limit := 20
	if l := c.Query("limit"); l != "" {
		if parsed, err := strconv.Atoi(l); err == nil && parsed > 0 {
			limit = parsed
		}
	}

	certs, err := h.repo.GetCertificatesByUser(c.Request.Context(), uid, nodeType, limit)
	if err != nil {
		log.Error().Err(err).Msg("list certificates")
		respondError(c, http.StatusInternalServerError, "failed to list certificates")
		return
	}

	var summaries []CertSummary
	for _, cert := range certs {
		summaries = append(summaries, CertSummary{
			NodeName:  cert.NodeName,
			NodeType:  cert.NodeType,
			IP:        cert.IPAddress,
			CreatedAt: cert.CreatedAt.Format(time.RFC3339),
			ExpiresAt: cert.ExpiresAt.Format(time.RFC3339),
			IsRevoked: cert.IsRevoked,
		})
	}

	if summaries == nil {
		summaries = []CertSummary{}
	}

	c.JSON(http.StatusOK, CertListResponse{Certificates: summaries})
}

// RevokeCert handles POST /api/nebula/revoke-cert
func (h *Handler) RevokeCert(c *gin.Context) {
	var req RevokeCertRequest
	if err := c.ShouldBindJSON(&req); err != nil {
		respondError(c, http.StatusBadRequest, "invalid request body")
		return
	}

	userID, _ := c.Get("userID")
	uid := userID.(string)

	revokedAt, err := h.repo.RevokeCertificate(c.Request.Context(), uid, req.NodeName, req.Reason)
	if err != nil {
		log.Error().Err(err).Msg("revoke certificate")
		respondError(c, http.StatusInternalServerError, "failed to revoke certificate")
		return
	}

	if revokedAt == nil {
		respondError(c, http.StatusNotFound, "certificate not found or already revoked")
		return
	}

	log.Info().Str("userID", uid).Str("nodeName", req.NodeName).Str("reason", req.Reason).Msg("certificate revoked")

	c.JSON(http.StatusOK, RevokeCertResponse{
		Success:   true,
		RevokedAt: revokedAt.Format(time.RFC3339),
	})
}

// GetCRL handles GET /api/nebula/crl (public, cached 1 hour)
func (h *Handler) GetCRL(c *gin.Context) {
	h.crlMu.RLock()
	if h.crlCache != nil && time.Since(h.crlCacheTime) < time.Hour {
		entries := h.crlCache
		h.crlMu.RUnlock()
		c.Header("Cache-Control", "public, max-age=3600")
		c.JSON(http.StatusOK, gin.H{"revokedCertificates": entries})
		return
	}
	h.crlMu.RUnlock()

	entries, err := h.repo.GetRevokedCertificates(c.Request.Context())
	if err != nil {
		log.Error().Err(err).Msg("get CRL")
		respondError(c, http.StatusInternalServerError, "failed to get CRL")
		return
	}

	if entries == nil {
		entries = []db.RevokedCertEntry{}
	}

	h.crlMu.Lock()
	h.crlCache = entries
	h.crlCacheTime = time.Now()
	h.crlMu.Unlock()

	c.Header("Cache-Control", "public, max-age=3600")
	c.JSON(http.StatusOK, gin.H{"revokedCertificates": entries})
}
