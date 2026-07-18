-- ---------------------------------------------------------------------------
-- FIX: 为 customer_admin 补全公司管理后端接口所需权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. 前端菜单/路由使用 company:view，按钮使用 company:create/update/delete。
--   2. 后端 CompanyController 实际使用 company:read（列表/详情/树）和
--      company:write（创建/更新）。
--   3. 已存在的 customer_admin 角色缺少 company:read 和 company:write，
--      导致租户管理员进入公司管理页面即提示无权限，且无法新增/编辑公司。
-- 修复：
--   1. 为所有未删除的 customer_admin 角色补充 company:read 和 company:write。
--   2. 保持幂等：ON DUPLICATE KEY UPDATE。
-- ---------------------------------------------------------------------------

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'company:read' AS permission_code, 'button' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'company:write', 'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);
