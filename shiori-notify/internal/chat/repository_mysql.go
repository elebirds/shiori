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
	if err := repo.initSchema(); err != nil {
		_ = db.Close()
		return nil, err
	}
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

func (r *MySQLRepository) initSchema() error {
	_, err := r.db.Exec(`
CREATE TABLE IF NOT EXISTS conversation (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    listing_id BIGINT NOT NULL,
    buyer_id BIGINT NOT NULL,
    seller_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    UNIQUE KEY uk_conv_triplet (listing_id, buyer_id, seller_id),
    KEY idx_conv_buyer_updated (buyer_id, id DESC),
    KEY idx_conv_seller_updated (seller_id, id DESC)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS message (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT NOT NULL,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    client_msg_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_msg_idem (conversation_id, sender_id, client_msg_id),
    KEY idx_msg_conv_id (conversation_id, id),
    CONSTRAINT fk_msg_conv_id FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS member_state (
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    last_read_msg_id BIGINT NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (conversation_id, user_id),
    KEY idx_member_state_user (user_id, conversation_id),
    CONSTRAINT fk_member_state_conv_id FOREIGN KEY (conversation_id) REFERENCES conversation(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
`)
	if err != nil {
		return fmt.Errorf("init chat schema failed: %w", err)
	}
	return nil
}
