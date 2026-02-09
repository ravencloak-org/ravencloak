package api

import (
	"net/http"
	"strings"
	"sync"
	"time"

	"github.com/dsjkeeplearning/kos-auth-backend/go-nebula-sidecar/service"
	"github.com/gin-gonic/gin"
	"github.com/rs/zerolog/log"
)

// rateLimitEntry tracks per-user request counts for rate limiting.
type rateLimitEntry struct {
	count     int
	windowEnd time.Time
}

// AuthMiddleware validates JWT tokens via the token service and injects user context.
func AuthMiddleware(tokenService *service.TokenService) gin.HandlerFunc {
	var mu sync.Mutex
	limits := make(map[string]*rateLimitEntry)

	return func(c *gin.Context) {
		authHeader := c.GetHeader("Authorization")
		if authHeader == "" {
			respondError(c, http.StatusUnauthorized, "missing authorization header")
			c.Abort()
			return
		}

		token := strings.TrimPrefix(authHeader, "Bearer ")
		if token == authHeader {
			respondError(c, http.StatusUnauthorized, "invalid authorization format")
			c.Abort()
			return
		}

		result, err := tokenService.ValidateToken(c.Request.Context(), token)
		if err != nil {
			log.Error().Err(err).Msg("token validation error")
			respondError(c, http.StatusInternalServerError, "token validation failed")
			c.Abort()
			return
		}

		if !result.Valid {
			respondError(c, http.StatusUnauthorized, "invalid or expired token")
			c.Abort()
			return
		}

		// Rate limiting: 10 requests per hour per user
		mu.Lock()
		entry, exists := limits[result.UserID]
		now := time.Now()
		if !exists || now.After(entry.windowEnd) {
			entry = &rateLimitEntry{count: 0, windowEnd: now.Add(time.Hour)}
			limits[result.UserID] = entry
		}
		entry.count++
		currentCount := entry.count
		mu.Unlock()

		if currentCount > 10 {
			respondError(c, http.StatusTooManyRequests, "rate limit exceeded (10 requests per hour)")
			c.Abort()
			return
		}

		// Inject user info into context
		c.Set("userID", result.UserID)
		c.Set("roles", result.Roles)
		c.Next()
	}
}

// RequireRole checks that the authenticated user has one of the specified roles.
func RequireRole(roles ...string) gin.HandlerFunc {
	return func(c *gin.Context) {
		userRoles, exists := c.Get("roles")
		if !exists {
			respondError(c, http.StatusForbidden, "no roles found")
			c.Abort()
			return
		}

		roleList, ok := userRoles.([]string)
		if !ok {
			respondError(c, http.StatusForbidden, "invalid roles format")
			c.Abort()
			return
		}

		for _, required := range roles {
			for _, userRole := range roleList {
				if strings.EqualFold(required, userRole) {
					c.Next()
					return
				}
			}
		}

		respondError(c, http.StatusForbidden, "insufficient permissions")
		c.Abort()
	}
}
