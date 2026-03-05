package chat

import (
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
)

func TestHTTPUserCapabilityCheckerIsBannedRetryAndErrors(t *testing.T) {
	t.Run("retry_once_on_5xx_then_success", func(t *testing.T) {
		var callCount int32
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			current := atomic.AddInt32(&callCount, 1)
			if current == 1 {
				http.Error(w, "bad gateway", http.StatusBadGateway)
				return
			}
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"userId":1001,"capabilities":["CHAT_SEND"]}`))
		}))
		defer srv.Close()

		checker := NewHTTPUserCapabilityChecker(srv.URL, 0)
		banned, err := checker.IsBanned(1001, "CHAT_SEND")
		if err != nil {
			t.Fatalf("expected retry success, got error=%v", err)
		}
		if !banned {
			t.Fatalf("expected banned=true after retry")
		}
		if atomic.LoadInt32(&callCount) != 2 {
			t.Fatalf("expected 2 calls, got %d", atomic.LoadInt32(&callCount))
		}
	})

	t.Run("returns_typed_error_on_4xx", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, "bad request", http.StatusBadRequest)
		}))
		defer srv.Close()

		checker := NewHTTPUserCapabilityChecker(srv.URL, 0)
		_, err := checker.IsBanned(1001, "CHAT_SEND")
		if err == nil {
			t.Fatalf("expected error")
		}
		typed, ok := err.(*ErrCapabilityCheckFailed)
		if !ok {
			t.Fatalf("expected ErrCapabilityCheckFailed, got %T", err)
		}
		if typed.Reason != "upstream_status" || typed.StatusCode != http.StatusBadRequest {
			t.Fatalf("unexpected typed error: %+v", typed)
		}
	})

	t.Run("returns_typed_error_on_decode", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{`))
		}))
		defer srv.Close()

		checker := NewHTTPUserCapabilityChecker(srv.URL, 0)
		_, err := checker.IsBanned(1001, "CHAT_SEND")
		if err == nil {
			t.Fatalf("expected error")
		}
		typed, ok := err.(*ErrCapabilityCheckFailed)
		if !ok {
			t.Fatalf("expected ErrCapabilityCheckFailed, got %T", err)
		}
		if typed.Reason != "decode_error" {
			t.Fatalf("unexpected reason: %s", typed.Reason)
		}
	})
}
