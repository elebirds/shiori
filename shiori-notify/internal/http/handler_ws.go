package notifyhttp

import (
	"encoding/json"
	"errors"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/gorilla/websocket"
	notifyauth "github.com/hhm/shiori/shiori-notify/internal/auth"
	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
)

var wsUpgrader = websocket.Upgrader{
	ReadBufferSize:  1024,
	WriteBufferSize: 1024,
	CheckOrigin: func(r *http.Request) bool {
		return true
	},
}

type wsIncoming struct {
	Type           string `json:"type"`
	ChatTicket     string `json:"chatTicket"`
	ConversationID int64  `json:"conversationId"`
	ClientMsgID    string `json:"clientMsgId"`
	Content        string `json:"content"`
	LastReadMsgID  int64  `json:"lastReadMsgId"`
}

func (s *Server) handleWS(c *gin.Context) {
	userID, ok := s.resolveWSUserID(c)
	if !ok {
		return
	}
	lastEventID := strings.TrimSpace(c.Query("lastEventId"))

	conn, err := wsUpgrader.Upgrade(c.Writer, c.Request, nil)
	if err != nil {
		s.logger.Warn().Err(err).Msg("WebSocket 握手失败")
		return
	}

	client := ws.NewClient(conn, s.cfg.WSWriteTimeout, s.cfg.WSPingInterval)
	s.hub.Register(userID, client)
	connections := s.hub.ConnectionCount()
	metrics.SetWSConnections(connections)
	s.logger.Info().
		Str("userId", userID).
		Str("lastEventId", lastEventID).
		Int("connections", connections).
		Msg("WebSocket 连接已建立")

	s.replayForWS(userID, lastEventID, client)
	numericUserID := int64(0)
	if parsedUserID, parseErr := strconv.ParseInt(userID, 10, 64); parseErr == nil && parsedUserID > 0 {
		numericUserID = parsedUserID
	} else {
		s.logger.Debug().Str("userId", userID).Msg("websocket user id is non-numeric, chat commands disabled")
	}
	joinedConversations := make(map[int64]struct{})

	go client.Run(func(payload []byte) {
		s.handleWSMessage(client, numericUserID, joinedConversations, payload)
	}, func() {
		s.hub.Remove(userID, client)
		connections := s.hub.ConnectionCount()
		metrics.SetWSConnections(connections)
		s.logger.Info().
			Str("userId", userID).
			Int("connections", connections).
		Msg("WebSocket 连接已断开")
	})
}

func (s *Server) handleWSMessage(client *ws.Client, userID int64, joined map[int64]struct{}, payload []byte) {
	if client == nil {
		return
	}
	var incoming wsIncoming
	if err := json.Unmarshal(payload, &incoming); err != nil {
		_ = client.Send(mustJSON(map[string]any{
			"type":    "error",
			"code":    "CHAT_INVALID_JSON",
			"message": "invalid websocket payload",
		}))
		return
	}

	switch strings.ToLower(strings.TrimSpace(incoming.Type)) {
	case "join":
		if userID <= 0 {
			s.sendChatError(client, "CHAT_INVALID_USER", chat.ErrInvalidArgument)
			return
		}
		s.handleWSJoin(client, userID, joined, incoming)
	case "send":
		if userID <= 0 {
			s.sendChatError(client, "CHAT_INVALID_USER", chat.ErrInvalidArgument)
			return
		}
		s.handleWSSend(client, userID, joined, incoming)
	case "read":
		if userID <= 0 {
			s.sendChatError(client, "CHAT_INVALID_USER", chat.ErrInvalidArgument)
			return
		}
		s.handleWSRead(client, userID, joined, incoming)
	default:
		_ = client.Send(mustJSON(map[string]any{
			"type":    "error",
			"code":    "CHAT_UNSUPPORTED_TYPE",
			"message": "unsupported message type",
		}))
	}
}

