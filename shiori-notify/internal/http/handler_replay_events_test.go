package notifyhttp

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/hhm/shiori/shiori-notify/internal/event"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

func TestHandleReplayEvents(t *testing.T) {
	cfg := config.Config{
		ReplayDefaultLimit: 2,
		ReplayMaxLimit:     5,
		WSReplayLimit:      3,
	}
	eventStore := store.NewMemoryEventStore(20)
	eventStore.Save("u1", testEnvelope("evt-1"))
	eventStore.Save("u1", testEnvelope("evt-2"))
	eventStore.Save("u1", testEnvelope("evt-3"))

	logger := zerolog.New(io.Discard)
	srv := NewServer(cfg, ws.NewHub(), eventStore, &logger)

	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/notify/events?userId=u1&limit=2", nil)
	srv.engine.ServeHTTP(rec, req)
	if rec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec.Code)
	}

	resp := decodeReplayResp(t, rec.Body.Bytes())
	if resp.Code != 0 {
		t.Fatalf("expected code=0, got %d", resp.Code)
	}
	if len(resp.Data.Items) != 2 {
		t.Fatalf("expected 2 items, got %d", len(resp.Data.Items))
	}
	if !resp.Data.HasMore {
		t.Fatalf("expected hasMore=true")
	}
	if resp.Data.NextEventID != "evt-2" {
		t.Fatalf("unexpected nextEventId: %s", resp.Data.NextEventID)
	}

	rec2 := httptest.NewRecorder()
	req2 := httptest.NewRequest(http.MethodGet, "/api/notify/events?userId=u1&afterEventId=evt-2&limit=2", nil)
	srv.engine.ServeHTTP(rec2, req2)
	if rec2.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", rec2.Code)
	}
	resp2 := decodeReplayResp(t, rec2.Body.Bytes())
	if len(resp2.Data.Items) != 1 || resp2.Data.Items[0].EventID != "evt-3" {
		t.Fatalf("unexpected items: %+v", resp2.Data.Items)
	}
	if resp2.Data.HasMore {
		t.Fatalf("expected hasMore=false")
	}
}

func TestHandleReplayEventsInvalidParam(t *testing.T) {
	cfg := config.Config{
		ReplayDefaultLimit: 2,
		ReplayMaxLimit:     5,
	}
	logger := zerolog.New(io.Discard)
	srv := NewServer(cfg, ws.NewHub(), store.NewMemoryEventStore(10), &logger)

	rec := httptest.NewRecorder()
	req := httptest.NewRequest(http.MethodGet, "/api/notify/events?limit=2", nil)
	srv.engine.ServeHTTP(rec, req)
	if rec.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec.Code)
	}

	rec2 := httptest.NewRecorder()
	req2 := httptest.NewRequest(http.MethodGet, "/api/notify/events?userId=u1&limit=-1", nil)
	srv.engine.ServeHTTP(rec2, req2)
	if rec2.Code != http.StatusBadRequest {
		t.Fatalf("expected 400, got %d", rec2.Code)
	}
}

type replayResp struct {
	Code int    `json:"code"`
	Msg  string `json:"message"`
	Data struct {
		UserID       string           `json:"userId"`
		AfterEventID string           `json:"afterEventId"`
		Limit        int              `json:"limit"`
		Items        []event.Envelope `json:"items"`
		NextEventID  string           `json:"nextEventId"`
		HasMore      bool             `json:"hasMore"`
	} `json:"data"`
}

func decodeReplayResp(t *testing.T, body []byte) replayResp {
	t.Helper()
	var resp replayResp
	if err := json.Unmarshal(body, &resp); err != nil {
		t.Fatalf("decode response failed: %v, body=%s", err, string(body))
	}
	return resp
}

func testEnvelope(eventID string) event.Envelope {
	return event.Envelope{
		EventID:     eventID,
		Type:        "OrderPaid",
		AggregateID: "O-1",
		CreatedAt:   "2026-03-04T00:00:00Z",
		Payload:     []byte(`{"userId":"u1"}`),
	}
}
