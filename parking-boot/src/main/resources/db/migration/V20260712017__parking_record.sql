-- =============================================================================
-- Flyway 迁移：停车记录表（T30｜入场通行与停车记录主链路）
-- =============================================================================
-- 内容：
--   1. 创建 parking_record 表 — 记录车辆入场、在场状态
--   2. 功能性唯一索引 — 同车同停车场仅一条有效在场记录（数据库级保障）
--   3. 辅助索引 — 按状态/车牌/事件查询
-- =============================================================================

-- 1. 停车记录表
CREATE TABLE parking_record (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL                COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL                COMMENT '停车场 ID',
    lane_id             BIGINT       DEFAULT NULL            COMMENT '入场车道 ID',
    device_id           BIGINT       DEFAULT NULL            COMMENT '入场相机设备 ID',
    standardized_plate  VARCHAR(32)  NOT NULL                COMMENT '标准化车牌号',
    entry_event_id      BIGINT       DEFAULT NULL            COMMENT '关联的入场识别事件 ID（recognition_event_log.id）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PARKING' COMMENT '状态：PARKING-在场, COMPLETED-已完成, CANCELLED-已作废',
    entry_time          DATETIME     NOT NULL                COMMENT '入场时间',
    exit_time           DATETIME     DEFAULT NULL            COMMENT '出场时间',
    fee_rule_version    VARCHAR(64)  DEFAULT NULL            COMMENT '收费规则版本（占位，T34 实现）',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='停车记录表';

-- 2. 功能性唯一索引：同车同停车场仅允许一条 PARKING 记录
--    利用 MySQL 8 功能索引，仅对 status='PARKING' 的行生效；
--    CASE WHEN 对非 PARKING 行返回 NULL，MySQL 中多个 NULL 不冲突。
CREATE UNIQUE INDEX uk_active_parking
    ON parking_record(parking_lot_id, standardized_plate,
        (CASE WHEN status = 'PARKING' THEN 1 ELSE NULL END));

-- 3. 辅助索引
CREATE INDEX idx_parking_record_lot_status ON parking_record(parking_lot_id, status);
CREATE INDEX idx_parking_record_plate      ON parking_record(standardized_plate);
CREATE INDEX idx_parking_record_entry_event ON parking_record(entry_event_id);
