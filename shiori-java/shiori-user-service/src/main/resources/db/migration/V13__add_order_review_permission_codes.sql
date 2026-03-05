INSERT INTO u_permission (perm_code, perm_name, domain, description, status, is_deleted)
VALUES
    ('order.review.create', '创建订单评价', 'order', '允许对已完成订单创建评价', 1, 0),
    ('order.review.update', '修改订单评价', 'order', '允许在时限内修改自己的订单评价', 1, 0),
    ('order.review.moderate', '订单评价治理', 'order', '允许后台治理订单评价可见性', 1, 0)
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
    'order.review.create',
    'order.review.update'
)
WHERE r.role_code = 'ROLE_USER';

INSERT IGNORE INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON p.perm_code IN (
    'order.review.create',
    'order.review.update',
    'order.review.moderate'
)
WHERE r.role_code = 'ROLE_ADMIN';

