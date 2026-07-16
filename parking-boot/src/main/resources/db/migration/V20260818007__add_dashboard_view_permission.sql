-- =====================================================
-- Phase 2 D4: 新增仪表盘查看权限（dashboard:view）
-- =====================================================

INSERT INTO sys_permission (code, name, description, type)
SELECT 'dashboard:view', '仪表盘查看', '查看首页仪表盘数据', 'OPERATION'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'dashboard:view');

-- 授权给固定角色
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT role_code, 'dashboard:view'
FROM (SELECT 'super_admin' AS role_code UNION ALL
      SELECT 'customer_admin' UNION ALL
      SELECT 'parking_manager' UNION ALL
      SELECT 'finance' UNION ALL
      SELECT 'device_maintenance') rp
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_code = rp.role_code AND srp.permission_code = 'dashboard:view');

-- 授权给自定义角色中的 SUPER_ADMIN
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT cr.id, 'dashboard:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'dashboard:view');
