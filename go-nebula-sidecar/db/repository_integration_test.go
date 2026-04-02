package db_test

import (
	"context"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/db"
	"github.com/testcontainers/testcontainers-go/modules/postgres"
)

func setupTestDB(t *testing.T) (*db.Repository, func()) {
	t.Helper()
	ctx := context.Background()

	container, err := postgres.Run(ctx,
		"postgres:16-alpine",
		postgres.WithDatabase("testdb"),
		postgres.WithUsername("postgres"),
		postgres.WithPassword("postgres"),
		postgres.BasicWaitStrategies(),
	)
	if err != nil {
		t.Fatalf("start postgres container: %v", err)
	}

	connStr, err := container.ConnectionString(ctx, "sslmode=disable")
	if err != nil {
		container.Terminate(ctx)
		t.Fatalf("get connection string: %v", err)
	}

	if err := db.RunMigrations(connStr); err != nil {
		container.Terminate(ctx)
		t.Fatalf("run migrations: %v", err)
	}

	pool, err := db.ConnectURL(ctx, connStr)
	if err != nil {
		container.Terminate(ctx)
		t.Fatalf("connect to db: %v", err)
	}

	repo := db.NewRepository(pool)
	cleanup := func() {
		pool.Close()
		container.Terminate(ctx)
	}
	return repo, cleanup
}

func TestCreateAndGetCertificate(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	cert := &db.NebulaCertificate{
		UserID:         "user-abc",
		NodeType:       "laptop",
		NodeName:       "my-laptop",
		CertificatePEM: "-----BEGIN NEBULA CERTIFICATE-----\ntest\n-----END NEBULA CERTIFICATE-----",
		PrivateKeyPEM:  "-----BEGIN NEBULA X25519 PRIVATE KEY-----\ntest\n-----END NEBULA X25519 PRIVATE KEY-----",
		IPAddress:      "192.168.100.101",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}

	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create certificate: %v", err)
	}
	if cert.ID == "" {
		t.Error("expected ID to be populated after create")
	}

	certs, err := repo.GetCertificatesByUser(ctx, "user-abc", nil, 10)
	if err != nil {
		t.Fatalf("get certificates: %v", err)
	}
	if len(certs) != 1 {
		t.Fatalf("expected 1 cert, got %d", len(certs))
	}
	if certs[0].NodeName != "my-laptop" {
		t.Errorf("expected node name my-laptop, got %s", certs[0].NodeName)
	}
}

func TestCreateCertificate_DuplicateNodeConflict(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	cert := &db.NebulaCertificate{
		UserID:         "user-dup",
		NodeType:       "laptop",
		NodeName:       "dup-laptop",
		CertificatePEM: "cert-pem",
		PrivateKeyPEM:  "key-pem",
		IPAddress:      "192.168.100.110",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("first create: %v", err)
	}

	cert2 := &db.NebulaCertificate{
		UserID:         "user-dup",
		NodeType:       "laptop",
		NodeName:       "dup-laptop",
		CertificatePEM: "cert-pem-2",
		PrivateKeyPEM:  "key-pem-2",
		IPAddress:      "192.168.100.111",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert2); err == nil {
		t.Error("expected duplicate node conflict error")
	}
}

func TestGetCertificateByUserAndNode(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	cert := &db.NebulaCertificate{
		UserID:         "user-xyz",
		NodeType:       "laptop",
		NodeName:       "xyz-laptop",
		CertificatePEM: "cert",
		PrivateKeyPEM:  "key",
		IPAddress:      "192.168.100.120",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create: %v", err)
	}

	found, err := repo.GetCertificateByUserAndNode(ctx, "user-xyz", "xyz-laptop")
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if found == nil {
		t.Fatal("expected to find certificate")
	}
	if found.IPAddress != "192.168.100.120" {
		t.Errorf("expected IP 192.168.100.120, got %s", found.IPAddress)
	}

	notFound, err := repo.GetCertificateByUserAndNode(ctx, "user-xyz", "nonexistent")
	if err != nil {
		t.Fatalf("get nonexistent: %v", err)
	}
	if notFound != nil {
		t.Error("expected nil for nonexistent node")
	}
}

func TestRevokeCertificate(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	cert := &db.NebulaCertificate{
		UserID:         "user-rev",
		NodeType:       "laptop",
		NodeName:       "rev-laptop",
		CertificatePEM: "cert",
		PrivateKeyPEM:  "key",
		IPAddress:      "192.168.100.130",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create: %v", err)
	}

	revokedAt, err := repo.RevokeCertificate(ctx, "user-rev", "rev-laptop", "device-lost")
	if err != nil {
		t.Fatalf("revoke: %v", err)
	}
	if revokedAt == nil {
		t.Fatal("expected non-nil revokedAt")
	}

	// Revoking again should return nil (already revoked)
	revokedAt2, err := repo.RevokeCertificate(ctx, "user-rev", "rev-laptop", "again")
	if err != nil {
		t.Fatalf("second revoke: %v", err)
	}
	if revokedAt2 != nil {
		t.Error("expected nil for already-revoked cert")
	}
}

