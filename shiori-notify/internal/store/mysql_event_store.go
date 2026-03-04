package store

import (
	"database/sql"
	"errors"
	"fmt"
	"strings"
	"time"

	_ "github.com/go-sql-driver/mysql"
	"github.com/hhm/shiori/shiori-notify/internal/event"
)

const defaultMySQLStoreMaxPerUser = 10000

type MySQLEventStore struct {
	db         *sql.DB
	maxPerUser int
}

func NewMySQLEventStore(
	dsn string,
	maxPerUser int,
	maxOpenConns int,
	maxIdleConns int,
	connMaxLifetime time.Duration,
) (*MySQLEventStore, error) {
	trimmedDSN := strings.TrimSpace(dsn)
	if trimmedDSN == "" {
		return nil, errors.New("mysql dsn is required when store driver=mysql")
	}

	db, err := sql.Open("mysql", trimmedDSN)
	if err != nil {
		return nil, fmt.Errorf("open mysql failed: %w", err)
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
		return nil, fmt.Errorf("ping mysql failed: %w", err)
	}

	if maxPerUser <= 0 {
		maxPerUser = defaultMySQLStoreMaxPerUser
	}

	store := &MySQLEventStore{
		db:         db,
		maxPerUser: maxPerUser,
	}
	if err := store.initSchema(); err != nil {
		_ = db.Close()
		return nil, err
	}
	return store, nil
}

func (s *MySQLEventStore) Save(userID string, env event.Envelope) (bool, error) {
	if strings.TrimSpace(userID) == "" || strings.TrimSpace(env.EventID) == "" {
		return false, nil
	}

	createdAt := time.Now().UTC()
	if parsed, err := time.Parse(time.RFC3339Nano, strings.TrimSpace(env.CreatedAt)); err == nil {
		createdAt = parsed.UTC()
	}

	result, err := s.db.Exec(
		`INSERT IGNORE INTO n_notification_event (
			user_id, event_id, event_type, aggregate_id, created_at, payload, is_read, read_at
		) VALUES (?, ?, ?, ?, ?, ?, 0, NULL)`,
		userID,
		env.EventID,
		env.Type,
		env.AggregateID,
		createdAt,
		[]byte(env.Payload),
	)
	if err != nil {
		return false, fmt.Errorf("insert notification event failed: %w", err)
	}

	rowsAffected, err := result.RowsAffected()
	if err != nil {
		return false, fmt.Errorf("read insert rows affected failed: %w", err)
	}

	saved := rowsAffected > 0
	if saved {
		if err := s.trimUserEvents(userID); err != nil {
			return true, err
		}
	}
	return saved, nil
}

func (s *MySQLEventStore) List(userID, afterEventID string, limit int) ([]NotificationEvent, string, bool, error) {
	if strings.TrimSpace(userID) == "" {
		return nil, "", false, nil
	}

	normalizedLimit := normalizeListLimit(limit)
	startID, err := s.lookupEventRowID(userID, afterEventID)
	if err != nil {
		return nil, "", false, err
	}

	rows, err := s.db.Query(
		`SELECT
			id,
			event_id,
			event_type,
			aggregate_id,
			DATE_FORMAT(created_at, '%Y-%m-%dT%H:%i:%s.%fZ') AS created_at_rfc3339,
			payload,
			is_read,
			COALESCE(DATE_FORMAT(read_at, '%Y-%m-%dT%H:%i:%s.%fZ'), '') AS read_at_rfc3339
		FROM n_notification_event
		WHERE user_id = ? AND id > ?
		ORDER BY id ASC
		LIMIT ?`,
		userID,
		startID,
		normalizedLimit+1,
	)
	if err != nil {
		return nil, "", false, fmt.Errorf("query notification events failed: %w", err)
	}
	defer rows.Close()

	events := make([]NotificationEvent, 0, normalizedLimit+1)
	for rows.Next() {
		var (
			id          int64
			eventID     string
			eventType   string
			aggregateID string
			createdAt   string
			payload     []byte
			isRead      int
			readAt      string
		)
		if err := rows.Scan(
			&id,
			&eventID,
			&eventType,
			&aggregateID,
			&createdAt,
			&payload,
			&isRead,
			&readAt,
		); err != nil {
			return nil, "", false, fmt.Errorf("scan notification event failed: %w", err)
		}
		_ = id

		events = append(events, NotificationEvent{
			EventID:     eventID,
			Type:        eventType,
			AggregateID: aggregateID,
			CreatedAt:   createdAt,
			Payload:     append([]byte(nil), payload...),
			Read:        isRead == 1,
			ReadAt:      readAt,
		})
	}
	if err := rows.Err(); err != nil {
		return nil, "", false, fmt.Errorf("iterate notification events failed: %w", err)
	}

	hasMore := len(events) > normalizedLimit
	if hasMore {
		events = events[:normalizedLimit]
	}

	nextEventID := ""
	if len(events) > 0 {
		nextEventID = events[len(events)-1].EventID
	}
	return events, nextEventID, hasMore, nil
}

