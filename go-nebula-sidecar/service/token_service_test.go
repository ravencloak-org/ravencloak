package service

import (
	"testing"
)

func TestIsValidTokenFormat(t *testing.T) {
	tests := []struct {
		name     string
		token    string
		expected bool
	}{
		{"valid JWT", "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ1c2VyMSJ9.signature", true},
		{"empty string", "", false},
		{"whitespace only", "   ", false},
		{"one part", "singletoken", false},
		{"two parts", "header.payload", false},
		{"three parts", "a.b.c", true},
		{"four parts", "a.b.c.d", false},
		{"with whitespace", " a.b.c ", true},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := isValidTokenFormat(tt.token)
			if result != tt.expected {
				t.Errorf("isValidTokenFormat(%q) = %v, want %v", tt.token, result, tt.expected)
			}
		})
	}
}

func TestHashToken(t *testing.T) {
	// Deterministic
	hash1 := hashToken("my-test-token")
	hash2 := hashToken("my-test-token")
	if hash1 != hash2 {
		t.Error("same token should produce same hash")
	}

	// Different tokens produce different hashes
	hash3 := hashToken("different-token")
	if hash1 == hash3 {
		t.Error("different tokens should produce different hashes")
	}

	// Hash is hex-encoded SHA-256 (64 chars)
	if len(hash1) != 64 {
		t.Errorf("expected hash length 64, got %d", len(hash1))
	}
}
