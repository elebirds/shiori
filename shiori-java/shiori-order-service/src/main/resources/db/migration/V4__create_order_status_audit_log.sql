CREATE TABLE IF NOT EXISTS o_order_status_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    target_order_no VARCHAR(64) NOT NULL,
    operator_user_id BIGINT NULL,
    source VARCHAR(32) NOT NULL,
    from_status TINYINT NOT NULL,
    to_status TINYINT NOT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_o_order_status_audit_order (target_order_no, created_at),
    KEY idx_o_order_status_audit_source (source, created_at),
    KEY idx_o_order_status_audit_operator (operator_user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
