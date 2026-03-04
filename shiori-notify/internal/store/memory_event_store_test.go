package store

import (
	"testing"

	"github.com/hhm/shiori/shiori-notify/internal/event"
)

func TestMemoryEventStoreSaveListAndCursor(t *testing.T) {
	s := NewMemoryEventStore(10)
	_, _ = s.Save("u1", envelope("evt-1"))
	_, _ = s.Save("u1", envelope("evt-2"))
	_, _ = s.Save("u1", envelope("evt-3"))

	items, next, hasMore, err := s.List("u1", "", 2)
	if err != nil {
		t.Fatalf("list failed: %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if next != "evt-2" {
		t.Fatalf("expected next evt-2, got %s", next)
	}
	if !hasMore {
		t.Fatalf("expected hasMore=true")
	}

	items, next, hasMore, err = s.List("u1", "evt-2", 2)
	if err != nil {
		t.Fatalf("list with cursor failed: %v", err)
	}
	if len(items) != 1 || items[0].EventID != "evt-3" {
		t.Fatalf("unexpected cursor list result: %+v", items)
	}
	if next != "evt-3" {
		t.Fatalf("unexpected next: %s", next)
	}
	if hasMore {
		t.Fatalf("expected hasMore=false")
	}
}

func TestMemoryEventStoreDeduplicateByUserIDAndEventID(t *testing.T) {
	s := NewMemoryEventStore(10)
	ok, _ := s.Save("u1", envelope("evt-1"))
	if !ok {
		t.Fatalf("first save should succeed")
	}
	ok, _ = s.Save("u1", envelope("evt-1"))
	if ok {
		t.Fatalf("duplicate save should fail")
	}
	ok, _ = s.Save("u2", envelope("evt-1"))
	if !ok {
		t.Fatalf("same eventId for different user should succeed")
	}
}

func TestMemoryEventStoreRetention(t *testing.T) {
	s := NewMemoryEventStore(2)
	_, _ = s.Save("u1", envelope("evt-1"))
	_, _ = s.Save("u1", envelope("evt-2"))
	_, _ = s.Save("u1", envelope("evt-3"))

	items, _, _, err := s.List("u1", "", 10)
	if err != nil {
		t.Fatalf("list failed: %v", err)
	}
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if items[0].EventID != "evt-2" || items[1].EventID != "evt-3" {
		t.Fatalf("unexpected retained items: %+v", items)
	}

	items, _, _, err = s.List("u1", "evt-1", 10)
	if err != nil {
		t.Fatalf("list with evicted cursor failed: %v", err)
	}
	if len(items) != 2 || items[0].EventID != "evt-2" {
		t.Fatalf("unexpected list after evicted cursor: %+v", items)
	}
}

func TestMemoryEventStoreReadOps(t *testing.T) {
	s := NewMemoryEventStore(10)
	_, _ = s.Save("u1", envelope("evt-1"))
	_, _ = s.Save("u1", envelope("evt-2"))

	unread, err := s.UnreadCount("u1")
	if err != nil {
		t.Fatalf("unread count failed: %v", err)
	}
	if unread != 2 {
		t.Fatalf("expected unread=2, got %d", unread)
	}

	marked, err := s.MarkRead("u1", "evt-1")
	if err != nil {
		t.Fatalf("mark read failed: %v", err)
	}
	if !marked {
		t.Fatalf("expected mark read success")
	}

	unread, err = s.UnreadCount("u1")
	if err != nil {
		t.Fatalf("unread count failed: %v", err)
	}
	if unread != 1 {
		t.Fatalf("expected unread=1, got %d", unread)
	}

	affected, err := s.MarkAllRead("u1")
	if err != nil {
		t.Fatalf("mark all read failed: %v", err)
	}
	if affected != 1 {
		t.Fatalf("expected affected=1, got %d", affected)
	}

	unread, err = s.UnreadCount("u1")
	if err != nil {
		t.Fatalf("unread count failed: %v", err)
	}
	if unread != 0 {
		t.Fatalf("expected unread=0, got %d", unread)
	}
}

func envelope(eventID string) event.Envelope {
	return event.Envelope{
		EventID:     eventID,
		Type:        "OrderPaid",
		AggregateID: "order-1",
		CreatedAt:   "2026-03-04T00:00:00Z",
		Payload:     []byte(`{"userId":"u1"}`),
	}
}

