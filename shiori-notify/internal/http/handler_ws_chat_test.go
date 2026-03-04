package notifyhttp

import (
	"encoding/json"
	"io"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

func TestWSChatJoinSendRead(t *testing.T) {
	cfg := config.Config{
		AuthEnabled:         false,
		WSPath:              "/ws",
		ChatEnabled:         true,
		ChatMaxLimit:        100,
		ReplayDefaultLimit:  20,
		ReplayMaxLimit:      50,
		WSWriteTimeout:      2 * time.Second,
		WSPingInterval:      0,
		WSReplayLimit:       10,
	}
	logger := zerolog.New(io.Discard)
	srv := NewServer(cfg, ws.NewHub(), store.NewMemoryEventStore(20), nil, &logger)
	srv.WithChat(chat.NewService(chatTestRepo{}, staticVerifier{}, 100), nil)

	ts := httptest.NewServer(srv.engine)
	defer ts.Close()

	wsURL := "ws" + strings.TrimPrefix(ts.URL, "http") + "/ws"
	buyerConn, _, err := websocket.DefaultDialer.Dial(wsURL+"?userId=1001", nil)
	if err != nil {
		t.Fatalf("dial buyer ws failed: %v", err)
	}
	defer buyerConn.Close()

	sellerConn, _, err := websocket.DefaultDialer.Dial(wsURL+"?userId=2002", nil)
	if err != nil {
		t.Fatalf("dial seller ws failed: %v", err)
	}
	defer sellerConn.Close()

	if err := buyerConn.WriteJSON(map[string]any{
		"type":       "join",
		"chatTicket": "ticket",
	}); err != nil {
		t.Fatalf("send join failed: %v", err)
	}

	_ = buyerConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var joinAck map[string]any
	if err := buyerConn.ReadJSON(&joinAck); err != nil {
		t.Fatalf("read join_ack failed: %v", err)
	}
	if joinAck["type"] != "join_ack" {
		t.Fatalf("unexpected join ack payload: %+v", joinAck)
	}

	if err := buyerConn.WriteJSON(map[string]any{
		"type":           "send",
		"conversationId": 11,
		"clientMsgId":    "client-1",
		"content":        "hello",
	}); err != nil {
		t.Fatalf("send chat message failed: %v", err)
	}

	_ = buyerConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	var sendAck map[string]any
	if err := buyerConn.ReadJSON(&sendAck); err != nil {
		t.Fatalf("read send_ack failed: %v", err)
	}
	if sendAck["type"] != "send_ack" {
		t.Fatalf("unexpected send ack payload: %+v", sendAck)
	}

	_ = sellerConn.SetReadDeadline(time.Now().Add(2 * time.Second))
	_, rawSellerMsg, err := sellerConn.ReadMessage()
	if err != nil {
		t.Fatalf("seller read chat message failed: %v", err)
	}
	var sellerMsg map[string]any
	if err := json.Unmarshal(rawSellerMsg, &sellerMsg); err != nil {
		t.Fatalf("decode seller message failed: %v", err)
	}
	if sellerMsg["type"] != "chat_message" {
		t.Fatalf("unexpected seller message payload: %+v", sellerMsg)
	}
}
