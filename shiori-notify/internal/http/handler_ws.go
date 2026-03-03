package notifyhttp

import (
	"encoding/json"
	"net/http"
	"strings"

	"github.com/gin-gonic/gin"
	"github.com/gorilla/websocket"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
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
	userID := strings.TrimSpace(c.Query("userId"))
	if userID == "" {
		c.JSON(http.StatusBadRequest, gin.H{"error": "userId is required"})
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

func (s *Server) replayForWS(userID, afterEventID string, client *ws.Client) {
	if s.replayStore == nil || client == nil {
		return
	}

	limit := s.cfg.WSReplayLimit
	if limit <= 0 {
		limit = s.cfg.ReplayDefaultLimit
	}
	items, nextEventID, hasMore := s.replayStore.List(userID, afterEventID, limit)
	metrics.IncReplayQuery("ws", "success")
	metrics.AddReplayEvents("ws", len(items))
	if len(items) == 0 {
		return
	}

	delivered := 0
	for i := range items {
		payload, err := json.Marshal(items[i])
		if err != nil {
			metrics.IncReplayQuery("ws", "marshal_error")
			s.logger.Warn().Err(err).
				Str("eventId", items[i].EventID).
				Str("userId", userID).
				Msg("补偿事件序列化失败，跳过")
			continue
		}
		if err := client.Send(payload); err != nil {
			metrics.IncReplayQuery("ws", "send_error")
			s.logger.Warn().Err(err).
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
