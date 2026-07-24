-- ============================================
-- 停车 SaaS 联调测试数据初始化脚本（已对齐实际表结构）
-- 执行方式：mysql -u root -p jushan_platform < test-data.sql
-- 或直接粘贴到 MySQL 客户端执行
-- ============================================

-- 0. 公司（若已存在则跳过，保留 parkflow 生成的默认公司）
INSERT INTO company (id, tenant_id, parent_id, name, level, status, sort_order, path, created_at, updated_at)
VALUES (1, 1, NULL, '测试公司', 1, 1, 0, '/1/', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 1. 租户
INSERT INTO sys_tenant (id, name, code, status, created_at, updated_at)
VALUES (1, '测试租户', 'TEST', 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 2. 车场（注意：status 是 VARCHAR，不是 INT）
INSERT INTO parking_lot (id, tenant_id, company_id, name, address, total_spaces, current_vehicles, status, region_type, business_hours, version, created_at, updated_at)
VALUES (1, 1, 1, '测试车场-A', '测试地址', 100, 0, 'ENABLED', 1, '00:00-24:00', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 3. 区域（注意：lot_id 不是 parking_lot_id）
INSERT INTO parking_zone (id, tenant_id, lot_id, name, tag, level, total_spaces, fixed_spaces, temp_spaces, status, version, created_at, updated_at)
VALUES (1, 1, 1, '地面停车场', 'NORMAL', 1, 100, 0, 100, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 4. 通道（注意：lot_id, lane_no 必填, type 是 TINYINT 1=入口 2=出口, status 是 TINYINT）
INSERT INTO parking_lane (id, tenant_id, lot_id, zone_id, lane_no, name, type, status, tide_mode, camera_mode, version, created_at, updated_at)
VALUES (1, 1, 1, 1, 'A1', '入口-1', 1, 1, 0, 1, 0, NOW(), NOW()),
       (2, 1, 1, 1, 'A2', '出口-1', 2, 1, 0, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE lane_no = VALUES(lane_no);

-- 5. 设备（必须填充 vendor_id, model_id, code, capabilities, description）
-- 臻识 vendor_id=1（Flyway 种子数据已插入），C5 model_id=1
INSERT INTO device (id, tenant_id, parking_lot_id, lane_id, vendor_id, model_id, name, code, device_sn, device_type, status, capabilities, description, created_at, updated_at)
VALUES
(1, 1, 1, 1, 1, 1, '入口相机', 'CAM-1', '917e2298-8ddf3e46', 'CAMERA', 'ENABLED', 'RECOGNIZE,CAPTURE', '臻识 C5 入口相机', NOW(), NOW()),
(2, 1, 1, 1, 1, 1, '入口道闸', 'GATE-1', 'GATE-001', 'GATE', 'ENABLED', 'GATE_OPEN,GATE_CLOSE', '入口道闸', NOW(), NOW()),
(3, 1, 1, 2, 1, 1, '出口相机', 'CAM-2', '917e2298-8ddf3e47', 'CAMERA', 'ENABLED', 'RECOGNIZE,CAPTURE', '臻识 C5 出口相机', NOW(), NOW()),
(4, 1, 1, 2, 1, 1, '出口道闸', 'GATE-2', 'GATE-002', 'GATE', 'ENABLED', 'GATE_OPEN,GATE_CLOSE', '出口道闸', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 6. 月卡车辆（status 是 VARCHAR: ACTIVE/EXPIRED/DISABLED）
INSERT INTO sys_vehicle (id, tenant_id, parking_lot_id, plate_number, vehicle_type, valid_start_date, valid_end_date, status, created_at, updated_at)
VALUES (1, 1, 1, '京A12345', 'MONTHLY', CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 MONTH), 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 7. 固定车位车辆
INSERT INTO sys_vehicle (id, tenant_id, parking_lot_id, plate_number, vehicle_type, status, created_at, updated_at)
VALUES (2, 1, 1, '京B67890', 'FREE', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 8. 固定车位绑定（status 是 TINYINT: 1=生效中, zone_id 可为 NULL）
INSERT INTO fixed_space_binding (id, tenant_id, parking_lot_id, zone_id, space_no, vehicle_id, valid_start, valid_end, status, created_at, updated_at)
VALUES (1, 1, 1, 1, 'A-001', 2, CURDATE(), DATE_ADD(CURDATE(), INTERVAL 1 MONTH), 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 9. 黑名单（policy_type='BLACKLIST', policy_key=车牌, policy_value=JSON, sort_order 必填, status 是 VARCHAR）
INSERT INTO access_policy (id, tenant_id, parking_lot_id, policy_type, policy_key, policy_value, description, sort_order, status, created_at, updated_at)
VALUES (1, 1, 1, 'BLACKLIST', '京J00000', '{"triggerMode":1}', '测试黑名单-禁止入场', 0, 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 10. 系统参数（列顺序: id, config_key, config_value, description, group_name, value_type, options）
INSERT INTO sys_config (id, config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES
(1, 'available_space.refresh_interval_minutes', '5', '余位刷新间隔（分钟）', '基础设置', 'INT', NOW(), NOW()),
(2, 'mock_payment.timeout_minutes', '15', '模拟支付超时（分钟）', '岗亭设置', 'INT', NOW(), NOW()),
(3, 'blacklist.trigger_mode', '1', '1=禁止入场,2=允许但告警,3=按类型区分', '告警设置', 'ENUM', NOW(), NOW()),
(4, 'monthly_pass.count_in_available_space', 'false', '月卡是否计入余位', '计费设置', 'BOOLEAN', NOW(), NOW()),
(5, 'vehicle.bind_limit_per_user', '5', '用户车辆绑定上限', '基础设置', 'INT', NOW(), NOW()),
(6, 'monthly_pass.expiry_reminder_days', '7', '月卡到期提醒天数', '计费设置', 'INT', NOW(), NOW()),
(7, 'remote_gate.alert_auto_dismiss_seconds', '10', '远程开闸弹窗自动消失秒数', '告警设置', 'INT', NOW(), NOW()),
(8, 'booth.fee_reduction_threshold_cents', '50000', '费用减免超X元需二次确认（分）', '岗亭设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 11. 超级管理员（password_hash 不是 password, roles 是 JSON 数组, status 是 VARCHAR, credential_status 必填）
-- 密码: 123456，BCrypt 哈希值
INSERT INTO sys_user (id, tenant_id, username, password_hash, display_name, roles, status, credential_status, created_at, updated_at)
VALUES (1, 1, 'admin', '$2b$10$hzwrEyjiygSbm3GNvXb/lOUOQp1krznHj1Q/aqEW4f5d.UWxnvOKq', '超级管理员', '["super_admin"]', 'ENABLED', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 12. 岗亭管理员
INSERT INTO sys_user (id, tenant_id, username, password_hash, display_name, roles, status, credential_status, created_at, updated_at)
VALUES (2, 1, 'booth001', '$2b$10$hzwrEyjiygSbm3GNvXb/lOUOQp1krznHj1Q/aqEW4f5d.UWxnvOKq', '岗亭管理员-1', '["booth_admin"]', 'ENABLED', 'ACTIVE', NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 13. 岗亭管理员-车场关联（employee_parking_lot 没有 updated_at 列）
INSERT INTO employee_parking_lot (id, tenant_id, employee_id, parking_lot_id, created_at)
VALUES (1, 1, 2, 1, NOW())
ON DUPLICATE KEY UPDATE employee_id = VALUES(employee_id);

-- 14. 模拟支付配置
INSERT INTO mock_payment_config (id, tenant_id, parking_lot_id, timeout_minutes, enabled, created_at, updated_at)
VALUES (1, 1, 1, 15, 1, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