func TestGetRevokedCertificates(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	cert := &db.NebulaCertificate{
		UserID:         "user-crl",
		NodeType:       "laptop",
		NodeName:       "crl-laptop",
		CertificatePEM: "cert",
		PrivateKeyPEM:  "key",
		IPAddress:      "192.168.100.140",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create: %v", err)
	}
	if _, err := repo.RevokeCertificate(ctx, "user-crl", "crl-laptop", "test"); err != nil {
		t.Fatalf("revoke: %v", err)
	}

	entries, err := repo.GetRevokedCertificates(ctx)
	if err != nil {
		t.Fatalf("get CRL: %v", err)
	}
	if len(entries) != 1 {
		t.Fatalf("expected 1 CRL entry, got %d", len(entries))
	}
	if entries[0].Reason != "test" {
		t.Errorf("expected reason 'test', got %s", entries[0].Reason)
	}
}

func TestGetNextAvailableIP_EC2(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	ip, err := repo.GetNextAvailableIP(ctx, "ec2", "uat")
	if err != nil {
		t.Fatalf("get next IP: %v", err)
	}
	if ip != "192.168.100.10" {
		t.Errorf("expected 192.168.100.10, got %s", ip)
	}

	// Allocate it
	cert := &db.NebulaCertificate{
		UserID:         "system",
		NodeType:       "ec2",
		NodeName:       "uat-server-01",
		CertificatePEM: "cert",
		PrivateKeyPEM:  "key",
		IPAddress:      ip,
		Groups:         []string{"ec2"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create: %v", err)
	}

	// Next IP should be .11
	ip2, err := repo.GetNextAvailableIP(ctx, "ec2", "uat")
	if err != nil {
		t.Fatalf("get next IP 2: %v", err)
	}
	if ip2 != "192.168.100.11" {
		t.Errorf("expected 192.168.100.11, got %s", ip2)
	}
}

func TestGetNextAvailableIP_Laptop(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	ip, err := repo.GetNextAvailableIP(ctx, "laptop", "")
	if err != nil {
		t.Fatalf("get next IP: %v", err)
	}
	// Should be in laptop range
	var fourth int
	if _, err := fmt.Sscanf(ip, "192.168.100.%d", &fourth); err != nil || fourth < 100 || fourth > 199 {
		t.Errorf("expected laptop IP in 192.168.100.100-199, got %s", ip)
	}
}

func TestCheckIPExists(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	exists, err := repo.CheckIPExists(ctx, "192.168.100.150")
	if err != nil {
		t.Fatalf("check IP: %v", err)
	}
	if exists {
		t.Error("IP should not exist yet")
	}

	cert := &db.NebulaCertificate{
		UserID:         "user-ip",
		NodeType:       "laptop",
		NodeName:       "ip-laptop",
		CertificatePEM: "cert",
		PrivateKeyPEM:  "key",
		IPAddress:      "192.168.100.150",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create: %v", err)
	}

	exists, err = repo.CheckIPExists(ctx, "192.168.100.150")
	if err != nil {
		t.Fatalf("check IP after create: %v", err)
	}
	if !exists {
		t.Error("IP should exist after allocation")
	}
}

func TestCheckUserNodeExists(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	exists, err := repo.CheckUserNodeExists(ctx, "user-node", "laptop", "node-laptop")
	if err != nil {
		t.Fatalf("check: %v", err)
	}
	if exists {
		t.Error("should not exist yet")
	}

	cert := &db.NebulaCertificate{
		UserID:         "user-node",
		NodeType:       "laptop",
		NodeName:       "node-laptop",
		CertificatePEM: "cert",
		PrivateKeyPEM:  "key",
		IPAddress:      "192.168.100.160",
		Groups:         []string{"developer"},
		ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
	}
	if err := repo.CreateCertificate(ctx, cert); err != nil {
		t.Fatalf("create: %v", err)
	}

	exists, err = repo.CheckUserNodeExists(ctx, "user-node", "laptop", "node-laptop")
	if err != nil {
		t.Fatalf("check after create: %v", err)
	}
	if !exists {
		t.Error("should exist after creation")
	}
}

func TestTokenCache(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	// Not cached yet
	cached, err := repo.GetCachedToken(ctx, "hash-abc")
	if err != nil {
		t.Fatalf("get cached: %v", err)
	}
	if cached != nil {
		t.Error("expected nil for uncached token")
	}

	// Cache it
	entry := &db.CachedToken{
		TokenHash: "hash-abc",
		UserID:    "user-tok",
		Roles:     []string{"developer", "admin"},
		ExpiresAt: time.Now().UTC().Add(5 * time.Minute),
	}
	if err := repo.CacheToken(ctx, entry); err != nil {
		t.Fatalf("cache token: %v", err)
	}

	// Retrieve
	cached, err = repo.GetCachedToken(ctx, "hash-abc")
	if err != nil {
		t.Fatalf("get after cache: %v", err)
	}
	if cached == nil {
		t.Fatal("expected cached entry")
	}
	if cached.UserID != "user-tok" {
		t.Errorf("expected user-tok, got %s", cached.UserID)
	}
	if len(cached.Roles) != 2 {
		t.Errorf("expected 2 roles, got %d", len(cached.Roles))
	}
}

func TestTokenCache_Expired(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	entry := &db.CachedToken{
		TokenHash: "hash-expired",
		UserID:    "user-exp",
		Roles:     []string{"developer"},
		ExpiresAt: time.Now().UTC().Add(-1 * time.Minute), // already expired
	}
	if err := repo.CacheToken(ctx, entry); err != nil {
		t.Fatalf("cache: %v", err)
	}

	cached, err := repo.GetCachedToken(ctx, "hash-expired")
	if err != nil {
		t.Fatalf("get expired: %v", err)
	}
	if cached != nil {
		t.Error("expired token should not be returned")
	}
}

func TestCleanExpiredTokenCache(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	// Insert expired
	for i := 0; i < 3; i++ {
		entry := &db.CachedToken{
			TokenHash: fmt.Sprintf("hash-clean-%d", i),
			UserID:    "user-clean",
			Roles:     []string{"developer"},
			ExpiresAt: time.Now().UTC().Add(-1 * time.Minute),
		}
		if err := repo.CacheToken(ctx, entry); err != nil {
			t.Fatalf("cache: %v", err)
		}
	}

	// Insert valid
	valid := &db.CachedToken{
		TokenHash: "hash-valid",
		UserID:    "user-clean",
		Roles:     []string{"developer"},
		ExpiresAt: time.Now().UTC().Add(5 * time.Minute),
	}
	if err := repo.CacheToken(ctx, valid); err != nil {
		t.Fatalf("cache valid: %v", err)
	}

	deleted, err := repo.CleanExpiredTokenCache(ctx)
	if err != nil {
		t.Fatalf("clean: %v", err)
	}
	if deleted != 3 {
		t.Errorf("expected 3 deleted, got %d", deleted)
	}
}

func TestGetCertificatesByUser_NodeTypeFilter(t *testing.T) {
	repo, cleanup := setupTestDB(t)
	defer cleanup()
	ctx := context.Background()

	for i, nodeType := range []string{"laptop", "laptop", "ec2"} {
		ip := fmt.Sprintf("192.168.100.%d", 170+i)
		if nodeType == "ec2" {
			ip = "192.168.100.10"
		}
		cert := &db.NebulaCertificate{
			UserID:         "user-filter",
			NodeType:       nodeType,
			NodeName:       fmt.Sprintf("node-%d", i),
			CertificatePEM: "cert",
			PrivateKeyPEM:  "key",
			IPAddress:      ip,
			Groups:         []string{nodeType},
			ExpiresAt:      time.Now().Add(365 * 24 * time.Hour),
		}
		if err := repo.CreateCertificate(ctx, cert); err != nil {
			t.Fatalf("create cert %d: %v", i, err)
		}
	}

	laptopType := "laptop"
	certs, err := repo.GetCertificatesByUser(ctx, "user-filter", &laptopType, 10)
	if err != nil {
		t.Fatalf("get: %v", err)
	}
	if len(certs) != 2 {
		t.Errorf("expected 2 laptop certs, got %d", len(certs))
	}
}

func TestParseDeviceInfo(t *testing.T) {
	raw := json.RawMessage(`{"description":"MacBook Pro M2","os":"macOS"}`)
	info := db.ParseDeviceInfo(raw)
	if info == nil {
		t.Fatal("expected non-nil map")
	}
	if info["description"] != "MacBook Pro M2" {
		t.Errorf("expected MacBook Pro M2, got %s", info["description"])
	}

	// Nil input
	if db.ParseDeviceInfo(nil) != nil {
		t.Error("expected nil for nil input")
	}
}
