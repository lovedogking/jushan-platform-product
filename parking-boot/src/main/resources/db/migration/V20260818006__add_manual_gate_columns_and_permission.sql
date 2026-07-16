-- =====================================================
-- Phase 2 D3: device_command_audit 补充字段 + 权限
-- =====================================================

-- 1. 添加车牌号字段（手动开闸时记录关联车辆）
ALTER TABLE device_command_audit
    ADD COLUMN plate_number VARCHAR(32) DEFAULT NULL COMMENT '关联车牌号（手动开闸时记录）' AFTER command_type;

-- 2. 添加费用字段（分，手工计费时记录）
ALTER TABLE device_command_audit
    ADD COLUMN fee_cents INT DEFAULT NULL COMMENT '费用（分，手工计费时记录）' AFTER plate_number;

-- 3. 插入 device:audit 权限
INSERT INTO sys_permission (code, name, description, type)
SELECT 'device:audit', '设备命令审计', '查看设备命令调用审计记录', 'OPERATION'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'device:audit');

-- 4. 授权给固定角色
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT role_code, 'device:audit'
FROM (SELECT 'super_admin' AS role_code UNION ALL
      SELECT 'device_maintenance' UNION ALL
      SELECT 'parking_manager') rp
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_code = rp.role_code AND srp.permission_code = 'device:audit');

-- 5. 授权给自定义角色中的 SUPER_ADMIN
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT cr.id, 'device:audit', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'device:audit');
