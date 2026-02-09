package service

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
)

func TestCallAuthBackend_ValidToken(t *testing.T) {
	// Mock auth backend
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		auth := r.Header.Get("Authorization")
		if auth != "Bearer valid.jwt.token" {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		json.NewEncoder(w).Encode(TokenValidationResult{
			UserID: "user-123",
			Roles:  []string{"developer", "admin"},
		})
	}))
	defer server.Close()

	cfg := &config.Config{
		AuthBackendURL:              server.URL,
		AuthBackendTokenValidateURL: "/api/nebula/validate-token",
		TokenCacheTTL:               300,
	}
	svc := NewTokenService(cfg, nil) // no repo for this test

	result, err := svc.callAuthBackend(context.Background(), "valid.jwt.token")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if !result.Valid {
		t.Error("expected valid result")
	}
	if result.UserID != "user-123" {
		t.Errorf("expected userID user-123, got %s", result.UserID)
	}
	if len(result.Roles) != 2 {
		t.Errorf("expected 2 roles, got %d", len(result.Roles))
	}
}

func TestCallAuthBackend_InvalidToken(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusUnauthorized)
	}))
	defer server.Close()

	cfg := &config.Config{
		AuthBackendURL:              server.URL,
		AuthBackendTokenValidateURL: "/api/nebula/validate-token",
		TokenCacheTTL:               300,
	}
	svc := NewTokenService(cfg, nil)

	result, err := svc.callAuthBackend(context.Background(), "bad.jwt.token")
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}
	if result.Valid {
		t.Error("expected invalid result for bad token")
	}
}

func TestCallAuthBackend_ServerError(t *testing.T) {
	server := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusInternalServerError)
	}))
	defer server.Close()

	cfg := &config.Config{
		AuthBackendURL:              server.URL,
		AuthBackendTokenValidateURL: "/api/nebula/validate-token",
		TokenCacheTTL:               300,
	}
	svc := NewTokenService(cfg, nil)

	_, err := svc.callAuthBackend(context.Background(), "some.jwt.token")
	if err == nil {
		t.Fatal("expected error for server error response")
	}
}

func TestCallAuthBackend_ServerDown(t *testing.T) {
	cfg := &config.Config{
		AuthBackendURL:              "http://localhost:1", // unreachable
		AuthBackendTokenValidateURL: "/api/nebula/validate-token",
		TokenCacheTTL:               300,
	}
	svc := NewTokenService(cfg, nil)

	_, err := svc.callAuthBackend(context.Background(), "some.jwt.token")
	if err == nil {
		t.Fatal("expected error for unreachable server")
	}
}