func (s *MySQLEventStore) MarkRead(userID, eventID string) (bool, error) {
	if strings.TrimSpace(userID) == "" || strings.TrimSpace(eventID) == "" {
		return false, nil
	}

	result, err := s.db.Exec(
		`UPDATE n_notification_event
		SET is_read = 1,
		    read_at = COALESCE(read_at, UTC_TIMESTAMP(3))
		WHERE user_id = ? AND event_id = ? AND is_read = 0`,
		userID,
		eventID,
	)
	if err != nil {
		return false, fmt.Errorf("mark read failed: %w", err)
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return false, fmt.Errorf("read mark read rows failed: %w", err)
	}
	return rows > 0, nil
}

func (s *MySQLEventStore) MarkAllRead(userID string) (int64, error) {
	if strings.TrimSpace(userID) == "" {
		return 0, nil
	}

	result, err := s.db.Exec(
		`UPDATE n_notification_event
		SET is_read = 1,
		    read_at = COALESCE(read_at, UTC_TIMESTAMP(3))
		WHERE user_id = ? AND is_read = 0`,
		userID,
	)
	if err != nil {
		return 0, fmt.Errorf("mark all read failed: %w", err)
	}
	rows, err := result.RowsAffected()
	if err != nil {
		return 0, fmt.Errorf("read mark all rows failed: %w", err)
	}
	return rows, nil
}

func (s *MySQLEventStore) UnreadCount(userID string) (int64, error) {
	if strings.TrimSpace(userID) == "" {
		return 0, nil
	}

	var count int64
	if err := s.db.QueryRow(
		`SELECT COUNT(1) FROM n_notification_event WHERE user_id = ? AND is_read = 0`,
		userID,
	).Scan(&count); err != nil {
		return 0, fmt.Errorf("query unread count failed: %w", err)
	}
	return count, nil
}

func (s *MySQLEventStore) Close() error {
	if s == nil || s.db == nil {
		return nil
	}
	return s.db.Close()
}

func (s *MySQLEventStore) initSchema() error {
	_, err := s.db.Exec(`
CREATE TABLE IF NOT EXISTS n_notification_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    payload JSON NOT NULL,
    is_read TINYINT(1) NOT NULL DEFAULT 0,
    read_at DATETIME(3) NULL,
    inserted_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_n_notify_user_event (user_id, event_id),
    KEY idx_n_notify_user_cursor (user_id, id),
    KEY idx_n_notify_user_unread (user_id, is_read, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
`)
	if err != nil {
		return fmt.Errorf("init notification schema failed: %w", err)
	}
	return nil
}

func (s *MySQLEventStore) lookupEventRowID(userID, eventID string) (int64, error) {
	trimmedEventID := strings.TrimSpace(eventID)
	if trimmedEventID == "" {
		return 0, nil
	}

	var id int64
	err := s.db.QueryRow(
		`SELECT id FROM n_notification_event WHERE user_id = ? AND event_id = ? LIMIT 1`,
		userID,
		trimmedEventID,
	).Scan(&id)
	if err == nil {
		return id, nil
	}
	if errors.Is(err, sql.ErrNoRows) {
		return 0, nil
	}
	return 0, fmt.Errorf("lookup afterEventId failed: %w", err)
}

func (s *MySQLEventStore) trimUserEvents(userID string) error {
	if s.maxPerUser <= 0 {
		return nil
	}
	_, err := s.db.Exec(
		`DELETE FROM n_notification_event
		WHERE user_id = ?
		  AND id NOT IN (
		      SELECT id FROM (
		          SELECT id
		          FROM n_notification_event
		          WHERE user_id = ?
		          ORDER BY id DESC
		          LIMIT ?
		      ) latest
		  )`,
		userID,
		userID,
		s.maxPerUser,
	)
	if err != nil {
		return fmt.Errorf("trim notification events failed: %w", err)
	}
	return nil
}

func normalizeListLimit(limit int) int {
	if limit <= 0 {
		return defaultListLimit
	}
	if limit > maxListLimit {
		return maxListLimit
	}
	return limit
}
