package config

import (
	"os"
	"testing"
)

func TestLoad_Defaults(t *testing.T) {
	// Set required env var
	os.Setenv("NEBULA_LIGHTHOUSE_EXTERNAL_IP", "1.2.3.4")
	defer os.Unsetenv("NEBULA_LIGHTHOUSE_EXTERNAL_IP")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.DBHost != "localhost" {
		t.Errorf("expected DBHost=localhost, got %s", cfg.DBHost)
	}
	if cfg.DBPort != "5432" {
		t.Errorf("expected DBPort=5432, got %s", cfg.DBPort)
	}
	if cfg.DBName != "kos-auth" {
		t.Errorf("expected DBName=kos-auth, got %s", cfg.DBName)
	}
	if cfg.ServicePort != "8081" {
		t.Errorf("expected ServicePort=8081, got %s", cfg.ServicePort)
	}
	if cfg.TokenCacheTTL != 300 {
		t.Errorf("expected TokenCacheTTL=300, got %d", cfg.TokenCacheTTL)
	}
	if cfg.CertValidityDays != 365 {
		t.Errorf("expected CertValidityDays=365, got %d", cfg.CertValidityDays)
	}
	if cfg.NebulaLighthousePort != 4242 {
		t.Errorf("expected NebulaLighthousePort=4242, got %d", cfg.NebulaLighthousePort)
	}
}

func TestLoad_MissingLighthouseIP(t *testing.T) {
	os.Unsetenv("NEBULA_LIGHTHOUSE_EXTERNAL_IP")

	_, err := Load()
	if err == nil {
		t.Fatal("expected error for missing NEBULA_LIGHTHOUSE_EXTERNAL_IP")
	}
}

func TestLoad_CustomValues(t *testing.T) {
	envs := map[string]string{
		"NEBULA_LIGHTHOUSE_EXTERNAL_IP": "5.6.7.8",
		"DB_HOST":                       "myhost",
		"DB_PORT":                       "5433",
		"DB_NAME":                       "mydb",
		"SERVICE_PORT":                  "9090",
		"TOKEN_CACHE_TTL_SECONDS":       "600",
		"CERT_VALIDITY_DAYS":            "730",
	}
	for k, v := range envs {
		os.Setenv(k, v)
		defer os.Unsetenv(k)
	}

	cfg, err := Load()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	if cfg.DBHost != "myhost" {
		t.Errorf("expected DBHost=myhost, got %s", cfg.DBHost)
	}
	if cfg.DBPort != "5433" {
		t.Errorf("expected DBPort=5433, got %s", cfg.DBPort)
	}
	if cfg.DBName != "mydb" {
		t.Errorf("expected DBName=mydb, got %s", cfg.DBName)
	}
	if cfg.ServicePort != "9090" {
		t.Errorf("expected ServicePort=9090, got %s", cfg.ServicePort)
	}
	if cfg.TokenCacheTTL != 600 {
		t.Errorf("expected TokenCacheTTL=600, got %d", cfg.TokenCacheTTL)
	}
	if cfg.CertValidityDays != 730 {
		t.Errorf("expected CertValidityDays=730, got %d", cfg.CertValidityDays)
	}
}

func TestDatabaseURL(t *testing.T) {
	os.Setenv("NEBULA_LIGHTHOUSE_EXTERNAL_IP", "1.2.3.4")
	defer os.Unsetenv("NEBULA_LIGHTHOUSE_EXTERNAL_IP")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("unexpected error: %v", err)
	}

	expected := "postgres://postgres:postgres@localhost:5432/kos-auth?sslmode=disable"
	if cfg.DatabaseURL() != expected {
		t.Errorf("expected %s, got %s", expected, cfg.DatabaseURL())
	}
}
