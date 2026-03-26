package main

import (
	"context"
	"errors"
	"fmt"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"github.com/hhm/shiori/shiori-notify/internal/app"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	notifylog "github.com/hhm/shiori/shiori-notify/internal/log"
	notifyNacos "github.com/hhm/shiori/shiori-notify/internal/nacos"
	"github.com/joho/godotenv"
)

func main() {
	if err := run(); err != nil {
		_, _ = fmt.Fprintf(os.Stderr, "[shiori-notify] %v\n", err)
		os.Exit(1)
	}
}

func run() error {
	loadDotEnvIfPresent()

	nacosConn, err := config.LoadNacosConnFromEnv()
	if err != nil {
		return fmt.Errorf("Nacos 连接配置错误: %w", err)
	}

	cfg, err := notifyNacos.LoadRuntimeConfigFromNacos(nacosConn)
	if err != nil {
		return fmt.Errorf("读取 Nacos 业务配置失败: %w", err)
	}

	logger := notifylog.New(cfg.LogLevel)
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	cleanupRegistration, err := notifyNacos.RegisterNotifyInstance(ctx, nacosConn, cfg)
	if err != nil {
		return fmt.Errorf("Nacos 服务注册失败: %w", err)
	}
	defer func() {
		if cleanupRegistration == nil {
			return
		}
		if cleanupErr := cleanupRegistration(); cleanupErr != nil {
			logger.Warn().Err(cleanupErr).Msg("Nacos 服务注销失败")
		}
	}()

	application, err := app.New(cfg, logger)
	if err != nil {
		return fmt.Errorf("应用初始化失败: %w", err)
	}

	logger.Info().
		Str("nacosAddr", nacosConn.Addr).
		Str("nacosGroup", nacosConn.Group).
		Str("nacosNamespace", nacosConn.Namespace).
		Str("httpAddr", cfg.HTTPAddr).
		Str("wsPath", cfg.WSPath).
		Str("storeDriver", cfg.StoreDriver).
		Bool("kafkaEnabled", cfg.KafkaEnabled).
		Strs("kafkaTopics", cfg.KafkaTopics).
		Bool("authEnabled", cfg.AuthEnabled).
		Bool("chatEnabled", cfg.ChatEnabled).
		Bool("chatPubSubEnabled", cfg.ChatPubSubEnabled).
		Str("chatPubSubChannel", cfg.ChatPubSubChannel).
		Str("instanceID", cfg.InstanceID).
		Msg("服务启动中")

	if err = application.Run(ctx); err != nil {
		return fmt.Errorf("服务异常退出: %w", err)
	}

	logger.Info().Msg("服务已停止")
	return nil
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
