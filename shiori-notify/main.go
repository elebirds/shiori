package main

import (
	"context"
	"os"
	"os/signal"
	"syscall"

	"github.com/hhm/shiori/shiori-notify/internal/app"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	notifylog "github.com/hhm/shiori/shiori-notify/internal/log"
)

func main() {
	cfg := config.Load()
	logger := notifylog.New(cfg.LogLevel)

	application := app.New(cfg, logger)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	logger.Info().
		Str("httpAddr", cfg.HTTPAddr).
		Str("rabbitmqExchange", cfg.RabbitMQExchange).
		Str("rabbitmqQueue", cfg.RabbitMQQueue).
		Msg("服务启动中")

	if err := application.Run(ctx); err != nil {
		logger.Error().Err(err).Msg("服务异常退出")
		os.Exit(1)
	}

	logger.Info().Msg("服务已停止")
}
