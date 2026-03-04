package app

import (
	"context"
	"errors"
	"fmt"
	"strings"

	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/hhm/shiori/shiori-notify/internal/chatmq"
	notifyauth "github.com/hhm/shiori/shiori-notify/internal/auth"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	notifyhttp "github.com/hhm/shiori/shiori-notify/internal/http"
	"github.com/hhm/shiori/shiori-notify/internal/mq"
	"github.com/hhm/shiori/shiori-notify/internal/router"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

type App struct {
	logger     *zerolog.Logger
	httpSrv    *notifyhttp.Server
	consumer   *mq.Consumer
	chatSub    *chatmq.Consumer
	closeFns   []func() error
}

func New(cfg config.Config, logger *zerolog.Logger) (*App, error) {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}

	eventStore, storeClose, err := buildEventStore(cfg)
	if err != nil {
		return nil, err
	}
	closeFns := make([]func() error, 0, 2)
	if storeClose != nil {
		closeFns = append(closeFns, storeClose)
	}

	authVerifier, err := notifyauth.NewJWTVerifier(cfg.AuthEnabled, cfg.JWTHmacSecret, cfg.JWTIssuer)
	if err != nil {
		closeAll(closeFns, logger)
		return nil, err
	}

	hub := ws.NewHub()
	var (
		chatService     *chat.Service
		chatPublisher   chat.Broadcaster
		chatSubscriber  *chatmq.Consumer
	)
	if cfg.ChatEnabled {
		if strings.ToLower(strings.TrimSpace(cfg.StoreDriver)) != "mysql" {
			closeAll(closeFns, logger)
			return nil, errors.New("chat requires mysql store driver")
		}
		chatRepo, repoErr := chat.NewMySQLRepository(
			cfg.MySQLDSN,
			cfg.MySQLMaxOpenConns,
			cfg.MySQLMaxIdleConns,
			cfg.MySQLConnMaxLifetime,
		)
		if repoErr != nil {
			closeAll(closeFns, logger)
			return nil, repoErr
		}
		closeFns = append(closeFns, chatRepo.Close)
		ticketVerifier, verifyErr := chat.NewRS256TicketVerifier(chat.TicketVerifierConfig{
			Enabled:            cfg.ChatEnabled,
			Issuer:             cfg.ChatTicketIssuer,
			PublicKeyPEMBase64: cfg.ChatTicketPublicKey,
		})
		if verifyErr != nil {
			closeAll(closeFns, logger)
			return nil, verifyErr
		}
		chatService = chat.NewService(chatRepo, ticketVerifier, cfg.ChatMaxLimit)
		chatPublisher = chatmq.NewPublisher(
			cfg.ChatMQEnabled,
			cfg.RabbitMQAddr,
			cfg.ChatMQExchange,
			cfg.InstanceID,
			logger,
		)
		chatSubscriber = chatmq.NewConsumer(
			cfg.ChatMQEnabled,
			cfg.RabbitMQAddr,
			cfg.ChatMQExchange,
			cfg.InstanceID,
			hub,
			logger,
		)
	}

	r := router.New(hub, eventStore, logger)
	httpSrv := notifyhttp.NewServer(cfg, hub, eventStore, authVerifier, logger)
	httpSrv.WithChat(chatService, chatPublisher)
	consumer := mq.NewConsumer(cfg, r, logger)

	return &App{
		logger:   logger,
		httpSrv:  httpSrv,
		consumer: consumer,
		chatSub:  chatSubscriber,
		closeFns: closeFns,
	}, nil
}

func (a *App) Run(ctx context.Context) error {
	defer func() {
		closeAll(a.closeFns, a.logger)
	}()

	components := []func(context.Context) error{
		a.httpSrv.Run,
		a.consumer.Run,
	}
	if a.chatSub != nil {
		components = append(components, a.chatSub.Run)
	}
	errCh := make(chan error, len(components))

	for i := range components {
		runFn := components[i]
		go func(run func(context.Context) error) {
			errCh <- run(ctx)
		}(runFn)
	}

	completed := 0
	for completed < len(components) {
		err := <-errCh
		completed++

		if err != nil && !errors.Is(err, context.Canceled) {
			return err
		}

		if ctx.Err() == nil && completed < len(components) {
			return fmt.Errorf("component exited unexpectedly")
		}
	}

	a.logger.Info().Msg("所有组件已停止")
	return nil
}

func closeAll(closeFns []func() error, logger *zerolog.Logger) {
	for i := len(closeFns) - 1; i >= 0; i-- {
		if closeFns[i] == nil {
			continue
		}
		if err := closeFns[i](); err != nil {
			logger.Warn().Err(err).Msg("close resource failed")
		}
	}
}

func buildEventStore(cfg config.Config) (store.EventStore, func() error, error) {
	switch strings.ToLower(strings.TrimSpace(cfg.StoreDriver)) {
	case "", "memory":
		memStore := store.NewMemoryEventStore(cfg.StoreMaxPerUser)
		return memStore, nil, nil
	case "mysql":
		mysqlStore, err := store.NewMySQLEventStore(
			cfg.MySQLDSN,
			cfg.StoreMaxPerUser,
			cfg.MySQLMaxOpenConns,
			cfg.MySQLMaxIdleConns,
			cfg.MySQLConnMaxLifetime,
		)
		if err != nil {
			return nil, nil, err
		}
		return mysqlStore, func() error {
			return mysqlStore.Close()
		}, nil
	default:
		return nil, nil, fmt.Errorf("unsupported notify store driver: %s", cfg.StoreDriver)
	}
}
