package api

import (
	"testing"
)

func TestIsValidNodeName(t *testing.T) {
	tests := []struct {
		name     string
		input    string
		expected bool
	}{
		{"simple", "my-node", true},
		{"with underscore", "my_node", true},
		{"with dots", "my.node.01", true},
		{"alphanumeric", "node123", true},
		{"uppercase", "MyNode", true},
		{"single char", "a", true},
		{"starts with number", "1node", true},
		{"empty", "", false},
		{"space", "my node", false},
		{"special chars", "my@node", false},
		{"starts with dash", "-node", false},
		{"starts with dot", ".node", false},
		{"unicode", "nöde", false},
		{"slash", "my/node", false},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			result := isValidNodeName(tt.input)
			if result != tt.expected {
				t.Errorf("isValidNodeName(%q) = %v, want %v", tt.input, result, tt.expected)
			}
		})
	}
}

func TestIsValidNodeType(t *testing.T) {
	tests := []struct {
		input    string
		expected bool
	}{
		{"laptop", true},
		{"ec2", true},
		{"server", false},
		{"", false},
		{"LAPTOP", false},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			result := isValidNodeType(tt.input)
			if result != tt.expected {
				t.Errorf("isValidNodeType(%q) = %v, want %v", tt.input, result, tt.expected)
			}
		})
	}
}

func TestIsValidEnvironment(t *testing.T) {
	tests := []struct {
		input    string
		expected bool
	}{
		{"uat", true},
		{"prod", true},
		{"staging", true},
		{"dev", true},
		{"test", false},
		{"", false},
		{"UAT", false},
	}

	for _, tt := range tests {
		t.Run(tt.input, func(t *testing.T) {
			result := isValidEnvironment(tt.input)
			if result != tt.expected {
				t.Errorf("isValidEnvironment(%q) = %v, want %v", tt.input, result, tt.expected)
			}
		})
	}
}