func (s *Server) handleWSJoin(client *ws.Client, userID int64, joined map[int64]struct{}, incoming wsIncoming) {
	if s.chat == nil {
		_ = client.Send(mustJSON(map[string]any{
			"type":    "error",
			"code":    "CHAT_DISABLED",
			"message": "chat service unavailable",
		}))
		return
	}
	conversation, claims, err := s.chat.Join(userID, incoming.ChatTicket)
	if err != nil {
		s.sendChatError(client, "CHAT_JOIN_FAILED", err)
		return
	}
	joined[conversation.ID] = struct{}{}
	_ = client.Send(mustJSON(map[string]any{
		"type":           "join_ack",
		"conversationId": conversation.ID,
		"listingId":      conversation.ListingID,
		"buyerId":        claims.BuyerID,
		"sellerId":       claims.SellerID,
		"jti":            claims.JTI,
		"expireAt":       claims.ExpiresAt.UTC().Format(time.RFC3339Nano),
	}))
}

func (s *Server) handleWSSend(client *ws.Client, userID int64, joined map[int64]struct{}, incoming wsIncoming) {
	if s.chat == nil {
		_ = client.Send(mustJSON(map[string]any{
			"type":    "error",
			"code":    "CHAT_DISABLED",
			"message": "chat service unavailable",
		}))
		return
	}
	if incoming.ConversationID <= 0 {
		s.sendChatError(client, "CHAT_INVALID_ARGUMENT", chat.ErrInvalidArgument)
		return
	}
	if len(joined) > 0 {
		if _, ok := joined[incoming.ConversationID]; !ok {
			s.sendChatError(client, "CHAT_NOT_JOINED", chat.ErrForbidden)
			return
		}
	}
	sendResult, err := s.chat.Send(userID, incoming.ConversationID, incoming.ClientMsgID, incoming.Content)
	if err != nil {
		s.sendChatError(client, "CHAT_SEND_FAILED", err)
		return
	}
	message := sendResult.Message
	_ = client.Send(mustJSON(map[string]any{
		"type":           "send_ack",
		"conversationId": message.ConversationID,
		"clientMsgId":    message.ClientMsgID,
		"messageId":      message.ID,
		"createdAt":      message.CreatedAt.UTC().Format(time.RFC3339Nano),
		"deduplicated":   sendResult.Deduplicated,
	}))

	if sendResult.Deduplicated {
		return
	}

	receiverPayload := mustJSON(map[string]any{
		"type":           "chat_message",
		"conversationId": message.ConversationID,
		"messageId":      message.ID,
		"listingId":      sendResult.Conversation.ListingID,
		"senderId":       message.SenderID,
		"receiverId":     message.ReceiverID,
		"clientMsgId":    message.ClientMsgID,
		"content":        message.Content,
		"createdAt":      message.CreatedAt.UTC().Format(time.RFC3339Nano),
	})
	_, _ = s.hub.SendToUser(strconv.FormatInt(message.ReceiverID, 10), receiverPayload)

	if s.chatMQ != nil {
		if err := s.chatMQ.PublishMessage(chat.BroadcastEvent{
			ConversationID: message.ConversationID,
			MessageID:      message.ID,
			ListingID:      sendResult.Conversation.ListingID,
			SenderID:       message.SenderID,
			ReceiverID:     message.ReceiverID,
			ClientMsgID:    message.ClientMsgID,
			Content:        message.Content,
			CreatedAt:      message.CreatedAt.UTC(),
		}); err != nil {
			s.logger.Warn().
				Err(err).
				Int64("conversationId", message.ConversationID).
				Int64("messageId", message.ID).
				Msg("publish chat broadcast event failed")
		}
	}
}

func (s *Server) handleWSRead(client *ws.Client, userID int64, joined map[int64]struct{}, incoming wsIncoming) {
	if s.chat == nil {
		_ = client.Send(mustJSON(map[string]any{
			"type":    "error",
			"code":    "CHAT_DISABLED",
			"message": "chat service unavailable",
		}))
		return
	}
	if incoming.ConversationID <= 0 {
		s.sendChatError(client, "CHAT_INVALID_ARGUMENT", chat.ErrInvalidArgument)
		return
	}
	if len(joined) > 0 {
		if _, ok := joined[incoming.ConversationID]; !ok {
			s.sendChatError(client, "CHAT_NOT_JOINED", chat.ErrForbidden)
			return
		}
	}
	lastReadMsgID, err := s.chat.Read(userID, incoming.ConversationID, incoming.LastReadMsgID)
	if err != nil {
		s.sendChatError(client, "CHAT_READ_FAILED", err)
		return
	}
	_ = client.Send(mustJSON(map[string]any{
		"type":           "read_ack",
		"conversationId": incoming.ConversationID,
		"lastReadMsgId":  lastReadMsgID,
	}))
}

