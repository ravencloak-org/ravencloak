package service

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/db"
	"github.com/rs/zerolog/log"
	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
)

type TokenValidationResult struct {
	UserID string   `json:"userId"`
	Roles  []string `json:"roles"`
	Valid  bool     `json:"valid"`
}

type TokenService struct {
	cfg        *config.Config
	repo       *db.Repository
	httpClient *http.Client
}

func NewTokenService(cfg *config.Config, repo *db.Repository) *TokenService {
	return &TokenService{
		cfg:  cfg,
		repo: repo,
		httpClient: &http.Client{
			Timeout:   5 * time.Second,
			Transport: otelhttp.NewTransport(http.DefaultTransport),
		},
	}
}

// ValidateToken validates a JWT token, checking cache first, then the auth backend.
func (s *TokenService) ValidateToken(ctx context.Context, token string) (*TokenValidationResult, error) {
	if !isValidTokenFormat(token) {
		return &TokenValidationResult{Valid: false}, nil
	}

	tokenHash := hashToken(token)

	// Check cache first
	cached, err := s.repo.GetCachedToken(ctx, tokenHash)
	if err != nil {
		log.Warn().Err(err).Msg("failed to check token cache, falling through to backend")
	}
	if cached != nil {
		return &TokenValidationResult{
			UserID: cached.UserID,
			Roles:  cached.Roles,
			Valid:  true,
		}, nil
	}

	// Call auth backend
	result, err := s.callAuthBackend(ctx, token)
	if err != nil {
		log.Error().Err(err).Msg("auth backend validation failed")

		// Graceful fallback: check cache with extended TTL (1 min)
		cached, cacheErr := s.repo.GetCachedToken(ctx, tokenHash)
		if cacheErr == nil && cached != nil {
			log.Warn().Msg("using stale cache entry as fallback")
			return &TokenValidationResult{
				UserID: cached.UserID,
				Roles:  cached.Roles,
				Valid:  true,
			}, nil
		}

		return nil, fmt.Errorf("validate token: %w", err)
	}

	if !result.Valid {
		return result, nil
	}

	// Cache the result
	cacheEntry := &db.CachedToken{
		TokenHash: tokenHash,
		UserID:    result.UserID,
		Roles:     result.Roles,
		ExpiresAt: time.Now().Add(time.Duration(s.cfg.TokenCacheTTL) * time.Second),
	}
	if err := s.repo.CacheToken(ctx, cacheEntry); err != nil {
		log.Warn().Err(err).Msg("failed to cache token validation result")
	}

	return result, nil
}

func (s *TokenService) callAuthBackend(ctx context.Context, token string) (*TokenValidationResult, error) {
	url := fmt.Sprintf("%s%s", s.cfg.AuthBackendURL, s.cfg.AuthBackendTokenValidateURL)

	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("create request: %w", err)
	}
	req.Header.Set("Authorization", "Bearer "+token)

	resp, err := s.httpClient.Do(req)
	if err != nil {
		return nil, fmt.Errorf("call auth backend: %w", err)
	}
	defer resp.Body.Close()

	body, err := io.ReadAll(resp.Body)
	if err != nil {
		return nil, fmt.Errorf("read response: %w", err)
	}

	if resp.StatusCode == http.StatusUnauthorized || resp.StatusCode == http.StatusForbidden {
		return &TokenValidationResult{Valid: false}, nil
	}

	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("auth backend returned status %d", resp.StatusCode)
	}

	var result TokenValidationResult
	if err := json.Unmarshal(body, &result); err != nil {
		return nil, fmt.Errorf("parse response: %w", err)
	}
	result.Valid = true

	return &result, nil
}

// hashToken returns a SHA-256 hex digest of the token.
func hashToken(token string) string {
	h := sha256.Sum256([]byte(token))
	return hex.EncodeToString(h[:])
}

// isValidTokenFormat checks basic JWT format (3 dot-separated parts).
func isValidTokenFormat(token string) bool {
	token = strings.TrimSpace(token)
	if token == "" {
		return false
	}
	parts := strings.Split(token, ".")
	return len(parts) == 3
}
