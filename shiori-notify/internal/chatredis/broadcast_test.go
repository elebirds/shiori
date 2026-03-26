package chatredis

import (
	"context"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/rs/zerolog"
)

type fakePublishClient struct {
	channel string
	payload string
	err     error
}

func (f *fakePublishClient) Publish(_ context.Context, channel string, payload string) error {
	f.channel = channel
	f.payload = payload
	return f.err
}

type fakeSubscription struct {
	messages []string
	index    int
}

func (f *fakeSubscription) ReceiveMessage(ctx context.Context) (string, error) {
	if f.index < len(f.messages) {
		msg := f.messages[f.index]
		f.index++
		return msg, nil
	}
	<-ctx.Done()
	return "", ctx.Err()
}

func (f *fakeSubscription) Close() error {
	return nil
}

type fakeSubscriber struct {
	channel string
	sub     *fakeSubscription
	err     error
}

func (f *fakeSubscriber) Subscribe(_ context.Context, channel string) (subscription, error) {
	f.channel = channel
	if f.err != nil {
		return nil, f.err
	}
	return f.sub, nil
}

type fakeHub struct {
	userID  string
	payload []byte
	sent    int
	err     error
}

func (f *fakeHub) SendToUser(userID string, payload []byte) (int, error) {
	f.userID = userID
	f.payload = append([]byte(nil), payload...)
	f.sent++
	return 1, f.err
}

func TestPublisherPublishMessageAddsOriginInstance(t *testing.T) {
	logger := zerolog.Nop()
	client := &fakePublishClient{}
	publisher := &Publisher{
		enabled:    true,
		channel:    "shiori.chat.event",
		instanceID: "notify-a",
		logger:     &logger,
		client:     client,
	}

	err := publisher.PublishMessage(chat.BroadcastEvent{
		ConversationID: 11,
		MessageID:      21,
		ListingID:      101,
		SenderID:       1001,
		ReceiverID:     2002,
		ClientMsgID:    "msg-1",
		Content:        "hello",
		CreatedAt:      time.Unix(1700000000, 0).UTC(),
	})
	if err != nil {
		t.Fatalf("PublishMessage failed: %v", err)
	}
	if client.channel != "shiori.chat.event" {
		t.Fatalf("unexpected channel: %s", client.channel)
	}

	var event chat.BroadcastEvent
	if err := json.Unmarshal([]byte(client.payload), &event); err != nil {
		t.Fatalf("unmarshal payload failed: %v", err)
	}
	if event.OriginInstance != "notify-a" {
		t.Fatalf("expected origin instance notify-a, got %s", event.OriginInstance)
	}
}

func TestConsumerRunPushesBroadcastMessage(t *testing.T) {
	logger := zerolog.Nop()
	body := `{"conversationId":11,"messageId":21,"listingId":101,"senderId":1001,"receiverId":2002,"clientMsgId":"msg-1","content":"hello","createdAt":"2026-03-26T10:00:00Z","originInstanceId":"notify-a"}`
	hub := &fakeHub{}
	subscriber := &fakeSubscriber{
		sub: &fakeSubscription{messages: []string{body}},
	}
	consumer := &Consumer{
		enabled:    true,
		channel:    "shiori.chat.event",
		instanceID: "notify-b",
		hub:        hub,
		logger:     &logger,
		subscriber: subscriber,
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() {
		done <- consumer.Run(ctx)
	}()

	deadline := time.After(300 * time.Millisecond)
	for hub.sent == 0 {
		select {
		case <-deadline:
			cancel()
			t.Fatal("expected broadcast message to be delivered")
		default:
			time.Sleep(10 * time.Millisecond)
		}
	}
	cancel()
	if err := <-done; err != nil && !errors.Is(err, context.Canceled) {
		t.Fatalf("Run returned error: %v", err)
	}
	if subscriber.channel != "shiori.chat.event" {
		t.Fatalf("unexpected subscribe channel: %s", subscriber.channel)
	}
	if hub.userID != "2002" {
		t.Fatalf("unexpected receiver userID: %s", hub.userID)
	}
}

func TestConsumerRunSkipsSelfOriginatedMessage(t *testing.T) {
	logger := zerolog.Nop()
	body := `{"conversationId":11,"messageId":21,"listingId":101,"senderId":1001,"receiverId":2002,"clientMsgId":"msg-1","content":"hello","createdAt":"2026-03-26T10:00:00Z","originInstanceId":"notify-a"}`
	hub := &fakeHub{}
	consumer := &Consumer{
		enabled:    true,
		channel:    "shiori.chat.event",
		instanceID: "notify-a",
		hub:        hub,
		logger:     &logger,
		subscriber: &fakeSubscriber{
			sub: &fakeSubscription{messages: []string{body}},
		},
	}

	ctx, cancel := context.WithCancel(context.Background())
	done := make(chan error, 1)
	go func() {
		done <- consumer.Run(ctx)
	}()

	time.Sleep(120 * time.Millisecond)
	cancel()
	if err := <-done; err != nil && !errors.Is(err, context.Canceled) {
		t.Fatalf("Run returned error: %v", err)
	}
	if hub.sent != 0 {
		t.Fatalf("expected self-originated message to be ignored, got sent=%d", hub.sent)
	}
}
