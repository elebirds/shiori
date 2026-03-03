CREATE TABLE IF NOT EXISTS o_order_operate_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    operator_user_id BIGINT NOT NULL,
    operation_type VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_order_operate_idem (operator_user_id, operation_type, idempotency_key),
    KEY idx_o_order_operate_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
