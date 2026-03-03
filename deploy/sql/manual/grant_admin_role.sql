-- 手工授予管理员角色（在 shiori_user 库执行）
-- 1) 先通过 /api/user/auth/register 注册普通账号
-- 2) 再执行本 SQL 授予 ROLE_ADMIN

USE shiori_user;

SET @target_username = 'your-username';

INSERT INTO u_user_role (user_id, role_id, created_at)
SELECT u.id, r.id, CURRENT_TIMESTAMP(3)
FROM u_user u
JOIN u_role r ON r.role_code = 'ROLE_ADMIN' AND r.status = 1 AND r.is_deleted = 0
WHERE u.username = @target_username
  AND u.is_deleted = 0
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
