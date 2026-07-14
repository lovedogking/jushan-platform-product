-- Sprint 5: TASK-0502 车位管控策略
-- 创建 parking_space_policy 表，支持余位计算与管控

CREATE TABLE IF NOT EXISTS parking_space_policy (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '策略ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    zone_id         BIGINT          NULL COMMENT '区域ID（NULL表示全场策略）',
    total_spaces    INT             NOT NULL DEFAULT 0 COMMENT '总车位数',
    fixed_spaces    INT             NOT NULL DEFAULT 0 COMMENT '固定车位数（月租/储值等）',
    temp_spaces     INT             NOT NULL DEFAULT 0 COMMENT '临时车位数',
    reserved_spaces INT             NOT NULL DEFAULT 0 COMMENT '预留车位数',
    warning_threshold INT           NOT NULL DEFAULT 10 COMMENT '余位预警阈值',
    full_action     VARCHAR(20)     NOT NULL DEFAULT 'WARN' COMMENT '满位动作：WARN-仅预警, BLOCK-禁止入场, ALLOW_VIP-仅允许VIP/月租',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_lot_zone (tenant_id, parking_lot_id, zone_id, deleted_at),
    INDEX idx_lot (parking_lot_id),
    INDEX idx_zone (zone_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车位管控策略表';
