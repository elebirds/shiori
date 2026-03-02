package notifyhttp

import (
	"context"
	"errors"
	"net"
	"net/http"
	"time"

	"github.com/gin-gonic/gin"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

type Server struct {
	cfg    config.Config
	hub    *ws.Hub
	logger *zerolog.Logger
	engine *gin.Engine
}

func NewServer(cfg config.Config, hub *ws.Hub, logger *zerolog.Logger) *Server {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}

	gin.SetMode(gin.ReleaseMode)
	engine := gin.New()
	engine.Use(gin.Recovery())

	s := &Server{
		cfg:    cfg,
		hub:    hub,
		logger: logger,
		engine: engine,
	}
	s.registerRoutes()
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
	s.engine.GET("/healthz", s.handleHealth)
	s.engine.GET("/ws", s.handleWS)
}
