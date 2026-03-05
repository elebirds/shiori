ALTER TABLE o_order
    ADD COLUMN finished_at DATETIME(3) NULL AFTER paid_at;

UPDATE o_order
SET finished_at = updated_at
WHERE status = 5
  AND finished_at IS NULL
  AND is_deleted = 0;

CREATE INDEX idx_o_order_status_finished_at
    ON o_order(status, finished_at);

CREATE TABLE IF NOT EXISTS o_order_review (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    reviewer_user_id BIGINT NOT NULL,
    reviewed_user_id BIGINT NOT NULL,
    reviewer_role VARCHAR(16) NOT NULL COMMENT 'BUYER,SELLER',
    communication_star TINYINT NOT NULL,
    timeliness_star TINYINT NOT NULL,
    credibility_star TINYINT NOT NULL,
    overall_star DECIMAL(3,1) NOT NULL,
    comment VARCHAR(280) NULL,
    visibility_status VARCHAR(24) NOT NULL DEFAULT 'VISIBLE' COMMENT 'VISIBLE,HIDDEN_BY_ADMIN',
    visibility_reason VARCHAR(280) NULL,
    visibility_operator_user_id BIGINT NULL,
    visibility_updated_at DATETIME(3) NULL,
    edit_count TINYINT NOT NULL DEFAULT 0,
    last_edited_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_order_review_order_reviewer (order_no, reviewer_user_id),
    KEY idx_o_order_review_reviewed_created (reviewed_user_id, created_at),
    KEY idx_o_order_review_reviewed_role_created (reviewed_user_id, reviewer_role, created_at),
    KEY idx_o_order_review_wall (reviewed_user_id, visibility_status, overall_star, created_at),
    KEY idx_o_order_review_order_no (order_no),
    KEY idx_o_order_review_reviewer (reviewer_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
