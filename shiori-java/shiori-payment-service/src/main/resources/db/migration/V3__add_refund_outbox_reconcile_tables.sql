CREATE TABLE IF NOT EXISTS p_trade_refund (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    refund_no VARCHAR(64) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    amount_cent BIGINT NOT NULL,
    status TINYINT NOT NULL COMMENT '1=PENDING_FUNDS,2=SUCCEEDED',
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_trade_refund_refund_no (refund_no),
    UNIQUE KEY uk_p_trade_refund_order_no (order_no),
    KEY idx_p_trade_refund_seller_status (seller_user_id, status, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_wallet_balance_outbox (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    user_id BIGINT NOT NULL,
    biz_no VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(16) NOT NULL COMMENT 'PENDING,SENT,FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(512) NULL,
    next_retry_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL,
    UNIQUE KEY uk_p_wallet_balance_outbox_event_id (event_id),
    KEY idx_p_wallet_balance_outbox_relay (status, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_reconcile_issue (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issue_no VARCHAR(64) NOT NULL,
    issue_type VARCHAR(64) NOT NULL,
    biz_no VARCHAR(64) NULL,
    severity VARCHAR(16) NOT NULL DEFAULT 'WARN',
    status VARCHAR(16) NOT NULL DEFAULT 'NEW' COMMENT 'NEW,ACKED,RESOLVED',
    detail_json TEXT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    resolved_at DATETIME(3) NULL,
    resolved_by_user_id BIGINT NULL,
    UNIQUE KEY uk_p_reconcile_issue_issue_no (issue_no),
    KEY idx_p_reconcile_issue_status_type (status, issue_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
