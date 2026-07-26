-- =============================================================================
-- Flyway 迁移：岗亭操作日志表
-- =============================================================================

CREATE TABLE gate_operation_log (
    id              BIGINT          AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '车场ID',
    lane_id         BIGINT          COMMENT '车道ID',
    lane_name       VARCHAR(128)    COMMENT '车道名称',
    operation_type  VARCHAR(32)     NOT NULL COMMENT '操作类型：OPEN_GATE-开闸, MANUAL_RELEASE-人工放行, CLOSE_GATE-关闸',
    reason          VARCHAR(256)    COMMENT '操作原因',
    plate_number    VARCHAR(32)     COMMENT '车牌号',
    direction       VARCHAR(16)     COMMENT '方向：ENTRY-入场, EXIT-出场',
    operator_id     BIGINT          COMMENT '操作员ID',
    operator_name   VARCHAR(64)     COMMENT '操作员姓名',
    fee_cents       INT             DEFAULT 0 COMMENT '实收金额（分）',
    entry_image     VARCHAR(512)    COMMENT '入场/抓拍照片URL',
    exit_image      VARCHAR(512)    COMMENT '出口照片URL',
    remark          VARCHAR(256)    COMMENT '备注',
    operation_time  DATETIME        NOT NULL COMMENT '操作时间',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_lane_id (lane_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_operation_time (operation_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗亭操作日志';
