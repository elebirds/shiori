package store

import (
	"encoding/json"
	"sync"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/event"
)

const (
	defaultMaxPerUser = 1000
	defaultListLimit  = 50
	maxListLimit      = 500
)

type EventStore interface {
	Save(userID string, env event.Envelope) (bool, error)
	List(userID, afterEventID string, limit int) (items []NotificationEvent, nextEventID string, hasMore bool, err error)
	MarkRead(userID, eventID string) (bool, error)
	MarkAllRead(userID string) (int64, error)
	UnreadCount(userID string) (int64, error)
}

type NotificationEvent struct {
	EventID     string          `json:"eventId"`
	Type        string          `json:"type"`
	AggregateID string          `json:"aggregateId"`
	CreatedAt   string          `json:"createdAt"`
	Payload     json.RawMessage `json:"payload"`
	Read        bool            `json:"read"`
	ReadAt      string          `json:"readAt,omitempty"`
}

type MemoryEventStore struct {
	mu         sync.RWMutex
	maxPerUser int
	byUser     map[string][]NotificationEvent
	index      map[string]map[string]int
}

func NewMemoryEventStore(maxPerUser int) *MemoryEventStore {
	if maxPerUser <= 0 {
		maxPerUser = defaultMaxPerUser
	}
	return &MemoryEventStore{
		maxPerUser: maxPerUser,
		byUser:     make(map[string][]NotificationEvent),
		index:      make(map[string]map[string]int),
	}
}

func (s *MemoryEventStore) Save(userID string, env event.Envelope) (bool, error) {
	if userID == "" || env.EventID == "" {
		return false, nil
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	if _, ok := s.index[userID]; !ok {
		s.index[userID] = make(map[string]int)
	}
	if _, exists := s.index[userID][env.EventID]; exists {
		return false, nil
	}

	clonedPayload := append([]byte(nil), env.Payload...)
	entry := NotificationEvent{
		EventID:     env.EventID,
		Type:        env.Type,
		AggregateID: env.AggregateID,
		CreatedAt:   env.CreatedAt,
		Payload:     clonedPayload,
		Read:        false,
		ReadAt:      "",
	}
	s.byUser[userID] = append(s.byUser[userID], entry)
	s.index[userID][entry.EventID] = len(s.byUser[userID]) - 1

	if len(s.byUser[userID]) > s.maxPerUser {
		s.byUser[userID] = s.byUser[userID][1:]
		s.rebuildIndexLocked(userID)
	}

	return true, nil
}

func (s *MemoryEventStore) List(userID, afterEventID string, limit int) ([]NotificationEvent, string, bool, error) {
	if userID == "" {
		return nil, "", false, nil
	}

	if limit <= 0 {
		limit = defaultListLimit
	}
	if limit > maxListLimit {
		limit = maxListLimit
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	events := s.byUser[userID]
	if len(events) == 0 {
		return nil, "", false, nil
	}

	start := 0
	if afterEventID != "" {
		if idx, ok := s.index[userID][afterEventID]; ok {
			start = idx + 1
		}
	}

	if start >= len(events) {
		return nil, "", false, nil
	}

	end := start + limit
	if end > len(events) {
		end = len(events)
	}

	items := make([]NotificationEvent, 0, end-start)
	for i := start; i < end; i++ {
		items = append(items, cloneNotificationEvent(events[i]))
	}

	nextEventID := ""
	if len(items) > 0 {
		nextEventID = items[len(items)-1].EventID
	}
	hasMore := end < len(events)
	return items, nextEventID, hasMore, nil
}

func (s *MemoryEventStore) MarkRead(userID, eventID string) (bool, error) {
	if userID == "" || eventID == "" {
		return false, nil
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	idx, ok := s.index[userID][eventID]
	if !ok {
		return false, nil
	}
	if idx < 0 || idx >= len(s.byUser[userID]) {
		return false, nil
	}
	if s.byUser[userID][idx].Read {
		return false, nil
	}

	s.byUser[userID][idx].Read = true
	s.byUser[userID][idx].ReadAt = time.Now().UTC().Format(time.RFC3339Nano)
	return true, nil
}

func (s *MemoryEventStore) MarkAllRead(userID string) (int64, error) {
	if userID == "" {
		return 0, nil
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	events := s.byUser[userID]
	if len(events) == 0 {
		return 0, nil
	}

	now := time.Now().UTC().Format(time.RFC3339Nano)
	var affected int64
	for i := range events {
		if events[i].Read {
			continue
		}
		events[i].Read = true
		events[i].ReadAt = now
		affected++
	}
	s.byUser[userID] = events
	return affected, nil
}

func (s *MemoryEventStore) UnreadCount(userID string) (int64, error) {
	if userID == "" {
		return 0, nil
	}

	s.mu.RLock()
	defer s.mu.RUnlock()

	var unread int64
	for _, item := range s.byUser[userID] {
		if !item.Read {
			unread++
		}
	}
	return unread, nil
}

func (s *MemoryEventStore) rebuildIndexLocked(userID string) {
	m := make(map[string]int, len(s.byUser[userID]))
	for i := range s.byUser[userID] {
		m[s.byUser[userID][i].EventID] = i
	}
	s.index[userID] = m
}

func cloneNotificationEvent(src NotificationEvent) NotificationEvent {
	clone := src
	if src.Payload != nil {
		clone.Payload = append([]byte(nil), src.Payload...)
	}
	return clone
}
