package db

import (
	"context"
	"encoding/json"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"
	"github.com/jackc/pgx/v5/pgxpool"
)

type Repository struct {
	pool *pgxpool.Pool
}

func NewRepository(pool *pgxpool.Pool) *Repository {
	return &Repository{pool: pool}
}

// CreateCertificate inserts a new certificate record and returns its ID.
func (r *Repository) CreateCertificate(ctx context.Context, cert *NebulaCertificate) error {
	query := `
		INSERT INTO nebula_certificates
			(user_id, node_type, node_name, certificate_pem, private_key_pem,
			 ip_address, environment, groups, device_info, expires_at)
		VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
		RETURNING id, created_at`

	return r.pool.QueryRow(ctx, query,
		cert.UserID, cert.NodeType, cert.NodeName,
		cert.CertificatePEM, cert.PrivateKeyPEM,
		cert.IPAddress, cert.Environment, cert.Groups,
		cert.DeviceInfo, cert.ExpiresAt,
	).Scan(&cert.ID, &cert.CreatedAt)
}

// GetCertificatesByUser returns all non-revoked certs for a user, with optional type filter.
func (r *Repository) GetCertificatesByUser(ctx context.Context, userID string, nodeType *string, limit int) ([]NebulaCertificate, error) {
	query := `
		SELECT id, user_id, node_type, node_name, ip_address, environment,
		       groups, device_info, created_at, expires_at, is_revoked, revoked_at
		FROM nebula_certificates
		WHERE user_id = $1`
	args := []any{userID}

	if nodeType != nil {
		query += " AND node_type = $2"
		args = append(args, *nodeType)
	}

	query += " ORDER BY created_at DESC"

	if limit > 0 {
		query += fmt.Sprintf(" LIMIT %d", limit)
	}

	rows, err := r.pool.Query(ctx, query, args...)
	if err != nil {
		return nil, fmt.Errorf("query certificates: %w", err)
	}
	defer rows.Close()

	var certs []NebulaCertificate
	for rows.Next() {
		var c NebulaCertificate
		if err := rows.Scan(
			&c.ID, &c.UserID, &c.NodeType, &c.NodeName, &c.IPAddress,
			&c.Environment, &c.Groups, &c.DeviceInfo,
			&c.CreatedAt, &c.ExpiresAt, &c.IsRevoked, &c.RevokedAt,
		); err != nil {
			return nil, fmt.Errorf("scan certificate: %w", err)
		}
		certs = append(certs, c)
	}
	return certs, rows.Err()
}

// GetCertificateByUserAndNode returns a specific cert for a user by node name.
func (r *Repository) GetCertificateByUserAndNode(ctx context.Context, userID, nodeName string) (*NebulaCertificate, error) {
	query := `
		SELECT id, user_id, node_type, node_name, certificate_pem, private_key_pem,
		       ip_address, environment, groups, device_info, created_at, expires_at,
		       is_revoked, revoked_at, revoke_reason
		FROM nebula_certificates
		WHERE user_id = $1 AND node_name = $2`

	var c NebulaCertificate
	err := r.pool.QueryRow(ctx, query, userID, nodeName).Scan(
		&c.ID, &c.UserID, &c.NodeType, &c.NodeName,
		&c.CertificatePEM, &c.PrivateKeyPEM,
		&c.IPAddress, &c.Environment, &c.Groups, &c.DeviceInfo,
		&c.CreatedAt, &c.ExpiresAt, &c.IsRevoked, &c.RevokedAt, &c.RevokeReason,
	)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("query certificate: %w", err)
	}
	return &c, nil
}

// RevokeCertificate marks a certificate as revoked and adds it to the CRL.
func (r *Repository) RevokeCertificate(ctx context.Context, userID, nodeName, reason string) (*time.Time, error) {
	tx, err := r.pool.Begin(ctx)
	if err != nil {
		return nil, fmt.Errorf("begin transaction: %w", err)
	}
	defer tx.Rollback(ctx)

	now := time.Now().UTC()

	// Mark certificate as revoked
	var certID string
	err = tx.QueryRow(ctx, `
		UPDATE nebula_certificates
		SET is_revoked = true, revoked_at = $1, revoke_reason = $2
		WHERE user_id = $3 AND node_name = $4 AND is_revoked = false
		RETURNING id`,
		now, reason, userID, nodeName,
	).Scan(&certID)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("revoke certificate: %w", err)
	}

	// Add to CRL
	_, err = tx.Exec(ctx, `
		INSERT INTO certificate_revocation_list (certificate_serial, revoked_at, reason)
		VALUES ($1, $2, $3)
		ON CONFLICT (certificate_serial) DO NOTHING`,
		certID, now, reason,
	)
	if err != nil {
		return nil, fmt.Errorf("add to CRL: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return nil, fmt.Errorf("commit transaction: %w", err)
	}
	return &now, nil
}

