package chat

import (
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"
)

type MySQLRepository struct {
	db *sql.DB
}

func NewMySQLRepository(dsn string, maxOpenConns, maxIdleConns int, connMaxLifetime time.Duration) (*MySQLRepository, error) {
	trimmedDSN := strings.TrimSpace(dsn)
	if trimmedDSN == "" {
		return nil, errors.New("mysql dsn is required for chat repository")
	}
	db, err := sql.Open("mysql", trimmedDSN)
	if err != nil {
		return nil, fmt.Errorf("open mysql for chat failed: %w", err)
	}
	if maxOpenConns > 0 {
		db.SetMaxOpenConns(maxOpenConns)
	}
	if maxIdleConns > 0 {
		db.SetMaxIdleConns(maxIdleConns)
	}
	if connMaxLifetime > 0 {
		db.SetConnMaxLifetime(connMaxLifetime)
	}
	if err := db.Ping(); err != nil {
		_ = db.Close()
		return nil, fmt.Errorf("ping mysql for chat failed: %w", err)
	}
	repo := &MySQLRepository{db: db}
	return repo, nil
}

func (r *MySQLRepository) Close() error {
	if r == nil || r.db == nil {
		return nil
	}
	return r.db.Close()
}

func (r *MySQLRepository) GetOrCreateConversation(listingID, buyerID, sellerID int64) (Conversation, error) {
	if listingID <= 0 || buyerID <= 0 || sellerID <= 0 {
		return Conversation{}, ErrInvalidArgument
	}
	if _, err := r.db.Exec(
		`INSERT IGNORE INTO conversation
			(listing_id, buyer_id, seller_id, created_at, updated_at, status)
		 VALUES (?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3), 'ACTIVE')`,
		listingID, buyerID, sellerID,
	); err != nil {
		return Conversation{}, fmt.Errorf("insert conversation failed: %w", err)
	}

	conversation, err := r.getConversationByTriplet(listingID, buyerID, sellerID)
	if err != nil {
		return Conversation{}, err
	}
	if _, err := r.db.Exec(
		`INSERT INTO member_state (conversation_id, user_id, last_read_msg_id, updated_at)
		 VALUES (?, ?, 0, UTC_TIMESTAMP(3)), (?, ?, 0, UTC_TIMESTAMP(3))
		 ON DUPLICATE KEY UPDATE updated_at = VALUES(updated_at)`,
		conversation.ID, buyerID, conversation.ID, sellerID,
	); err != nil {
		return Conversation{}, fmt.Errorf("init member state failed: %w", err)
	}
	return conversation, nil
}

func (r *MySQLRepository) GetConversationForUser(conversationID, userID int64) (Conversation, error) {
	if conversationID <= 0 || userID <= 0 {
		return Conversation{}, ErrInvalidArgument
	}
	row := r.db.QueryRow(
		`SELECT id, listing_id, buyer_id, seller_id, created_at, updated_at
		 FROM conversation
		 WHERE id = ?
		   AND (buyer_id = ? OR seller_id = ?)
		 LIMIT 1`,
		conversationID, userID, userID,
	)
	conversation, err := scanConversation(row)
	if err == nil {
		return conversation, nil
	}
	if errors.Is(err, sql.ErrNoRows) {
		var one int
		if err := r.db.QueryRow(`SELECT 1 FROM conversation WHERE id = ? LIMIT 1`, conversationID).Scan(&one); err == nil {
			return Conversation{}, ErrForbidden
		}
		if errors.Is(err, sql.ErrNoRows) {
			return Conversation{}, ErrConversationAbsent
		}
	}
	return Conversation{}, err
}

