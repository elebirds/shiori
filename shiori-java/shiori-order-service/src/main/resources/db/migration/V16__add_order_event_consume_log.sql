CREATE TABLE IF NOT EXISTS o_event_consume_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    consumer_group VARCHAR(128) NOT NULL,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    topic VARCHAR(255) NULL,
    partition_id INT NULL,
    message_offset BIGINT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING',
    last_error VARCHAR(512) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    processed_at DATETIME(3) NULL,
    UNIQUE KEY uk_o_event_consume_log_group_event (consumer_group, event_id),
    KEY idx_o_event_consume_log_status_updated (status, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
