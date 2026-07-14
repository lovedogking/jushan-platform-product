-- Sprint 5: TASK-0501 车辆进出策略配置
-- 创建 access_policy 表，支持键值对存储策略配置

CREATE TABLE IF NOT EXISTS access_policy (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '策略ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    policy_type     VARCHAR(30)     NOT NULL COMMENT '策略类型：ENTRY-入场策略, EXIT-出场策略, BLACKLIST-黑名单策略, VIP-VIP策略',
    policy_key      VARCHAR(50)     NOT NULL COMMENT '策略键，如 allow_entry、auto_release、need_confirm',
    policy_value    VARCHAR(500)    NOT NULL COMMENT '策略值，如 true、false、10（分钟）',
    description     VARCHAR(200)    NULL COMMENT '策略说明',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_lot_type_key (tenant_id, parking_lot_id, policy_type, policy_key, deleted_at),
    INDEX idx_lot (parking_lot_id),
    INDEX idx_type (policy_type),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆进出策略配置表';