func (r *MySQLRepository) InsertMessage(conversationID, senderID, receiverID int64, clientMsgID, content string) (Message, bool, error) {
	if conversationID <= 0 || senderID <= 0 || receiverID <= 0 || strings.TrimSpace(clientMsgID) == "" || strings.TrimSpace(content) == "" {
		return Message{}, false, ErrInvalidArgument
	}
	result, err := r.db.Exec(
		`INSERT IGNORE INTO message
			(conversation_id, sender_id, receiver_id, content, client_msg_id, created_at)
		 VALUES (?, ?, ?, ?, ?, UTC_TIMESTAMP(3))`,
		conversationID, senderID, receiverID, content, clientMsgID,
	)
	if err != nil {
		return Message{}, false, fmt.Errorf("insert message failed: %w", err)
	}

	rows, err := result.RowsAffected()
	if err != nil {
		return Message{}, false, fmt.Errorf("read insert message rows failed: %w", err)
	}
	if rows == 0 {
		msg, findErr := r.findMessageByClientMsgID(conversationID, senderID, clientMsgID)
		if findErr != nil {
			return Message{}, false, findErr
		}
		return msg, true, nil
	}

	if _, err := r.db.Exec(
		`UPDATE conversation
		 SET updated_at = UTC_TIMESTAMP(3)
		 WHERE id = ?`,
		conversationID,
	); err != nil {
		return Message{}, false, fmt.Errorf("touch conversation updated_at failed: %w", err)
	}

	insertID, err := result.LastInsertId()
	if err != nil {
		return Message{}, false, fmt.Errorf("read message insert id failed: %w", err)
	}
	msg, err := r.findMessageByID(insertID)
	if err != nil {
		return Message{}, false, err
	}
	return msg, false, nil
}

func (r *MySQLRepository) UpdateLastRead(conversationID, userID, lastReadMsgID int64) (int64, error) {
	if conversationID <= 0 || userID <= 0 || lastReadMsgID < 0 {
		return 0, ErrInvalidArgument
	}
	if _, err := r.db.Exec(
		`INSERT INTO member_state (conversation_id, user_id, last_read_msg_id, updated_at)
		 VALUES (?, ?, ?, UTC_TIMESTAMP(3))
		 ON DUPLICATE KEY UPDATE
		   last_read_msg_id = GREATEST(last_read_msg_id, VALUES(last_read_msg_id)),
		   updated_at = VALUES(updated_at)`,
		conversationID, userID, lastReadMsgID,
	); err != nil {
		return 0, fmt.Errorf("update member read state failed: %w", err)
	}
	var current int64
	if err := r.db.QueryRow(
		`SELECT last_read_msg_id
		 FROM member_state
		 WHERE conversation_id = ? AND user_id = ?
		 LIMIT 1`,
		conversationID, userID,
	).Scan(&current); err != nil {
		return 0, fmt.Errorf("query member read state failed: %w", err)
	}
	return current, nil
}

func (r *MySQLRepository) ListConversations(userID, cursor int64, limit int) ([]ConversationItem, bool, error) {
	if userID <= 0 {
		return nil, false, ErrInvalidArgument
	}
	query := `
SELECT c.id,
       c.listing_id,
       c.buyer_id,
       c.seller_id,
       c.created_at,
       c.updated_at,
       lm.id,
       lm.conversation_id,
       lm.sender_id,
       lm.receiver_id,
       lm.client_msg_id,
       lm.content,
       lm.created_at,
       CASE
         WHEN lm.id IS NULL THEN 0
         WHEN lm.sender_id = ? THEN 0
         WHEN COALESCE(ms.last_read_msg_id, 0) < lm.id THEN 1
         ELSE 0
       END AS has_unread
FROM conversation c
LEFT JOIN message lm
  ON lm.id = (
      SELECT MAX(m2.id)
      FROM message m2
      WHERE m2.conversation_id = c.id
  )
LEFT JOIN member_state ms
  ON ms.conversation_id = c.id AND ms.user_id = ?
WHERE (c.buyer_id = ? OR c.seller_id = ?)
`
	args := []any{userID, userID, userID, userID}
	if cursor > 0 {
		query += "  AND c.id < ?\n"
		args = append(args, cursor)
	}
	query += "ORDER BY c.id DESC\nLIMIT ?"
	args = append(args, limit+1)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, false, fmt.Errorf("query conversations failed: %w", err)
	}
	defer rows.Close()

	items := make([]ConversationItem, 0, limit+1)
	for rows.Next() {
		var (
			item             ConversationItem
			lmID             sql.NullInt64
			lmConversationID sql.NullInt64
			lmSenderID       sql.NullInt64
			lmReceiverID     sql.NullInt64
			lmClientMsgID    sql.NullString
			lmContent        sql.NullString
			lmAt             sql.NullTime
			unreadInt        int
		)
		if err := rows.Scan(
			&item.Conversation.ID,
			&item.Conversation.ListingID,
			&item.Conversation.BuyerID,
			&item.Conversation.SellerID,
			&item.Conversation.CreatedAt,
			&item.Conversation.UpdatedAt,
			&lmID,
			&lmConversationID,
			&lmSenderID,
			&lmReceiverID,
			&lmClientMsgID,
			&lmContent,
			&lmAt,
			&unreadInt,
		); err != nil {
			return nil, false, fmt.Errorf("scan conversations failed: %w", err)
		}
		item.HasUnread = unreadInt == 1
		if lmID.Valid {
			lm := Message{
				ID:             lmID.Int64,
				ConversationID: lmConversationID.Int64,
				SenderID:       lmSenderID.Int64,
				ReceiverID:     lmReceiverID.Int64,
				ClientMsgID:    lmClientMsgID.String,
				Content:        lmContent.String,
			}
			if lmAt.Valid {
				lm.CreatedAt = lmAt.Time.UTC()
			}
			item.LastMessage = &lm
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, false, fmt.Errorf("iterate conversations failed: %w", err)
	}
	hasMore := len(items) > limit
	if hasMore {
		items = items[:limit]
	}
	return items, hasMore, nil
}

