CREATE TABLE IF NOT EXISTS o_order (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(64) NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    status TINYINT NOT NULL COMMENT '1=UNPAID,2=PAID,3=CANCELED',
    total_amount_cent BIGINT NOT NULL,
    item_count INT NOT NULL,
    payment_no VARCHAR(64) NULL,
    cancel_reason VARCHAR(128) NULL,
    timeout_at DATETIME(3) NOT NULL,
    paid_at DATETIME(3) NULL,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_order_order_no (order_no),
    UNIQUE KEY uk_o_order_payment_no (payment_no),
    KEY idx_o_order_buyer_created (buyer_user_id, created_at),
    KEY idx_o_order_seller_created (seller_user_id, created_at),
    KEY idx_o_order_status_timeout (status, timeout_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS o_order_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    product_id BIGINT NOT NULL,
    product_no VARCHAR(64) NOT NULL,
    sku_id BIGINT NOT NULL,
    sku_no VARCHAR(64) NOT NULL,
    sku_name VARCHAR(128) NOT NULL,
    spec_json VARCHAR(512) NULL,
    price_cent BIGINT NOT NULL,
    quantity INT NOT NULL,
    subtotal_cent BIGINT NOT NULL,
    owner_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_o_order_item_order_no (order_no),
    KEY idx_o_order_item_product (product_id),
    KEY idx_o_order_item_sku (sku_id),
    CONSTRAINT fk_o_order_item_order
        FOREIGN KEY (order_id) REFERENCES o_order (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS o_order_create_idempotency (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_user_id BIGINT NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_order_idempotency_buyer_key (buyer_user_id, idempotency_key),
    KEY idx_o_order_idempotency_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS o_outbox_event (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    event_id VARCHAR(64) NOT NULL,
    aggregate_id VARCHAR(64) NOT NULL,
    type VARCHAR(64) NOT NULL,
    payload TEXT NOT NULL,
    exchange_name VARCHAR(128) NOT NULL,
    routing_key VARCHAR(128) NOT NULL,
    status VARCHAR(16) NOT NULL COMMENT 'PENDING,SENT,FAILED',
    retry_count INT NOT NULL DEFAULT 0,
    last_error VARCHAR(512) NULL,
    next_retry_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    sent_at DATETIME(3) NULL,
    UNIQUE KEY uk_o_outbox_event_id (event_id),
    KEY idx_o_outbox_relay (status, next_retry_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
