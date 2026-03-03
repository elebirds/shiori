package notifyhttp

import (
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
		Int("connections", connections).
		Msg("WebSocket 连接已建立")

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
