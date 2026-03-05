INSERT INTO u_permission (perm_code, perm_name, domain, description, status, is_deleted)
VALUES
    ('chat.send', '发送聊天消息', 'chat', '允许发送聊天消息', 1, 0),
    ('chat.read', '读取聊天消息', 'chat', '允许读取聊天消息', 1, 0),
    ('order.create', '创建订单', 'order', '允许创建订单', 1, 0),
    ('order.pay', '支付订单', 'order', '允许支付订单', 1, 0),
    ('order.cancel', '取消订单', 'order', '允许取消订单', 1, 0),
    ('order.deliver', '订单发货', 'order', '允许卖家发货', 1, 0),
    ('order.finish', '订单完成', 'order', '允许完结订单', 1, 0),
    ('order.confirm_receipt', '确认收货', 'order', '允许买家确认收货', 1, 0),
    ('product.create', '创建商品', 'product', '允许创建商品', 1, 0),
    ('product.update', '更新商品', 'product', '允许更新商品信息', 1, 0),
    ('product.publish', '发布商品', 'product', '允许发布商品', 1, 0),
    ('product.off_shelf', '下架商品', 'product', '允许下架商品', 1, 0),
    ('product.stock.adjust', '库存变更', 'product', '允许库存变更操作', 1, 0),
    ('admin.user.permission_override.manage', '用户权限覆盖管理', 'admin', '允许管理用户权限覆盖', 1, 0)
ON DUPLICATE KEY UPDATE
    perm_name = VALUES(perm_name),
    domain = VALUES(domain),
    description = VALUES(description),
    status = VALUES(status),
    is_deleted = VALUES(is_deleted),
    updated_at = CURRENT_TIMESTAMP(3);

INSERT INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON p.perm_code IN (
    'chat.send',
    'chat.read',
    'order.create',
    'order.pay',
    'order.cancel',
    'order.deliver',
    'order.finish',
    'order.confirm_receipt',
    'product.create',
    'product.update',
    'product.publish',
    'product.off_shelf'
)
WHERE r.role_code = 'ROLE_USER'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);

INSERT INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON 1 = 1
WHERE r.role_code = 'ROLE_ADMIN'
ON DUPLICATE KEY UPDATE role_id = VALUES(role_id);
