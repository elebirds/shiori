package notifyhttp

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
)

func (s *Server) handleReplayEvents(c *gin.Context) {
	if s.replayStore == nil {
		metrics.IncReplayQuery("api", "store_unavailable")
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50300,
			"message": "replay store unavailable",
		})
		return
	}

	userID := strings.TrimSpace(c.Query("userId"))
	if userID == "" {
		metrics.IncReplayQuery("api", "invalid_param")
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40001,
			"message": "userId is required",
		})
		return
	}

	afterEventID := strings.TrimSpace(c.Query("afterEventId"))
	limit, ok := s.parseReplayLimit(c.Query("limit"))
	if !ok {
		metrics.IncReplayQuery("api", "invalid_param")
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40002,
			"message": "limit must be a positive integer",
		})
		return
	}

	items, nextEventID, hasMore := s.replayStore.List(userID, afterEventID, limit)
	metrics.IncReplayQuery("api", "success")
	metrics.AddReplayEvents("api", len(items))

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"userId":       userID,
			"afterEventId": afterEventID,
			"limit":        limit,
			"items":        items,
			"nextEventId":  nextEventID,
			"hasMore":      hasMore,
		},
	})
}

func (s *Server) parseReplayLimit(raw string) (int, bool) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return s.normalizeReplayLimit(s.cfg.ReplayDefaultLimit), true
	}

	n, err := strconv.Atoi(trimmed)
	if err != nil || n <= 0 {
		return 0, false
	}
	return s.normalizeReplayLimit(n), true
}

func (s *Server) normalizeReplayLimit(limit int) int {
	if limit <= 0 {
		limit = s.cfg.ReplayDefaultLimit
	}
	maxLimit := s.cfg.ReplayMaxLimit
	if maxLimit <= 0 {
		maxLimit = limit
	}
	if limit > maxLimit {
		return maxLimit
	}
	return limit
}
