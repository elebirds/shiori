package kafkaevent

import (
	"context"
	"testing"

	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/rs/zerolog"
)

type fakeRouter struct {
	called   int
	envelope event.Envelope
}

func (f *fakeRouter) Route(_ context.Context, env event.Envelope) error {
	f.called++
	f.envelope = env
	return nil
}

func TestHandleMessageRoutesValidOrderOutboxCDC(t *testing.T) {
	t.Parallel()

	router := &fakeRouter{}
	logger := zerolog.Nop()
	consumer := NewConsumer(nil, router, &logger)

	raw := []byte(`{
		"event_id":"event-1",
		"aggregate_type":"order",
		"aggregate_id":"O1001",
		"message_key":"O1001",
		"type":"OrderPaid",
		"payload":"{\"eventId\":\"event-1\",\"type\":\"OrderPaid\",\"aggregateId\":\"O1001\",\"createdAt\":\"2026-03-07T00:00:00Z\",\"payload\":{\"userId\":1001}}",
		"status":"PENDING"
	}`)

	consumer.handleMessage(context.Background(), raw)

	if router.called != 1 {
		t.Fatalf("expected route to be called once, got %d", router.called)
	}
	if router.envelope.EventID != "event-1" {
		t.Fatalf("expected eventId=event-1, got %s", router.envelope.EventID)
	}
	if router.envelope.Type != "OrderPaid" {
		t.Fatalf("expected type=OrderPaid, got %s", router.envelope.Type)
	}
}

func TestHandleMessageRoutesSchemaWrappedOrderOutboxCDC(t *testing.T) {
	t.Parallel()

	router := &fakeRouter{}
	logger := zerolog.Nop()
	consumer := NewConsumer(nil, router, &logger)

	raw := []byte(`{
		"schema":{"type":"struct"},
		"payload":{
			"event_id":"event-3",
			"aggregate_type":"order",
			"aggregate_id":"O1003",
			"message_key":"O1003",
			"type":"OrderPaid",
			"payload":"{\"eventId\":\"event-3\",\"type\":\"OrderPaid\",\"aggregateId\":\"O1003\",\"createdAt\":\"2026-03-07T00:00:00Z\",\"payload\":{\"userId\":1003}}",
			"status":"PENDING"
		}
	}`)

	consumer.handleMessage(context.Background(), raw)

	if router.called != 1 {
		t.Fatalf("expected route to be called once, got %d", router.called)
	}
	if router.envelope.EventID != "event-3" {
		t.Fatalf("expected eventId=event-3, got %s", router.envelope.EventID)
	}
	if router.envelope.Type != "OrderPaid" {
		t.Fatalf("expected type=OrderPaid, got %s", router.envelope.Type)
	}
}

func TestHandleMessageIgnoresWhenOutboxStatusNotPending(t *testing.T) {
	t.Parallel()

	router := &fakeRouter{}
	logger := zerolog.Nop()
	consumer := NewConsumer(nil, router, &logger)

	raw := []byte(`{
		"event_id":"event-2",
		"aggregate_type":"user",
		"aggregate_id":"1001",
		"message_key":"1001",
		"type":"UserStatusChanged",
		"payload":"{\"eventId\":\"event-2\",\"type\":\"UserStatusChanged\",\"aggregateId\":\"1001\",\"createdAt\":\"2026-03-07T00:00:00Z\",\"payload\":{\"targetUserId\":1001}}",
		"status":"SENT"
	}`)

	consumer.handleMessage(context.Background(), raw)

	if router.called != 0 {
		t.Fatalf("expected route not to be called, got %d", router.called)
	}
}
