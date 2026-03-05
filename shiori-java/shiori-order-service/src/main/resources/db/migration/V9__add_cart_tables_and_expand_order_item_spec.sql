CREATE TABLE IF NOT EXISTS o_cart (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_cart_buyer (buyer_user_id),
    KEY idx_o_cart_seller (seller_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS o_cart_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    cart_id BIGINT NOT NULL,
    buyer_user_id BIGINT NOT NULL,
    seller_user_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    sku_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_o_cart_item_buyer_sku (buyer_user_id, sku_id),
    KEY idx_o_cart_item_cart (cart_id),
    KEY idx_o_cart_item_product (product_id),
    CONSTRAINT fk_o_cart_item_cart
        FOREIGN KEY (cart_id) REFERENCES o_cart (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

ALTER TABLE o_order_item
    MODIFY COLUMN spec_json VARCHAR(2048) NULL;
