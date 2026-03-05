package notifyhttp

import (
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
)

type readConversationRequest struct {
	LastReadMsgID int64 `json:"lastReadMsgId"`
}

type startConversationRequest struct {
	ChatTicket string `json:"chatTicket"`
}

type createReportRequest struct {
	ConversationID int64  `json:"conversationId"`
	MessageID      *int64 `json:"messageId"`
	TargetUserID   *int64 `json:"targetUserId"`
	Reason         string `json:"reason"`
}

type handleReportRequest struct {
	Status string `json:"status"`
	Remark string `json:"remark"`
}

type forbiddenWordRequest struct {
	Word      string `json:"word"`
	MatchType string `json:"matchType"`
	Policy    string `json:"policy"`
	Mask      string `json:"mask"`
	Status    string `json:"status"`
}

func (s *Server) handleListConversations(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
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
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40010,
			"message": "cursor must be a valid int64",
		})
		return
	}
	limit, limitValid := parseIntQuery(c.Query("limit"))
	if !limitValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
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

	s.writeJSON(c, http.StatusOK, gin.H{
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
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
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
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40012,
			"message": "conversationId must be a positive integer",
		})
		return
	}
	beforeText := c.Query("before")
	afterSeqText := c.Query("afterSeq")
	hasBefore := strings.TrimSpace(beforeText) != ""
	hasAfterSeq := strings.TrimSpace(afterSeqText) != ""
	if hasBefore && hasAfterSeq {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40023,
			"message": "before and afterSeq cannot be used together",
		})
		return
	}
	before, beforeValid := parseInt64Query(beforeText)
	if !beforeValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40013,
			"message": "before must be a valid int64",
		})
		return
	}
	afterSeq, afterSeqValid := parseInt64Query(afterSeqText)
	if !afterSeqValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40022,
			"message": "afterSeq must be a valid int64",
		})
		return
	}
	limit, limitValid := parseIntQuery(c.Query("limit"))
	if !limitValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "limit must be a positive integer",
		})
		return
	}

	if hasAfterSeq {
		items, hasMore, listErr := s.chat.ListMessagesAfter(userID, conversationID, afterSeq, limit)
		if listErr != nil {
			metrics.IncChatCompensationQuery("error")
			s.writeChatError(c, listErr, "list messages failed")
			return
		}

		responseItems := make([]gin.H, 0, len(items))
		nextAfterSeq := afterSeq
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
			nextAfterSeq = item.ID
		}
		if len(items) > 0 {
			metrics.IncChatCompensationQuery("hit")
			metrics.AddChatCompensationMessages(len(items))
		} else {
			metrics.IncChatCompensationQuery("miss")
		}
		s.writeJSON(c, http.StatusOK, gin.H{
			"code":    0,
			"message": "success",
			"data": gin.H{
				"items":        responseItems,
				"hasMore":      hasMore,
				"nextAfterSeq": nextAfterSeq,
			},
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

	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items":      responseItems,
			"hasMore":    hasMore,
			"nextBefore": nextBefore,
		},
	})
}

func (s *Server) handleGetConversation(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
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
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40012,
			"message": "conversationId must be a positive integer",
		})
		return
	}
	conversation, getErr := s.chat.GetConversationForUser(userID, conversationID)
	if getErr != nil {
		s.writeChatError(c, getErr, "query conversation failed")
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

func (s *Server) handleReadConversation(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
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
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40012,
			"message": "conversationId must be a positive integer",
		})
		return
	}
	var request readConversationRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
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
	s.writeJSON(c, http.StatusOK, gin.H{
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
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
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
		s.writeJSON(c, http.StatusBadRequest, gin.H{
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
	s.writeJSON(c, http.StatusOK, gin.H{
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
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
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
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"unreadConversationCount": summary.UnreadConversationCount,
			"unreadMessageCount":      summary.UnreadMessageCount,
		},
	})
}

func (s *Server) handleBlockUser(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	targetUserID, err := strconv.ParseInt(strings.TrimSpace(c.Param("targetUserId")), 10, 64)
	if err != nil || targetUserID <= 0 {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40017,
			"message": "targetUserId must be a positive integer",
		})
		return
	}
	if err := s.chat.Block(userID, targetUserID); err != nil {
		s.writeChatError(c, err, "block user failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"targetUserId": targetUserID,
			"blocked":      true,
		},
	})
}

