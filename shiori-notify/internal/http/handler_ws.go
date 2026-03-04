package notifyhttp

import (
	"encoding/json"
	"errors"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
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

	go client.Run(func() {
		s.hub.Remove(userID, client)
		connections := s.hub.ConnectionCount()
		metrics.SetWSConnections(connections)
		s.logger.Info().
			Str("userId", userID).
			Int("connections", connections).
			Msg("WebSocket 连接已断开")
	})
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
