CREATE TABLE IF NOT EXISTS p_product (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_no VARCHAR(32) NOT NULL,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    description TEXT NULL,
    cover_object_key VARCHAR(255) NULL,
    status TINYINT NOT NULL DEFAULT 1 COMMENT '1=DRAFT,2=ON_SALE,3=OFF_SHELF',
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_product_product_no (product_no),
    KEY idx_p_product_owner_status (owner_user_id, status),
    KEY idx_p_product_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS p_sku (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    sku_no VARCHAR(32) NOT NULL,
    sku_name VARCHAR(120) NOT NULL,
    spec_json VARCHAR(1024) NULL,
    price_cent BIGINT NOT NULL,
    stock INT NOT NULL,
    is_deleted TINYINT(1) NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_p_sku_sku_no (sku_no),
    KEY idx_p_sku_product_id (product_id),
    KEY idx_p_sku_price (price_cent),
    CONSTRAINT fk_p_sku_product_id FOREIGN KEY (product_id) REFERENCES p_product(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
