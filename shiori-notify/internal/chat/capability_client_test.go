package chat

import (
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHTTPUserCapabilityCheckerIsBanned(t *testing.T) {
	t.Run("banned_when_capability_exists", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{"userId":1001,"capabilities":["CHAT_SEND","ORDER_CREATE"]}`))
		}))
		defer srv.Close()

		checker := NewHTTPUserCapabilityChecker(srv.URL, 0)
		banned, err := checker.IsBanned(1001, "CHAT_SEND")
		if err != nil {
			t.Fatalf("expected no error, got %v", err)
		}
		if !banned {
			t.Fatalf("expected banned=true")
		}
	})

	t.Run("fail_open_on_5xx", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			http.Error(w, "bad gateway", http.StatusBadGateway)
		}))
		defer srv.Close()

		checker := NewHTTPUserCapabilityChecker(srv.URL, 0)
		banned, err := checker.IsBanned(1001, "CHAT_SEND")
		if err != nil {
			t.Fatalf("expected fail-open without error, got %v", err)
		}
		if banned {
			t.Fatalf("expected banned=false on fail-open")
		}
	})

	t.Run("fail_open_on_invalid_payload", func(t *testing.T) {
		srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			w.Header().Set("Content-Type", "application/json")
			_, _ = w.Write([]byte(`{`))
		}))
		defer srv.Close()

		checker := NewHTTPUserCapabilityChecker(srv.URL, 0)
		banned, err := checker.IsBanned(1001, "CHAT_SEND")
		if err != nil {
			t.Fatalf("expected fail-open without error, got %v", err)
		}
		if banned {
			t.Fatalf("expected banned=false on fail-open")
		}
	})
}
