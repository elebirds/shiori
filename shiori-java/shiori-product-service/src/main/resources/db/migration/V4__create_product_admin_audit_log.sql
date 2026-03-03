CREATE TABLE IF NOT EXISTS p_admin_audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_user_id BIGINT NOT NULL,
    target_product_id BIGINT NOT NULL,
    action VARCHAR(64) NOT NULL,
    before_json LONGTEXT NULL,
    after_json LONGTEXT NULL,
    reason VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_p_admin_audit_operator (operator_user_id, created_at),
    KEY idx_p_admin_audit_target (target_product_id, created_at),
    KEY idx_p_admin_audit_action_time (action, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
