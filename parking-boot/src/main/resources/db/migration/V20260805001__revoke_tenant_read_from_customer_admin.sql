-- ---------------------------------------------------------------------------
-- FIX: 撤销 customer_admin 的 tenant:read 权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. tenant:read / tenant:write 是平台级权限，允许查看/操作所有租户信息。
--   2. customer_admin 是租户内管理员，不应拥有跨租户查看能力。
--   3. V20260804001 误将 tenant:read 补入 customer_admin 默认权限。
-- 修复：
--   1. 从所有 customer_admin 角色中删除 tenant:read 权限关联。
--   2. 不影响平台级角色（如 SUPER_ADMIN）的 tenant:read。
-- ---------------------------------------------------------------------------

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_custom_role scr ON rp.role_id = scr.id
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
  AND rp.permission_code = 'tenant:read';
