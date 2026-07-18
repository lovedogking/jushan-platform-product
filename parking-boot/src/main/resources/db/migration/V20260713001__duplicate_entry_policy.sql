-- =============================================================================
-- Flyway 迁移：重复入场处理策略（P003）
-- =============================================================================
-- 内容：
--   1. parking_lot 表新增 duplicate_entry_policy 字段
--   2. parking_record 表新增 entry_image_path 字段（UPDATE 策略时保存最新抓拍）
--   3. 创建异常重复入场记录表 duplicate_entry_log（供运营处置）
-- =============================================================================

-- 1. 停车场重复入场策略配置
--    REJECT: 拒绝并记录异常（默认，兼容现有行为）
--    UPDATE: 更新原记录入场时间和抓拍
--    EXCEPTION: 创建异常待处理记录，不阻止入场
ALTER TABLE parking_lot
    ADD COLUMN duplicate_entry_policy VARCHAR(20) NOT NULL DEFAULT 'REJECT'
        COMMENT '重复入场策略：REJECT-拒绝, UPDATE-更新原记录, EXCEPTION-创建异常记录';

-- 2. 停车记录新增入场抓拍路径（UPDATE 策略时保存最新图片）
ALTER TABLE parking_record
    ADD COLUMN entry_image_path VARCHAR(500) DEFAULT NULL COMMENT '入场抓拍图片路径（UPDATE 策略时更新）';

-- 3. 异常重复入场记录表（供运营人员查看和处置）
CREATE TABLE duplicate_entry_log (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL                COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL                COMMENT '停车场 ID',
    lane_id             BIGINT       DEFAULT NULL            COMMENT '车道 ID',
    device_id           BIGINT       DEFAULT NULL            COMMENT '设备 ID',
    standardized_plate  VARCHAR(32)  NOT NULL                COMMENT '标准化车牌号',
    existing_record_id  BIGINT       NOT NULL                COMMENT '关联的已有停车记录 ID',
    strategy            VARCHAR(20)  NOT NULL                COMMENT '执行策略：REJECT/UPDATE/EXCEPTION',
    action              VARCHAR(50)  NOT NULL                COMMENT '执行动作：REJECTED-已拒绝, UPDATED-已更新, EXCEPTION_CREATED-已创建异常',
    reason              VARCHAR(500) DEFAULT NULL            COMMENT '处置说明/原因',
    entry_event_id      BIGINT       DEFAULT NULL            COMMENT '关联的入场识别事件 ID',
    image_path          VARCHAR(500) DEFAULT NULL            COMMENT '本次抓拍图片路径',
    confidence          INT          DEFAULT NULL            COMMENT '识别置信度',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_duplicate_entry_lot_plate (parking_lot_id, standardized_plate),
    INDEX idx_duplicate_entry_record (existing_record_id),
    INDEX idx_duplicate_entry_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常重复入场记录表';
