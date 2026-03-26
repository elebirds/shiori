package kafkaevent

import (
	"context"
	"encoding/json"
	"errors"
	"strings"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/rs/zerolog"
)

type Reader interface {
	Read(context.Context) ([]byte, error)
	Close() error
}

type ReaderFactory func() (Reader, error)

type eventRouter interface {
	Route(context.Context, event.Envelope) error
}

type Consumer struct {
	newReader ReaderFactory
	router    eventRouter
	logger    *zerolog.Logger
}

type cdcRecord struct {
	AggregateType string `json:"aggregate_type"`
	Payload       string `json:"payload"`
	Status        string `json:"status"`
}

type schemaWrappedCDCRecord struct {
	Payload cdcRecord `json:"payload"`
}

func NewConsumer(readerFactory ReaderFactory, router eventRouter, logger *zerolog.Logger) *Consumer {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	return &Consumer{
		newReader: readerFactory,
		router:    router,
		logger:    logger,
	}
}

func (c *Consumer) Run(ctx context.Context) error {
	if c.newReader == nil {
		return errors.New("kafka reader factory is not configured")
	}

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
			Msg("Kafka CDC 消费中断，准备重试")

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
	reader, err := c.newReader()
	if err != nil {
		return err
	}
	defer reader.Close()

	c.logger.Info().Msg("Kafka CDC 消费器已启动")
	for {
		raw, readErr := reader.Read(ctx)
		if readErr != nil {
			return readErr
		}
		c.handleMessage(ctx, raw)
	}
}

func (c *Consumer) handleMessage(ctx context.Context, raw []byte) {
	record, err := decodeCDCRecord(raw)
	if err != nil {
		metrics.IncMQConsume("invalid_json", "")
		c.logger.Warn().Err(err).Msg("CDC 事件 JSON 非法或格式不支持，已跳过")
		return
	}

	if !strings.EqualFold(strings.TrimSpace(record.Status), "PENDING") {
		return
	}
	if !isSupportedAggregateType(record.AggregateType) {
		return
	}
	if strings.TrimSpace(record.Payload) == "" {
		metrics.IncMQConsume("invalid_envelope", "")
		return
	}

	var env event.Envelope
	if err := json.Unmarshal([]byte(record.Payload), &env); err != nil {
		metrics.IncMQConsume("invalid_envelope", "")
		c.logger.Warn().Err(err).Msg("CDC payload 不是合法事件 Envelope，已跳过")
		return
	}

	if err := env.Validate(); err != nil {
		metrics.IncMQConsume("invalid_envelope", env.Type)
		c.logger.Warn().Err(err).Msg("CDC 事件 Envelope 非法，已跳过")
		return
	}

	if err := c.router.Route(ctx, env); err != nil {
		metrics.IncMQConsume("route_failed", env.Type)
		c.logger.Warn().
			Err(err).
			Str("eventId", env.EventID).
			Str("type", env.Type).
			Msg("CDC 事件路由失败，已跳过")
		return
	}

	metrics.IncMQConsume("routed", env.Type)
}

func decodeCDCRecord(raw []byte) (cdcRecord, error) {
	var wrapped schemaWrappedCDCRecord
	if err := json.Unmarshal(raw, &wrapped); err == nil {
		if wrapped.Payload.AggregateType != "" || wrapped.Payload.Payload != "" || wrapped.Payload.Status != "" {
			return wrapped.Payload, nil
		}
	}

	var record cdcRecord
	if err := json.Unmarshal(raw, &record); err != nil {
		return cdcRecord{}, err
	}
	if record.AggregateType == "" && record.Payload == "" && record.Status == "" {
		return cdcRecord{}, errors.New("unsupported cdc message shape")
	}
	return record, nil
}

func isSupportedAggregateType(raw string) bool {
	switch strings.ToLower(strings.TrimSpace(raw)) {
	case "order", "user":
		return true
	default:
		return false
	}
}
