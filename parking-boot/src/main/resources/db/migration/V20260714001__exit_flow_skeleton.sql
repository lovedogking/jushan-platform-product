-- =============================================================================
-- Flyway 迁移：出口识别与出场流程骨架（P004）
-- =============================================================================
-- 内容：
--   1. 创建 parking_order 表 —— 停车订单骨架，后续 P008 扩展状态机
--   2. 创建 exit_record 表 —— 出场记录，与 parking_record / recognition_event_log 关联
--   3. parking_record 表新增 exit_event_id 字段 —— 出场事件可追溯
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 停车订单表（P004 骨架，P008 将完善状态机、支付单关联、回调幂等）
-- -----------------------------------------------------------------------------
CREATE TABLE parking_order
(
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id        BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id   BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id BIGINT      NOT NULL COMMENT '关联停车记录 ID',
    plate_number     VARCHAR(32)  NOT NULL COMMENT '车牌号（标准化后）',
    amount_cents     INT          NOT NULL DEFAULT 0 COMMENT '订单金额（分），禁止负值',
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待支付, PAID-已支付, COMPLETED-已完成, CANCELLED-已取消',
    pay_time         DATETIME     DEFAULT NULL COMMENT '支付时间',
    exit_time        DATETIME     DEFAULT NULL COMMENT '出场时间（订单完成时）',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_parking_record_id (parking_record_id) COMMENT '按停车记录查询',
    INDEX idx_plate_number (plate_number) COMMENT '按车牌查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车订单表（P004 骨架）';

-- -----------------------------------------------------------------------------
-- 2. 出场记录表
-- -----------------------------------------------------------------------------
CREATE TABLE exit_record
(
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id   BIGINT       DEFAULT NULL COMMENT '关联停车记录 ID（NO_RECORD 时为空）',
    exit_event_id       BIGINT       DEFAULT NULL COMMENT '关联的出场识别事件 ID（recognition_event_log.id）',
    lane_id             BIGINT       DEFAULT NULL COMMENT '出场车道 ID',
    device_id           BIGINT       DEFAULT NULL COMMENT '出场相机设备 ID',
    standardized_plate  VARCHAR(32)  NOT NULL COMMENT '标准化车牌号',
    exit_time           DATETIME     NOT NULL COMMENT '出场时间',
    fee_cents           INT          NOT NULL DEFAULT 0 COMMENT '计算费用（分）',
    paid_cents          INT          NOT NULL DEFAULT 0 COMMENT '已支付金额（分）',
    release_decision    VARCHAR(32)  NOT NULL COMMENT '放行决策：PAID-已支付放行, ZERO_FEE-零元放行, UNAUTHORIZED-授权放行, PENDING_PAYMENT-待支付不放行, NO_RECORD-无在场记录, EXCEPTION-异常',
    order_id            BIGINT       DEFAULT NULL COMMENT '关联订单 ID',
    reason              VARCHAR(500) DEFAULT NULL COMMENT '决策原因/备注',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_exit_record_lot_plate (parking_lot_id, standardized_plate) COMMENT '按停车场+车牌查询',
    INDEX idx_exit_record_record (parking_record_id) COMMENT '按停车记录查询',
    INDEX idx_exit_record_event (exit_event_id) COMMENT '按出场事件查询',
    INDEX idx_exit_record_decision (release_decision) COMMENT '按放行决策查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出场记录表';

-- -----------------------------------------------------------------------------
-- 3. 停车记录新增出场事件关联
-- -----------------------------------------------------------------------------
ALTER TABLE parking_record
    ADD COLUMN exit_event_id BIGINT DEFAULT NULL COMMENT '关联的出场识别事件 ID（recognition_event_log.id）';
