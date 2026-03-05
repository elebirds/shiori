CREATE TABLE IF NOT EXISTS u_user_capability_ban (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    capability_code VARCHAR(64) NOT NULL,
    is_banned TINYINT(1) NOT NULL DEFAULT 1,
    reason VARCHAR(255) NULL,
    operator_user_id BIGINT NOT NULL,
    start_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    end_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_u_user_capability_ban (user_id, capability_code),
    KEY idx_u_user_capability_ban_user_active (user_id, is_banned, end_at),
    KEY idx_u_user_capability_ban_capability (capability_code, is_banned)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
