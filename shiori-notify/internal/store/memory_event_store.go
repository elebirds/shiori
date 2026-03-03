package store

import (
	"sync"

	"github.com/hhm/shiori/shiori-notify/internal/event"
)

const (
	defaultMaxPerUser = 1000
	defaultListLimit  = 50
	maxListLimit      = 500
)

type EventStore interface {
	Save(userID string, env event.Envelope) bool
	List(userID, afterEventID string, limit int) (items []event.Envelope, nextEventID string, hasMore bool)
}

type MemoryEventStore struct {
	mu         sync.RWMutex
	maxPerUser int
	seen       map[string]struct{}
	byUser     map[string][]event.Envelope
	index      map[string]map[string]int
}

func NewMemoryEventStore(maxPerUser int) *MemoryEventStore {
	if maxPerUser <= 0 {
		maxPerUser = defaultMaxPerUser
	}
	return &MemoryEventStore{
		maxPerUser: maxPerUser,
		seen:       make(map[string]struct{}),
		byUser:     make(map[string][]event.Envelope),
		index:      make(map[string]map[string]int),
	}
}

func (s *MemoryEventStore) Save(userID string, env event.Envelope) bool {
	if userID == "" || env.EventID == "" {
		return false
	}

	s.mu.Lock()
	defer s.mu.Unlock()

	if _, exists := s.seen[env.EventID]; exists {
		return false
	}

	cloned := cloneEnvelope(env)
	s.byUser[userID] = append(s.byUser[userID], cloned)
	if _, ok := s.index[userID]; !ok {
		s.index[userID] = make(map[string]int)
	}
	s.index[userID][cloned.EventID] = len(s.byUser[userID]) - 1
	s.seen[cloned.EventID] = struct{}{}

	if len(s.byUser[userID]) > s.maxPerUser {
		evicted := s.byUser[userID][0]
		s.byUser[userID] = s.byUser[userID][1:]
		delete(s.seen, evicted.EventID)
		s.rebuildIndexLocked(userID)
	}

	return true
}

func (s *MemoryEventStore) List(userID, afterEventID string, limit int) ([]event.Envelope, string, bool) {
	if userID == "" {
		return nil, "", false
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
		return nil, "", false
	}

	start := 0
	if afterEventID != "" {
		if idx, ok := s.index[userID][afterEventID]; ok {
			start = idx + 1
		}
	}

	if start >= len(events) {
		return nil, "", false
	}

	end := start + limit
	if end > len(events) {
		end = len(events)
	}

	items := make([]event.Envelope, 0, end-start)
	for i := start; i < end; i++ {
		items = append(items, cloneEnvelope(events[i]))
	}

	nextEventID := ""
	if len(items) > 0 {
		nextEventID = items[len(items)-1].EventID
	}
	hasMore := end < len(events)
	return items, nextEventID, hasMore
}

func (s *MemoryEventStore) rebuildIndexLocked(userID string) {
	m := make(map[string]int, len(s.byUser[userID]))
	for i := range s.byUser[userID] {
		m[s.byUser[userID][i].EventID] = i
	}
	s.index[userID] = m
}

func cloneEnvelope(src event.Envelope) event.Envelope {
	clone := src
	if src.Payload != nil {
		clone.Payload = append([]byte(nil), src.Payload...)
	}
	return clone
}