func (r *MySQLRepository) ListMessages(userID, conversationID, before int64, limit int) ([]Message, bool, error) {
	if userID <= 0 || conversationID <= 0 {
		return nil, false, ErrInvalidArgument
	}
	query := `
SELECT m.id, m.conversation_id, m.sender_id, m.receiver_id, m.client_msg_id, m.content, m.created_at
FROM message m
JOIN conversation c ON c.id = m.conversation_id
WHERE m.conversation_id = ?
  AND (c.buyer_id = ? OR c.seller_id = ?)
`
	args := []any{conversationID, userID, userID}
	if before > 0 {
		query += "  AND m.id < ?\n"
		args = append(args, before)
	}
	query += "ORDER BY m.id DESC\nLIMIT ?"
	args = append(args, limit+1)

	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, false, fmt.Errorf("query messages failed: %w", err)
	}
	defer rows.Close()

	items := make([]Message, 0, limit+1)
	for rows.Next() {
		var item Message
		if err := rows.Scan(
			&item.ID,
			&item.ConversationID,
			&item.SenderID,
			&item.ReceiverID,
			&item.ClientMsgID,
			&item.Content,
			&item.CreatedAt,
		); err != nil {
			return nil, false, fmt.Errorf("scan messages failed: %w", err)
		}
		item.CreatedAt = item.CreatedAt.UTC()
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, false, fmt.Errorf("iterate messages failed: %w", err)
	}
	hasMore := len(items) > limit
	if hasMore {
		items = items[:limit]
	}
	return items, hasMore, nil
}

func (r *MySQLRepository) CountUnreadConversations(userID int64) (int64, error) {
	if userID <= 0 {
		return 0, ErrInvalidArgument
	}
	row := r.db.QueryRow(
		`SELECT COUNT(*)
		   FROM conversation c
		   LEFT JOIN member_state ms
		     ON ms.conversation_id = c.id AND ms.user_id = ?
		  WHERE (c.buyer_id = ? OR c.seller_id = ?)
		    AND EXISTS (
		        SELECT 1
		          FROM message m
		         WHERE m.conversation_id = c.id
		           AND m.sender_id <> ?
		           AND m.id > COALESCE(ms.last_read_msg_id, 0)
		    )`,
		userID, userID, userID, userID,
	)
	var count int64
	if err := row.Scan(&count); err != nil {
		return 0, fmt.Errorf("count unread conversations failed: %w", err)
	}
	return count, nil
}

