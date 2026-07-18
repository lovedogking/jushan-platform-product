-- =====================================================
-- Phase 2 D1: 新增通行记录查看权限（record:view）
-- =====================================================

-- 1. 插入权限记录
INSERT INTO sys_permission (code, name, description)
SELECT 'record:view', '通行记录查看', '查看和导出通行记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'record:view');

-- 2. 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(cr.id, ':record:view'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'record:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'record:view');
