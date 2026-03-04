package notifyhttp

import (
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
)

func (s *Server) handleMarkRead(c *gin.Context) {
	if s.eventStore == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50300,
			"message": "store unavailable",
		})
		return
	}

	userID, ok := s.resolveAPIUserID(c)
	if !ok {
		return
	}

	eventID := strings.TrimSpace(c.Param("eventId"))
	if eventID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40003,
			"message": "eventId is required",
		})
		return
	}

	marked, err := s.eventStore.MarkRead(userID, eventID)
	if err != nil {
		metrics.IncReadOp("mark_read", "error")
		s.logger.Warn().Err(err).
			Str("userId", userID).
			Str("eventId", eventID).
			Msg("单条已读失败")
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    50002,
			"message": "mark read failed",
		})
		return
	}
	metrics.IncReadOp("mark_read", "success")
	if marked {
		metrics.AddReadMarked("mark_read", 1)
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"eventId": eventID,
			"marked":  marked,
		},
	})
}

func (s *Server) handleMarkAllRead(c *gin.Context) {
	if s.eventStore == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50300,
			"message": "store unavailable",
		})
		return
	}

	userID, ok := s.resolveAPIUserID(c)
	if !ok {
		return
	}

	affected, err := s.eventStore.MarkAllRead(userID)
	if err != nil {
		metrics.IncReadOp("read_all", "error")
		s.logger.Warn().Err(err).
			Str("userId", userID).
			Msg("全部已读失败")
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    50003,
			"message": "mark all read failed",
		})
		return
	}
	metrics.IncReadOp("read_all", "success")
	metrics.AddReadMarked("read_all", int(affected))

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"affected": affected,
		},
	})
}

func (s *Server) handleSummary(c *gin.Context) {
	if s.eventStore == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50300,
			"message": "store unavailable",
		})
		return
	}

	userID, ok := s.resolveAPIUserID(c)
	if !ok {
		return
	}

	unreadCount, err := s.eventStore.UnreadCount(userID)
	if err != nil {
		s.logger.Warn().Err(err).
			Str("userId", userID).
			Msg("查询未读计数失败")
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    50004,
			"message": "query unread summary failed",
		})
		return
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"unreadCount": unreadCount,
		},
	})
}
