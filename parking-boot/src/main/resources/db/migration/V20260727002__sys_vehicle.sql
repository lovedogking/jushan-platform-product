-- Sprint 4: TASK-0402 车辆主表与多类型登记
-- 创建 sys_vehicle 和 sys_vehicle_multi_plate 表

CREATE TABLE IF NOT EXISTS sys_vehicle (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '车辆ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    plate_color     VARCHAR(10)     NULL COMMENT '车牌颜色',
    vehicle_type    VARCHAR(20)     NOT NULL COMMENT '车辆类型：FREE-免费车, MONTHLY-月租车, PREPAID-储值车, VIP-贵宾车, SUPER-超级车牌, BLACKLIST-黑名单',
    owner_name      VARCHAR(30)     NULL COMMENT '车主姓名',
    owner_phone     VARCHAR(20)     NULL COMMENT '车主电话',
    department_id   BIGINT          NULL COMMENT '所属部门ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '所属停车场ID',
    valid_start_date DATE           NULL COMMENT '月卡/固定车有效期开始',
    valid_end_date  DATE            NULL COMMENT '月卡/固定车有效期结束',
    prepaid_balance DECIMAL(18,2)   NULL DEFAULT 0 COMMENT '储值车余额（元）',
    fee_rule_id     BIGINT          NULL COMMENT '月卡/固定车收费标准ID',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, EXPIRED-已过期, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_tenant_plate (tenant_id, plate_number, deleted_at),
    INDEX idx_tenant_type (tenant_id, vehicle_type),
    INDEX idx_department (department_id),
    INDEX idx_parking_lot (parking_lot_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆主表';

CREATE TABLE IF NOT EXISTS sys_vehicle_multi_plate (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '绑定ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '主车辆ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '绑定车牌号（大写）',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-有效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_vehicle (vehicle_id),
    INDEX idx_plate (plate_number),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一位多车绑定表';
