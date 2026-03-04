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
}

type TicketVerifier interface {
	Verify(ticket string) (ChatTicketClaims, error)
}
