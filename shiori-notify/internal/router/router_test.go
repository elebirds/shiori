package router

import (
	"context"
	"encoding/json"
	"io"
	"testing"

	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/rs/zerolog"
)

type hubCall struct {
	userID  string
	payload []byte
}

type mockHub struct {
	calls      []hubCall
	kickedUser []string
}

func (m *mockHub) SendToUser(userID string, payload []byte) (int, error) {
	m.calls = append(m.calls, hubCall{
		userID:  userID,
		payload: append([]byte(nil), payload...),
	})
	return 1, nil
}

func (m *mockHub) KickUser(userID string) int {
	m.kickedUser = append(m.kickedUser, userID)
	return 1
}

type mockStore struct {
	saveCalls []string
	saveOK    bool
}

func (m *mockStore) Save(userID string, _ event.Envelope) (bool, error) {
	m.saveCalls = append(m.saveCalls, userID)
	return m.saveOK, nil
}

func TestRouteOrderPaid(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	store := &mockStore{saveOK: true}
	r := New(hub, store, &logger)

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
	if len(hub.calls) != 1 {
		t.Fatalf("expected 1 push call, got %d", len(hub.calls))
	}
	if len(store.saveCalls) != 1 {
		t.Fatalf("expected save once, got %d", len(store.saveCalls))
	}
	if hub.calls[0].userID != "u1" {
		t.Fatalf("unexpected routed user: %s", hub.calls[0].userID)
	}

	var got event.Envelope
	if err := json.Unmarshal(hub.calls[0].payload, &got); err != nil {
		t.Fatalf("payload should be envelope json: %v", err)
	}
	if got.EventID != env.EventID {
		t.Fatalf("unexpected payload event id: %s", got.EventID)
	}
}

func TestRouteOrderCreatedToBuyerAndSeller(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	store := &mockStore{saveOK: true}
	r := New(hub, store, &logger)

	env := event.Envelope{
		EventID:     "evt-created",
		Type:        "OrderCreated",
		AggregateID: "order-2",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"buyerUserId":101,"sellerUserId":"202"}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}

	if len(hub.calls) != 2 {
		t.Fatalf("expected 2 push calls, got %d", len(hub.calls))
	}
	if len(store.saveCalls) != 2 {
		t.Fatalf("expected save twice, got %d", len(store.saveCalls))
	}

	users := map[string]bool{}
	for _, call := range hub.calls {
		users[call.userID] = true
	}
	if !users["101"] || !users["202"] {
		t.Fatalf("unexpected users routed: %+v", users)
	}
}

func TestRouteOrderFinishedToBuyerAndSeller(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	store := &mockStore{saveOK: true}
	r := New(hub, store, &logger)

	env := event.Envelope{
		EventID:     "evt-finished",
		Type:        "OrderFinished",
		AggregateID: "order-3",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"buyerUserId":"301","sellerUserId":302}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}

	if len(hub.calls) != 2 {
		t.Fatalf("expected 2 push calls, got %d", len(hub.calls))
	}
	if len(store.saveCalls) != 2 {
		t.Fatalf("expected save twice, got %d", len(store.saveCalls))
	}

	users := map[string]bool{}
	for _, call := range hub.calls {
		users[call.userID] = true
	}
	if !users["301"] || !users["302"] {
		t.Fatalf("unexpected users routed: %+v", users)
	}
}

func TestRouteUserGovernanceEvent(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	store := &mockStore{saveOK: true}
	r := New(hub, store, &logger)

	env := event.Envelope{
		EventID:     "evt-user-1",
		Type:        "UserPasswordReset",
		AggregateID: "10001",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"targetUserId":"10001","mustChangePassword":true}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}
	if len(hub.calls) != 1 {
		t.Fatalf("expected 1 push call, got %d", len(hub.calls))
	}
	if hub.calls[0].userID != "10001" {
		t.Fatalf("unexpected target user: %s", hub.calls[0].userID)
	}
}

func TestRouteUnknownEvent(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	r := New(hub, nil, &logger)

	env := event.Envelope{
		EventID:     "evt-unknown",
		Type:        "UnsupportedType",
		AggregateID: "x-1",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"any":"value"}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}
	if len(hub.calls) != 0 {
		t.Fatalf("unknown event should not push, got calls=%d", len(hub.calls))
	}
}

func TestRouteDeduplicated(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	store := &mockStore{saveOK: false}
	r := New(hub, store, &logger)

	env := event.Envelope{
		EventID:     "evt-dup",
		Type:        "OrderPaid",
		AggregateID: "order-dup",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"userId":"u1"}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}
	if len(store.saveCalls) != 1 {
		t.Fatalf("expected save once, got %d", len(store.saveCalls))
	}
	if len(hub.calls) != 0 {
		t.Fatalf("deduplicated event should not push, got calls=%d", len(hub.calls))
	}
}

func TestRouteAuthzChangeShouldKickUser(t *testing.T) {
	hub := &mockHub{}
	logger := zerolog.New(io.Discard)
	store := &mockStore{saveOK: true}
	r := New(hub, store, &logger)

	env := event.Envelope{
		EventID:     "evt-authz",
		Type:        "UserPermissionOverrideChanged",
		AggregateID: "10001",
		CreatedAt:   "2026-03-02T00:00:00Z",
		Payload:     []byte(`{"userId":"10001","version":3}`),
	}

	if err := r.Route(context.Background(), env); err != nil {
		t.Fatalf("unexpected route error: %v", err)
	}
	if len(hub.kickedUser) != 1 || hub.kickedUser[0] != "10001" {
		t.Fatalf("expected kicked user 10001, got %+v", hub.kickedUser)
	}
	if len(hub.calls) != 0 {
		t.Fatalf("authz change should not push payload, got calls=%d", len(hub.calls))
	}
	if len(store.saveCalls) != 0 {
		t.Fatalf("authz change should not write store, got=%d", len(store.saveCalls))
	}
}
