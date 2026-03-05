package notifyhttp

import (
	"errors"
	"net/http"
	"strconv"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/chat"
)

func (s *Server) handleInternalGetConversation(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveGatewaySignedUserID(c)
	if !ok {
		return
	}
	conversationID, err := strconv.ParseInt(strings.TrimSpace(c.Param("conversationId")), 10, 64)
	if err != nil || conversationID <= 0 {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40012,
			"message": "conversationId must be a positive integer",
		})
		return
	}
	conversation, getErr := s.chat.GetConversationForUser(userID, conversationID)
	if getErr != nil {
		switch {
		case errors.Is(getErr, chat.ErrConversationAbsent):
			s.writeJSON(c, http.StatusNotFound, gin.H{
				"code":    40401,
				"message": "conversation not found",
			})
		case errors.Is(getErr, chat.ErrForbidden):
			s.writeJSON(c, http.StatusForbidden, gin.H{
				"code":    40301,
				"message": "conversation access forbidden",
			})
		default:
			s.writeJSON(c, http.StatusInternalServerError, gin.H{
				"code":    50010,
				"message": "query conversation failed",
			})
		}
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"conversationId": conversation.ID,
			"listingId":      conversation.ListingID,
			"buyerId":        conversation.BuyerID,
			"sellerId":       conversation.SellerID,
		},
	})
}
