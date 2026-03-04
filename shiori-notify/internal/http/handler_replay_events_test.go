package notifyhttp

import (
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/golang-jwt/jwt/v5"
	notifyauth "github.com/hhm/shiori/shiori-notify/internal/auth"
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
		AuthEnabled:        false,
	}
	eventStore := store.NewMemoryEventStore(20)
	_, _ = eventStore.Save("u1", testEnvelope("evt-1"))
	_, _ = eventStore.Save("u1", testEnvelope("evt-2"))
	_, _ = eventStore.Save("u1", testEnvelope("evt-3"))

	logger := zerolog.New(io.Discard)
	srv := NewServer(cfg, ws.NewHub(), eventStore, nil, &logger)

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
		AuthEnabled:        false,
	}
	logger := zerolog.New(io.Discard)
	srv := NewServer(cfg, ws.NewHub(), store.NewMemoryEventStore(10), nil, &logger)

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

func TestReadAndSummaryEndpoints(t *testing.T) {
	cfg := config.Config{
		ReplayDefaultLimit: 20,
		ReplayMaxLimit:     50,
		AuthEnabled:        false,
	}
	logger := zerolog.New(io.Discard)
	eventStore := store.NewMemoryEventStore(10)
	_, _ = eventStore.Save("u1", testEnvelope("evt-1"))
	_, _ = eventStore.Save("u1", testEnvelope("evt-2"))
	srv := NewServer(cfg, ws.NewHub(), eventStore, nil, &logger)

	recSummary1 := httptest.NewRecorder()
	reqSummary1 := httptest.NewRequest(http.MethodGet, "/api/notify/summary?userId=u1", nil)
	srv.engine.ServeHTTP(recSummary1, reqSummary1)
	if recSummary1.Code != http.StatusOK {
		t.Fatalf("summary 1 expected 200, got %d", recSummary1.Code)
	}
	if got := decodeUnreadCount(t, recSummary1.Body.Bytes()); got != 2 {
		t.Fatalf("expected unread=2, got %d", got)
	}

	recMark := httptest.NewRecorder()
	reqMark := httptest.NewRequest(http.MethodPost, "/api/notify/events/evt-1/read?userId=u1", nil)
	srv.engine.ServeHTTP(recMark, reqMark)
	if recMark.Code != http.StatusOK {
		t.Fatalf("mark read expected 200, got %d", recMark.Code)
	}

	recSummary2 := httptest.NewRecorder()
	reqSummary2 := httptest.NewRequest(http.MethodGet, "/api/notify/summary?userId=u1", nil)
	srv.engine.ServeHTTP(recSummary2, reqSummary2)
	if recSummary2.Code != http.StatusOK {
		t.Fatalf("summary 2 expected 200, got %d", recSummary2.Code)
	}
	if got := decodeUnreadCount(t, recSummary2.Body.Bytes()); got != 1 {
		t.Fatalf("expected unread=1, got %d", got)
	}

	recReadAll := httptest.NewRecorder()
	reqReadAll := httptest.NewRequest(http.MethodPost, "/api/notify/events/read-all?userId=u1", nil)
	srv.engine.ServeHTTP(recReadAll, reqReadAll)
	if recReadAll.Code != http.StatusOK {
		t.Fatalf("read-all expected 200, got %d", recReadAll.Code)
	}

	recSummary3 := httptest.NewRecorder()
	reqSummary3 := httptest.NewRequest(http.MethodGet, "/api/notify/summary?userId=u1", nil)
	srv.engine.ServeHTTP(recSummary3, reqSummary3)
	if recSummary3.Code != http.StatusOK {
		t.Fatalf("summary 3 expected 200, got %d", recSummary3.Code)
	}
	if got := decodeUnreadCount(t, recSummary3.Body.Bytes()); got != 0 {
		t.Fatalf("expected unread=0, got %d", got)
	}
}

func TestAuthEnabledRequiresBearerToken(t *testing.T) {
	cfg := config.Config{
		ReplayDefaultLimit: 20,
		ReplayMaxLimit:     50,
		AuthEnabled:        true,
	}
	logger := zerolog.New(io.Discard)
	eventStore := store.NewMemoryEventStore(10)
	_, _ = eventStore.Save("1001", testEnvelope("evt-1"))

	verifier, err := notifyauth.NewJWTVerifier(true, "test-secret", "shiori")
	if err != nil {
		t.Fatalf("new verifier failed: %v", err)
	}
	srv := NewServer(cfg, ws.NewHub(), eventStore, verifier, &logger)

	unauthorizedRec := httptest.NewRecorder()
	unauthorizedReq := httptest.NewRequest(http.MethodGet, "/api/notify/summary", nil)
	srv.engine.ServeHTTP(unauthorizedRec, unauthorizedReq)
	if unauthorizedRec.Code != http.StatusUnauthorized {
		t.Fatalf("expected 401, got %d", unauthorizedRec.Code)
	}

	token := jwt.NewWithClaims(jwt.SigningMethodHS256, jwt.MapClaims{
		"uid": "1001",
		"iss": "shiori",
		"exp": 32503680000, // year 3000
	})
	tokenText, err := token.SignedString([]byte("test-secret"))
	if err != nil {
		t.Fatalf("sign token failed: %v", err)
	}

	authorizedRec := httptest.NewRecorder()
	authorizedReq := httptest.NewRequest(http.MethodGet, "/api/notify/summary", nil)
	authorizedReq.Header.Set("Authorization", "Bearer "+tokenText)
	srv.engine.ServeHTTP(authorizedRec, authorizedReq)
	if authorizedRec.Code != http.StatusOK {
		t.Fatalf("expected 200, got %d", authorizedRec.Code)
	}
}

type replayResp struct {
	Code int    `json:"code"`
	Msg  string `json:"message"`
	Data struct {
		AfterEventID string                    `json:"afterEventId"`
		Limit        int                       `json:"limit"`
		Items        []store.NotificationEvent `json:"items"`
		NextEventID  string                    `json:"nextEventId"`
		HasMore      bool                      `json:"hasMore"`
	} `json:"data"`
}

type unreadSummaryResp struct {
	Code int `json:"code"`
	Data struct {
		UnreadCount int64 `json:"unreadCount"`
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

func decodeUnreadCount(t *testing.T, body []byte) int64 {
	t.Helper()
	var resp unreadSummaryResp
	if err := json.Unmarshal(body, &resp); err != nil {
		t.Fatalf("decode unread summary failed: %v, body=%s", err, string(body))
	}
	return resp.Data.UnreadCount
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