func (r *MySQLRepository) CountUnreadMessages(userID int64) (int64, error) {
	if userID <= 0 {
		return 0, ErrInvalidArgument
	}
	row := r.db.QueryRow(
		`SELECT COUNT(*)
		   FROM message m
		   JOIN conversation c
		     ON c.id = m.conversation_id
		   LEFT JOIN member_state ms
		     ON ms.conversation_id = c.id AND ms.user_id = ?
		  WHERE (c.buyer_id = ? OR c.seller_id = ?)
		    AND m.sender_id <> ?
		    AND m.id > COALESCE(ms.last_read_msg_id, 0)`,
		userID, userID, userID, userID,
	)
	var count int64
	if err := row.Scan(&count); err != nil {
		return 0, fmt.Errorf("count unread messages failed: %w", err)
	}
	return count, nil
}

func (r *MySQLRepository) IsEitherBlocked(userA, userB int64) (bool, error) {
	if userA <= 0 || userB <= 0 {
		return false, ErrInvalidArgument
	}
	var exists int
	if err := r.db.QueryRow(
		`SELECT EXISTS(
		   SELECT 1
		     FROM chat_block
		    WHERE (blocker_user_id = ? AND target_user_id = ?)
		       OR (blocker_user_id = ? AND target_user_id = ?)
		 )`,
		userA, userB, userB, userA,
	).Scan(&exists); err != nil {
		return false, fmt.Errorf("query block relation failed: %w", err)
	}
	return exists == 1, nil
}

func (r *MySQLRepository) UpsertBlock(blockerUserID, targetUserID int64) error {
	if blockerUserID <= 0 || targetUserID <= 0 || blockerUserID == targetUserID {
		return ErrInvalidArgument
	}
	if _, err := r.db.Exec(
		`INSERT INTO chat_block (blocker_user_id, target_user_id, created_at)
		 VALUES (?, ?, UTC_TIMESTAMP(3))
		 ON DUPLICATE KEY UPDATE created_at = created_at`,
		blockerUserID, targetUserID,
	); err != nil {
		return fmt.Errorf("upsert block failed: %w", err)
	}
	return nil
}

func (r *MySQLRepository) DeleteBlock(blockerUserID, targetUserID int64) error {
	if blockerUserID <= 0 || targetUserID <= 0 || blockerUserID == targetUserID {
		return ErrInvalidArgument
	}
	if _, err := r.db.Exec(
		`DELETE FROM chat_block WHERE blocker_user_id = ? AND target_user_id = ?`,
		blockerUserID, targetUserID,
	); err != nil {
		return fmt.Errorf("delete block failed: %w", err)
	}
	return nil
}

func (r *MySQLRepository) ListBlocksByUser(userID int64) ([]BlockRecord, error) {
	if userID <= 0 {
		return nil, ErrInvalidArgument
	}
	rows, err := r.db.Query(
		`SELECT blocker_user_id, target_user_id, created_at
		   FROM chat_block
		  WHERE blocker_user_id = ?
		  ORDER BY created_at DESC`,
		userID,
	)
	if err != nil {
		return nil, fmt.Errorf("query blocks by user failed: %w", err)
	}
	defer rows.Close()

	items := make([]BlockRecord, 0)
	for rows.Next() {
		var item BlockRecord
		if err := rows.Scan(&item.BlockerUserID, &item.TargetUserID, &item.CreatedAt); err != nil {
			return nil, fmt.Errorf("scan block record failed: %w", err)
		}
		item.CreatedAt = item.CreatedAt.UTC()
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate block records failed: %w", err)
	}
	return items, nil
}

