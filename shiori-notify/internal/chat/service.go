package chat

import (
	"errors"
	"fmt"
	"regexp"
	"strings"
	"sync"
	"time"
)

type Service struct {
	repo         Repository
	ticketVerify TicketVerifier
	maxPageLimit int
	rateLimiter  *conversationRateLimiter
}

const (
	maxClientMsgIDLength = 64
	maxContentLength     = 2000
	defaultSendBurst     = 8
	defaultSendWindow    = 10 * time.Second
	defaultSendCooldown  = 30 * time.Second
)

func NewService(repo Repository, ticketVerify TicketVerifier, maxPageLimit int) *Service {
	if maxPageLimit <= 0 {
		maxPageLimit = 100
	}
	return &Service{
		repo:         repo,
		ticketVerify: ticketVerify,
		maxPageLimit: maxPageLimit,
		rateLimiter:  newConversationRateLimiter(defaultSendBurst, defaultSendWindow, defaultSendCooldown),
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
		return SendResult{}, err
	}

	receiverID := conversation.BuyerID
	if receiverID == userID {
		receiverID = conversation.SellerID
	}

	blocked, err := s.repo.IsEitherBlocked(userID, receiverID)
	if err != nil {
		return SendResult{}, err
	}
	if blocked {
		return SendResult{}, ErrBlocked
	}

	allowed, retryAfterSeconds := s.rateLimiter.Allow(userID, conversation.ID)
	if !allowed {
		return SendResult{}, &ErrRateLimited{RetryAfterSeconds: retryAfterSeconds}
	}

	originalContent := content
	processedContent, action, matchedWord, err := s.applyForbiddenRules(content)
	if err != nil {
		if errors.Is(err, ErrForbiddenWord) && action != "" {
			_ = s.repo.InsertModerationAudit(
				userID,
				conversation.ID,
				originalContent,
				originalContent,
				action,
				matchedWord,
			)
		}
		return SendResult{}, err
	}

	message, deduplicated, err := s.repo.InsertMessage(conversation.ID, userID, receiverID, clientMsgID, processedContent)
	if err != nil {
		return SendResult{}, err
	}
	if action == "MASK" && !deduplicated {
		_ = s.repo.InsertModerationAudit(
			userID,
			conversation.ID,
			originalContent,
			processedContent,
			action,
			matchedWord,
		)
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

func (s *Service) Block(userID, targetUserID int64) error {
	if userID <= 0 || targetUserID <= 0 || userID == targetUserID {
		return ErrInvalidArgument
	}
	return s.repo.UpsertBlock(userID, targetUserID)
}

func (s *Service) Unblock(userID, targetUserID int64) error {
	if userID <= 0 || targetUserID <= 0 || userID == targetUserID {
		return ErrInvalidArgument
	}
	return s.repo.DeleteBlock(userID, targetUserID)
}

func (s *Service) ListBlocks(userID int64) ([]BlockRecord, error) {
	if userID <= 0 {
		return nil, ErrInvalidArgument
	}
	return s.repo.ListBlocksByUser(userID)
}

func (s *Service) ListBlocksForAdmin(query BlockQuery) ([]BlockRecord, int64, error) {
	if query.Page <= 0 {
		query.Page = 1
	}
	if query.Size <= 0 {
		query.Size = 20
	}
	if query.Size > s.maxPageLimit {
		query.Size = s.maxPageLimit
	}
	return s.repo.ListBlocksForAdmin(query)
}

func (s *Service) Report(userID int64, request CreateReportRequest) (ReportRecord, error) {
	if userID <= 0 || request.ConversationID <= 0 || strings.TrimSpace(request.Reason) == "" {
		return ReportRecord{}, ErrInvalidArgument
	}
	conversation, err := s.repo.GetConversationForUser(request.ConversationID, userID)
	if err != nil {
		return ReportRecord{}, err
	}

	targetUserID := conversation.SellerID
	if userID == conversation.SellerID {
		targetUserID = conversation.BuyerID
	}
	if request.TargetUserID != nil && *request.TargetUserID > 0 {
		targetUserID = *request.TargetUserID
	}
	if targetUserID == userID {
		return ReportRecord{}, ErrInvalidArgument
	}

	return s.repo.InsertReport(
		userID,
		targetUserID,
		request.ConversationID,
		request.MessageID,
		strings.TrimSpace(request.Reason),
	)
}

func (s *Service) ListReports(status string, page, size int) ([]ReportRecord, int64, error) {
	normalizedPage := page
	if normalizedPage <= 0 {
		normalizedPage = 1
	}
	normalizedSize := size
	if normalizedSize <= 0 {
		normalizedSize = 20
	}
	if normalizedSize > s.maxPageLimit {
		normalizedSize = s.maxPageLimit
	}
	return s.repo.ListReports(strings.TrimSpace(strings.ToUpper(status)), normalizedPage, normalizedSize)
}

func (s *Service) HandleReport(reportID, operatorUserID int64, status, remark string) error {
	if reportID <= 0 || operatorUserID <= 0 {
		return ErrInvalidArgument
	}
	normalizedStatus := strings.TrimSpace(strings.ToUpper(status))
	if !isAllowedReportStatus(normalizedStatus) {
		return ErrInvalidArgument
	}
	return s.repo.HandleReport(reportID, operatorUserID, normalizedStatus, strings.TrimSpace(remark))
}

func (s *Service) ListForbiddenWords(includeDisabled bool) ([]ForbiddenWordRule, error) {
	return s.repo.ListForbiddenWords(includeDisabled)
}

func (s *Service) UpsertForbiddenWord(operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error) {
	if operatorUserID <= 0 || strings.TrimSpace(request.Word) == "" {
		return ForbiddenWordRule{}, ErrInvalidArgument
	}
	request.Word = strings.TrimSpace(request.Word)
	request.MatchType = normalizeMatchType(request.MatchType)
	request.Policy = normalizePolicy(request.Policy)
	request.Status = normalizeRuleStatus(request.Status)
	request.Mask = strings.TrimSpace(request.Mask)
	return s.repo.UpsertForbiddenWord(operatorUserID, request)
}

func (s *Service) UpdateForbiddenWord(ruleID, operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error) {
	if ruleID <= 0 || operatorUserID <= 0 || strings.TrimSpace(request.Word) == "" {
		return ForbiddenWordRule{}, ErrInvalidArgument
	}
	request.Word = strings.TrimSpace(request.Word)
	request.MatchType = normalizeMatchType(request.MatchType)
	request.Policy = normalizePolicy(request.Policy)
	request.Status = normalizeRuleStatus(request.Status)
	request.Mask = strings.TrimSpace(request.Mask)
	return s.repo.UpdateForbiddenWord(ruleID, operatorUserID, request)
}

func (s *Service) DeleteForbiddenWord(ruleID, operatorUserID int64) error {
	if ruleID <= 0 || operatorUserID <= 0 {
		return ErrInvalidArgument
	}
	return s.repo.DeleteForbiddenWord(ruleID, operatorUserID)
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

func (s *Service) applyForbiddenRules(content string) (string, string, string, error) {
	rules, err := s.repo.ListForbiddenWords(false)
	if err != nil {
		return "", "", "", err
	}
	for _, rule := range rules {
		word := strings.TrimSpace(rule.Word)
		if word == "" || !strings.EqualFold(strings.TrimSpace(rule.Status), "ACTIVE") {
			continue
		}
		matchType := normalizeMatchType(rule.MatchType)
		if !matchWord(content, word, matchType) {
			continue
		}
		policy := normalizePolicy(rule.Policy)
		if policy == "REJECT" {
			return "", "REJECT", word, ErrForbiddenWord
		}

		mask := strings.TrimSpace(rule.Mask)
		if mask == "" {
			mask = "***"
		}
		return applyMask(content, word, matchType, mask), "MASK", word, nil
	}
	return content, "", "", nil
}

func matchWord(content, word, matchType string) bool {
	switch normalizeMatchType(matchType) {
	case "EXACT":
		return strings.EqualFold(strings.TrimSpace(content), strings.TrimSpace(word))
	default:
		return strings.Contains(strings.ToLower(content), strings.ToLower(word))
	}
}

func applyMask(content, word, matchType, mask string) string {
	switch normalizeMatchType(matchType) {
	case "EXACT":
		if strings.EqualFold(strings.TrimSpace(content), strings.TrimSpace(word)) {
			return mask
		}
		return content
	default:
		replacer := regexp.MustCompile(`(?i)` + regexp.QuoteMeta(word))
		return replacer.ReplaceAllString(content, mask)
	}
}

func normalizeMatchType(matchType string) string {
	normalized := strings.TrimSpace(strings.ToUpper(matchType))
	if normalized == "EXACT" {
		return "EXACT"
	}
	return "KEYWORD"
}

func normalizePolicy(policy string) string {
	normalized := strings.TrimSpace(strings.ToUpper(policy))
	if normalized == "REJECT" {
		return "REJECT"
	}
	return "MASK"
}

func normalizeRuleStatus(status string) string {
	normalized := strings.TrimSpace(strings.ToUpper(status))
	if normalized == "DISABLED" {
		return "DISABLED"
	}
	return "ACTIVE"
}

func isAllowedReportStatus(status string) bool {
	switch status {
	case "PENDING", "RESOLVED", "REJECTED", "IGNORED":
		return true
	default:
		return false
	}
}

type rateLimitEntry struct {
	Timestamps    []time.Time
	CooldownUntil time.Time
}

type conversationRateLimiter struct {
	mu       sync.Mutex
	burst    int
	window   time.Duration
	cooldown time.Duration
	entries  map[string]*rateLimitEntry
}

func newConversationRateLimiter(burst int, window, cooldown time.Duration) *conversationRateLimiter {
	if burst <= 0 {
		burst = defaultSendBurst
	}
	if window <= 0 {
		window = defaultSendWindow
	}
	if cooldown <= 0 {
		cooldown = defaultSendCooldown
	}
	return &conversationRateLimiter{
		burst:    burst,
		window:   window,
		cooldown: cooldown,
		entries:  map[string]*rateLimitEntry{},
	}
}

func (l *conversationRateLimiter) Allow(userID, conversationID int64) (bool, int) {
	if l == nil {
		return true, 0
	}
	now := time.Now()
	key := fmt.Sprintf("%d:%d", userID, conversationID)

	l.mu.Lock()
	defer l.mu.Unlock()

	entry, exists := l.entries[key]
	if !exists {
		entry = &rateLimitEntry{}
		l.entries[key] = entry
	}
	if now.Before(entry.CooldownUntil) {
		return false, ceilDurationSeconds(entry.CooldownUntil.Sub(now))
	}

	cutoff := now.Add(-l.window)
	filtered := entry.Timestamps[:0]
	for _, ts := range entry.Timestamps {
		if ts.After(cutoff) {
			filtered = append(filtered, ts)
		}
	}
	entry.Timestamps = filtered
	if len(entry.Timestamps) >= l.burst {
		entry.CooldownUntil = now.Add(l.cooldown)
		entry.Timestamps = entry.Timestamps[:0]
		return false, ceilDurationSeconds(l.cooldown)
	}

	entry.Timestamps = append(entry.Timestamps, now)
	return true, 0
}

func ceilDurationSeconds(duration time.Duration) int {
	if duration <= 0 {
		return 1
	}
	seconds := int(duration / time.Second)
	if duration%time.Second != 0 {
		seconds++
	}
	if seconds <= 0 {
		return 1
	}
	return seconds
}
