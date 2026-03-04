package notifyhttp

import (
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
)

func (s *Server) handleReplayEvents(c *gin.Context) {
	if s.eventStore == nil {
		metrics.IncReplayQuery("api", "store_unavailable")
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50300,
			"message": "replay store unavailable",
		})
		return
	}

	userID, ok := s.resolveAPIUserID(c)
	if !ok {
		metrics.IncReplayQuery("api", "auth_failed")
		return
	}

	afterEventID := strings.TrimSpace(c.Query("afterEventId"))
	limit, valid := s.parseReplayLimit(c.Query("limit"))
	if !valid {
		metrics.IncReplayQuery("api", "invalid_param")
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40002,
			"message": "limit must be a positive integer",
		})
		return
	}

	items, nextEventID, hasMore, err := s.eventStore.List(userID, afterEventID, limit)
	if err != nil {
		metrics.IncReplayQuery("api", "store_error")
		s.logger.Warn().Err(err).
			Str("userId", userID).
			Str("afterEventId", afterEventID).
			Int("limit", limit).
			Msg("通知补偿查询失败")
		s.writeJSON(c, http.StatusInternalServerError, gin.H{
			"code":    50001,
			"message": "query notify events failed",
		})
		return
	}

	metrics.IncReplayQuery("api", "success")
	metrics.AddReplayEvents("api", len(items))

	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
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