func (r *MySQLRepository) ListBlocksForAdmin(query BlockQuery) ([]BlockRecord, int64, error) {
	if query.Page <= 0 {
		query.Page = 1
	}
	if query.Size <= 0 {
		query.Size = 20
	}
	whereClauses := make([]string, 0, 2)
	args := make([]any, 0, 4)
	if query.BlockerUserID > 0 {
		whereClauses = append(whereClauses, "blocker_user_id = ?")
		args = append(args, query.BlockerUserID)
	}
	if query.TargetUserID > 0 {
		whereClauses = append(whereClauses, "target_user_id = ?")
		args = append(args, query.TargetUserID)
	}
	whereSQL := ""
	if len(whereClauses) > 0 {
		whereSQL = "WHERE " + strings.Join(whereClauses, " AND ")
	}

	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM chat_block %s", whereSQL)
	var total int64
	if err := r.db.QueryRow(countQuery, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count admin blocks failed: %w", err)
	}

	offset := (query.Page - 1) * query.Size
	listArgs := append(append([]any{}, args...), query.Size, offset)
	listQuery := fmt.Sprintf(
		`SELECT blocker_user_id, target_user_id, created_at
		   FROM chat_block
		 %s
		  ORDER BY created_at DESC
		  LIMIT ? OFFSET ?`,
		whereSQL,
	)
	rows, err := r.db.Query(listQuery, listArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("query admin blocks failed: %w", err)
	}
	defer rows.Close()

	items := make([]BlockRecord, 0, query.Size)
	for rows.Next() {
		var item BlockRecord
		if err := rows.Scan(&item.BlockerUserID, &item.TargetUserID, &item.CreatedAt); err != nil {
			return nil, 0, fmt.Errorf("scan admin block failed: %w", err)
		}
		item.CreatedAt = item.CreatedAt.UTC()
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("iterate admin blocks failed: %w", err)
	}
	return items, total, nil
}

func (r *MySQLRepository) InsertReport(reporterUserID, targetUserID, conversationID int64, messageID *int64, reason string) (ReportRecord, error) {
	if reporterUserID <= 0 || targetUserID <= 0 || conversationID <= 0 || strings.TrimSpace(reason) == "" {
		return ReportRecord{}, ErrInvalidArgument
	}
	result, err := r.db.Exec(
		`INSERT INTO chat_report
		    (reporter_user_id, target_user_id, conversation_id, message_id, reason, status, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, 'PENDING', UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))`,
		reporterUserID, targetUserID, conversationID, messageID, strings.TrimSpace(reason),
	)
	if err != nil {
		return ReportRecord{}, fmt.Errorf("insert report failed: %w", err)
	}
	id, err := result.LastInsertId()
	if err != nil {
		return ReportRecord{}, fmt.Errorf("read report insert id failed: %w", err)
	}
	return r.findReportByID(id)
}

func (r *MySQLRepository) ListReports(status string, page, size int) ([]ReportRecord, int64, error) {
	if page <= 0 {
		page = 1
	}
	if size <= 0 {
		size = 20
	}
	whereSQL := ""
	args := make([]any, 0, 2)
	normalizedStatus := strings.TrimSpace(strings.ToUpper(status))
	if normalizedStatus != "" {
		whereSQL = "WHERE status = ?"
		args = append(args, normalizedStatus)
	}

	countQuery := fmt.Sprintf("SELECT COUNT(*) FROM chat_report %s", whereSQL)
	var total int64
	if err := r.db.QueryRow(countQuery, args...).Scan(&total); err != nil {
		return nil, 0, fmt.Errorf("count reports failed: %w", err)
	}

	offset := (page - 1) * size
	listArgs := append(append([]any{}, args...), size, offset)
	listQuery := fmt.Sprintf(
		`SELECT id, reporter_user_id, target_user_id, conversation_id, message_id, reason, status, remark, handled_by, handled_at, created_at, updated_at
		   FROM chat_report
		 %s
		  ORDER BY id DESC
		  LIMIT ? OFFSET ?`,
		whereSQL,
	)
	rows, err := r.db.Query(listQuery, listArgs...)
	if err != nil {
		return nil, 0, fmt.Errorf("query reports failed: %w", err)
	}
	defer rows.Close()

	items := make([]ReportRecord, 0, size)
	for rows.Next() {
		item, scanErr := scanReportRecord(rows)
		if scanErr != nil {
			return nil, 0, scanErr
		}
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, 0, fmt.Errorf("iterate reports failed: %w", err)
	}
	return items, total, nil
}

func (r *MySQLRepository) HandleReport(reportID, operatorUserID int64, status, remark string) error {
	if reportID <= 0 || operatorUserID <= 0 || strings.TrimSpace(status) == "" {
		return ErrInvalidArgument
	}
	result, err := r.db.Exec(
		`UPDATE chat_report
		    SET status = ?, remark = ?, handled_by = ?, handled_at = UTC_TIMESTAMP(3), updated_at = UTC_TIMESTAMP(3)
		  WHERE id = ?`,
		strings.TrimSpace(strings.ToUpper(status)),
		strings.TrimSpace(remark),
		operatorUserID,
		reportID,
	)
	if err != nil {
		return fmt.Errorf("handle report failed: %w", err)
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return fmt.Errorf("read handle report rows failed: %w", err)
	}
	if affected == 0 {
		return ErrReportAbsent
	}
	return nil
}

