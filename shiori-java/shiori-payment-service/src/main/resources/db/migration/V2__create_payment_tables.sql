CREATE TABLE IF NOT EXISTS p_wallet_account (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    available_balance_cent BIGINT NOT NULL DEFAULT 0,
    frozen_balance_cent BIGINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_wallet_account_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_wallet_ledger (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    biz_type VARCHAR(32) NOT NULL,
    biz_no VARCHAR(64) NOT NULL,
    change_type VARCHAR(64) NOT NULL,
    delta_available_cent BIGINT NOT NULL,
    delta_frozen_cent BIGINT NOT NULL,
    available_after_cent BIGINT NOT NULL,
    frozen_after_cent BIGINT NOT NULL,
    remark VARCHAR(255) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_p_wallet_ledger_user_time (user_id, created_at),
    KEY idx_p_wallet_ledger_biz (biz_type, biz_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_trade_payment (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    payment_no VARCHAR(64) NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    amount_cent BIGINT NOT NULL,
    status TINYINT NOT NULL COMMENT '1=RESERVED,2=SETTLED,3=RELEASED',
    reserved_at DATETIME(3) NULL,
    settled_at DATETIME(3) NULL,
    released_at DATETIME(3) NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_trade_payment_order_no (order_no),
    UNIQUE KEY uk_p_trade_payment_payment_no (payment_no),
    KEY idx_p_trade_payment_buyer_status (buyer_user_id, status),
    KEY idx_p_trade_payment_seller_status (seller_user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_cdk_batch (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_no VARCHAR(64) NOT NULL,
    quantity INT NOT NULL,
    amount_cent BIGINT NOT NULL,
    expire_at DATETIME(3) NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_cdk_batch_batch_no (batch_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_cdk_code (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    batch_id BIGINT NOT NULL,
    code_hash CHAR(64) NOT NULL,
    code_mask VARCHAR(32) NOT NULL,
    amount_cent BIGINT NOT NULL,
    expire_at DATETIME(3) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=UNUSED,2=USED',
    redeemed_by_user_id BIGINT NULL,
    redeemed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_cdk_code_hash (code_hash),
    KEY idx_p_cdk_code_batch (batch_id),
    KEY idx_p_cdk_code_status_expire (status, expire_at),
    CONSTRAINT fk_p_cdk_code_batch_id
        FOREIGN KEY (batch_id) REFERENCES p_cdk_batch (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
