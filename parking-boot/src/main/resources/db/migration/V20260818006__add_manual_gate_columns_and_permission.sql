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
INSERT INTO sys_permission (code, name, description)
SELECT 'device:audit', '设备命令审计', '查看设备命令调用审计记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'device:audit');

-- 4. 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(MD5(CONCAT(cr.id, ':device:audit')), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'device:audit', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'device:audit');