func (r *MySQLRepository) ListForbiddenWords(includeDisabled bool) ([]ForbiddenWordRule, error) {
	query := `SELECT id, word, match_type, policy, mask, status, created_by, updated_by, created_at, updated_at
	            FROM chat_forbidden_word`
	args := make([]any, 0, 1)
	if !includeDisabled {
		query += " WHERE status = 'ACTIVE'"
	}
	query += " ORDER BY id DESC"
	rows, err := r.db.Query(query, args...)
	if err != nil {
		return nil, fmt.Errorf("query forbidden words failed: %w", err)
	}
	defer rows.Close()

	items := make([]ForbiddenWordRule, 0)
	for rows.Next() {
		var (
			item      ForbiddenWordRule
			createdBy sql.NullInt64
			updatedBy sql.NullInt64
		)
		if err := rows.Scan(
			&item.ID,
			&item.Word,
			&item.MatchType,
			&item.Policy,
			&item.Mask,
			&item.Status,
			&createdBy,
			&updatedBy,
			&item.CreatedAt,
			&item.UpdatedAt,
		); err != nil {
			return nil, fmt.Errorf("scan forbidden word failed: %w", err)
		}
		if createdBy.Valid {
			v := createdBy.Int64
			item.CreatedBy = &v
		}
		if updatedBy.Valid {
			v := updatedBy.Int64
			item.UpdatedBy = &v
		}
		item.CreatedAt = item.CreatedAt.UTC()
		item.UpdatedAt = item.UpdatedAt.UTC()
		items = append(items, item)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate forbidden words failed: %w", err)
	}
	return items, nil
}

func (r *MySQLRepository) UpsertForbiddenWord(operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error) {
	if operatorUserID <= 0 || strings.TrimSpace(request.Word) == "" {
		return ForbiddenWordRule{}, ErrInvalidArgument
	}
	result, err := r.db.Exec(
		`INSERT INTO chat_forbidden_word
		    (word, match_type, policy, mask, status, created_by, updated_by, created_at, updated_at)
		 VALUES (?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))
		 ON DUPLICATE KEY UPDATE
		    match_type = VALUES(match_type),
		    policy = VALUES(policy),
		    mask = VALUES(mask),
		    status = VALUES(status),
		    updated_by = VALUES(updated_by),
		    updated_at = UTC_TIMESTAMP(3)`,
		strings.TrimSpace(request.Word),
		strings.TrimSpace(strings.ToUpper(request.MatchType)),
		strings.TrimSpace(strings.ToUpper(request.Policy)),
		strings.TrimSpace(request.Mask),
		strings.TrimSpace(strings.ToUpper(request.Status)),
		operatorUserID,
		operatorUserID,
	)
	if err != nil {
		return ForbiddenWordRule{}, fmt.Errorf("upsert forbidden word failed: %w", err)
	}
	insertID, err := result.LastInsertId()
	if err == nil && insertID > 0 {
		return r.findForbiddenWordByID(insertID)
	}
	return r.findForbiddenWordByWord(strings.TrimSpace(request.Word))
}

func (r *MySQLRepository) UpdateForbiddenWord(ruleID, operatorUserID int64, request UpsertForbiddenWordRequest) (ForbiddenWordRule, error) {
	if ruleID <= 0 || operatorUserID <= 0 || strings.TrimSpace(request.Word) == "" {
		return ForbiddenWordRule{}, ErrInvalidArgument
	}
	result, err := r.db.Exec(
		`UPDATE chat_forbidden_word
		    SET word = ?, match_type = ?, policy = ?, mask = ?, status = ?, updated_by = ?, updated_at = UTC_TIMESTAMP(3)
		  WHERE id = ?`,
		strings.TrimSpace(request.Word),
		strings.TrimSpace(strings.ToUpper(request.MatchType)),
		strings.TrimSpace(strings.ToUpper(request.Policy)),
		strings.TrimSpace(request.Mask),
		strings.TrimSpace(strings.ToUpper(request.Status)),
		operatorUserID,
		ruleID,
	)
	if err != nil {
		return ForbiddenWordRule{}, fmt.Errorf("update forbidden word failed: %w", err)
	}
	affected, err := result.RowsAffected()
	if err != nil {
		return ForbiddenWordRule{}, fmt.Errorf("read update forbidden word rows failed: %w", err)
	}
	if affected == 0 {
		return ForbiddenWordRule{}, ErrForbiddenWordRule
	}
	return r.findForbiddenWordByID(ruleID)
}

