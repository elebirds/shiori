INSERT INTO u_permission (perm_code, perm_name, domain, description, status, is_deleted)
VALUES
    ('order.refund.apply', '申请退款', 'order', '允许买家发起退款申请', 1, 0),
    ('order.refund.review', '审核退款', 'order', '允许卖家审核退款申请', 1, 0),
    ('order.refund.manage', '管理退款', 'order', '允许管理员管理退款与重试', 1, 0),
    ('payment.wallet.ledger.read', '读取钱包流水', 'payment', '允许读取自己的钱包流水', 1, 0),
    ('payment.wallet.ledger.manage', '管理钱包流水', 'payment', '允许管理员检索平台钱包流水', 1, 0)
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
    'order.refund.apply',
    'order.refund.review',
    'payment.wallet.ledger.read'
)
WHERE r.role_code = 'ROLE_USER';

INSERT IGNORE INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON p.perm_code IN (
    'order.refund.apply',
    'order.refund.review',
    'order.refund.manage',
    'payment.wallet.ledger.read',
    'payment.wallet.ledger.manage'
)
WHERE r.role_code = 'ROLE_ADMIN';
