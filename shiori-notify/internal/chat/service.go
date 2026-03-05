package chat

import (
	"errors"
	"strings"
)

type Service struct {
	repo         Repository
	ticketVerify TicketVerifier
	maxPageLimit int
}

const (
	maxClientMsgIDLength = 64
	maxContentLength     = 2000
)

func NewService(repo Repository, ticketVerify TicketVerifier, maxPageLimit int) *Service {
	if maxPageLimit <= 0 {
		maxPageLimit = 100
	}
	return &Service{
		repo:         repo,
		ticketVerify: ticketVerify,
		maxPageLimit: maxPageLimit,
	}
}

func (s *Service) Join(userID int64, ticket string) (Conversation, ChatTicketClaims, error) {
	if userID <= 0 || strings.TrimSpace(ticket) == "" {
		return Conversation{}, ChatTicketClaims{}, ErrInvalidArgument
	}
	if s.ticketVerify == nil {
		return Conversation{}, ChatTicketClaims{}, ErrInvalidTicket
	}
	claims, err := s.ticketVerify.Verify(ticket)
	if err != nil {
		return Conversation{}, ChatTicketClaims{}, err
	}
	if claims.BuyerID <= 0 || claims.SellerID <= 0 || claims.ListingID <= 0 {
		return Conversation{}, ChatTicketClaims{}, ErrInvalidTicket
	}
	if userID != claims.BuyerID && userID != claims.SellerID {
		return Conversation{}, ChatTicketClaims{}, ErrForbidden
	}
	conversation, err := s.repo.GetOrCreateConversation(claims.ListingID, claims.BuyerID, claims.SellerID)
	if err != nil {
		return Conversation{}, ChatTicketClaims{}, err
	}
	return conversation, claims, nil
}

func (s *Service) Start(userID int64, ticket string) (Conversation, ChatTicketClaims, error) {
	return s.Join(userID, ticket)
}

func (s *Service) GetConversationForUser(userID, conversationID int64) (Conversation, error) {
	if userID <= 0 || conversationID <= 0 {
		return Conversation{}, ErrInvalidArgument
	}
	return s.repo.GetConversationForUser(conversationID, userID)
}

func (s *Service) Send(userID, conversationID int64, clientMsgID, content string) (SendResult, error) {
	if userID <= 0 || conversationID <= 0 {
		return SendResult{}, ErrInvalidArgument
	}
	clientMsgID = strings.TrimSpace(clientMsgID)
	content = strings.TrimSpace(content)
	if clientMsgID == "" || content == "" {
		return SendResult{}, ErrInvalidArgument
	}
	if len(clientMsgID) > maxClientMsgIDLength || len(content) > maxContentLength {
		return SendResult{}, ErrInvalidArgument
	}
	conversation, err := s.repo.GetConversationForUser(conversationID, userID)
	if err != nil {
		if errors.Is(err, ErrForbidden) || errors.Is(err, ErrConversationAbsent) {
			return SendResult{}, err
		}
		return SendResult{}, err
	}

	receiverID := conversation.BuyerID
	if receiverID == userID {
		receiverID = conversation.SellerID
	}

	message, deduplicated, err := s.repo.InsertMessage(conversation.ID, userID, receiverID, clientMsgID, content)
	if err != nil {
		return SendResult{}, err
	}
	return SendResult{
		Conversation: conversation,
		Message:      message,
		Deduplicated: deduplicated,
	}, nil
}

func (s *Service) Summary(userID int64) (Summary, error) {
	if userID <= 0 {
		return Summary{}, ErrInvalidArgument
	}
	unreadConversations, err := s.repo.CountUnreadConversations(userID)
	if err != nil {
		return Summary{}, err
	}
	unreadMessages, err := s.repo.CountUnreadMessages(userID)
	if err != nil {
		return Summary{}, err
	}
	return Summary{
		UnreadConversationCount: unreadConversations,
		UnreadMessageCount:      unreadMessages,
	}, nil
}

func (s *Service) Read(userID, conversationID, lastReadMsgID int64) (int64, error) {
	if userID <= 0 || conversationID <= 0 || lastReadMsgID < 0 {
		return 0, ErrInvalidArgument
	}
	_, err := s.repo.GetConversationForUser(conversationID, userID)
	if err != nil {
		return 0, err
	}
	return s.repo.UpdateLastRead(conversationID, userID, lastReadMsgID)
}

func (s *Service) ListConversations(userID, cursor int64, limit int) ([]ConversationItem, bool, error) {
	if userID <= 0 {
		return nil, false, ErrInvalidArgument
	}
	return s.repo.ListConversations(userID, cursor, s.normalizeLimit(limit))
}

func (s *Service) ListMessages(userID, conversationID, before int64, limit int) ([]Message, bool, error) {
	if userID <= 0 || conversationID <= 0 {
		return nil, false, ErrInvalidArgument
	}
	return s.repo.ListMessages(userID, conversationID, before, s.normalizeLimit(limit))
}

func (s *Service) normalizeLimit(limit int) int {
	if limit <= 0 {
		return 20
	}
	if limit > s.maxPageLimit {
		return s.maxPageLimit
	}
	return limit
}
