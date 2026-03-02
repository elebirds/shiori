package ws

import (
	"errors"
	"sync"
	"testing"
)

type mockSender struct {
	mu      sync.Mutex
	sendErr error
	closed  bool
	sent    int
}

func (m *mockSender) Send(_ []byte) error {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.sendErr != nil {
		return m.sendErr
	}
	m.sent++
	return nil
}

func (m *mockSender) Close() error {
	m.mu.Lock()
	defer m.mu.Unlock()
	m.closed = true
	return nil
}

func TestHubRegisterRemoveAndCount(t *testing.T) {
	hub := NewHub()
	s1 := &mockSender{}
	s2 := &mockSender{}

	hub.Register("u1", s1)
	hub.Register("u1", s2)
	if got := hub.ConnectionCount(); got != 2 {
		t.Fatalf("unexpected connection count: %d", got)
	}
	if got := hub.UserConnectionCount("u1"); got != 2 {
		t.Fatalf("unexpected user connection count: %d", got)
	}

	hub.Remove("u1", s1)
	if got := hub.ConnectionCount(); got != 1 {
		t.Fatalf("unexpected connection count after remove: %d", got)
	}
}

func TestHubSendToUserNoSession(t *testing.T) {
	hub := NewHub()
	_, err := hub.SendToUser("missing", []byte("hello"))
	if !errors.Is(err, ErrNoSession) {
		t.Fatalf("expected ErrNoSession, got: %v", err)
	}
}

func TestHubSendToUserConcurrent(t *testing.T) {
	hub := NewHub()

	const size = 50
	wg := sync.WaitGroup{}
	for i := 0; i < size; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			hub.Register("u1", &mockSender{})
		}()
	}
	wg.Wait()

	sent, err := hub.SendToUser("u1", []byte("payload"))
	if err != nil {
		t.Fatalf("unexpected send error: %v", err)
	}
	if sent != size {
		t.Fatalf("unexpected sent count: %d", sent)
	}
}
