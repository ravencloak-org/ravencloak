package api

import (
	"context"
	"fmt"
	"net/http"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
	"github.com/gin-gonic/gin"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Handler struct {
	cfg *config.Config
	db  *pgxpool.Pool
}

func NewHandler(cfg *config.Config, db *pgxpool.Pool) *Handler {
	return &Handler{cfg: cfg, db: db}
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
	if err := h.db.Ping(ctx); err != nil {
		resp.Status = "degraded"
		resp.Database = "disconnected"
	}

	// Check auth backend
	authURL := fmt.Sprintf("%s/health", h.cfg.AuthBackendURL)
	client := &http.Client{Timeout: 2 * time.Second}
	authResp, err := client.Get(authURL)
	if err != nil || authResp.StatusCode >= 500 {
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
