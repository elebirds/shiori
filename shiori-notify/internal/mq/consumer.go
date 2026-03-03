package mq

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/hhm/shiori/shiori-notify/internal/router"
	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/rs/zerolog"
)

type Consumer struct {
	cfg    config.Config
	router *router.Router
	logger *zerolog.Logger
}

func NewConsumer(cfg config.Config, r *router.Router, logger *zerolog.Logger) *Consumer {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	return &Consumer{cfg: cfg, router: r, logger: logger}
}

func (c *Consumer) Run(ctx context.Context) error {
	backoff := time.Second

	for {
		select {
		case <-ctx.Done():
			return nil
		default:
		}

		err := c.consumeOnce(ctx)
		if err == nil || errors.Is(err, context.Canceled) {
			if ctx.Err() != nil {
				return nil
			}
			backoff = time.Second
			continue
		}

		c.logger.Warn().
			Err(err).
			Str("nextRetry", backoff.String()).
			Msg("MQ 消费中断，准备重试")

		timer := time.NewTimer(backoff)
		select {
		case <-ctx.Done():
			timer.Stop()
			return nil
		case <-timer.C:
		}

		backoff *= 2
		if backoff > 30*time.Second {
			backoff = 30 * time.Second
		}
	}
}

func (c *Consumer) consumeOnce(ctx context.Context) error {
	conn, err := amqp.Dial(c.cfg.RabbitMQAddr)
	if err != nil {
		return fmt.Errorf("dial rabbitmq: %w", err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("open channel: %w", err)
	}
	defer ch.Close()

	if err := DeclareTopology(ch, c.cfg); err != nil {
		return err
	}

	deliveries, err := ch.Consume(
		c.cfg.RabbitMQQueue,
		"",
		false,
		false,
		false,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("register consumer: %w", err)
	}

	c.logger.Info().
		Str("exchange", c.cfg.RabbitMQExchange).
		Str("queue", c.cfg.RabbitMQQueue).
		Str("routingKey", c.cfg.RabbitMQRoutingKey).
		Msg("MQ 消费器已启动")

	for {
		select {
		case <-ctx.Done():
			return nil
		case delivery, ok := <-deliveries:
			if !ok {
				return errors.New("delivery channel closed")
			}
			c.handleDelivery(ctx, delivery)
		}
	}
}

func (c *Consumer) handleDelivery(ctx context.Context, delivery amqp.Delivery) {
	var env event.Envelope
	if err := json.Unmarshal(delivery.Body, &env); err != nil {
		metrics.IncMQConsume("invalid_json", "")
		c.logger.Warn().Err(err).Msg("事件 JSON 非法，已确认并跳过")
		c.ack(delivery)
		return
	}

	if err := env.Validate(); err != nil {
		metrics.IncMQConsume("invalid_envelope", env.Type)
		c.logger.Warn().Err(err).Msg("事件 Envelope 非法，已确认并跳过")
		c.ack(delivery)
		return
	}

	if err := c.router.Route(ctx, env); err != nil {
		metrics.IncMQConsume("route_failed", env.Type)
		c.logger.Warn().
			Err(err).
			Str("eventId", env.EventID).
			Str("type", env.Type).
			Msg("事件路由失败，已确认并跳过")
	} else {
		metrics.IncMQConsume("routed", env.Type)
	}

	c.ack(delivery)
}

func (c *Consumer) ack(delivery amqp.Delivery) {
	if err := delivery.Ack(false); err != nil {
		c.logger.Warn().Err(err).Msg("消息确认失败")
	}
}
