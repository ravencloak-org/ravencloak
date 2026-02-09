package service

import (
	"context"
	"crypto/sha256"
	"encoding/binary"
	"fmt"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/db"
)

type AllocationService struct {
	repo *db.Repository
}

func NewAllocationService(repo *db.Repository) *AllocationService {
	return &AllocationService{repo: repo}
}

// AllocateIP determines the next available IP for a given node type.
// EC2: 192.168.100.10-99 (sequential per environment)
// Laptop: 192.168.100.100-199 (hash-based on user ID, with collision fallback)
func (s *AllocationService) AllocateIP(ctx context.Context, userID, nodeType, environment string) (string, error) {
	switch nodeType {
	case "ec2":
		return s.repo.GetNextAvailableIP(ctx, "ec2", environment)
	case "laptop":
		return s.allocateLaptopIP(ctx, userID)
	default:
		return "", fmt.Errorf("unknown node type: %s", nodeType)
	}
}

// allocateLaptopIP uses a hash of the user ID to pick a preferred IP,
// then falls back to sequential scan on collision.
func (s *AllocationService) allocateLaptopIP(ctx context.Context, userID string) (string, error) {
	preferred := hashUserToOctet(userID)
	ip := fmt.Sprintf("192.168.100.%d", preferred)

	exists, err := s.repo.CheckIPExists(ctx, ip)
	if err != nil {
		return "", fmt.Errorf("check IP: %w", err)
	}
	if !exists {
		return ip, nil
	}

	// Collision — fall back to sequential scan
	return s.repo.GetNextAvailableIP(ctx, "laptop", "")
}

// hashUserToOctet returns a value in [100, 199] based on user ID hash.
func hashUserToOctet(userID string) int {
	h := sha256.Sum256([]byte(userID))
	n := binary.BigEndian.Uint32(h[:4])
	return 100 + int(n%100)
}
