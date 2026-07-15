-- ---------------------------------------------------------------------------
-- FIX: 为已存在的 customer_admin 角色补全租户级管理权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. TenantService.resolveCustomerAdminRoleId 在旧代码中创建角色时未初始化权限。
--   2. 已通过审核的租户（如 13562996687）对应的 customer_admin 角色权限为空，
--      导致登录后 JWT 权限列表为空，无法进入车场运营 / 设备运维 / 平台管理。
-- 修复：
--   1. 为所有未删除的 customer_admin 角色插入完整默认权限。
--   2. 使用 role_id + permission_code 的 MD5 派生确定性正 bigint 作为主键，避免冲突。
--   3. 保持幂等：ON DUPLICATE KEY UPDATE 仅更新 permission_type / data_scope。
-- ---------------------------------------------------------------------------

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(MD5(CONCAT(scr.id, ':', perm.permission_code)), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    -- 企业管理
    SELECT 'company:view' AS permission_code, 'menu' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'company:create', 'button', 'company'
    UNION ALL SELECT 'company:update', 'button', 'company'
    UNION ALL SELECT 'company:delete', 'button', 'company'
    -- 员工账号
    UNION ALL SELECT 'account:view', 'menu', 'company'
    UNION ALL SELECT 'account:create', 'button', 'company'
    UNION ALL SELECT 'account:update', 'button', 'company'
    UNION ALL SELECT 'account:delete', 'button', 'company'
    -- 角色权限
    UNION ALL SELECT 'role:view', 'menu', 'company'
    UNION ALL SELECT 'role:create', 'button', 'company'
    UNION ALL SELECT 'role:update', 'button', 'company'
    UNION ALL SELECT 'role:delete', 'button', 'company'
    -- 停车场
    UNION ALL SELECT 'parking:view', 'menu', 'company'
    UNION ALL SELECT 'parking:read', 'button', 'company'
    UNION ALL SELECT 'parking:write', 'button', 'company'
    UNION ALL SELECT 'parking:update', 'button', 'company'
    UNION ALL SELECT 'parking:delete', 'button', 'company'
    UNION ALL SELECT 'parking:disable', 'button', 'company'
    -- 区域/车道
    UNION ALL SELECT 'lane:view', 'menu', 'company'
    UNION ALL SELECT 'lane:update', 'button', 'company'
    UNION ALL SELECT 'lane:delete', 'button', 'company'
    -- 设备
    UNION ALL SELECT 'device:read', 'menu', 'company'
    UNION ALL SELECT 'device:manage', 'button', 'company'
    -- 收费规则
    UNION ALL SELECT 'fee:read', 'menu', 'company'
    UNION ALL SELECT 'fee:write', 'button', 'company'
    -- 计费记录
    UNION ALL SELECT 'billing:read', 'menu', 'company'
    UNION ALL SELECT 'billing:write', 'button', 'company'
    UNION ALL SELECT 'billing:switch', 'button', 'company'
    -- 停车记录
    UNION ALL SELECT 'record:read', 'menu', 'company'
    -- 岗亭
    UNION ALL SELECT 'booth:view', 'menu', 'company'
    UNION ALL SELECT 'booth:operate', 'button', 'company'
    UNION ALL SELECT 'booth:monitor', 'button', 'company'
    -- 车辆
    UNION ALL SELECT 'vehicle:view', 'menu', 'company'
    UNION ALL SELECT 'vehicle:create', 'button', 'company'
    UNION ALL SELECT 'vehicle:update', 'button', 'company'
    UNION ALL SELECT 'vehicle:delete', 'button', 'company'
    -- 部门
    UNION ALL SELECT 'department:view', 'menu', 'company'
    UNION ALL SELECT 'department:create', 'button', 'company'
    UNION ALL SELECT 'department:update', 'button', 'company'
    UNION ALL SELECT 'department:delete', 'button', 'company'
    -- 用户
    UNION ALL SELECT 'user:read', 'menu', 'company'
    UNION ALL SELECT 'user:write', 'button', 'company'
    -- 租户自身信息
    UNION ALL SELECT 'tenant:read', 'menu', 'company'
    -- 小程序
    UNION ALL SELECT 'miniapp:view', 'menu', 'company'
    UNION ALL SELECT 'miniapp:operate', 'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);
