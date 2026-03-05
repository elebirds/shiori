INSERT INTO u_permission (perm_code, perm_name, domain, description, status, is_deleted)
VALUES
    ('payment.wallet.read', '读取钱包余额', 'payment', '允许读取自己的钱包余额', 1, 0),
    ('payment.cdk.redeem', '兑换CDK', 'payment', '允许兑换CDK入账余额', 1, 0),
    ('payment.cdk.manage', '管理CDK批次', 'payment', '允许创建和管理CDK批次', 1, 0)
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
    'payment.wallet.read',
    'payment.cdk.redeem'
)
WHERE r.role_code = 'ROLE_USER';

INSERT IGNORE INTO u_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM u_role r
JOIN u_permission p ON p.perm_code IN (
    'payment.wallet.read',
    'payment.cdk.redeem',
    'payment.cdk.manage'
)
WHERE r.role_code = 'ROLE_ADMIN';
