-- Sprint 4: TASK-0403 储值车账户与流水
-- 创建 sys_vehicle_wallet 和 sys_vehicle_wallet_log 表

CREATE TABLE IF NOT EXISTS sys_vehicle_wallet (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '钱包ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID',
    balance         DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '当前余额（元）',
    total_recharge  DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '累计充值金额（元）',
    total_consume   DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '累计消费金额（元）',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_vehicle (vehicle_id, deleted_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值车钱包表';

CREATE TABLE IF NOT EXISTS sys_vehicle_wallet_log (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '流水ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID',
    wallet_id       BIGINT          NOT NULL COMMENT '钱包ID',
    log_type        VARCHAR(20)     NOT NULL COMMENT '流水类型：RECHARGE-充值, CONSUME-消费, REFUND-退款, ADJUST-调账',
    amount          DECIMAL(18,2)   NOT NULL COMMENT '变动金额（元，正数增加，负数减少）',
    balance_before  DECIMAL(18,2)   NOT NULL COMMENT '变动前余额（元）',
    balance_after   DECIMAL(18,2)   NOT NULL COMMENT '变动后余额（元）',
    order_id        BIGINT          NULL COMMENT '关联订单ID',
    operator_id     BIGINT          NULL COMMENT '操作人ID',
    operator_name   VARCHAR(30)     NULL COMMENT '操作人姓名',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_wallet (wallet_id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_type (log_type),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值车钱包流水表';
