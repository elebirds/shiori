package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"github.com/joho/godotenv"
	"github.com/hhm/shiori/shiori-notify/internal/app"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	notifylog "github.com/hhm/shiori/shiori-notify/internal/log"
)

func main() {
	loadDotEnvIfPresent()
	cfg := config.Load()
	logger := notifylog.New(cfg.LogLevel)

	application, err := app.New(cfg, logger)
	if err != nil {
		logger.Error().Err(err).Msg("应用初始化失败")
		os.Exit(1)
	}

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	logger.Info().
		Str("httpAddr", cfg.HTTPAddr).
		Strs("rabbitmqExchanges", cfg.RabbitMQExchanges).
		Str("rabbitmqExchange", cfg.RabbitMQExchange).
		Str("rabbitmqQueue", cfg.RabbitMQQueue).
		Strs("rabbitmqRoutingKeys", cfg.RabbitMQRoutingKeys).
		Str("storeDriver", cfg.StoreDriver).
		Bool("authEnabled", cfg.AuthEnabled).
		Msg("服务启动中")

	if err = application.Run(ctx); err != nil {
		logger.Error().Err(err).Msg("服务异常退出")
		os.Exit(1)
	}

	logger.Info().Msg("服务已停止")
}

func loadDotEnvIfPresent() {
	envFile := strings.TrimSpace(os.Getenv("NOTIFY_ENV_FILE"))
	if envFile == "" {
		envFile = ".env"
	}

	if err := godotenv.Load(envFile); err != nil {
		if errors.Is(err, os.ErrNotExist) {
			return
		}
		_, _ = fmt.Fprintf(os.Stderr, "[shiori-notify] 加载 env 文件失败(%s): %v\n", envFile, err)
	}
}
