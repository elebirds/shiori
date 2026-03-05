INSERT INTO u_user_permission_override (
    user_id,
    permission_code,
    effect,
    start_at,
    end_at,
    reason,
    operator_user_id,
    created_at,
    updated_at
)
SELECT b.user_id,
       m.permission_code,
       'DENY',
       b.start_at,
       b.end_at,
       COALESCE(b.reason, 'migrated from capability ban'),
       b.operator_user_id,
       CURRENT_TIMESTAMP(3),
       CURRENT_TIMESTAMP(3)
FROM u_user_capability_ban b
JOIN (
    SELECT 'CHAT_SEND' AS capability_code, 'chat.send' AS permission_code
    UNION ALL SELECT 'CHAT_READ', 'chat.read'
    UNION ALL SELECT 'ORDER_CREATE', 'order.create'
    UNION ALL SELECT 'PRODUCT_PUBLISH', 'product.publish'
) m ON m.capability_code = b.capability_code
WHERE b.is_banned = 1
ON DUPLICATE KEY UPDATE
    effect = 'DENY',
    start_at = VALUES(start_at),
    end_at = VALUES(end_at),
    reason = VALUES(reason),
    operator_user_id = VALUES(operator_user_id),
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO u_user_authz_version (user_id, version, updated_at)
SELECT DISTINCT user_id, 2, CURRENT_TIMESTAMP(3)
FROM u_user_permission_override
ON DUPLICATE KEY UPDATE
    version = GREATEST(u_user_authz_version.version, VALUES(version)),
    updated_at = CURRENT_TIMESTAMP(3);
