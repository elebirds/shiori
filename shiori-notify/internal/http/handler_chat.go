package notifyhttp

import (
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/chat"
)

type readConversationRequest struct {
	LastReadMsgID int64 `json:"lastReadMsgId"`
}

type startConversationRequest struct {
	ChatTicket string `json:"chatTicket"`
}

func (s *Server) handleListConversations(c *gin.Context) {
	if s.chat == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	cursor, valid := parseInt64Query(c.Query("cursor"))
	if !valid {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40010,
			"message": "cursor must be a valid int64",
		})
		return
	}
	limit, limitValid := parseIntQuery(c.Query("limit"))
	if !limitValid {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "limit must be a positive integer",
		})
		return
	}
	items, hasMore, err := s.chat.ListConversations(userID, cursor, limit)
	if err != nil {
		s.writeChatError(c, err, "list conversations failed")
		return
	}

	responseItems := make([]gin.H, 0, len(items))
	nextCursor := int64(0)
	for i := range items {
		item := items[i]
		entry := gin.H{
			"conversationId": item.Conversation.ID,
			"listingId":      item.Conversation.ListingID,
			"buyerId":        item.Conversation.BuyerID,
			"sellerId":       item.Conversation.SellerID,
			"hasUnread":      item.HasUnread,
			"updatedAt":      item.Conversation.UpdatedAt.UTC().Format(time.RFC3339Nano),
		}
		if item.LastMessage != nil {
			entry["lastMessage"] = gin.H{
				"messageId":   item.LastMessage.ID,
				"senderId":    item.LastMessage.SenderID,
				"receiverId":  item.LastMessage.ReceiverID,
				"clientMsgId": item.LastMessage.ClientMsgID,
				"content":     item.LastMessage.Content,
				"createdAt":   item.LastMessage.CreatedAt.UTC().Format(time.RFC3339Nano),
			}
		}
		responseItems = append(responseItems, entry)
		nextCursor = item.Conversation.ID
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items":      responseItems,
			"hasMore":    hasMore,
			"nextCursor": nextCursor,
		},
	})
}

func (s *Server) handleListMessages(c *gin.Context) {
	if s.chat == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	conversationID, err := strconv.ParseInt(strings.TrimSpace(c.Param("conversationId")), 10, 64)
	if err != nil || conversationID <= 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40012,
			"message": "conversationId must be a positive integer",
		})
		return
	}
	before, beforeValid := parseInt64Query(c.Query("before"))
	if !beforeValid {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40013,
			"message": "before must be a valid int64",
		})
		return
	}
	limit, limitValid := parseIntQuery(c.Query("limit"))
	if !limitValid {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "limit must be a positive integer",
		})
		return
	}

	items, hasMore, listErr := s.chat.ListMessages(userID, conversationID, before, limit)
	if listErr != nil {
		s.writeChatError(c, listErr, "list messages failed")
		return
	}

	responseItems := make([]gin.H, 0, len(items))
	nextBefore := int64(0)
	for i := range items {
		item := items[i]
		responseItems = append(responseItems, gin.H{
			"messageId":      item.ID,
			"conversationId": item.ConversationID,
			"senderId":       item.SenderID,
			"receiverId":     item.ReceiverID,
			"clientMsgId":    item.ClientMsgID,
			"content":        item.Content,
			"createdAt":      item.CreatedAt.UTC().Format(time.RFC3339Nano),
		})
		nextBefore = item.ID
	}

	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items":      responseItems,
			"hasMore":    hasMore,
			"nextBefore": nextBefore,
		},
	})
}

func (s *Server) handleReadConversation(c *gin.Context) {
	if s.chat == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	conversationID, err := strconv.ParseInt(strings.TrimSpace(c.Param("conversationId")), 10, 64)
	if err != nil || conversationID <= 0 {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40012,
			"message": "conversationId must be a positive integer",
		})
		return
	}
	var request readConversationRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40014,
			"message": "invalid request body",
		})
		return
	}

	lastReadMsgID, readErr := s.chat.Read(userID, conversationID, request.LastReadMsgID)
	if readErr != nil {
		s.writeChatError(c, readErr, "update read state failed")
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"conversationId": conversationID,
			"lastReadMsgId":  lastReadMsgID,
		},
	})
}

func (s *Server) handleStartConversation(c *gin.Context) {
	if s.chat == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}

	var request startConversationRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil || strings.TrimSpace(request.ChatTicket) == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40014,
			"message": "invalid request body",
		})
		return
	}

	conversation, claims, err := s.chat.Start(userID, request.ChatTicket)
	if err != nil {
		s.writeChatError(c, err, "start conversation failed")
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"conversationId": conversation.ID,
			"listingId":      conversation.ListingID,
			"buyerId":        claims.BuyerID,
			"sellerId":       claims.SellerID,
			"jti":            claims.JTI,
			"expireAt":       claims.ExpiresAt.UTC().Format(time.RFC3339Nano),
		},
	})
}

func (s *Server) handleChatSummary(c *gin.Context) {
	if s.chat == nil {
		c.JSON(http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	summary, err := s.chat.Summary(userID)
	if err != nil {
		s.writeChatError(c, err, "query chat summary failed")
		return
	}
	c.JSON(http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"unreadConversationCount": summary.UnreadConversationCount,
			"unreadMessageCount":      summary.UnreadMessageCount,
		},
	})
}

func (s *Server) resolveChatUserID(c *gin.Context) (int64, bool) {
	userIDText, ok := s.resolveAPIUserID(c)
	if !ok {
		return 0, false
	}
	userID, err := strconv.ParseInt(strings.TrimSpace(userIDText), 10, 64)
	if err != nil || userID <= 0 {
		c.JSON(http.StatusUnauthorized, gin.H{
			"code":    40102,
			"message": "invalid user id in token",
		})
		return 0, false
	}
	return userID, true
}

func parseInt64Query(raw string) (int64, bool) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return 0, true
	}
	parsed, err := strconv.ParseInt(trimmed, 10, 64)
	if err != nil || parsed < 0 {
		return 0, false
	}
	return parsed, true
}

func parseIntQuery(raw string) (int, bool) {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return 0, true
	}
	parsed, err := strconv.Atoi(trimmed)
	if err != nil || parsed <= 0 {
		return 0, false
	}
	return parsed, true
}

func (s *Server) writeChatError(c *gin.Context, err error, fallbackMessage string) {
	switch {
	case errors.Is(err, chat.ErrForbidden):
		c.JSON(http.StatusForbidden, gin.H{
			"code":    40301,
			"message": "conversation access forbidden",
		})
	case errors.Is(err, chat.ErrConversationAbsent):
		c.JSON(http.StatusNotFound, gin.H{
			"code":    40401,
			"message": "conversation not found",
		})
	case errors.Is(err, chat.ErrInvalidArgument):
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40015,
			"message": "invalid chat request",
		})
	default:
		s.logger.Warn().Err(err).Msg(fallbackMessage)
		c.JSON(http.StatusInternalServerError, gin.H{
			"code":    50010,
			"message": fallbackMessage,
		})
	}
}
