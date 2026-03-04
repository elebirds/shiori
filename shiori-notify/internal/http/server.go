package notifyhttp

import (
	"context"
	"errors"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
	notifyauth "github.com/hhm/shiori/shiori-notify/internal/auth"
	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

type Server struct {
	cfg        config.Config
	hub        *ws.Hub
	eventStore store.EventStore
	auth       *notifyauth.JWTVerifier
	chat       *chat.Service
	chatMQ     chat.Broadcaster
	logger     *zerolog.Logger
	engine     *gin.Engine
}

func NewServer(
	cfg config.Config,
	hub *ws.Hub,
	eventStore store.EventStore,
	auth *notifyauth.JWTVerifier,
	logger *zerolog.Logger,
) *Server {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}

	gin.SetMode(gin.ReleaseMode)
	engine := gin.New()
	engine.Use(gin.Recovery())

	s := &Server{
		cfg:        cfg,
		hub:        hub,
		eventStore: eventStore,
		auth:       auth,
		logger:     logger,
		engine:     engine,
	}
	s.registerRoutes()
	return s
}

func (s *Server) WithChat(chatService *chat.Service, broadcaster chat.Broadcaster) *Server {
	s.chat = chatService
	s.chatMQ = broadcaster
	return s
}

func (s *Server) Run(ctx context.Context) error {
	httpServer := &http.Server{
		Addr:              s.cfg.HTTPAddr,
		Handler:           s.engine,
		ReadHeaderTimeout: 5 * time.Second,
	}

	listener, err := net.Listen("tcp", s.cfg.HTTPAddr)
	if err != nil {
		return err
	}

	serveErrCh := make(chan error, 1)
	go func() {
		serveErrCh <- httpServer.Serve(listener)
	}()

	go func() {
		<-ctx.Done()
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		if err := httpServer.Shutdown(shutdownCtx); err != nil {
			s.logger.Warn().Err(err).Msg("HTTP 服务优雅关闭失败")
		}
	}()

	err = <-serveErrCh
	if err != nil && !errors.Is(err, http.ErrServerClosed) {
		return err
	}
	return nil
}

func (s *Server) registerRoutes() {
	wsPath := strings.TrimSpace(s.cfg.WSPath)
	if wsPath == "" {
		wsPath = "/ws"
	}
	if !strings.HasPrefix(wsPath, "/") {
		wsPath = "/" + wsPath
	}

	s.engine.GET("/healthz", s.handleHealth)
	s.engine.GET(wsPath, s.handleWS)

	notifyGroup := s.engine.Group("/api/notify")
	{
		notifyGroup.GET("/events", s.handleReplayEvents)
		notifyGroup.POST("/events/:eventId/read", s.handleMarkRead)
		notifyGroup.POST("/events/read-all", s.handleMarkAllRead)
		notifyGroup.GET("/summary", s.handleSummary)
	}

	chatGroup := s.engine.Group("/api/chat")
	{
		chatGroup.GET("/summary", s.handleChatSummary)
		chatGroup.POST("/conversations/start", s.handleStartConversation)
		chatGroup.GET("/conversations", s.handleListConversations)
		chatGroup.GET("/conversations/:conversationId/messages", s.handleListMessages)
		chatGroup.POST("/conversations/:conversationId/read", s.handleReadConversation)
	}

	if s.cfg.MetricsEnabled {
		s.engine.GET("/metrics", gin.WrapH(metrics.Handler()))
	}
}
