package app

import (
	"context"
	"errors"
	"fmt"
	"strings"

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
	storeClose func() error
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

	authVerifier, err := notifyauth.NewJWTVerifier(cfg.AuthEnabled, cfg.JWTHmacSecret, cfg.JWTIssuer)
	if err != nil {
		if storeClose != nil {
			_ = storeClose()
		}
		return nil, err
	}

	hub := ws.NewHub()
	r := router.New(hub, eventStore, logger)
	httpSrv := notifyhttp.NewServer(cfg, hub, eventStore, authVerifier, logger)
	consumer := mq.NewConsumer(cfg, r, logger)

	return &App{
		logger:     logger,
		httpSrv:    httpSrv,
		consumer:   consumer,
		storeClose: storeClose,
	}, nil
}

func (a *App) Run(ctx context.Context) error {
	defer func() {
		if a.storeClose != nil {
			if err := a.storeClose(); err != nil {
				a.logger.Warn().Err(err).Msg("关闭通知存储失败")
			}
		}
	}()

	errCh := make(chan error, 2)

	go func() { errCh <- a.httpSrv.Run(ctx) }()
	go func() { errCh <- a.consumer.Run(ctx) }()

	completed := 0
	for completed < 2 {
		err := <-errCh
		completed++

		if err != nil && !errors.Is(err, context.Canceled) {
			return err
		}

		if ctx.Err() == nil && completed < 2 {
			return fmt.Errorf("component exited unexpectedly")
		}
	}

	a.logger.Info().Msg("所有组件已停止")
	return nil
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
