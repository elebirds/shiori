package store

import (
	"testing"

	"github.com/hhm/shiori/shiori-notify/internal/event"
)

func TestMemoryEventStoreSaveListAndCursor(t *testing.T) {
	s := NewMemoryEventStore(10)
	s.Save("u1", envelope("evt-1"))
	s.Save("u1", envelope("evt-2"))
	s.Save("u1", envelope("evt-3"))

	items, next, hasMore := s.List("u1", "", 2)
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if next != "evt-2" {
		t.Fatalf("expected next evt-2, got %s", next)
	}
	if !hasMore {
		t.Fatalf("expected hasMore=true")
	}

	items, next, hasMore = s.List("u1", "evt-2", 2)
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

func TestMemoryEventStoreDeduplicateByEventID(t *testing.T) {
	s := NewMemoryEventStore(10)
	if !s.Save("u1", envelope("evt-1")) {
		t.Fatalf("first save should succeed")
	}
	if s.Save("u1", envelope("evt-1")) {
		t.Fatalf("duplicate save should fail")
	}
	items, _, _ := s.List("u1", "", 10)
	if len(items) != 1 {
		t.Fatalf("expected 1 item, got %d", len(items))
	}
}

func TestMemoryEventStoreRetention(t *testing.T) {
	s := NewMemoryEventStore(2)
	s.Save("u1", envelope("evt-1"))
	s.Save("u1", envelope("evt-2"))
	s.Save("u1", envelope("evt-3"))

	items, _, _ := s.List("u1", "", 10)
	if len(items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(items))
	}
	if items[0].EventID != "evt-2" || items[1].EventID != "evt-3" {
		t.Fatalf("unexpected retained items: %+v", items)
	}

	// 已淘汰的 cursor 不存在时，会从可用窗口头部返回。
	items, _, _ = s.List("u1", "evt-1", 10)
	if len(items) != 2 || items[0].EventID != "evt-2" {
		t.Fatalf("unexpected list after evicted cursor: %+v", items)
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
