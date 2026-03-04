package notifyhttp

import (
	"errors"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	notifyauth "github.com/hhm/shiori/shiori-notify/internal/auth"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
)

func (s *Server) resolveAPIUserID(c *gin.Context) (string, bool) {
	if s.auth != nil && s.auth.Enabled() {
		accessToken := parseBearerToken(c.GetHeader("Authorization"))
		userID, err := s.auth.ParseUserIDFromToken(accessToken)
		if err != nil {
			reason := "invalid_token"
			if errors.Is(err, notifyauth.ErrMissingToken) {
				reason = "missing_token"
			}
			metrics.IncAuthFailure("http", reason)
			s.writeJSON(c, http.StatusUnauthorized, gin.H{
				"code":    40101,
				"message": "invalid access token",
			})
			return "", false
		}
		return userID, true
	}

	userID := strings.TrimSpace(c.Query("userId"))
	if userID == "" {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40001,
			"message": "userId is required when auth disabled",
		})
		return "", false
	}
	return userID, true
}

func parseBearerToken(authorization string) string {
	trimmed := strings.TrimSpace(authorization)
	if trimmed == "" {
		return ""
	}
	parts := strings.Fields(trimmed)
	if len(parts) != 2 {
		return ""
	}
	if !strings.EqualFold(parts[0], "Bearer") {
		return ""
	}
	return parts[1]
}