func (s *Server) handleUnblockUser(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	targetUserID, err := strconv.ParseInt(strings.TrimSpace(c.Param("targetUserId")), 10, 64)
	if err != nil || targetUserID <= 0 {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40017,
			"message": "targetUserId must be a positive integer",
		})
		return
	}
	if err := s.chat.Unblock(userID, targetUserID); err != nil {
		s.writeChatError(c, err, "unblock user failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"targetUserId": targetUserID,
			"blocked":      false,
		},
	})
}

func (s *Server) handleListBlocks(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	items, err := s.chat.ListBlocks(userID)
	if err != nil {
		s.writeChatError(c, err, "list blocks failed")
		return
	}
	responseItems := make([]gin.H, 0, len(items))
	for i := range items {
		item := items[i]
		responseItems = append(responseItems, gin.H{
			"blockerUserId": item.BlockerUserID,
			"targetUserId":  item.TargetUserID,
			"createdAt":     item.CreatedAt.UTC().Format(time.RFC3339Nano),
		})
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items": responseItems,
		},
	})
}

func (s *Server) handleCreateReport(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	userID, ok := s.resolveChatUserID(c)
	if !ok {
		return
	}
	var request createReportRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40014,
			"message": "invalid request body",
		})
		return
	}
	report, err := s.chat.Report(userID, chat.CreateReportRequest{
		ConversationID: request.ConversationID,
		MessageID:      request.MessageID,
		TargetUserID:   request.TargetUserID,
		Reason:         request.Reason,
	})
	if err != nil {
		s.writeChatError(c, err, "create report failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"id":             report.ID,
			"reporterUserId": report.ReporterUserID,
			"targetUserId":   report.TargetUserID,
			"conversationId": report.ConversationID,
			"messageId":      report.MessageID,
			"reason":         report.Reason,
			"status":         report.Status,
			"createdAt":      report.CreatedAt.UTC().Format(time.RFC3339Nano),
			"updatedAt":      report.UpdatedAt.UTC().Format(time.RFC3339Nano),
		},
	})
}

func (s *Server) handleAdminListReports(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	if _, ok := s.resolveGatewaySignedAdmin(c); !ok {
		return
	}
	page, pageValid := parseIntQuery(c.Query("page"))
	if !pageValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "page must be a positive integer",
		})
		return
	}
	size, sizeValid := parseIntQuery(c.Query("size"))
	if !sizeValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "size must be a positive integer",
		})
		return
	}
	if page <= 0 {
		page = 1
	}
	if size <= 0 {
		size = 20
	}
	items, total, err := s.chat.ListReports(c.Query("status"), page, size)
	if err != nil {
		s.writeChatError(c, err, "list reports failed")
		return
	}
	responseItems := make([]gin.H, 0, len(items))
	for i := range items {
		item := items[i]
		responseItems = append(responseItems, gin.H{
			"id":             item.ID,
			"reporterUserId": item.ReporterUserID,
			"targetUserId":   item.TargetUserID,
			"conversationId": item.ConversationID,
			"messageId":      item.MessageID,
			"reason":         item.Reason,
			"status":         item.Status,
			"remark":         item.Remark,
			"handledBy":      item.HandledBy,
			"handledAt":      item.HandledAt,
			"createdAt":      item.CreatedAt.UTC().Format(time.RFC3339Nano),
			"updatedAt":      item.UpdatedAt.UTC().Format(time.RFC3339Nano),
		})
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items": responseItems,
			"total": total,
			"page":  page,
			"size":  size,
		},
	})
}

func (s *Server) handleAdminHandleReport(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	operatorUserID, ok := s.resolveGatewaySignedAdmin(c)
	if !ok {
		return
	}
	reportID, err := strconv.ParseInt(strings.TrimSpace(c.Param("reportId")), 10, 64)
	if err != nil || reportID <= 0 {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40018,
			"message": "reportId must be a positive integer",
		})
		return
	}
	var request handleReportRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40014,
			"message": "invalid request body",
		})
		return
	}
	if err := s.chat.HandleReport(reportID, operatorUserID, request.Status, request.Remark); err != nil {
		s.writeChatError(c, err, "handle report failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"reportId": reportID,
			"status":   strings.TrimSpace(strings.ToUpper(request.Status)),
		},
	})
}

