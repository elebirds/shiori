CREATE TABLE IF NOT EXISTS s_schema_guard (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    note VARCHAR(128) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO s_schema_guard (note)
VALUES ('social-service schema initialized') AS new
ON DUPLICATE KEY UPDATE note = new.note;

CREATE TABLE IF NOT EXISTS s_post (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    post_no VARCHAR(32) NOT NULL,
    author_user_id BIGINT NOT NULL,
    source_type VARCHAR(32) NOT NULL COMMENT 'MANUAL/AUTO_PRODUCT',
    content_html TEXT NULL,
    related_product_id BIGINT NULL,
    related_product_no VARCHAR(32) NULL,
    related_product_title VARCHAR(120) NULL,
    related_product_cover_object_key VARCHAR(255) NULL,
    related_product_min_price_cent BIGINT NULL,
    related_product_max_price_cent BIGINT NULL,
    related_product_campus_code VARCHAR(64) NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_s_post_post_no (post_no),
    KEY idx_s_post_author_created (author_user_id, is_deleted, created_at, id),
    KEY idx_s_post_feed_created (is_deleted, created_at, id),
    KEY idx_s_post_related_product (related_product_id, is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS s_event_consume_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_s_event_consume_log_event_id (event_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