// GetRevokedCertificates returns all entries in the CRL.
func (r *Repository) GetRevokedCertificates(ctx context.Context) ([]RevokedCertEntry, error) {
	rows, err := r.pool.Query(ctx, `
		SELECT certificate_serial, revoked_at, reason
		FROM certificate_revocation_list
		ORDER BY revoked_at DESC`)
	if err != nil {
		return nil, fmt.Errorf("query CRL: %w", err)
	}
	defer rows.Close()

	var entries []RevokedCertEntry
	for rows.Next() {
		var e RevokedCertEntry
		if err := rows.Scan(&e.CertificateSerial, &e.RevokedAt, &e.Reason); err != nil {
			return nil, fmt.Errorf("scan CRL entry: %w", err)
		}
		entries = append(entries, e)
	}
	return entries, rows.Err()
}

// GetNextAvailableIP finds the next free IP in the given range.
func (r *Repository) GetNextAvailableIP(ctx context.Context, nodeType, environment string) (string, error) {
	var startOctet, endOctet int

	switch nodeType {
	case "ec2":
		startOctet = 10
		endOctet = 99
	case "laptop":
		startOctet = 100
		endOctet = 199
	default:
		return "", fmt.Errorf("unknown node type: %s", nodeType)
	}

	// Get all used IPs in this range
	rows, err := r.pool.Query(ctx, `
		SELECT ip_address FROM nebula_certificates
		WHERE ip_address LIKE '192.168.100.%'
		  AND is_revoked = false`)
	if err != nil {
		return "", fmt.Errorf("query used IPs: %w", err)
	}
	defer rows.Close()

	usedIPs := make(map[string]bool)
	for rows.Next() {
		var ip string
		if err := rows.Scan(&ip); err != nil {
			return "", fmt.Errorf("scan IP: %w", err)
		}
		usedIPs[ip] = true
	}
	if err := rows.Err(); err != nil {
		return "", err
	}

	// Find first available
	for i := startOctet; i <= endOctet; i++ {
		ip := fmt.Sprintf("192.168.100.%d", i)
		if !usedIPs[ip] {
			return ip, nil
		}
	}

	return "", fmt.Errorf("no available IPs in range %d-%d for node type %s", startOctet, endOctet, nodeType)
}

// CacheToken stores a validated token result.
func (r *Repository) CacheToken(ctx context.Context, token *CachedToken) error {
	_, err := r.pool.Exec(ctx, `
		INSERT INTO token_cache (token_hash, user_id, roles, expires_at)
		VALUES ($1, $2, $3, $4)
		ON CONFLICT (token_hash) DO UPDATE
		SET user_id = $2, roles = $3, cached_at = CURRENT_TIMESTAMP, expires_at = $4`,
		token.TokenHash, token.UserID, token.Roles, token.ExpiresAt,
	)
	if err != nil {
		return fmt.Errorf("cache token: %w", err)
	}
	return nil
}

// GetCachedToken retrieves a cached token if it hasn't expired.
func (r *Repository) GetCachedToken(ctx context.Context, tokenHash string) (*CachedToken, error) {
	var t CachedToken
	err := r.pool.QueryRow(ctx, `
		SELECT token_hash, user_id, roles, cached_at, expires_at
		FROM token_cache
		WHERE token_hash = $1 AND expires_at > CURRENT_TIMESTAMP`,
		tokenHash,
	).Scan(&t.TokenHash, &t.UserID, &t.Roles, &t.CachedAt, &t.ExpiresAt)
	if err == pgx.ErrNoRows {
		return nil, nil
	}
	if err != nil {
		return nil, fmt.Errorf("get cached token: %w", err)
	}
	return &t, nil
}

// CleanExpiredTokenCache removes expired token cache entries.
func (r *Repository) CleanExpiredTokenCache(ctx context.Context) (int64, error) {
	tag, err := r.pool.Exec(ctx, `DELETE FROM token_cache WHERE expires_at <= CURRENT_TIMESTAMP`)
	if err != nil {
		return 0, fmt.Errorf("clean token cache: %w", err)
	}
	return tag.RowsAffected(), nil
}

// CheckIPExists checks if an IP address is already allocated.
func (r *Repository) CheckIPExists(ctx context.Context, ip string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM nebula_certificates
			WHERE ip_address = $1 AND is_revoked = false
		)`, ip,
	).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("check IP: %w", err)
	}
	return exists, nil
}

// CheckUserNodeExists checks if a user already has a cert for the given node type + name.
func (r *Repository) CheckUserNodeExists(ctx context.Context, userID, nodeType, nodeName string) (bool, error) {
	var exists bool
	err := r.pool.QueryRow(ctx, `
		SELECT EXISTS(
			SELECT 1 FROM nebula_certificates
			WHERE user_id = $1 AND node_type = $2 AND node_name = $3 AND is_revoked = false
		)`, userID, nodeType, nodeName,
	).Scan(&exists)
	if err != nil {
		return false, fmt.Errorf("check user node: %w", err)
	}
	return exists, nil
}

// GetUserDeviceInfo is a helper for returning device info as a map.
func ParseDeviceInfo(info json.RawMessage) map[string]string {
	if info == nil {
		return nil
	}
	var m map[string]string
	if err := json.Unmarshal(info, &m); err != nil {
		return nil
	}
	return m
}
