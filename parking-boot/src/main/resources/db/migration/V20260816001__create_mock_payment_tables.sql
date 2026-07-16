-- =============================================================================
-- Phase 0 S0-3: 模拟支付系统 — 新建 mock_payment_config / mock_payment_record
-- =============================================================================
-- 设计目的：
-- 1. 彻底切断真实支付（4pyun.com），所有支付走内部模拟流程
-- 2. 按车场独立配置支付超时时间
-- 3. 完整记录模拟支付流水，支持运营端手动标记支付
-- =============================================================================

-- 模拟支付车场级配置
CREATE TABLE `mock_payment_config` (
    `id`               BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
    `parking_lot_id`   BIGINT       NOT NULL COMMENT '车场ID',
    `timeout_minutes`  INT          NOT NULL DEFAULT 15 COMMENT '支付超时时间（分钟）',
    `enabled`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用模拟支付',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`       DATETIME     DEFAULT NULL COMMENT '软删除时间',
    UNIQUE KEY `uk_lot` (`parking_lot_id`, `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟支付车场配置';

-- 模拟支付流水记录
CREATE TABLE `mock_payment_record` (
    `id`               BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
    `parking_lot_id`   BIGINT       NOT NULL COMMENT '车场ID',
    `order_id`         BIGINT       NOT NULL COMMENT '关联订单ID',
    `plate_number`     VARCHAR(20)  NOT NULL COMMENT '车牌号',
    `amount`           DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '支付金额（元）',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=待支付 2=已支付 3=已超时',
    `paid_at`          DATETIME     DEFAULT NULL COMMENT '支付时间',
    `paid_by`          VARCHAR(50)  DEFAULT NULL COMMENT '支付人（用户ID或系统标记）',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_lot_plate` (`parking_lot_id`, `plate_number`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟支付流水';

-- 插入默认配置（为现有车场创建初始模拟支付配置）
-- 注意：此 INSERT 依赖于 parking_lot 表已有数据，如果表空则不插入
INSERT IGNORE INTO mock_payment_config (id, tenant_id, parking_lot_id, timeout_minutes, enabled, created_at, updated_at)
SELECT
    FLOOR(RAND() * 9000000000000000000) + 1000000000000000000 as id,
    pl.tenant_id,
    pl.id AS parking_lot_id,
    15 AS timeout_minutes,
    1 AS enabled,
    NOW() AS created_at,
    NOW() AS updated_at
FROM parking_lot pl
WHERE pl.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM mock_payment_config mpc WHERE mpc.parking_lot_id = pl.id AND mpc.deleted_at IS NULL);
