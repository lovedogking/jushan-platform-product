-- =====================================================
-- Phase 2 D1: 新增通行记录查看权限（record:view）
-- =====================================================

-- 1. 插入权限记录
INSERT INTO sys_permission (code, name, description, type)
SELECT 'record:view', '通行记录查看', '查看和导出通行记录', 'OPERATION'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'record:view');

-- 2. 授权给固定角色
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT role_code, 'record:view'
FROM (SELECT 'super_admin' AS role_code UNION ALL
      SELECT 'customer_admin' UNION ALL
      SELECT 'parking_manager' UNION ALL
      SELECT 'finance') rp
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_code = rp.role_code AND srp.permission_code = 'record:view');

-- 3. 授权给自定义角色中的 SUPER_ADMIN
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT cr.id, 'record:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'record:view');
