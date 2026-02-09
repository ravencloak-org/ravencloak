package config

import (
	"fmt"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	// Auth backend integration
	AuthBackendURL              string
	AuthBackendTokenValidateURL string

	// Database (same PostgreSQL as Spring auth app)
	DBHost     string
	DBPort     string
	DBName     string
	DBUser     string
	DBPassword string
	DBPoolSize int

	// Nebula configuration
	NebulaLighthouseIP         string
	NebulaLighthouseExternalIP string
	NebulaLighthousePort       int
	NebulaCACertPath           string
	NebulaCAKeyPath            string

	// Service
	ServicePort     string
	LogLevel        string
	TokenCacheTTL   int // seconds
	CertValidityDays int
	RenewalWarningDays int
}

func Load() (*Config, error) {
	cfg := &Config{
		AuthBackendURL:              getEnv("AUTH_BACKEND_URL", "http://auth-backend:8080"),
		AuthBackendTokenValidateURL: getEnv("AUTH_BACKEND_TOKEN_VALIDATE_ENDPOINT", "/api/nebula/validate-token"),

		DBHost:     getEnv("DB_HOST", "localhost"),
		DBPort:     getEnv("DB_PORT", "5432"),
		DBName:     getEnv("DB_NAME", "kos-auth"),
		DBUser:     getEnv("DB_USERNAME", "postgres"),
		DBPassword: getEnv("DB_PASSWORD", "postgres"),
		DBPoolSize: getEnvInt("DATABASE_POOL_SIZE", 10),

		NebulaLighthouseIP:         getEnv("NEBULA_LIGHTHOUSE_IP", "192.168.100.1"),
		NebulaLighthouseExternalIP: getEnv("NEBULA_LIGHTHOUSE_EXTERNAL_IP", ""),
		NebulaLighthousePort:       getEnvInt("NEBULA_LIGHTHOUSE_PORT", 4242),
		NebulaCACertPath:           getEnv("NEBULA_CA_CERT_PATH", "/etc/nebula/ca.crt"),
		NebulaCAKeyPath:            getEnv("NEBULA_CA_KEY_PATH", "/etc/nebula/ca.key"),

		ServicePort:        getEnv("SERVICE_PORT", "8081"),
		LogLevel:           strings.ToLower(getEnv("SERVICE_LOG_LEVEL", "info")),
		TokenCacheTTL:      getEnvInt("TOKEN_CACHE_TTL_SECONDS", 300),
		CertValidityDays:   getEnvInt("CERT_VALIDITY_DAYS", 365),
		RenewalWarningDays: getEnvInt("CERT_RENEWAL_WARNING_DAYS", 30),
	}

	if cfg.NebulaLighthouseExternalIP == "" {
		return nil, fmt.Errorf("NEBULA_LIGHTHOUSE_EXTERNAL_IP is required")
	}

	return cfg, nil
}

func (c *Config) DatabaseURL() string {
	return fmt.Sprintf("postgres://%s:%s@%s:%s/%s?sslmode=disable",
		c.DBUser, c.DBPassword, c.DBHost, c.DBPort, c.DBName)
}

func getEnv(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	if v := os.Getenv(key); v != "" {
		if i, err := strconv.Atoi(v); err == nil {
			return i
		}
	}
	return fallback
}
