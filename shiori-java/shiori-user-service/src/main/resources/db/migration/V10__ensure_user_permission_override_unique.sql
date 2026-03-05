SET @has_override_unique_index := (
    SELECT COUNT(1)
    FROM INFORMATION_SCHEMA.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'u_user_permission_override'
      AND INDEX_NAME = 'uk_u_user_permission_override'
);

SET @override_unique_ddl := IF(
    @has_override_unique_index = 0,
    'ALTER TABLE u_user_permission_override ADD UNIQUE KEY uk_u_user_permission_override (user_id, permission_code)',
    'SELECT 1'
);

PREPARE stmt FROM @override_unique_ddl;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
