package chatredis

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
	"github.com/redis/go-redis/v9"
	"github.com/rs/zerolog"
)

type Hub interface {
	SendToUser(userID string, payload []byte) (int, error)
}

type publishClient interface {
	Publish(ctx context.Context, channel string, payload string) error
}

type subscriber interface {
	Subscribe(ctx context.Context, channel string) (subscription, error)
}

type subscription interface {
	ReceiveMessage(ctx context.Context) (string, error)
	Close() error
}

type Publisher struct {
	enabled    bool
	channel    string
	instanceID string
	logger     *zerolog.Logger
	client     publishClient
	closeFn    func() error
}

type Consumer struct {
	enabled    bool
	channel    string
	instanceID string
	hub        Hub
	logger     *zerolog.Logger
	subscriber subscriber
	closeFn    func() error
}

func NewPublisher(enabled bool, addr, channel, instanceID string, logger *zerolog.Logger) *Publisher {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	publisher := &Publisher{
		enabled:    enabled,
		channel:    channel,
		instanceID: instanceID,
		logger:     logger,
	}
	if !enabled {
		return publisher
	}

	client := redis.NewClient(&redis.Options{Addr: addr})
	publisher.client = &redisPublishClient{client: client}
	publisher.closeFn = client.Close
	return publisher
}

func (p *Publisher) PublishMessage(event chat.BroadcastEvent) error {
	if p == nil || !p.enabled {
		return nil
	}
	if p.client == nil {
		return errors.New("redis chat publisher client is not configured")
	}
	event.OriginInstance = p.instanceID
	body, err := json.Marshal(event)
	if err != nil {
		return fmt.Errorf("marshal chat broadcast event failed: %w", err)
	}
	if err := p.client.Publish(context.Background(), p.channel, string(body)); err != nil {
		return fmt.Errorf("publish chat broadcast event failed: %w", err)
	}
	return nil
}

func (p *Publisher) Close() error {
	if p == nil || p.closeFn == nil {
		return nil
	}
	return p.closeFn()
}

func NewConsumer(enabled bool, addr, channel, instanceID string, hub Hub, logger *zerolog.Logger) *Consumer {
	if logger == nil {
		nop := zerolog.Nop()
		logger = &nop
	}
	consumer := &Consumer{
		enabled:    enabled,
		channel:    channel,
		instanceID: instanceID,
		hub:        hub,
		logger:     logger,
	}
	if !enabled {
		return consumer
	}

	client := redis.NewClient(&redis.Options{Addr: addr})
	consumer.subscriber = &redisSubscriber{client: client}
	consumer.closeFn = client.Close
	return consumer
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

		c.logger.Warn().Err(err).Str("nextRetry", backoff.String()).Msg("chat pubsub consumer interrupted")
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

func (c *Consumer) Close() error {
	if c == nil || c.closeFn == nil {
		return nil
	}
	return c.closeFn()
}

func (c *Consumer) consumeOnce(ctx context.Context) error {
	if c.subscriber == nil {
		return errors.New("redis chat subscriber is not configured")
	}
	sub, err := c.subscriber.Subscribe(ctx, c.channel)
	if err != nil {
		return fmt.Errorf("subscribe chat pubsub channel failed: %w", err)
	}
	defer func() {
		if closeErr := sub.Close(); closeErr != nil {
			c.logger.Warn().Err(closeErr).Msg("close chat pubsub subscription failed")
		}
	}()

	c.logger.Info().Str("channel", c.channel).Msg("chat pubsub consumer started")
	for {
		payload, recvErr := sub.ReceiveMessage(ctx)
		if recvErr != nil {
			if errors.Is(recvErr, context.Canceled) {
				return recvErr
			}
			return fmt.Errorf("receive chat pubsub message failed: %w", recvErr)
		}
		c.handleMessage(payload)
	}
}

func (c *Consumer) handleMessage(payload string) {
	var event chat.BroadcastEvent
	if err := json.Unmarshal([]byte(payload), &event); err != nil {
		c.logger.Warn().Err(err).Msg("invalid chat pubsub payload")
		return
	}
	if event.OriginInstance == c.instanceID {
		return
	}

	body, err := json.Marshal(map[string]any{
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

	sent, sendErr := c.hub.SendToUser(strconv.FormatInt(event.ReceiverID, 10), body)
	if sendErr != nil {
		if errors.Is(sendErr, ws.ErrNoSession) {
			return
		}
		c.logger.Warn().
			Err(sendErr).
			Int64("receiverId", event.ReceiverID).
			Int64("messageId", event.MessageID).
			Msg("push chat message from pubsub failed")
		return
	}
	if sent > 0 {
		metrics.ObserveChatDeliveryLatency("broadcast", time.Since(event.CreatedAt))
	}
}

type redisPublishClient struct {
	client *redis.Client
}

func (c *redisPublishClient) Publish(ctx context.Context, channel string, payload string) error {
	return c.client.Publish(ctx, channel, payload).Err()
}

type redisSubscriber struct {
	client *redis.Client
}

func (s *redisSubscriber) Subscribe(ctx context.Context, channel string) (subscription, error) {
	pubsub := s.client.Subscribe(ctx, channel)
	return &redisSubscription{pubsub: pubsub}, nil
}

type redisSubscription struct {
	pubsub *redis.PubSub
}

func (s *redisSubscription) ReceiveMessage(ctx context.Context) (string, error) {
	message, err := s.pubsub.ReceiveMessage(ctx)
	if err != nil {
		return "", err
	}
	return message.Payload, nil
}

func (s *redisSubscription) Close() error {
	return s.pubsub.Close()
}
