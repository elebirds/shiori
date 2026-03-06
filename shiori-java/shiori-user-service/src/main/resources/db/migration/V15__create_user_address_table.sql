CREATE TABLE IF NOT EXISTS u_user_address (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    receiver_name VARCHAR(32) NOT NULL,
    receiver_phone VARCHAR(20) NOT NULL,
    province VARCHAR(32) NOT NULL,
    city VARCHAR(32) NOT NULL,
    district VARCHAR(32) NOT NULL,
    detail_address VARCHAR(128) NOT NULL,
    is_default TINYINT NOT NULL DEFAULT 0,
    is_deleted TINYINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_u_user_address_user (user_id, is_deleted, created_at, id),
    KEY idx_u_user_address_default (user_id, is_default, is_deleted, id),
    CONSTRAINT fk_u_user_address_user_id FOREIGN KEY (user_id) REFERENCES u_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
