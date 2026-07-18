-- Phase 4-1: 新增 fee:reduce 权限（费用减免独立权限码，最小权限原则）
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:reduce', '费用减免', '对收费金额进行减免，需要二次确认超阈值操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:reduce');

-- 为 booth_operator 角色授权
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT 'booth_operator', 'fee:reduce'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_code = 'booth_operator' AND permission_code = 'fee:reduce'
);

-- 为 parking_manager 角色授权
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT 'parking_manager', 'fee:reduce'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_code = 'parking_manager' AND permission_code = 'fee:reduce'
);
