-- =============================================================================
-- A3: 系统参数管理 — 扩展 sys_config 表 + 默认参数注入
-- =============================================================================
-- 本迁移：
-- 1. 新增分组（group_name）、值类型（value_type）、枚举选项（options）字段
-- 2. 注入 Phase 1 A3 定义的全部 8 个系统参数的默认记录
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 扩展 sys_config 结构
-- ---------------------------------------------------------------------------
ALTER TABLE sys_config
    ADD COLUMN group_name VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '参数分组' AFTER description,
    ADD COLUMN value_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING / INT / BOOLEAN / ENUM' AFTER group_name,
    ADD COLUMN options TEXT COMMENT 'ENUM 类型的可选值，JSON 数组，如 ["1","2","3"]' AFTER value_type;

-- ---------------------------------------------------------------------------
-- 2. 注入默认系统参数
-- ---------------------------------------------------------------------------

-- ===== 基础设置 =====
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('available_space.refresh_interval_minutes', '5', '余位刷新间隔（分钟）', '基础设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('vehicle.bind_limit_per_user', '5', '用户车辆绑定数量上限', '基础设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

-- ===== 计费设置 =====
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('monthly_pass.count_in_available_space', 'false', '月卡是否计入余位', '计费设置', 'BOOLEAN', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('monthly_pass.expiry_reminder_days', '7', '月卡到期提醒天数', '计费设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('booth.fee_reduction_threshold_cents', '50000', '费用减免超 X 元需二次确认（单位：分）', '计费设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

-- ===== 告警设置 =====
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, options, created_at, updated_at)
VALUES ('blacklist.trigger_mode', '1', '黑名单触发模式：1=禁止入场, 2=允许但告警, 3=按类型区分', '告警设置', 'ENUM', '["1","2","3"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('remote_gate.alert_auto_dismiss_seconds', '10', '远程开闸弹窗自动消失秒数', '告警设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

-- ===== 岗亭设置 =====
-- mock_payment.timeout_minutes 已在 mock_payment_config 表中按车场配置，此处仅做全局兜底
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('mock_payment.timeout_minutes', '15', '模拟支付超时（分钟），按车场配置时优先使用 mock_payment_config 表', '岗亭设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);
