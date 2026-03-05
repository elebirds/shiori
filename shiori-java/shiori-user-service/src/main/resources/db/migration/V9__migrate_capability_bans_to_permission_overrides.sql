UPDATE u_user_permission_override o
JOIN u_user_capability_ban b ON b.user_id = o.user_id AND b.is_banned = 1
JOIN (
    SELECT 'CHAT_SEND' AS capability_code, 'chat.send' AS permission_code
    UNION ALL SELECT 'CHAT_READ', 'chat.read'
    UNION ALL SELECT 'ORDER_CREATE', 'order.create'
    UNION ALL SELECT 'PRODUCT_PUBLISH', 'product.publish'
) m ON m.capability_code = b.capability_code
   AND o.permission_code = m.permission_code
SET o.effect = 'DENY',
    o.start_at = b.start_at,
    o.end_at = b.end_at,
    o.reason = COALESCE(b.reason, 'migrated from capability ban'),
    o.operator_user_id = b.operator_user_id,
    o.updated_at = CURRENT_TIMESTAMP(3);

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
LEFT JOIN u_user_permission_override o
       ON o.user_id = b.user_id
      AND o.permission_code = m.permission_code
WHERE b.is_banned = 1
  AND o.id IS NULL;

UPDATE u_user_authz_version
SET version = GREATEST(version, 2),
    updated_at = CURRENT_TIMESTAMP(3)
WHERE user_id IN (
    SELECT DISTINCT o.user_id
    FROM u_user_permission_override o
);

INSERT INTO u_user_authz_version (user_id, version, updated_at)
SELECT DISTINCT o.user_id, 2, CURRENT_TIMESTAMP(3)
FROM u_user_permission_override o
LEFT JOIN u_user_authz_version v ON v.user_id = o.user_id
WHERE v.user_id IS NULL;
