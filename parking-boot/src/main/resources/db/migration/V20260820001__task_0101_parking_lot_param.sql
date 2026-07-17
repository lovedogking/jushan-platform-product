-- =============================================================================
-- 任务包 1-1：车场级参数层 —— sys_config 参数分层 + 7 项车场级参数 + mock_payment_config 超时迁移
-- =============================================================================
-- 需求依据：V1.1 3.1.2（ADMIN-002 车场级参数表）、6.1 数据字典（参数层级 1=全局/2=车场级）
--
-- 模型取舍（方案 A）：扩展 sys_config 而非新建表，改动最小、复用实体/元数据列。
--   - 采用 parking_lot_id=0 作为"全局"哨兵（而非 NULL）：MySQL 唯一索引将多个 NULL 视为互异，
--     无法约束全局行唯一，故用 0 哨兵使唯一键 (config_key, parking_lot_id) 正确生效。
--   - param_level：1=全局，2=车场级。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. sys_config 结构扩展（现有 8 行经默认值自动成为全局行 parking_lot_id=0 / param_level=1）
-- ---------------------------------------------------------------------------
ALTER TABLE sys_config
    ADD COLUMN parking_lot_id BIGINT NOT NULL DEFAULT 0 COMMENT '归属车场ID，0=全局' AFTER config_value,
    ADD COLUMN param_level TINYINT NOT NULL DEFAULT 1 COMMENT '参数层级：1=全局，2=车场级' AFTER parking_lot_id;

-- ---------------------------------------------------------------------------
-- 2. 唯一键调整：(config_key) -> (config_key, parking_lot_id)
-- ---------------------------------------------------------------------------
ALTER TABLE sys_config DROP INDEX uk_config_key;
ALTER TABLE sys_config ADD UNIQUE KEY uk_key_lot (config_key, parking_lot_id) COMMENT '同一参数在同一层级唯一';

-- ---------------------------------------------------------------------------
-- 3. 注入 7 项车场级参数的"全局默认行"（可被车场覆盖）
--    - 已存在的 3 项（mock_payment.timeout_minutes / monthly_pass.expiry_reminder_days /
--      monthly_pass.count_in_available_space）来自 V20260818002，此处仅对齐分组/类型。
--    - 新增 4 项（未支付出场 / 欠费再出场 / 识别失败 / 支付出场窗口期）。
-- ---------------------------------------------------------------------------

-- 对齐已有 3 项的分组/类型（幂等）
UPDATE sys_config SET group_name = '岗亭设置', value_type = 'INT'
    WHERE config_key = 'mock_payment.timeout_minutes' AND parking_lot_id = 0;
UPDATE sys_config SET group_name = '计费设置', value_type = 'INT'
    WHERE config_key = 'monthly_pass.expiry_reminder_days' AND parking_lot_id = 0;
UPDATE sys_config SET group_name = '计费设置', value_type = 'BOOLEAN'
    WHERE config_key = 'monthly_pass.count_in_available_space' AND parking_lot_id = 0;

-- 新增 4 项全局默认行
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('pay.exit_window_minutes', '15', 0, 1, '支付后出场窗口期（分钟）', '出场设置', 'INT', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('exit.unpaid_strategy', 'BLOCK', 0, 1, '未支付出场策略：BLOCK=拦截, ALLOW_ARREARS=允许欠费放行', '出场设置', 'ENUM', '["BLOCK","ALLOW_ARREARS"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('arrears.reexit_strategy', 'MUST_PAY', 0, 1, '欠费车辆再次出场策略：MUST_PAY=必须补缴, REMIND_ONLY=仅提醒', '出场设置', 'ENUM', '["MUST_PAY","REMIND_ONLY"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('recognition.fail_strategy', 'MANUAL', 0, 1, '识别失败处理策略：MANUAL=人工处理, AUTO_RELEASE=自动放行', '出场设置', 'ENUM', '["MANUAL","AUTO_RELEASE"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

-- ---------------------------------------------------------------------------
-- 4. mock_payment_config.timeout_minutes 迁移到车场级参数体系
--    仅迁移"非默认(≠15)"的车场覆盖，等于默认的车场继续继承全局 15（生效值不变、避免冗余覆盖行）。
-- ---------------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, created_at, updated_at)
SELECT 'mock_payment.timeout_minutes', CAST(mpc.timeout_minutes AS CHAR), mpc.parking_lot_id, 2,
       '模拟支付超时（分钟）', '岗亭设置', 'INT', NOW(), NOW()
FROM mock_payment_config mpc
WHERE mpc.deleted_at IS NULL
  AND mpc.timeout_minutes IS NOT NULL
  AND mpc.timeout_minutes <> 15
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), param_level = VALUES(param_level), updated_at = NOW();

-- ---------------------------------------------------------------------------
-- 5. 标记 mock_payment_config.timeout_minutes 列为废弃（读取源已切换至 sys_config 车场级参数）
-- ---------------------------------------------------------------------------
ALTER TABLE mock_payment_config
    MODIFY COLUMN timeout_minutes INT NOT NULL DEFAULT 15
    COMMENT '[已废弃-任务包1-1] 超时改由 sys_config(mock_payment.timeout_minutes) 车场级参数管理，本列仅兼容同步';