func (r *MySQLRepository) DeleteForbiddenWord(ruleID, operatorUserID int64) error {
	if ruleID <= 0 || operatorUserID <= 0 {
		return ErrInvalidArgument
	}
	if _, err := r.db.Exec(`DELETE FROM chat_forbidden_word WHERE id = ?`, ruleID); err != nil {
		return fmt.Errorf("delete forbidden word failed: %w", err)
	}
	return nil
}

func (r *MySQLRepository) InsertModerationAudit(userID, conversationID int64, originalContent, processedContent, action, matchedWord string) error {
	if userID <= 0 || conversationID <= 0 || strings.TrimSpace(action) == "" {
		return ErrInvalidArgument
	}
	if _, err := r.db.Exec(
		`INSERT INTO chat_moderation_audit
		    (user_id, conversation_id, original_content, processed_content, action, matched_word, created_at)
		 VALUES (?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(3))`,
		userID,
		conversationID,
		originalContent,
		processedContent,
		strings.TrimSpace(strings.ToUpper(action)),
		strings.TrimSpace(matchedWord),
	); err != nil {
		return fmt.Errorf("insert moderation audit failed: %w", err)
	}
	return nil
}

func (r *MySQLRepository) getConversationByTriplet(listingID, buyerID, sellerID int64) (Conversation, error) {
	row := r.db.QueryRow(
		`SELECT id, listing_id, buyer_id, seller_id, created_at, updated_at
		 FROM conversation
		 WHERE listing_id = ? AND buyer_id = ? AND seller_id = ?
		 LIMIT 1`,
		listingID, buyerID, sellerID,
	)
	conversation, err := scanConversation(row)
	if err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return Conversation{}, ErrConversationAbsent
		}
		return Conversation{}, err
	}
	return conversation, nil
}

func (r *MySQLRepository) findMessageByID(messageID int64) (Message, error) {
	row := r.db.QueryRow(
		`SELECT id, conversation_id, sender_id, receiver_id, client_msg_id, content, created_at
		 FROM message
		 WHERE id = ?
		 LIMIT 1`,
		messageID,
	)
	var message Message
	if err := row.Scan(
		&message.ID,
		&message.ConversationID,
		&message.SenderID,
		&message.ReceiverID,
		&message.ClientMsgID,
		&message.Content,
		&message.CreatedAt,
	); err != nil {
		return Message{}, fmt.Errorf("query message by id failed: %w", err)
	}
	message.CreatedAt = message.CreatedAt.UTC()
	return message, nil
}

func (r *MySQLRepository) findMessageByClientMsgID(conversationID, senderID int64, clientMsgID string) (Message, error) {
	row := r.db.QueryRow(
		`SELECT id, conversation_id, sender_id, receiver_id, client_msg_id, content, created_at
		 FROM message
		 WHERE conversation_id = ? AND sender_id = ? AND client_msg_id = ?
		 LIMIT 1`,
		conversationID, senderID, clientMsgID,
	)
	var message Message
	if err := row.Scan(
		&message.ID,
		&message.ConversationID,
		&message.SenderID,
		&message.ReceiverID,
		&message.ClientMsgID,
		&message.Content,
		&message.CreatedAt,
	); err != nil {
		return Message{}, fmt.Errorf("query message by client_msg_id failed: %w", err)
	}
	message.CreatedAt = message.CreatedAt.UTC()
	return message, nil
}

