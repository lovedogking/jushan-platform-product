-- =============================================================================
-- 任务包 3-1：月卡独立实体 — 建表 + 存量迁移 + vehicle_renewal_log 扩展
-- =============================================================================

-- Step 1: 创建 monthly_pass 表
CREATE TABLE monthly_pass (
    id               BIGINT          NOT NULL PRIMARY KEY COMMENT '主键（Snowflake）',
    tenant_id        BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id   BIGINT          NOT NULL COMMENT '车场ID',
    plate_number     VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    plate_color      VARCHAR(10)     DEFAULT NULL COMMENT '车牌颜色',
    vehicle_type     VARCHAR(20)     DEFAULT NULL COMMENT '车辆类型（小型车/大型车等）',
    valid_start_date DATE            NOT NULL COMMENT '有效期起',
    valid_end_date   DATE            NOT NULL COMMENT '有效期止',
    amount_cents     INT             NOT NULL DEFAULT 0 COMMENT '费用（分）',
    paid_amount_cents INT            NOT NULL DEFAULT 0 COMMENT '实收金额（分）',
    pay_method       VARCHAR(20)     NOT NULL COMMENT '缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER',
    pass_status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '月卡状态：ACTIVE-生效中 / EXPIRED-已过期 / CANCELLED-已注销',
    applicant_id     BIGINT          DEFAULT NULL COMMENT '申请人ID（小程序用户ID）',
    source           VARCHAR(20)     NOT NULL COMMENT '来源：ADMIN-运营端 / MINIAPP-小程序端',
    owner_name       VARCHAR(30)     DEFAULT NULL COMMENT '车主姓名',
    owner_phone      VARCHAR(20)     DEFAULT NULL COMMENT '车主电话',
    remark           VARCHAR(200)    DEFAULT NULL COMMENT '备注',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at       DATETIME(3)     DEFAULT NULL COMMENT '软删除时间',

    INDEX idx_tenant     (tenant_id),
    INDEX idx_plate      (plate_number),
    INDEX idx_lot        (parking_lot_id),
    INDEX idx_status     (pass_status),
    INDEX idx_lot_plate  (parking_lot_id, plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月卡独立实体表';

-- Step 2: 存量数据迁移（sys_vehicle.vehicleType=MONTHLY → monthly_pass）
INSERT INTO monthly_pass (
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    amount_cents, paid_amount_cents, pay_method,
    pass_status, applicant_id, source,
    owner_name, owner_phone, remark,
    created_at, updated_at
)
SELECT
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    0, 0, 'OTHER',
    CASE
        WHEN status = 'ACTIVE' AND valid_end_date >= CURDATE() THEN 'ACTIVE'
        ELSE 'EXPIRED'
    END,
    NULL, 'ADMIN',
    owner_name, owner_phone, remark,
    COALESCE(created_at, NOW()), NOW()
FROM sys_vehicle
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 3: 改写旧 MONTHLY 记录（旧判断口径下线）
UPDATE sys_vehicle
SET vehicle_type = 'FREE',
    status = 'EXPIRED',
    updated_at = NOW()
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 4: vehicle_renewal_log 扩展
ALTER TABLE vehicle_renewal_log
    ADD COLUMN monthly_pass_id BIGINT DEFAULT NULL COMMENT '月卡ID（新体系）' AFTER vehicle_id;
