-- Sprint 6: TASK-0601 在场车辆管理
-- 创建 parking_session 表，记录车辆在场状态

CREATE TABLE IF NOT EXISTS parking_session (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '在场记录ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    lane_id         BIGINT          NULL COMMENT '入场通道ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（大写）',
    plate_color     VARCHAR(10)     NULL COMMENT '车牌颜色',
    vehicle_type    VARCHAR(20)     NULL COMMENT '车辆类型判定结果',
    entry_time      DATETIME(3)     NOT NULL COMMENT '入场时间',
    entry_image     VARCHAR(255)    NULL COMMENT '入场抓拍图片URL',
    entry_operator  BIGINT          NULL COMMENT '入场操作人ID（人工放行时）',
    exit_time       DATETIME(3)     NULL COMMENT '出场时间',
    exit_lane_id    BIGINT          NULL COMMENT '出场通道ID',
    exit_image      VARCHAR(255)    NULL COMMENT '出场抓拍图片URL',
    exit_operator   BIGINT          NULL COMMENT '出场操作人ID',
    status          VARCHAR(20)     NOT NULL DEFAULT 'IN' COMMENT '状态：IN-在场, OUT-已出场, EXCEPTION-异常',
    fee_amount      DECIMAL(18,2)   NULL DEFAULT 0 COMMENT '应收费用（元）',
    paid_amount     DECIMAL(18,2)   NULL DEFAULT 0 COMMENT '已付费用（元）',
    order_id        BIGINT          NULL COMMENT '关联订单ID',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_plate (plate_number),
    INDEX idx_lot_status (parking_lot_id, status),
    INDEX idx_tenant (tenant_id),
    INDEX idx_entry_time (entry_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在场车辆记录表';
