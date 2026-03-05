package chat

import (
	"errors"
	"strings"
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
	unreadConv   int64
	unreadMsg    int64
	forbid       bool
	missing      bool
	blocked      bool
	rules        []ForbiddenWordRule
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

func (f *fakeRepo) CountUnreadConversations(userID int64) (int64, error) {
	return f.unreadConv, nil
}

func (f *fakeRepo) CountUnreadMessages(userID int64) (int64, error) {
	return f.unreadMsg, nil
}

func (f *fakeRepo) IsEitherBlocked(userA, userB int64) (bool, error) {
	return f.blocked, nil
}

func (f *fakeRepo) UpsertBlock(blockerUserID, targetUserID int64) error {
	return nil
}

func (f *fakeRepo) DeleteBlock(blockerUserID, targetUserID int64) error {
	return nil
}

func (f *fakeRepo) ListBlocksByUser(userID int64) ([]BlockRecord, error) {
	return []BlockRecord{}, nil
}

func (f *fakeRepo) ListBlocksForAdmin(query BlockQuery) ([]BlockRecord, int64, error) {
	return []BlockRecord{}, 0, nil
}

func (f *fakeRepo) InsertReport(reporterUserID, targetUserID, conversationID int64, messageID *int64, reason string) (ReportRecord, error) {
	return ReportRecord{
		ID:             1,
		ReporterUserID: reporterUserID,
		TargetUserID:   targetUserID,
		ConversationID: conversationID,
		MessageID:      messageID,
		Reason:         reason,
		Status:         "PENDING",
		CreatedAt:      time.Now().UTC(),
		UpdatedAt:      time.Now().UTC(),
	}, nil
}

func (f *fakeRepo) ListReports(status string, page, size int) ([]ReportRecord, int64, error) {
	return []ReportRecord{}, 0, nil
}

func (f *fakeRepo) HandleReport(reportID, operatorUserID int64, status, remark string) error {
	return nil
}

func (f *fakeRepo) ListForbiddenWords(includeDisabled bool) ([]ForbiddenWordRule, error) {
	return f.rules, nil
}

func (f *fakeRepo) UpsertForbiddenWord(operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error) {
	return ForbiddenWordRule{
		ID:        1,
		Word:      request.Word,
		MatchType: request.MatchType,
		Policy:    request.Policy,
		Mask:      request.Mask,
		Status:    request.Status,
		CreatedAt: time.Now().UTC(),
		UpdatedAt: time.Now().UTC(),
	}, nil
}

func (f *fakeRepo) UpdateForbiddenWord(ruleID, operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error) {
	return ForbiddenWordRule{
		ID:        ruleID,
		Word:      request.Word,
		MatchType: request.MatchType,
		Policy:    request.Policy,
		Mask:      request.Mask,
		Status:    request.Status,
		CreatedAt: time.Now().UTC(),
		UpdatedAt: time.Now().UTC(),
	}, nil
}

func (f *fakeRepo) DeleteForbiddenWord(ruleID, operatorUserID int64) error {
	return nil
}

func (f *fakeRepo) InsertModerationAudit(userID, conversationID int64, originalContent, processedContent, action, matchedWord string) error {
	return nil
}

type fakeCapabilityChecker struct {
	banned bool
	err    error
}

func (f *fakeCapabilityChecker) IsBanned(userID int64, capability string) (bool, error) {
	if f.err != nil {
		return false, f.err
	}
	return f.banned, nil
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

func TestServiceStartReusesJoin(t *testing.T) {
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
	conversation, claims, err := svc.Start(1001, "ticket")
	if err != nil {
		t.Fatalf("start failed: %v", err)
	}
	if conversation.ID == 0 || claims.JTI == "" {
		t.Fatalf("unexpected start response: %+v %+v", conversation, claims)
	}
}

func TestServiceSummary(t *testing.T) {
	repo := &fakeRepo{
		unreadConv: 2,
		unreadMsg:  7,
	}
	svc := NewService(repo, &fakeTicketVerifier{}, 100)
	summary, err := svc.Summary(1001)
	if err != nil {
		t.Fatalf("summary failed: %v", err)
	}
	if summary.UnreadConversationCount != 2 || summary.UnreadMessageCount != 7 {
		t.Fatalf("unexpected summary: %+v", summary)
	}
}

func TestServiceSendRejectsOversizePayload(t *testing.T) {
	repo := &fakeRepo{
		conversation: Conversation{ID: 1, ListingID: 101, BuyerID: 1001, SellerID: 2002},
	}
	svc := NewService(repo, &fakeTicketVerifier{}, 100)
	tooLongClientMsgID := strings.Repeat("a", 65)
	if _, err := svc.Send(1001, 1, tooLongClientMsgID, "hi"); !errors.Is(err, ErrInvalidArgument) {
		t.Fatalf("expected invalid argument for too long client msg id, got %v", err)
	}
	tooLongContent := make([]byte, 2001)
	for i := range tooLongContent {
		tooLongContent[i] = 'a'
	}
	if _, err := svc.Send(1001, 1, "client-1", string(tooLongContent)); !errors.Is(err, ErrInvalidArgument) {
		t.Fatalf("expected invalid argument for too long content, got %v", err)
	}
}

func TestServiceSendBlockedByCapabilityBan(t *testing.T) {
	repo := &fakeRepo{
		conversation: Conversation{ID: 1, ListingID: 101, BuyerID: 1001, SellerID: 2002},
	}
	svc := NewService(repo, &fakeTicketVerifier{}, 100).WithCapabilityChecker(&fakeCapabilityChecker{banned: true})
	_, err := svc.Send(1001, 1, "client-1", "hi")
	if err == nil {
		t.Fatalf("expected capability banned error")
	}
	var capabilityErr *ErrCapabilityBanned
	if !errors.As(err, &capabilityErr) {
		t.Fatalf("expected ErrCapabilityBanned, got %v", err)
	}
	if capabilityErr.Capability != "CHAT_SEND" {
		t.Fatalf("unexpected capability: %s", capabilityErr.Capability)
	}
}
