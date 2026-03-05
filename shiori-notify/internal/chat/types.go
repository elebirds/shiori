package chat

import "time"

type Conversation struct {
	ID        int64
	ListingID int64
	BuyerID   int64
	SellerID  int64
	CreatedAt time.Time
	UpdatedAt time.Time
}

type Message struct {
	ID             int64     `json:"messageId"`
	ConversationID int64     `json:"conversationId"`
	SenderID       int64     `json:"senderId"`
	ReceiverID     int64     `json:"receiverId"`
	ClientMsgID    string    `json:"clientMsgId"`
	Content        string    `json:"content"`
	CreatedAt      time.Time `json:"createdAt"`
}

type ConversationItem struct {
	Conversation Conversation
	LastMessage  *Message
	HasUnread    bool
}

type ChatTicketClaims struct {
	BuyerID   int64
	SellerID  int64
	ListingID int64
	JTI       string
	ExpiresAt time.Time
}

type SendResult struct {
	Conversation Conversation
	Message      Message
	Deduplicated bool
}

type Summary struct {
	UnreadConversationCount int64
	UnreadMessageCount      int64
}

type BroadcastEvent struct {
	ConversationID int64     `json:"conversationId"`
	MessageID      int64     `json:"messageId"`
	ListingID      int64     `json:"listingId"`
	SenderID       int64     `json:"senderId"`
	ReceiverID     int64     `json:"receiverId"`
	ClientMsgID    string    `json:"clientMsgId"`
	Content        string    `json:"content"`
	CreatedAt      time.Time `json:"createdAt"`
	OriginInstance string    `json:"originInstanceId"`
}

type BlockRecord struct {
	BlockerUserID int64     `json:"blockerUserId"`
	TargetUserID  int64     `json:"targetUserId"`
	CreatedAt     time.Time `json:"createdAt"`
}

type ReportRecord struct {
	ID             int64      `json:"id"`
	ReporterUserID int64      `json:"reporterUserId"`
	TargetUserID   int64      `json:"targetUserId"`
	ConversationID int64      `json:"conversationId"`
	MessageID      *int64     `json:"messageId,omitempty"`
	Reason         string     `json:"reason"`
	Status         string     `json:"status"`
	Remark         string     `json:"remark,omitempty"`
	HandledBy      *int64     `json:"handledBy,omitempty"`
	HandledAt      *time.Time `json:"handledAt,omitempty"`
	CreatedAt      time.Time  `json:"createdAt"`
	UpdatedAt      time.Time  `json:"updatedAt"`
}

type ForbiddenWordRule struct {
	ID        int64     `json:"id"`
	Word      string    `json:"word"`
	MatchType string    `json:"matchType"`
	Policy    string    `json:"policy"`
	Mask      string    `json:"mask"`
	Status    string    `json:"status"`
	CreatedBy *int64    `json:"createdBy,omitempty"`
	UpdatedBy *int64    `json:"updatedBy,omitempty"`
	CreatedAt time.Time `json:"createdAt"`
	UpdatedAt time.Time `json:"updatedAt"`
}

type ModerationAuditRecord struct {
	ID               int64     `json:"id"`
	UserID           int64     `json:"userId"`
	ConversationID   int64     `json:"conversationId"`
	OriginalContent  string    `json:"originalContent"`
	ProcessedContent string    `json:"processedContent"`
	Action           string    `json:"action"`
	MatchedWord      string    `json:"matchedWord"`
	CreatedAt        time.Time `json:"createdAt"`
}

type CreateReportRequest struct {
	ConversationID int64
	MessageID      *int64
	TargetUserID   *int64
	Reason         string
}

type BlockQuery struct {
	BlockerUserID int64
	TargetUserID  int64
	Page          int
	Size          int
}

type UpsertForbiddenWordRequest struct {
	Word      string
	MatchType string
	Policy    string
	Mask      string
	Status    string
}

type Broadcaster interface {
	PublishMessage(event BroadcastEvent) error
}

type Repository interface {
	GetOrCreateConversation(listingID, buyerID, sellerID int64) (Conversation, error)
	GetConversationForUser(conversationID, userID int64) (Conversation, error)
	InsertMessage(conversationID, senderID, receiverID int64, clientMsgID, content string) (Message, bool, error)
	UpdateLastRead(conversationID, userID, lastReadMsgID int64) (int64, error)
	ListConversations(userID, cursor int64, limit int) ([]ConversationItem, bool, error)
	ListMessages(userID, conversationID, before int64, limit int) ([]Message, bool, error)
	CountUnreadConversations(userID int64) (int64, error)
	CountUnreadMessages(userID int64) (int64, error)
	IsEitherBlocked(userA, userB int64) (bool, error)
	UpsertBlock(blockerUserID, targetUserID int64) error
	DeleteBlock(blockerUserID, targetUserID int64) error
	ListBlocksByUser(userID int64) ([]BlockRecord, error)
	ListBlocksForAdmin(query BlockQuery) ([]BlockRecord, int64, error)
	InsertReport(reporterUserID, targetUserID, conversationID int64, messageID *int64, reason string) (ReportRecord, error)
	ListReports(status string, page, size int) ([]ReportRecord, int64, error)
	HandleReport(reportID, operatorUserID int64, status, remark string) error
	ListForbiddenWords(includeDisabled bool) ([]ForbiddenWordRule, error)
	UpsertForbiddenWord(operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error)
	UpdateForbiddenWord(ruleID, operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error)
	DeleteForbiddenWord(ruleID, operatorUserID int64) error
	InsertModerationAudit(userID, conversationID int64, originalContent, processedContent, action, matchedWord string) error
}

type TicketVerifier interface {
	Verify(ticket string) (ChatTicketClaims, error)
}
