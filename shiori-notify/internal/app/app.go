package app

import (
	"context"
	"errors"
	"fmt"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	notifyhttp "github.com/hhm/shiori/shiori-notify/internal/http"
	"github.com/hhm/shiori/shiori-notify/internal/mq"
	"github.com/hhm/shiori/shiori-notify/internal/router"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

type App struct {
	logger   *zerolog.Logger
	httpSrv  *notifyhttp.Server
	consumer *mq.Consumer
}

func New(cfg config.Config, logger *zerolog.Logger) *App {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}

	hub := ws.NewHub()
	eventStore := store.NewMemoryEventStore(cfg.StoreMaxPerUser)
	r := router.New(hub, eventStore, logger)
	httpSrv := notifyhttp.NewServer(cfg, hub, eventStore, logger)
	consumer := mq.NewConsumer(cfg, r, logger)

	return &App{
		logger:   logger,
		httpSrv:  httpSrv,
		consumer: consumer,
	}
}

func (a *App) Run(ctx context.Context) error {
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
