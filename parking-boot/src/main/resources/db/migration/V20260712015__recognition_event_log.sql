-- =============================================================================
-- Flyway 迁移：识别事件日志表（T28｜平台内部标准识别事件与测试入口）
-- =============================================================================
-- 用途：记录平台内部识别事件（Mock、人工触发、未来 Device Access），
--       提供事件追溯和审计基础。
-- 幂等由 T29 在业务层通过 event_id 唯一约束实现。
-- =============================================================================

CREATE TABLE recognition_event_log
(
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    event_id         VARCHAR(64)  NOT NULL COMMENT '事件唯一 ID（UUID v4）',
    tenant_id        BIGINT       NOT NULL COMMENT '租户 ID（后端从设备推导）',
    parking_lot_id   BIGINT       NOT NULL COMMENT '停车场 ID（后端推导）',
    lane_id          BIGINT       DEFAULT NULL COMMENT '车道 ID',
    device_id        BIGINT       NOT NULL COMMENT '平台设备主键（相机设备）',
    plate_number     VARCHAR(32)  NOT NULL COMMENT '车牌号',
    direction        VARCHAR(20)  NOT NULL COMMENT '方向：ENTRY-入场, EXIT-出场',
    event_time       DATETIME     NOT NULL COMMENT '事件发生时间',
    confidence       INT          DEFAULT NULL COMMENT '识别置信度 0-100',
    image_path       VARCHAR(512) DEFAULT NULL COMMENT '全景图路径占位',
    plate_image_path VARCHAR(512) DEFAULT NULL COMMENT '车牌特写图路径占位',
    source           VARCHAR(32)  NOT NULL COMMENT '事件来源：MANUAL/MOCK/DEVICE_ACCESS',
    raw_data         TEXT         DEFAULT NULL COMMENT '原始数据摘要（调试用，生产可裁剪）',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_event_id (event_id) COMMENT '按事件 ID 查询',
    INDEX idx_tenant_parking (tenant_id, parking_lot_id) COMMENT '租户+停车场数据隔离',
    INDEX idx_parking_lot (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_device_id (device_id) COMMENT '按设备查询',
    INDEX idx_plate_number (plate_number) COMMENT '按车牌查询',
    INDEX idx_direction (direction) COMMENT '按方向查询',
    INDEX idx_event_time (event_time) COMMENT '按时间范围查询',
    INDEX idx_source (source) COMMENT '按来源查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='识别事件日志';