func (r *MySQLRepository) findReportByID(reportID int64) (ReportRecord, error) {
	row := r.db.QueryRow(
		`SELECT id, reporter_user_id, target_user_id, conversation_id, message_id, reason, status, remark, handled_by, handled_at, created_at, updated_at
		   FROM chat_report
		  WHERE id = ?
		  LIMIT 1`,
		reportID,
	)
	item, err := scanReportRecord(row)
	if err != nil {
		return ReportRecord{}, err
	}
	return item, nil
}

func (r *MySQLRepository) findForbiddenWordByID(ruleID int64) (ForbiddenWordRule, error) {
	row := r.db.QueryRow(
		`SELECT id, word, match_type, policy, mask, status, created_by, updated_by, created_at, updated_at
		   FROM chat_forbidden_word
		  WHERE id = ?
		  LIMIT 1`,
		ruleID,
	)
	return scanForbiddenWord(row)
}

func (r *MySQLRepository) findForbiddenWordByWord(word string) (ForbiddenWordRule, error) {
	row := r.db.QueryRow(
		`SELECT id, word, match_type, policy, mask, status, created_by, updated_by, created_at, updated_at
		   FROM chat_forbidden_word
		  WHERE word = ?
		  LIMIT 1`,
		word,
	)
	return scanForbiddenWord(row)
}

type reportScanner interface {
	Scan(dest ...any) error
}

func scanReportRecord(scanner reportScanner) (ReportRecord, error) {
	var (
		item      ReportRecord
		messageID sql.NullInt64
		remark    sql.NullString
		handledBy sql.NullInt64
		handledAt sql.NullTime
	)
	if err := scanner.Scan(
		&item.ID,
		&item.ReporterUserID,
		&item.TargetUserID,
		&item.ConversationID,
		&messageID,
		&item.Reason,
		&item.Status,
		&remark,
		&handledBy,
		&handledAt,
		&item.CreatedAt,
		&item.UpdatedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ReportRecord{}, ErrReportAbsent
		}
		return ReportRecord{}, fmt.Errorf("scan report failed: %w", err)
	}
	if messageID.Valid {
		v := messageID.Int64
		item.MessageID = &v
	}
	if remark.Valid {
		item.Remark = remark.String
	}
	if handledBy.Valid {
		v := handledBy.Int64
		item.HandledBy = &v
	}
	if handledAt.Valid {
		t := handledAt.Time.UTC()
		item.HandledAt = &t
	}
	item.CreatedAt = item.CreatedAt.UTC()
	item.UpdatedAt = item.UpdatedAt.UTC()
	return item, nil
}

func scanForbiddenWord(row *sql.Row) (ForbiddenWordRule, error) {
	var (
		item      ForbiddenWordRule
		createdBy sql.NullInt64
		updatedBy sql.NullInt64
	)
	if err := row.Scan(
		&item.ID,
		&item.Word,
		&item.MatchType,
		&item.Policy,
		&item.Mask,
		&item.Status,
		&createdBy,
		&updatedBy,
		&item.CreatedAt,
		&item.UpdatedAt,
	); err != nil {
		if errors.Is(err, sql.ErrNoRows) {
			return ForbiddenWordRule{}, ErrForbiddenWordRule
		}
		return ForbiddenWordRule{}, fmt.Errorf("scan forbidden word failed: %w", err)
	}
	if createdBy.Valid {
		v := createdBy.Int64
		item.CreatedBy = &v
	}
	if updatedBy.Valid {
		v := updatedBy.Int64
		item.UpdatedBy = &v
	}
	item.CreatedAt = item.CreatedAt.UTC()
	item.UpdatedAt = item.UpdatedAt.UTC()
	return item, nil
}

func scanConversation(row *sql.Row) (Conversation, error) {
	var conversation Conversation
	if err := row.Scan(
		&conversation.ID,
		&conversation.ListingID,
		&conversation.BuyerID,
		&conversation.SellerID,
		&conversation.CreatedAt,
		&conversation.UpdatedAt,
	); err != nil {
		return Conversation{}, err
	}
	conversation.CreatedAt = conversation.CreatedAt.UTC()
	conversation.UpdatedAt = conversation.UpdatedAt.UTC()
	return conversation, nil
}