func (s *Server) handleAdminListBlocks(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	if _, ok := s.resolveGatewaySignedAdmin(c); !ok {
		return
	}
	page, pageValid := parseIntQuery(c.Query("page"))
	if !pageValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "page must be a positive integer",
		})
		return
	}
	size, sizeValid := parseIntQuery(c.Query("size"))
	if !sizeValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40011,
			"message": "size must be a positive integer",
		})
		return
	}
	if page <= 0 {
		page = 1
	}
	if size <= 0 {
		size = 20
	}
	blockerUserID, blockerValid := parseInt64Query(c.Query("blockerUserId"))
	if !blockerValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40019,
			"message": "blockerUserId must be a valid int64",
		})
		return
	}
	targetUserID, targetValid := parseInt64Query(c.Query("targetUserId"))
	if !targetValid {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40020,
			"message": "targetUserId must be a valid int64",
		})
		return
	}
	items, total, err := s.chat.ListBlocksForAdmin(chat.BlockQuery{
		BlockerUserID: blockerUserID,
		TargetUserID:  targetUserID,
		Page:          page,
		Size:          size,
	})
	if err != nil {
		s.writeChatError(c, err, "list admin blocks failed")
		return
	}
	responseItems := make([]gin.H, 0, len(items))
	for i := range items {
		item := items[i]
		responseItems = append(responseItems, gin.H{
			"blockerUserId": item.BlockerUserID,
			"targetUserId":  item.TargetUserID,
			"createdAt":     item.CreatedAt.UTC().Format(time.RFC3339Nano),
		})
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items": responseItems,
			"total": total,
			"page":  page,
			"size":  size,
		},
	})
}

func (s *Server) handleAdminListForbiddenWords(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	if _, ok := s.resolveGatewaySignedAdmin(c); !ok {
		return
	}
	includeDisabled := parseBoolQuery(c.Query("includeDisabled"))
	items, err := s.chat.ListForbiddenWords(includeDisabled)
	if err != nil {
		s.writeChatError(c, err, "list forbidden words failed")
		return
	}
	responseItems := make([]gin.H, 0, len(items))
	for i := range items {
		item := items[i]
		responseItems = append(responseItems, gin.H{
			"id":        item.ID,
			"word":      item.Word,
			"matchType": item.MatchType,
			"policy":    item.Policy,
			"mask":      item.Mask,
			"status":    item.Status,
			"createdBy": item.CreatedBy,
			"updatedBy": item.UpdatedBy,
			"createdAt": item.CreatedAt.UTC().Format(time.RFC3339Nano),
			"updatedAt": item.UpdatedAt.UTC().Format(time.RFC3339Nano),
		})
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"items": responseItems,
		},
	})
}

func (s *Server) handleAdminCreateForbiddenWord(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	operatorUserID, ok := s.resolveGatewaySignedAdmin(c)
	if !ok {
		return
	}
	var request forbiddenWordRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40014,
			"message": "invalid request body",
		})
		return
	}
	item, err := s.chat.UpsertForbiddenWord(operatorUserID, chat.UpsertForbiddenWordRequest{
		Word:      request.Word,
		MatchType: request.MatchType,
		Policy:    request.Policy,
		Mask:      request.Mask,
		Status:    request.Status,
	})
	if err != nil {
		s.writeChatError(c, err, "create forbidden word failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"id":        item.ID,
			"word":      item.Word,
			"matchType": item.MatchType,
			"policy":    item.Policy,
			"mask":      item.Mask,
			"status":    item.Status,
			"createdBy": item.CreatedBy,
			"updatedBy": item.UpdatedBy,
			"createdAt": item.CreatedAt.UTC().Format(time.RFC3339Nano),
			"updatedAt": item.UpdatedAt.UTC().Format(time.RFC3339Nano),
		},
	})
}