func (s *Server) sendChatError(client *ws.Client, code string, err error) {
	message := "chat operation failed"
	switch {
	case errors.Is(err, chat.ErrInvalidTicket):
		message = "invalid chat ticket"
	case errors.Is(err, chat.ErrInvalidArgument):
		message = "invalid argument"
	case errors.Is(err, chat.ErrForbidden):
		message = "conversation access forbidden"
	case errors.Is(err, chat.ErrConversationAbsent):
		message = "conversation not found"
	}
	_ = client.Send(mustJSON(map[string]any{
		"type":    "error",
		"code":    code,
		"message": message,
	}))
}

func mustJSON(payload map[string]any) []byte {
	data, err := json.Marshal(payload)
	if err != nil {
		return []byte(`{"type":"error","code":"CHAT_INTERNAL","message":"internal error"}`)
	}
	return data
}

func (s *Server) resolveWSUserID(c *gin.Context) (string, bool) {
	if s.auth != nil && s.auth.Enabled() {
		accessToken := strings.TrimSpace(c.Query("accessToken"))
		userID, err := s.auth.ParseUserIDFromToken(accessToken)
		if err != nil {
			reason := "invalid_token"
			if errors.Is(err, notifyauth.ErrMissingToken) {
				reason = "missing_token"
			}
			metrics.IncAuthFailure("ws", reason)
			c.JSON(http.StatusUnauthorized, gin.H{
				"code":    40101,
				"message": "invalid access token",
			})
			return "", false
		}
		return userID, true
	}

	userID := strings.TrimSpace(c.Query("userId"))
	if userID == "" {
		c.JSON(http.StatusBadRequest, gin.H{
			"code":    40001,
			"message": "userId is required when auth disabled",
		})
		return "", false
	}
	return userID, true
}

func (s *Server) replayForWS(userID, afterEventID string, client *ws.Client) {
	if s.eventStore == nil || client == nil {
		return
	}

	limit := s.cfg.WSReplayLimit
	if limit <= 0 {
		limit = s.cfg.ReplayDefaultLimit
	}
	items, nextEventID, hasMore, err := s.eventStore.List(userID, afterEventID, limit)
	if err != nil {
		metrics.IncReplayQuery("ws", "store_error")
		s.logger.Warn().Err(err).
			Str("userId", userID).
			Str("afterEventId", afterEventID).
			Msg("WebSocket 补偿查询失败")
		return
	}
	metrics.IncReplayQuery("ws", "success")
	metrics.AddReplayEvents("ws", len(items))
	if len(items) == 0 {
		return
	}

	delivered := 0
	for i := range items {
		payload, marshalErr := json.Marshal(toEnvelope(items[i]))
		if marshalErr != nil {
			metrics.IncReplayQuery("ws", "marshal_error")
			s.logger.Warn().Err(marshalErr).
				Str("eventId", items[i].EventID).
				Str("userId", userID).
				Msg("补偿事件序列化失败，跳过")
			continue
		}
		if sendErr := client.Send(payload); sendErr != nil {
			metrics.IncReplayQuery("ws", "send_error")
			s.logger.Warn().Err(sendErr).
				Str("eventId", items[i].EventID).
				Str("userId", userID).
				Msg("补偿事件发送失败")
			break
		}
		delivered++
	}

	s.logger.Info().
		Str("userId", userID).
		Str("afterEventId", afterEventID).
		Str("nextEventId", nextEventID).
		Int("delivered", delivered).
		Bool("hasMore", hasMore).
		Msg("WebSocket 补偿事件回放完成")
}

func toEnvelope(item store.NotificationEvent) event.Envelope {
	return event.Envelope{
		EventID:     item.EventID,
		Type:        item.Type,
		AggregateID: item.AggregateID,
		CreatedAt:   item.CreatedAt,
		Payload:     append([]byte(nil), item.Payload...),
	}
}
