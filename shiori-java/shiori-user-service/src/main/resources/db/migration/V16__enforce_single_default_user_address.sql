-- 先清理历史数据：每个用户最多保留一个默认地址（取最新 id）
UPDATE u_user_address a
JOIN (
    SELECT user_id, MAX(id) AS keep_id
    FROM u_user_address
    WHERE is_deleted = 0 AND is_default = 1
    GROUP BY user_id
) k ON k.user_id = a.user_id
SET a.is_default = CASE WHEN a.id = k.keep_id THEN 1 ELSE 0 END
WHERE a.is_deleted = 0
  AND a.is_default = 1;

-- 对于没有默认地址但仍有地址的用户，补齐一个默认地址（取最新 id）
UPDATE u_user_address a
JOIN (
    SELECT user_id, MAX(id) AS keep_id
    FROM u_user_address
    WHERE is_deleted = 0
    GROUP BY user_id
) k ON k.user_id = a.user_id
LEFT JOIN (
    SELECT DISTINCT user_id
    FROM u_user_address
    WHERE is_deleted = 0 AND is_default = 1
) d ON d.user_id = a.user_id
SET a.is_default = CASE WHEN a.id = k.keep_id THEN 1 ELSE 0 END
WHERE a.is_deleted = 0
  AND d.user_id IS NULL;

ALTER TABLE u_user_address
    ADD COLUMN default_guard_user_id BIGINT GENERATED ALWAYS AS (
        CASE WHEN is_deleted = 0 AND is_default = 1 THEN user_id ELSE NULL END
    ) STORED,
    ADD UNIQUE KEY uk_u_user_address_default_guard (default_guard_user_id);
