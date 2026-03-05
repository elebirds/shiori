ALTER TABLE o_order
    ADD COLUMN refund_status VARCHAR(32) NULL AFTER payment_mode,
    ADD COLUMN refund_no VARCHAR(64) NULL AFTER refund_status,
    ADD COLUMN refund_amount_cent BIGINT NULL AFTER refund_no,
    ADD COLUMN refund_updated_at DATETIME(3) NULL AFTER refund_amount_cent;

CREATE UNIQUE INDEX uk_o_order_refund_no ON o_order(refund_no);
CREATE INDEX idx_o_order_refund_status_updated ON o_order(refund_status, refund_updated_at);

CREATE TABLE IF NOT EXISTS o_order_refund (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    amount_cent BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL COMMENT 'REQUESTED,REJECTED,PENDING_FUNDS,SUCCEEDED',
    apply_reason VARCHAR(255) NULL,
    reject_reason VARCHAR(255) NULL,
    reviewed_by_user_id BIGINT NULL,
    review_deadline_at DATETIME(3) NULL,
    reviewed_at DATETIME(3) NULL,
    auto_approved TINYINT NOT NULL DEFAULT 0,
    payment_no VARCHAR(64) NULL,
    last_error VARCHAR(255) NULL,
    retry_count INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_order_refund_refund_no (refund_no),
    UNIQUE KEY uk_o_order_refund_order_no (order_no),
    KEY idx_o_order_refund_seller_status (seller_user_id, status, updated_at),
    KEY idx_o_order_refund_buyer_created (buyer_user_id, created_at),
    KEY idx_o_order_refund_status_deadline (status, review_deadline_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS o_order_refund_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    from_status VARCHAR(32) NULL,
    to_status VARCHAR(32) NOT NULL,
    operator_user_id BIGINT NULL,
    operator_role VARCHAR(32) NOT NULL,
    action VARCHAR(32) NOT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_o_order_refund_audit_refund (refund_no, created_at),
    KEY idx_o_order_refund_audit_order (order_no, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
