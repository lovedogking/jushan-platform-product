-- ---------------------------------------------------------------------------
-- FIX: 为 customer_admin 角色补充缺失的菜单权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. 前端菜单要求 company:view / account:view / role:view 权限码才显示对应菜单
--   2. 后端 Controller 也使用这些权限码做接口鉴权
--   3. 但现有迁移中 customer_admin 角色只关联了 company:read / company:write 等旧权限码
--   4. role:view / role:create / role:update 等权限从未插入到 sys_role_permission
-- 修复：
--   1. 统一插入所有缺失的权限码到 sys_role_permission（按 role_id 关联）
--   2. 为 customer_admin 角色（role_code = 'customer_admin'）补充全部所需权限
--   3. 保持幂等：ON DUPLICATE KEY UPDATE
-- ---------------------------------------------------------------------------

-- 插入 customer_admin 所需的所有权限（按 role_id 动态匹配）
-- 使用 MD5 派生确定性正 bigint 作为主键，避免与现有 Snowflake ID 冲突
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'company:view' AS permission_code, 'menu' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'company:create', 'button', 'company'
    UNION ALL SELECT 'company:update', 'button', 'company'
    UNION ALL SELECT 'company:delete', 'button', 'company'
    UNION ALL SELECT 'account:view', 'menu', 'company'
    UNION ALL SELECT 'account:create', 'button', 'company'
    UNION ALL SELECT 'account:update', 'button', 'company'
    UNION ALL SELECT 'account:delete', 'button', 'company'
    UNION ALL SELECT 'role:view', 'menu', 'company'
    UNION ALL SELECT 'role:create', 'button', 'company'
    UNION ALL SELECT 'role:update', 'button', 'company'
    UNION ALL SELECT 'role:delete', 'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);

-- 同时补充 SUPER_ADMIN 的缺失权限（如果还没有的话）。
-- 注：SUPER_ADMIN 实际已拥有通配符 '*' 权限，此段仅为完整性保留。
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    'all' AS data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'company:view' AS permission_code, 'menu' AS permission_type
    UNION ALL SELECT 'company:create', 'button'
    UNION ALL SELECT 'company:update', 'button'
    UNION ALL SELECT 'company:delete', 'button'
    UNION ALL SELECT 'account:view', 'menu'
    UNION ALL SELECT 'account:create', 'button'
    UNION ALL SELECT 'account:update', 'button'
    UNION ALL SELECT 'account:delete', 'button'
    UNION ALL SELECT 'role:view', 'menu'
    UNION ALL SELECT 'role:create', 'button'
    UNION ALL SELECT 'role:update', 'button'
    UNION ALL SELECT 'role:delete', 'button'
) perm
WHERE scr.role_code = 'SUPER_ADMIN'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);
