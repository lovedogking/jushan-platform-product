-- =============================================================================
-- 车辆续费记录表（Phase 1 A1 — 月卡独立管理）
-- =============================================================================
-- 记录每次月卡续费操作的历史，包括续费月数、金额、续费前后有效期、操作人。
-- 与 sys_vehicle 的 valid_end_date 联动：每次续费成功后写入一条记录。
-- =============================================================================

CREATE TABLE IF NOT EXISTS vehicle_renewal_log (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '雪花ID',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '车场ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号',
    order_id        BIGINT          DEFAULT NULL COMMENT '关联续费订单ID',
    renewal_months  INT             NOT NULL COMMENT '续费月数',
    amount_cents    INT             NOT NULL COMMENT '金额（分）',
    old_valid_end   DATE            DEFAULT NULL COMMENT '续费前有效期',
    new_valid_end   DATE            NOT NULL COMMENT '续费后有效期',
    operator_id     BIGINT          NOT NULL COMMENT '操作人ID',
    remark          VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '更新时间',
    deleted_at      DATETIME        DEFAULT NULL COMMENT '删除时间（软删除）',

    INDEX idx_vehicle   (vehicle_id),
    INDEX idx_plate     (plate_number),
    INDEX idx_lot       (parking_lot_id),
    INDEX idx_operator  (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆续费记录';
