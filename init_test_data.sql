-- 清理并重新初始化联调数据
DELETE FROM parking_lane;
DELETE FROM parking_zone;
DELETE FROM parking_lot;
DELETE FROM sys_company;
DELETE FROM tenant;

INSERT INTO tenant (id, name, contact_person, contact_phone, status, created_at, updated_at)
VALUES (1, '测试租户', '测试联系人', '13800138000', 'ENABLED', NOW(), NOW());

INSERT INTO sys_company (id, tenant_id, parent_id, name, level, created_at, updated_at)
VALUES (1, 1, 0, '测试公司', 1, NOW(), NOW());

INSERT INTO parking_lot (id, tenant_id, company_id, name, total_spaces, status, created_at, updated_at)
VALUES (1, 1, 1, '测试停车场', 100, 'ENABLED', NOW(), NOW());

INSERT INTO parking_zone (id, tenant_id, lot_id, name, tag, level, total_spaces, fixed_spaces, temp_spaces, status, created_at, updated_at)
VALUES (1, 1, 1, 'A区', 'NORMAL', 1, 50, 0, 50, 1, NOW(), NOW());

INSERT INTO parking_lane (id, tenant_id, lot_id, zone_id, lane_no, name, type, status, created_at, updated_at)
VALUES 
(1, 1, 1, 1, 'L001', '入口车道1', 1, 1, NOW(), NOW()),
(2, 1, 1, 1, 'L002', '出口车道1', 2, 1, NOW(), NOW());

UPDATE sys_admin_account SET tenant_id = 1 WHERE username = 'super_admin';
