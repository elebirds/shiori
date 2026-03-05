package notifyhttp

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	notifyauth "github.com/hhm/shiori/shiori-notify/internal/auth"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
)

const (
	headerGatewayTS    = "X-Gateway-Ts"
	headerGatewaySign  = "X-Gateway-Sign"
	headerGatewayNonce = "X-Gateway-Nonce"
	headerUserID       = "X-User-Id"
	headerUserRoles    = "X-User-Roles"
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

func (s *Server) resolveGatewaySignedUserID(c *gin.Context) (int64, bool) {
	if strings.TrimSpace(s.cfg.GatewaySignSecret) == "" {
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
			"code":    40103,
			"message": "gateway sign is not configured",
		})
		return 0, false
	}
	userIDText := strings.TrimSpace(c.GetHeader(headerUserID))
	ts := strings.TrimSpace(c.GetHeader(headerGatewayTS))
	nonce := strings.TrimSpace(c.GetHeader(headerGatewayNonce))
	sign := strings.TrimSpace(c.GetHeader(headerGatewaySign))
	if userIDText == "" || ts == "" || nonce == "" || sign == "" {
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
			"code":    40104,
			"message": "missing gateway sign headers",
		})
		return 0, false
	}
	tsInt, err := strconv.ParseInt(ts, 10, 64)
	if err != nil {
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
			"code":    40105,
			"message": "invalid gateway timestamp",
		})
		return 0, false
	}
	maxSkew := s.cfg.GatewaySignMaxSkewSeconds
	if maxSkew <= 0 {
		maxSkew = 300
	}
	nowMs := time.Now().UnixMilli()
	if absInt64(nowMs-tsInt) > maxSkew*1000 {
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
			"code":    40106,
			"message": "gateway signature expired",
		})
		return 0, false
	}

	canonical := strings.Join([]string{
		strings.ToUpper(strings.TrimSpace(c.Request.Method)),
		c.Request.URL.Path,
		c.Request.URL.RawQuery,
		userIDText,
		strings.TrimSpace(c.GetHeader(headerUserRoles)),
		ts,
		nonce,
	}, "\n")
	expected := hmacSHA256Hex(strings.TrimSpace(s.cfg.GatewaySignSecret), canonical)
	if !hmac.Equal([]byte(expected), []byte(sign)) {
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
			"code":    40107,
			"message": "gateway signature invalid",
		})
		return 0, false
	}

	userID, parseErr := strconv.ParseInt(userIDText, 10, 64)
	if parseErr != nil || userID <= 0 {
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
			"code":    40108,
			"message": "invalid gateway user id",
		})
		return 0, false
	}
	return userID, true
}

func hmacSHA256Hex(secret, canonical string) string {
	mac := hmac.New(sha256.New, []byte(secret))
	mac.Write([]byte(canonical))
	return hex.EncodeToString(mac.Sum(nil))
}

func absInt64(v int64) int64 {
	if v < 0 {
		return -v
	}
	return v
}
