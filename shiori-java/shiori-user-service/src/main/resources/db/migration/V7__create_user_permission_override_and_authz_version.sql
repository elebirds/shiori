CREATE TABLE IF NOT EXISTS u_user_permission_override (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    permission_code VARCHAR(100) NOT NULL,
    effect VARCHAR(16) NOT NULL COMMENT 'ALLOW or DENY',
    start_at DATETIME(3) NULL,
    end_at DATETIME(3) NULL,
    reason VARCHAR(255) NULL,
    operator_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_u_user_permission_override (user_id, permission_code),
    KEY idx_u_user_permission_override_active (user_id, effect, start_at, end_at),
    KEY idx_u_user_permission_override_code (permission_code, effect)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE IF NOT EXISTS u_user_authz_version (
    user_id BIGINT NOT NULL PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 1,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_u_user_authz_version_user_id FOREIGN KEY (user_id) REFERENCES u_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

INSERT INTO u_user_authz_version (user_id, version, updated_at)
SELECT id, GREATEST(version, 1), CURRENT_TIMESTAMP(3)
FROM u_user
WHERE is_deleted = 0
ON DUPLICATE KEY UPDATE
    version = GREATEST(u_user_authz_version.version, VALUES(version)),
    updated_at = CURRENT_TIMESTAMP(3);
