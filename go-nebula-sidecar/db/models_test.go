package db

import (
	"encoding/json"
	"testing"
)

func TestParseDeviceInfo(t *testing.T) {
	tests := []struct {
		name     string
		input    json.RawMessage
		expected map[string]string
	}{
		{
			name:  "valid device info",
			input: json.RawMessage(`{"os":"macos","arch":"arm64","hostname":"macbook"}`),
			expected: map[string]string{
				"os":       "macos",
				"arch":     "arm64",
				"hostname": "macbook",
			},
		},
		{
			name:     "nil input",
			input:    nil,
			expected: nil,
		},
		{
			name:     "invalid JSON",
			input:    json.RawMessage(`not json`),
			expected: nil,
		},
		{
			name:     "empty object",
			input:    json.RawMessage(`{}`),
			expected: map[string]string{},
		},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := ParseDeviceInfo(tt.input)
			if tt.expected == nil {
				if result != nil {
					t.Errorf("expected nil, got %v", result)
				}
				return
			}
			if len(result) != len(tt.expected) {
				t.Errorf("expected %d entries, got %d", len(tt.expected), len(result))
				return
			}
			for k, v := range tt.expected {
				if result[k] != v {
					t.Errorf("key %s: expected %s, got %s", k, v, result[k])
				}
			}
		})
	}
}
