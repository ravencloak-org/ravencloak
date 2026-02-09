package main

import (
	"context"
	"fmt"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/api"
	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/config"
	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/db"
	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/service"
	"github.com/gin-gonic/gin"
	"github.com/rs/zerolog"
	"github.com/rs/zerolog/log"
)

func main() {
	// Load configuration
	cfg, err := config.Load()
	if err != nil {
		log.Fatal().Err(err).Msg("failed to load configuration")
	}

	// Configure logging
	setupLogging(cfg.LogLevel)
	log.Info().Str("port", cfg.ServicePort).Msg("starting nebula certificate sidecar")

	// Connect to database and run migrations
	pool, err := db.Connect(context.Background(), cfg)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to connect to database")
	}
	defer pool.Close()

	if err := db.RunMigrations(cfg.DatabaseURL()); err != nil {
		log.Fatal().Err(err).Msg("failed to run database migrations")
	}

	// Initialize services
	repo := db.NewRepository(pool)
	tokenSvc := service.NewTokenService(cfg, repo)
	allocSvc := service.NewAllocationService(repo)

	nebulaSvc, err := service.NewNebulaService(cfg)
	if err != nil {
		log.Fatal().Err(err).Msg("failed to initialize nebula service")
	}

	// Setup Gin router
	gin.SetMode(gin.ReleaseMode)
	router := gin.New()
	router.Use(api.RequestIDMiddleware())
	router.Use(api.LoggingMiddleware())
	router.Use(gin.Recovery())

	// Register routes
	handler := api.NewHandler(cfg, pool, repo, nebulaSvc, allocSvc)

	// Public endpoints
	router.GET("/health", handler.HealthCheck)
	router.GET("/api/nebula/crl", handler.GetCRL)

	// Authenticated endpoints
	authenticated := router.Group("/api/nebula")
	authenticated.Use(api.AuthMiddleware(tokenSvc))
	{
		authenticated.POST("/generate-cert", handler.GenerateCert)
		authenticated.GET("/list-certs", handler.ListCerts)
		authenticated.POST("/revoke-cert", handler.RevokeCert)

		// Admin-only
		authenticated.POST("/generate-ec2-cert", api.RequireRole("admin", "devops"), handler.GenerateEC2Cert)
	}

	// Start server with graceful shutdown
	srv := &http.Server{
		Addr:         fmt.Sprintf(":%s", cfg.ServicePort),
		Handler:      router,
		ReadTimeout:  10 * time.Second,
		WriteTimeout: 30 * time.Second,
		IdleTimeout:  60 * time.Second,
	}

	go func() {
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			log.Fatal().Err(err).Msg("server failed")
		}
	}()

	log.Info().Str("port", cfg.ServicePort).Msg("server started")

	// Wait for shutdown signal
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, syscall.SIGINT, syscall.SIGTERM)
	<-quit

	log.Info().Msg("shutting down server...")
	ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()

	if err := srv.Shutdown(ctx); err != nil {
		log.Fatal().Err(err).Msg("server forced to shutdown")
	}
	log.Info().Msg("server stopped")
}

func setupLogging(level string) {
	zerolog.TimeFieldFormat = zerolog.TimeFormatUnix

	switch level {
	case "debug":
		zerolog.SetGlobalLevel(zerolog.DebugLevel)
	case "warn":
		zerolog.SetGlobalLevel(zerolog.WarnLevel)
	case "error":
		zerolog.SetGlobalLevel(zerolog.ErrorLevel)
	default:
		zerolog.SetGlobalLevel(zerolog.InfoLevel)
	}
}
