package ws

import (
	"errors"
	"sync"
)

var ErrNoSession = errors.New("no active websocket session")

type Sender interface {
	Send(payload []byte) error
	Close() error
}

type Hub struct {
	mu       sync.RWMutex
	sessions map[string]map[Sender]struct{}
}

func NewHub() *Hub {
	return &Hub{sessions: make(map[string]map[Sender]struct{})}
}

func (h *Hub) Register(userID string, sender Sender) {
	if userID == "" || sender == nil {
		return
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	if _, ok := h.sessions[userID]; !ok {
		h.sessions[userID] = make(map[Sender]struct{})
	}
	h.sessions[userID][sender] = struct{}{}
}

func (h *Hub) Remove(userID string, sender Sender) {
	if userID == "" || sender == nil {
		return
	}

	h.mu.Lock()
	defer h.mu.Unlock()

	h.removeLocked(userID, sender)
}

func (h *Hub) SendToUser(userID string, payload []byte) (int, error) {
	h.mu.RLock()
	userSessions, ok := h.sessions[userID]
	if !ok || len(userSessions) == 0 {
		h.mu.RUnlock()
		return 0, ErrNoSession
	}

	senders := make([]Sender, 0, len(userSessions))
	for sender := range userSessions {
		senders = append(senders, sender)
	}
	h.mu.RUnlock()

	sent := 0
	var lastErr error
	for _, sender := range senders {
		if err := sender.Send(payload); err != nil {
			lastErr = err
			_ = sender.Close()
			h.Remove(userID, sender)
			continue
		}
		sent++
	}

	if sent == 0 {
		if lastErr != nil {
			return 0, lastErr
		}
		return 0, ErrNoSession
	}

	return sent, nil
}

func (h *Hub) ConnectionCount() int {
	h.mu.RLock()
	defer h.mu.RUnlock()

	total := 0
	for _, sessions := range h.sessions {
		total += len(sessions)
	}
	return total
}

func (h *Hub) UserConnectionCount(userID string) int {
	h.mu.RLock()
	defer h.mu.RUnlock()

	return len(h.sessions[userID])
}

func (h *Hub) removeLocked(userID string, sender Sender) {
	userSessions, ok := h.sessions[userID]
	if !ok {
		return
	}
	delete(userSessions, sender)
	if len(userSessions) == 0 {
		delete(h.sessions, userID)
	}
}
