package api

import (
	"github.com/gin-gonic/gin"
)

type ErrorResponse struct {
	Error     string `json:"error"`
	RequestID string `json:"requestId,omitempty"`
}

type HealthResponse struct {
	Status      string `json:"status"`
	Database    string `json:"database"`
	AuthBackend string `json:"authBackend"`
}

func respondError(c *gin.Context, status int, message string) {
	requestID, _ := c.Get("requestID")
	rid, _ := requestID.(string)
	c.JSON(status, ErrorResponse{
		Error:     message,
		RequestID: rid,
	})
}
