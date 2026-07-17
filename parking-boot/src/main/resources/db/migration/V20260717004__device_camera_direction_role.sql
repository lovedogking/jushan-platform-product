-- =============================================================================
-- Flyway 迁移：设备/车道模型扩展 — 识别方向 + 主备关系 + 解除一车道一相机限制
-- =============================================================================
-- 0-3｜ADMIN-004 双向通道 + ADMIN-005 主备/识别方向
-- 1. 移除 uq_lane_device_type 唯一索引（允许同一车道多台同类型设备）
-- 2. 新增 recognition_direction（识别方向）和 camera_role（主备角色）字段
-- 3. 存量单向车道相机数据回填（入口=1, 出口=2，双向=NULL需人工补录）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 删除唯一索引 uq_lane_device_type（保留 lane_id 普通索引）
--    V20260711010 创建了 UNIQUE INDEX uq_lane_device_type(lane_id, device_type)
--    此索引阻止同一车道绑定多台 CAMERA（无法配置双向双相机或主备双相机）
-- -----------------------------------------------------------------------------
DROP INDEX uq_lane_device_type ON device;

-- -----------------------------------------------------------------------------
-- 2. 新增识别方向字段
--    1=入场(ENTRY), 2=出场(EXIT), NULL=未指定
--    双向通道绑定的相机必须指定识别方向
-- -----------------------------------------------------------------------------
ALTER TABLE device
    ADD COLUMN recognition_direction TINYINT DEFAULT NULL COMMENT '识别方向：1=入场, 2=出场, NULL=未指定（单向通道可推断，双向通道必填）' AFTER device_type,
    ADD COLUMN camera_role TINYINT DEFAULT NULL COMMENT '主备角色：1=主相机, 2=备相机, NULL=单相机模式或无主备' AFTER recognition_direction;

-- -----------------------------------------------------------------------------
-- 3. 索引
-- -----------------------------------------------------------------------------
CREATE INDEX idx_recognition_direction ON device (recognition_direction);
CREATE INDEX idx_camera_role ON device (camera_role);
CREATE INDEX idx_lane_direction_role ON device (lane_id, recognition_direction, camera_role);

-- -----------------------------------------------------------------------------
-- 4. 存量数据回填：单向车道相机 recognition_direction 按车道类型回填
--    入口车道(type=1) → 相机 recognition_direction=1
--    出口车道(type=2) → 相机 recognition_direction=2
--    双向车道(type=3) → 不自动回填（NULL，需人工补录）
-- -----------------------------------------------------------------------------
UPDATE device d
    INNER JOIN parking_lane l ON d.lane_id = l.id AND l.type IN (1, 2)
SET d.recognition_direction = l.type
WHERE d.device_type = 'CAMERA' AND d.recognition_direction IS NULL;

-- 回填日志：输出需要人工补录的双向车道相机清单
-- 以下查询可在执行迁移后手动运行以识别需要人工补录的设备：
-- SELECT d.id AS device_id, d.name, d.device_sn, l.id AS lane_id, l.name AS lane_name, l.type AS lane_type
-- FROM device d INNER JOIN parking_lane l ON d.lane_id = l.id AND l.type = 3
-- WHERE d.device_type = 'CAMERA' AND d.recognition_direction IS NULL;
