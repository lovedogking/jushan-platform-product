-- =============================================================================
-- Flyway 迁移：车辆管理菜单与续费按钮所需权限
-- =============================================================================
-- 背景：
--   1. 前端车辆管理页与“续费”按钮依赖 vehicle:view / vehicle:renew 等权限码
--   2. 后端 SysVehicleController 已使用 vehicle:create/update/delete/view 做鉴权
--   3. 续费接口新增 vehicle:renew 权限码
-- 修复：为 customer_admin / SUPER_ADMIN 角色补齐车辆相关权限（幂等：ON DUPLICATE KEY UPDATE）
-- =============================================================================

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'vehicle:view'   AS permission_code, 'menu'   AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'vehicle:create', 'button', 'company'
    UNION ALL SELECT 'vehicle:update', 'button', 'company'
    UNION ALL SELECT 'vehicle:delete', 'button', 'company'
    UNION ALL SELECT 'vehicle:renew',  'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    'all' AS data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'vehicle:view'   AS permission_code, 'menu'   AS permission_type
    UNION ALL SELECT 'vehicle:create', 'button'
    UNION ALL SELECT 'vehicle:update', 'button'
    UNION ALL SELECT 'vehicle:delete', 'button'
    UNION ALL SELECT 'vehicle:renew',  'button'
) perm
WHERE scr.role_code = 'SUPER_ADMIN'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);
