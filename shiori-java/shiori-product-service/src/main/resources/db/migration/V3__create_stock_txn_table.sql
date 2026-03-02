CREATE TABLE IF NOT EXISTS p_stock_txn (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    op_type VARCHAR(16) NOT NULL COMMENT 'DEDUCT/RELEASE',
    quantity INT NOT NULL,
    success TINYINT(1) NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_stock_txn_biz_op (biz_no, op_type),
    KEY idx_p_stock_txn_sku_id (sku_id),
    CONSTRAINT fk_p_stock_txn_sku_id FOREIGN KEY (sku_id) REFERENCES p_sku(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
