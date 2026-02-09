package service

import (
	"fmt"
	"testing"
)

func TestHashUserToOctet(t *testing.T) {
	tests := []struct {
		userID string
	}{
		{"user-123"},
		{"user-456"},
		{"admin@example.com"},
		{"very-long-user-id-that-is-definitely-more-than-32-bytes-long"},
		{""},
	}

	for _, tt := range tests {
		t.Run(tt.userID, func(t *testing.T) {
			result := hashUserToOctet(tt.userID)
			if result < 100 || result > 199 {
				t.Errorf("hashUserToOctet(%q) = %d, want [100, 199]", tt.userID, result)
			}
		})
	}
}

func TestHashUserToOctet_Deterministic(t *testing.T) {
	a := hashUserToOctet("user-123")
	b := hashUserToOctet("user-123")
	if a != b {
		t.Errorf("same user ID should produce same octet: %d != %d", a, b)
	}
}

func TestHashUserToOctet_Distribution(t *testing.T) {
	// Check that different users get spread across the range
	seen := make(map[int]bool)
	for i := 0; i < 200; i++ {
		octet := hashUserToOctet(fmt.Sprintf("user-%d", i))
		seen[octet] = true
	}

	// With 200 users and 100 slots, we expect reasonable coverage
	if len(seen) < 50 {
		t.Errorf("poor distribution: only %d unique octets from 200 users", len(seen))
	}
}
