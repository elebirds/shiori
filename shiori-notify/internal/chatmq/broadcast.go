package chatmq

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/hhm/shiori/shiori-notify/internal/metrics"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	amqp "github.com/rabbitmq/amqp091-go"
	"github.com/rs/zerolog"
)

type Hub interface {
	SendToUser(userID string, payload []byte) (int, error)
}

type Publisher struct {
	enabled    bool
	addr       string
	exchange   string
	instanceID string
	logger     *zerolog.Logger
}

type Consumer struct {
	enabled    bool
	addr       string
	exchange   string
	instanceID string
	hub        Hub
	logger     *zerolog.Logger
}

func NewPublisher(enabled bool, addr, exchange, instanceID string, logger *zerolog.Logger) *Publisher {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	return &Publisher{
		enabled:    enabled,
		addr:       addr,
		exchange:   exchange,
		instanceID: instanceID,
		logger:     logger,
	}
}

func (p *Publisher) PublishMessage(event chat.BroadcastEvent) error {
	if p == nil || !p.enabled {
		return nil
	}
	conn, err := amqp.Dial(p.addr)
	if err != nil {
		return fmt.Errorf("dial rabbitmq for chat publish failed: %w", err)
	}
	defer conn.Close()
	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("open rabbitmq channel for chat publish failed: %w", err)
	}
	defer ch.Close()
	if err := declareExchange(ch, p.exchange); err != nil {
		return err
	}
	event.OriginInstance = p.instanceID
	body, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal chat broadcast event failed: %w", err)
	}
	if err := ch.PublishWithContext(
		context.Background(),
		p.exchange,
		"",
		false,
		false,
		amqp.Publishing{
			ContentType:  "application/json",
			Body:         body,
			Timestamp:    time.Now().UTC(),
			DeliveryMode: amqp.Persistent,
		},
	); err != nil {
		return fmt.Errorf("publish chat broadcast event failed: %w", err)
	}
	return nil
}

func NewConsumer(enabled bool, addr, exchange, instanceID string, hub Hub, logger *zerolog.Logger) *Consumer {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	return &Consumer{
		enabled:    enabled,
		addr:       addr,
		exchange:   exchange,
		instanceID: instanceID,
		hub:        hub,
		logger:     logger,
	}
}

func (c *Consumer) Run(ctx context.Context) error {
	if c == nil || !c.enabled {
		return nil
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

		c.logger.Warn().Err(err).Str("nextRetry", backoff.String()).Msg("chat broadcast consumer interrupted")
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
	conn, err := amqp.Dial(c.addr)
	if err != nil {
		return fmt.Errorf("dial rabbitmq for chat consume failed: %w", err)
	}
	defer conn.Close()
	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("open rabbitmq channel for chat consume failed: %w", err)
	}
	defer ch.Close()
	if err := declareExchange(ch, c.exchange); err != nil {
		return err
	}

	queue, err := ch.QueueDeclare(
		"",
		false,
		true,
		true,
		false,
		nil,
	)
	if err != nil {
		return fmt.Errorf("declare chat fanout queue failed: %w", err)
	}
	if err := ch.QueueBind(queue.Name, "", c.exchange, false, nil); err != nil {
		return fmt.Errorf("bind chat fanout queue failed: %w", err)
	}
	deliveries, err := ch.Consume(queue.Name, "", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("consume chat fanout queue failed: %w", err)
	}

	c.logger.Info().Str("exchange", c.exchange).Str("queue", queue.Name).Msg("chat broadcast consumer started")
	for {
		select {
		case <-ctx.Done():
			return nil
		case delivery, ok := <-deliveries:
			if !ok {
				return errors.New("chat delivery channel closed")
			}
			c.handleDelivery(delivery)
		}
	}
}

func (c *Consumer) handleDelivery(delivery amqp.Delivery) {
	defer func() {
		if err := delivery.Ack(false); err != nil {
			c.logger.Warn().Err(err).Msg("ack chat broadcast delivery failed")
		}
	}()

	var event chat.BroadcastEvent
	if err := json.Unmarshal(delivery.Body, &event); err != nil {
		c.logger.Warn().Err(err).Msg("invalid chat broadcast payload")
		return
	}
	if event.OriginInstance == c.instanceID {
		return
	}
	payload, err := json.Marshal(map[string]any{
		"type":           "chat_message",
		"conversationId": event.ConversationID,
		"messageId":      event.MessageID,
		"listingId":      event.ListingID,
		"senderId":       event.SenderID,
		"receiverId":     event.ReceiverID,
		"clientMsgId":    event.ClientMsgID,
		"content":        event.Content,
		"createdAt":      event.CreatedAt.UTC().Format(time.RFC3339Nano),
	})
	if err != nil {
		c.logger.Warn().Err(err).Msg("marshal ws chat payload failed")
		return
	}
	sent, sendErr := c.hub.SendToUser(strconv.FormatInt(event.ReceiverID, 10), payload)
	if sendErr != nil {
		if errors.Is(sendErr, ws.ErrNoSession) {
			return
		}
		c.logger.Warn().Err(sendErr).
			Int64("receiverId", event.ReceiverID).
			Int64("messageId", event.MessageID).
			Msg("push chat message from broadcast failed")
		return
	}
	if sent > 0 {
		metrics.ObserveChatDeliveryLatency("broadcast", time.Since(event.CreatedAt))
	}
}

func declareExchange(ch *amqp.Channel, exchange string) error {
	if err := ch.ExchangeDeclare(
		exchange,
		"fanout",
		true,
		false,
		false,
		false,
		nil,
	); err != nil {
		return fmt.Errorf("declare chat exchange failed: %w", err)
	}
	return nil
}
