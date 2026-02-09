package api

import (
	"regexp"
)

// Request types

type GenerateCertRequest struct {
	NodeType   string `json:"nodeType" binding:"required"`
	NodeName   string `json:"nodeName" binding:"required"`
	DeviceInfo string `json:"deviceInfo"`
}

type GenerateEC2CertRequest struct {
	Environment  string `json:"environment" binding:"required"`
	InstanceName string `json:"instanceName" binding:"required"`
	UserID       string `json:"userId"`
}

type RevokeCertRequest struct {
	NodeName string `json:"nodeName" binding:"required"`
	Reason   string `json:"reason" binding:"required"`
}

// Response types

type GenerateCertResponse struct {
	Certificate   string            `json:"certificate"`
	PrivateKey    string            `json:"privateKey"`
	Config        string            `json:"config"`
	LighthouseIP  string            `json:"lighthouseIp"`
	LighthousePort int              `json:"lighthousePort"`
	StaticHostMap map[string][]string `json:"staticHostMap"`
	IP            string            `json:"ip"`
	ExpiresAt     string            `json:"expiresAt"`
}

type CertListResponse struct {
	Certificates []CertSummary `json:"certificates"`
}

type CertSummary struct {
	NodeName  string  `json:"nodeName"`
	NodeType  string  `json:"nodeType"`
	IP        string  `json:"ip"`
	CreatedAt string  `json:"createdAt"`
	ExpiresAt string  `json:"expiresAt"`
	IsRevoked bool    `json:"isRevoked"`
}

type RevokeCertResponse struct {
	Success   bool   `json:"success"`
	RevokedAt string `json:"revokedAt"`
}

// Validation

var validNodeName = regexp.MustCompile(`^[a-zA-Z0-9][a-zA-Z0-9._-]*$`)

func isValidNodeName(name string) bool {
	return len(name) >= 1 && len(name) <= 255 && validNodeName.MatchString(name)
}

func isValidNodeType(t string) bool {
	return t == "laptop" || t == "ec2"
}

func isValidEnvironment(env string) bool {
	return env == "uat" || env == "prod" || env == "staging" || env == "dev"
}
