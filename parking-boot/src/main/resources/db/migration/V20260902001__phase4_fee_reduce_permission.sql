-- Phase 4-1: 新增 fee:reduce 权限（费用减免独立权限码，最小权限原则）
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:reduce', '费用减免', '对收费金额进行减免，需要二次确认超阈值操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:reduce');

-- 为 booth_operator 角色授权（通过 sys_custom_role 查找 role_id，兼容新旧 schema）
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT scr.id, 'fee:reduce', 'button', 'self'
FROM sys_custom_role scr
WHERE scr.role_code = 'booth_operator'
  AND scr.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = scr.id AND srp.permission_code = 'fee:reduce'
  );

-- 为 parking_manager 角色授权
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT scr.id, 'fee:reduce', 'button', 'parking'
FROM sys_custom_role scr
WHERE scr.role_code = 'parking_manager'
  AND scr.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = scr.id AND srp.permission_code = 'fee:reduce'
  );
