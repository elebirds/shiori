package chat

import (
	"errors"
	"testing"
	"time"
)

type fakeTicketVerifier struct {
	claims ChatTicketClaims
	err    error
}

func (f *fakeTicketVerifier) Verify(string) (ChatTicketClaims, error) {
	if f.err != nil {
		return ChatTicketClaims{}, f.err
	}
	return f.claims, nil
}

type fakeRepo struct {
	conversation Conversation
	message      Message
	dedup        bool
	lastRead     int64
	forbid       bool
	missing      bool
}

func (f *fakeRepo) GetOrCreateConversation(listingID, buyerID, sellerID int64) (Conversation, error) {
	if f.conversation.ID == 0 {
		f.conversation = Conversation{
			ID:        1,
			ListingID: listingID,
			BuyerID:   buyerID,
			SellerID:  sellerID,
			CreatedAt: time.Now().UTC(),
			UpdatedAt: time.Now().UTC(),
		}
	}
	return f.conversation, nil
}

func (f *fakeRepo) GetConversationForUser(conversationID, userID int64) (Conversation, error) {
	if f.missing {
		return Conversation{}, ErrConversationAbsent
	}
	if f.forbid {
		return Conversation{}, ErrForbidden
	}
	if f.conversation.ID == 0 || f.conversation.ID != conversationID {
		return Conversation{}, ErrConversationAbsent
	}
	if userID != f.conversation.BuyerID && userID != f.conversation.SellerID {
		return Conversation{}, ErrForbidden
	}
	return f.conversation, nil
}

func (f *fakeRepo) InsertMessage(conversationID, senderID, receiverID int64, clientMsgID, content string) (Message, bool, error) {
	if f.message.ID == 0 {
		f.message = Message{
			ID:             10,
			ConversationID: conversationID,
			SenderID:       senderID,
			ReceiverID:     receiverID,
			ClientMsgID:    clientMsgID,
			Content:        content,
			CreatedAt:      time.Now().UTC(),
		}
	}
	return f.message, f.dedup, nil
}

func (f *fakeRepo) UpdateLastRead(conversationID, userID, lastReadMsgID int64) (int64, error) {
	f.lastRead = lastReadMsgID
	return f.lastRead, nil
}

func (f *fakeRepo) ListConversations(userID, cursor int64, limit int) ([]ConversationItem, bool, error) {
	return []ConversationItem{}, false, nil
}

func (f *fakeRepo) ListMessages(userID, conversationID, before int64, limit int) ([]Message, bool, error) {
	return []Message{}, false, nil
}

func TestServiceJoinIdempotentConversation(t *testing.T) {
	repo := &fakeRepo{}
	svc := NewService(repo, &fakeTicketVerifier{
		claims: ChatTicketClaims{
			BuyerID:   1001,
			SellerID:  2002,
			ListingID: 101,
			JTI:       "jti-1",
			ExpiresAt: time.Now().Add(time.Minute),
		},
	}, 100)

	conv1, _, err := svc.Join(1001, "ticket")
	if err != nil {
		t.Fatalf("join failed: %v", err)
	}
	conv2, _, err := svc.Join(1001, "ticket")
	if err != nil {
		t.Fatalf("join failed: %v", err)
	}
	if conv1.ID != conv2.ID {
		t.Fatalf("expected same conversation id, got %d and %d", conv1.ID, conv2.ID)
	}
}

func TestServiceSendDeduplicated(t *testing.T) {
	repo := &fakeRepo{
		conversation: Conversation{ID: 1, ListingID: 101, BuyerID: 1001, SellerID: 2002},
		dedup:        true,
	}
	svc := NewService(repo, &fakeTicketVerifier{}, 100)
	result, err := svc.Send(1001, 1, "client-1", "hi")
	if err != nil {
		t.Fatalf("send failed: %v", err)
	}
	if !result.Deduplicated {
		t.Fatalf("expected deduplicated send result")
	}
}

func TestServiceSendForbidden(t *testing.T) {
	repo := &fakeRepo{
		conversation: Conversation{ID: 1, ListingID: 101, BuyerID: 1001, SellerID: 2002},
		forbid:       true,
	}
	svc := NewService(repo, &fakeTicketVerifier{}, 100)
	if _, err := svc.Send(9999, 1, "client-1", "hi"); !errors.Is(err, ErrForbidden) {
		t.Fatalf("expected forbidden error, got %v", err)
	}
}

func TestServiceReadMonotonic(t *testing.T) {
	repo := &fakeRepo{
		conversation: Conversation{ID: 1, ListingID: 101, BuyerID: 1001, SellerID: 2002},
	}
	svc := NewService(repo, &fakeTicketVerifier{}, 100)
	lastRead, err := svc.Read(1001, 1, 30)
	if err != nil {
		t.Fatalf("read failed: %v", err)
	}
	if lastRead != 30 {
		t.Fatalf("unexpected lastReadMsgID: %d", lastRead)
	}
}