func (s *Server) handleAdminUpdateForbiddenWord(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	operatorUserID, ok := s.resolveGatewaySignedAdmin(c)
	if !ok {
		return
	}
	ruleID, err := strconv.ParseInt(strings.TrimSpace(c.Param("ruleId")), 10, 64)
	if err != nil || ruleID <= 0 {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40021,
			"message": "ruleId must be a positive integer",
		})
		return
	}
	var request forbiddenWordRequest
	if bindErr := c.ShouldBindJSON(&request); bindErr != nil {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40014,
			"message": "invalid request body",
		})
		return
	}
	item, err := s.chat.UpdateForbiddenWord(ruleID, operatorUserID, chat.UpsertForbiddenWordRequest{
		Word:      request.Word,
		MatchType: request.MatchType,
		Policy:    request.Policy,
		Mask:      request.Mask,
		Status:    request.Status,
	})
	if err != nil {
		s.writeChatError(c, err, "update forbidden word failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"id":        item.ID,
			"word":      item.Word,
			"matchType": item.MatchType,
			"policy":    item.Policy,
			"mask":      item.Mask,
			"status":    item.Status,
			"createdBy": item.CreatedBy,
			"updatedBy": item.UpdatedBy,
			"createdAt": item.CreatedAt.UTC().Format(time.RFC3339Nano),
			"updatedAt": item.UpdatedAt.UTC().Format(time.RFC3339Nano),
		},
	})
}

func (s *Server) handleAdminDeleteForbiddenWord(c *gin.Context) {
	if s.chat == nil {
		s.writeJSON(c, http.StatusServiceUnavailable, gin.H{
			"code":    50301,
			"message": "chat service unavailable",
		})
		return
	}
	operatorUserID, ok := s.resolveGatewaySignedAdmin(c)
	if !ok {
		return
	}
	ruleID, err := strconv.ParseInt(strings.TrimSpace(c.Param("ruleId")), 10, 64)
	if err != nil || ruleID <= 0 {
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40021,
			"message": "ruleId must be a positive integer",
		})
		return
	}
	if err := s.chat.DeleteForbiddenWord(ruleID, operatorUserID); err != nil {
		s.writeChatError(c, err, "delete forbidden word failed")
		return
	}
	s.writeJSON(c, http.StatusOK, gin.H{
		"code":    0,
		"message": "success",
		"data": gin.H{
			"ruleId": ruleID,
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
		s.writeJSON(c, http.StatusUnauthorized, gin.H{
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

func parseBoolQuery(raw string) bool {
	trimmed := strings.TrimSpace(strings.ToLower(raw))
	return trimmed == "1" || trimmed == "true" || trimmed == "yes"
}

func (s *Server) writeChatError(c *gin.Context, err error, fallbackMessage string) {
	var rateLimited *chat.ErrRateLimited
	switch {
	case errors.Is(err, chat.ErrForbidden):
		s.writeJSON(c, http.StatusForbidden, gin.H{
			"code":    40301,
			"message": "conversation access forbidden",
		})
	case errors.Is(err, chat.ErrConversationAbsent):
		s.writeJSON(c, http.StatusNotFound, gin.H{
			"code":    40401,
			"message": "conversation not found",
		})
	case errors.Is(err, chat.ErrReportAbsent):
		s.writeJSON(c, http.StatusNotFound, gin.H{
			"code":    40402,
			"message": "report not found",
		})
	case errors.Is(err, chat.ErrForbiddenWordRule):
		s.writeJSON(c, http.StatusNotFound, gin.H{
			"code":    40403,
			"message": "forbidden word rule not found",
		})
	case errors.Is(err, chat.ErrInvalidArgument):
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40015,
			"message": "invalid chat request",
		})
	case errors.Is(err, chat.ErrBlocked):
		s.writeJSON(c, http.StatusForbidden, gin.H{
			"code":    40303,
			"message": "chat blocked between users",
		})
	case errors.Is(err, chat.ErrForbiddenWord):
		s.writeJSON(c, http.StatusBadRequest, gin.H{
			"code":    40016,
			"message": "chat message contains forbidden content",
		})
	case errors.As(err, &rateLimited):
		metrics.IncChatRateLimitHit("http")
		s.writeJSON(c, http.StatusTooManyRequests, gin.H{
			"code":    42901,
			"message": "chat message rate limited",
			"data": gin.H{
				"retryAfterSeconds": rateLimited.RetryAfterSeconds,
			},
		})
	default:
		s.logger.Warn().Err(err).Msg(fallbackMessage)
		s.writeJSON(c, http.StatusInternalServerError, gin.H{
			"code":    50010,
			"message": fallbackMessage,
		})
	}
}
