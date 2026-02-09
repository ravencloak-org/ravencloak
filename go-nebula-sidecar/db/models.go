package db

import (
	"encoding/json"
	"time"
)

type NebulaCertificate struct {
	ID             string          `json:"id"`
	UserID         string          `json:"userId"`
	NodeType       string          `json:"nodeType"`
	NodeName       string          `json:"nodeName"`
	CertificatePEM string          `json:"certificatePem"`
	PrivateKeyPEM  string          `json:"privateKeyPem"`
	IPAddress      string          `json:"ipAddress"`
	Environment    *string         `json:"environment,omitempty"`
	Groups         []string        `json:"groups"`
	DeviceInfo     json.RawMessage `json:"deviceInfo,omitempty"`
	CreatedAt      time.Time       `json:"createdAt"`
	ExpiresAt      time.Time       `json:"expiresAt"`
	IsRevoked      bool            `json:"isRevoked"`
	RevokedAt      *time.Time      `json:"revokedAt,omitempty"`
	RevokeReason   *string         `json:"revokeReason,omitempty"`
	LastAccessed   *time.Time      `json:"lastAccessed,omitempty"`
}

type CachedToken struct {
	TokenHash string    `json:"tokenHash"`
	UserID    string    `json:"userId"`
	Roles     []string  `json:"roles"`
	CachedAt  time.Time `json:"cachedAt"`
	ExpiresAt time.Time `json:"expiresAt"`
}

type RevokedCertEntry struct {
	CertificateSerial string    `json:"certificateSerial"`
	RevokedAt         time.Time `json:"revokedAt"`
	Reason            string    `json:"reason"`
}
