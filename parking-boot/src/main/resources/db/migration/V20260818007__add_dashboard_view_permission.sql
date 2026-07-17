-- =====================================================
-- Phase 2 D4: 新增仪表盘查看权限（dashboard:view）
-- =====================================================

INSERT INTO sys_permission (code, name, description)
SELECT 'dashboard:view', '仪表盘查看', '查看首页仪表盘数据'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'dashboard:view');

-- 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(MD5(CONCAT(cr.id, ':dashboard:view')), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'dashboard:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'dashboard:view');
