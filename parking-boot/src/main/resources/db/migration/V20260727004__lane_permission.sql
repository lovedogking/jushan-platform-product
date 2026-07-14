-- Sprint 4: TASK-0404 通道权限管理
-- 创建 lane_permission 表，支持车辆/部门级别的通道通行权限配置

CREATE TABLE IF NOT EXISTS lane_permission (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '权限ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    lane_id         BIGINT          NOT NULL COMMENT '通道ID（逻辑外键：parking_lane.id）',
    target_type     VARCHAR(20)     NOT NULL COMMENT '权限目标类型：VEHICLE-车辆, DEPARTMENT-部门',
    target_id       BIGINT          NOT NULL COMMENT '权限目标ID（车辆ID或部门ID）',
    direction       VARCHAR(20)     NOT NULL DEFAULT 'BOTH' COMMENT '允许方向：ENTRY-仅入口, EXIT-仅出口, BOTH-双向',
    valid_start     DATETIME(3)     NULL COMMENT '有效期开始（NULL表示永久）',
    valid_end       DATETIME(3)     NULL COMMENT '有效期结束（NULL表示永久）',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_lane_target (tenant_id, lane_id, target_type, target_id, deleted_at),
    INDEX idx_lane (lane_id),
    INDEX idx_target (target_type, target_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通道权限配置表';
