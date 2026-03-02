package router

import (
	"context"
	"encoding/json"
	"io"
	"testing"

	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/rs/zerolog"
)

type mockHub struct {
	userID  string
	payload []byte
	calls   int
}

func (m *mockHub) SendToUser(userID string, payload []byte) (int, error) {
	m.calls++
	m.userID = userID
	m.payload = payload
	return 1, nil
}

func TestRouteOrderPaid(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	r := New(hub, &logger)

	env := event.Envelope{
		EventID:     "evt-1",
		Type:        "OrderPaid",
		AggregateID: "order-1",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"userId":"u1"}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}
	if hub.calls != 1 {
		t.Fatalf("expected 1 call, got %d", hub.calls)
	}
	if hub.userID != "u1" {
		t.Fatalf("unexpected routed user: %s", hub.userID)
	}

	var got event.Envelope
	if err := json.Unmarshal(hub.payload, &got); err != nil {
		t.Fatalf("payload should be envelope json: %v", err)
	}
	if got.EventID != env.EventID {
		t.Fatalf("unexpected payload event id: %s", got.EventID)
	}
}

func TestRouteUnknownEvent(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	r := New(hub, &logger)

	env := event.Envelope{
		EventID:     "evt-2",
		Type:        "OrderCanceled",
		AggregateID: "order-2",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"userId":"u2"}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}
	if hub.calls != 0 {
		t.Fatalf("unknown event should not route, got calls=%d", hub.calls)
	}
}
