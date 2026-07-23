-- ============================================================
-- Flyway 迁移：车道控闸设备显式指定 + 设备型号级能力字段
-- ============================================================
-- 背景：双向车道（type=3）手动开闸时，按 laneId 任意取首台设备的解析逻辑
-- 无法区分"闸线到底接在哪台设备上"，存在开错闸风险。
-- 
-- 本迁移：
--   1. parking_lane 新增 gate_device_id —— 车道级显式指定唯一控闸设备
--   2. device_model 新增 capabilities —— 型号级默认能力（实例继承/可覆盖）
-- ============================================================

-- -----------------------------------------------------------------------------
-- 1. parking_lane 新增 gate_device_id
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lane
    ADD COLUMN gate_device_id BIGINT DEFAULT NULL
    COMMENT '唯一控闸设备ID，指向 device.id（GATE 设备或接线控闸的 CAMERA）；NULL=待补录，解析按回退规则+告警'
    AFTER gate_mode;

CREATE INDEX idx_lane_gate_device ON parking_lane (gate_device_id);

-- -----------------------------------------------------------------------------
-- 2. device_model 新增 capabilities —— 型号级默认能力
-- -----------------------------------------------------------------------------
ALTER TABLE device_model
    ADD COLUMN capabilities VARCHAR(500) DEFAULT NULL
    COMMENT '型号默认能力，逗号分隔（如 OPEN_GATE,CLOSE_GATE,CAPTURE,KEEP_OPEN）；设备实例默认继承、可覆盖';
