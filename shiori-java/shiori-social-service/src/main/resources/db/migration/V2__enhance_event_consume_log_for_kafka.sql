ALTER TABLE s_event_consume_log
    ADD COLUMN consumer_group VARCHAR(128) NOT NULL DEFAULT 'shiori-social-product-cdc' AFTER event_type,
    ADD COLUMN topic VARCHAR(255) NULL AFTER consumer_group,
    ADD COLUMN partition_id INT NULL AFTER topic,
    ADD COLUMN message_offset BIGINT NULL AFTER partition_id,
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'SUCCEEDED' AFTER message_offset,
    ADD COLUMN last_error VARCHAR(512) NULL AFTER status,
    ADD COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at,
    ADD COLUMN processed_at DATETIME(3) NULL AFTER updated_at;

UPDATE s_event_consume_log
SET consumer_group = 'shiori-social-product-cdc',
    status = 'SUCCEEDED',
    updated_at = created_at,
    processed_at = created_at
WHERE consumer_group = 'shiori-social-product-cdc';

ALTER TABLE s_event_consume_log
    DROP INDEX uk_s_event_consume_log_event_id,
    ADD UNIQUE KEY uk_s_event_consume_log_group_event (consumer_group, event_id),
    ADD KEY idx_s_event_consume_log_status_updated (status, updated_at, id);
