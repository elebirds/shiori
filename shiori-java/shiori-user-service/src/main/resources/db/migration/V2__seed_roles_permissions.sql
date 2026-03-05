INSERT INTO u_role (role_code, role_name, description, status, is_deleted)
VALUES
    ('ROLE_USER', '普通用户', '平台普通用户角色', 1, 0),
    ('ROLE_ADMIN', '管理员', '平台管理员角色', 1, 0)
AS new
ON DUPLICATE KEY UPDATE
    role_name = new.role_name,
    description = new.description,
    status = new.status,
    is_deleted = new.is_deleted,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO u_permission (perm_code, perm_name, domain, description, status, is_deleted)
VALUES
    ('user:profile:read', '读取用户资料', 'user', '读取当前用户资料', 1, 0),
    ('user:profile:update', '更新用户资料', 'user', '更新当前用户资料', 1, 0),
    ('product:read', '读取商品', 'product', '读取商品与库存信息', 1, 0),
    ('product:write', '管理商品', 'product', '新增或修改商品信息', 1, 0),
    ('order:read', '读取订单', 'order', '读取订单详情', 1, 0),
    ('order:create', '创建订单', 'order', '创建新订单', 1, 0),
    ('order:cancel', '取消订单', 'order', '取消未支付订单', 1, 0),
    ('admin:manage', '后台管理', 'admin', '平台管理权限', 1, 0)
AS new
ON DUPLICATE KEY UPDATE
    perm_name = new.perm_name,
    domain = new.domain,
    description = new.description,
    status = new.status,
    is_deleted = new.is_deleted,
    updated_at = CURRENT_TIMESTAMP(3);

INSERT IGNORE INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON p.perm_code IN (
    'user:profile:read',
    'user:profile:update',
    'product:read',
    'order:read',
    'order:create',
    'order:cancel'
)
WHERE r.role_code = 'ROLE_USER';

INSERT IGNORE INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON 1 = 1
WHERE r.role_code = 'ROLE_ADMIN';
