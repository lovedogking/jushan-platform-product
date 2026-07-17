-- Phase 1 A2：固定车位绑定关系表
-- 车位号 ↔ 车辆 专属绑定，非绑定车辆占用按临停计费
-- 用于固定车位车辆入场自动放行、出场不生成临停订单

CREATE TABLE fixed_space_binding (
    id BIGINT PRIMARY KEY COMMENT '雪花主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    parking_lot_id BIGINT NOT NULL COMMENT '车场ID',
    zone_id BIGINT DEFAULT NULL COMMENT '区域ID（可为空，不限制到区域）',
    space_no VARCHAR(20) NOT NULL COMMENT '具体车位号',
    vehicle_id BIGINT NOT NULL COMMENT '车辆ID（关联 sys_vehicle.id）',
    valid_start DATE NOT NULL COMMENT '有效期起',
    valid_end DATE NOT NULL COMMENT '有效期止',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=生效中, 2=已过期, 3=已注销',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted_at DATETIME(3) DEFAULT NULL COMMENT '删除时间',
    UNIQUE KEY uk_space (parking_lot_id, zone_id, space_no, deleted_at),
    UNIQUE KEY uk_vehicle (parking_lot_id, vehicle_id, deleted_at),
    INDEX idx_tenant (tenant_id),
    INDEX idx_plate_via_vehicle (vehicle_id),
    INDEX idx_lot (parking_lot_id),
    INDEX idx_expiring (parking_lot_id, status, valid_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固定车位绑定关系表';
