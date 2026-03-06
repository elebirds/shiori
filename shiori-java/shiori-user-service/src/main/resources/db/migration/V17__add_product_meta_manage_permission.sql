INSERT INTO u_permission (perm_code, perm_name, domain, description, status, is_deleted)
VALUES ('product.meta.manage', '商品元数据管理', 'product', '允许管理商品校区与分类元数据', 1, 0)
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
JOIN u_permission p ON p.perm_code = 'product.meta.manage'
WHERE r.role_code = 'ROLE_ADMIN';
