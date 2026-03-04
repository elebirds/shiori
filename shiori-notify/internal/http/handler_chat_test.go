package notifyhttp

import (
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/hhm/shiori/shiori-notify/internal/chat"
	"github.com/hhm/shiori/shiori-notify/internal/config"
	"github.com/hhm/shiori/shiori-notify/internal/store"
	"github.com/hhm/shiori/shiori-notify/internal/ws"
	"github.com/rs/zerolog"
)

type chatTestRepo struct{}

func (chatTestRepo) GetOrCreateConversation(listingID, buyerID, sellerID int64) (chat.Conversation, error) {
	return chat.Conversation{
		ID:        11,
		ListingID: listingID,
		BuyerID:   buyerID,
		SellerID:  sellerID,
		CreatedAt: time.Now().UTC(),
		UpdatedAt: time.Now().UTC(),
	}, nil
}

func (chatTestRepo) GetConversationForUser(conversationID, userID int64) (chat.Conversation, error) {
	return chat.Conversation{
		ID:        conversationID,
		ListingID: 101,
		BuyerID:   1001,
		SellerID:  2002,
		CreatedAt: time.Now().UTC(),
		UpdatedAt: time.Now().UTC(),
	}, nil
}

func (chatTestRepo) InsertMessage(conversationID, senderID, receiverID int64, clientMsgID, content string) (chat.Message, bool, error) {
	return chat.Message{
		ID:             21,
		ConversationID: conversationID,
		SenderID:       senderID,
		ReceiverID:     receiverID,
		ClientMsgID:    clientMsgID,
		Content:        content,
		CreatedAt:      time.Now().UTC(),
	}, false, nil
}

func (chatTestRepo) UpdateLastRead(conversationID, userID, lastReadMsgID int64) (int64, error) {
	return lastReadMsgID, nil
}

func (chatTestRepo) ListConversations(userID, cursor int64, limit int) ([]chat.ConversationItem, bool, error) {
	return []chat.ConversationItem{
		{
			Conversation: chat.Conversation{
				ID:        11,
				ListingID: 101,
				BuyerID:   1001,
				SellerID:  2002,
				CreatedAt: time.Now().UTC(),
				UpdatedAt: time.Now().UTC(),
			},
			LastMessage: &chat.Message{
				ID:             21,
				ConversationID: 11,
				SenderID:       2002,
				ReceiverID:     1001,
				ClientMsgID:    "m-1",
				Content:        "hi",
				CreatedAt:      time.Now().UTC(),
			},
			HasUnread: true,
		},
	}, false, nil
}

func (chatTestRepo) ListMessages(userID, conversationID, before int64, limit int) ([]chat.Message, bool, error) {
	return []chat.Message{
		{
			ID:             21,
			ConversationID: conversationID,
			SenderID:       2002,
			ReceiverID:     1001,
			ClientMsgID:    "m-1",
			Content:        "hi",
			CreatedAt:      time.Now().UTC(),
		},
	}, false, nil
}

func (chatTestRepo) CountUnreadConversations(userID int64) (int64, error) {
	return 1, nil
}

func (chatTestRepo) CountUnreadMessages(userID int64) (int64, error) {
	return 3, nil
}

type staticVerifier struct{}

func (staticVerifier) Verify(string) (chat.ChatTicketClaims, error) {
	return chat.ChatTicketClaims{
		BuyerID:   1001,
		SellerID:  2002,
		ListingID: 101,
		JTI:       "jti-1",
		ExpiresAt: time.Now().Add(time.Minute),
	}, nil
}

func TestChatHTTPHandlers(t *testing.T) {
	cfg := config.Config{
		AuthEnabled:        false,
		WSPath:             "/ws",
		ChatEnabled:        true,
		ChatMaxLimit:       100,
		ReplayMaxLimit:     50,
		ReplayDefaultLimit: 20,
	}
	logger := zerolog.New(io.Discard)
	srv := NewServer(cfg, ws.NewHub(), store.NewMemoryEventStore(20), nil, &logger)
	chatSvc := chat.NewService(chatTestRepo{}, staticVerifier{}, 100)
	srv.WithChat(chatSvc, nil)

	listConvRec := httptest.NewRecorder()
	listConvReq := httptest.NewRequest(http.MethodGet, "/api/chat/conversations?userId=1001&limit=20", nil)
	srv.engine.ServeHTTP(listConvRec, listConvReq)
	if listConvRec.Code != http.StatusOK {
		t.Fatalf("list conversations expected 200, got %d", listConvRec.Code)
	}

	listMsgRec := httptest.NewRecorder()
	listMsgReq := httptest.NewRequest(http.MethodGet, "/api/chat/conversations/11/messages?userId=1001&limit=20", nil)
	srv.engine.ServeHTTP(listMsgRec, listMsgReq)
	if listMsgRec.Code != http.StatusOK {
		t.Fatalf("list messages expected 200, got %d", listMsgRec.Code)
	}

	readRec := httptest.NewRecorder()
	readReq := httptest.NewRequest(http.MethodPost, "/api/chat/conversations/11/read?userId=1001", strings.NewReader(`{"lastReadMsgId":21}`))
	readReq.Header.Set("Content-Type", "application/json")
	srv.engine.ServeHTTP(readRec, readReq)
	if readRec.Code != http.StatusOK {
		t.Fatalf("read expected 200, got %d", readRec.Code)
	}

	startRec := httptest.NewRecorder()
	startReq := httptest.NewRequest(http.MethodPost, "/api/chat/conversations/start?userId=1001", strings.NewReader(`{"chatTicket":"ticket"}`))
	startReq.Header.Set("Content-Type", "application/json")
	srv.engine.ServeHTTP(startRec, startReq)
	if startRec.Code != http.StatusOK {
		t.Fatalf("start conversation expected 200, got %d", startRec.Code)
	}

	summaryRec := httptest.NewRecorder()
	summaryReq := httptest.NewRequest(http.MethodGet, "/api/chat/summary?userId=1001", nil)
	srv.engine.ServeHTTP(summaryRec, summaryReq)
	if summaryRec.Code != http.StatusOK {
		t.Fatalf("summary expected 200, got %d", summaryRec.Code)
	}
}
