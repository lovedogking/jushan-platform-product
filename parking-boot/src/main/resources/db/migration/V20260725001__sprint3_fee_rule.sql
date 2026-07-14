-- =============================================================================
-- Flyway 迁移：Sprint 3 - 收费规则引擎（fee_rule / fee_rule_segment）
-- =============================================================================
-- 对齐 PRD V1.0 和 section_03_ddl.md 规范，新建 fee_rule 表和 fee_rule_segment 表。
-- 所有业务表包含 tenant_id 和 deleted_at，主键使用 BIGINT（Snowflake）。
-- 金额字段使用 DECIMAL(18,2)，禁止 FLOAT/DOUBLE。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 新建 fee_rule（收费规则表）
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fee_rule (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '规则ID（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '适用区域ID（逻辑外键：parking_zone.id，NULL表示车场通用）',
    name                VARCHAR(128)    NOT NULL COMMENT '规则名称',
    billing_mode        TINYINT         NOT NULL DEFAULT 1 COMMENT '计费模式：1按时 2按次 3阶梯 4分时段',
    free_minutes        INT             NOT NULL DEFAULT 0 COMMENT '免费时长（分钟）',
    unit_minutes        INT             NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
    first_period_price  DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '首时段价格',
    subsequent_price    DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '后续单价',
    daily_cap           DECIMAL(18,2)       DEFAULT NULL COMMENT '24小时封顶金额',
    night_cap           DECIMAL(18,2)       DEFAULT NULL COMMENT '夜间封顶金额',
    priority            INT             NOT NULL DEFAULT 0 COMMENT '优先级，数字越大优先级越高',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    effective_start     DATETIME(3)         DEFAULT NULL COMMENT '生效开始时间',
    effective_end       DATETIME(3)         DEFAULT NULL COMMENT '生效结束时间',
    holiday_rules       JSON                DEFAULT NULL COMMENT '节假日特殊规则JSON',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_zone_id (zone_id),
    KEY idx_billing_mode (billing_mode),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则表';

-- ---------------------------------------------------------------------------
-- 2. 新建 fee_rule_segment（收费规则时段表）【辅助表】
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fee_rule_segment (
    id              BIGINT UNSIGNED NOT NULL COMMENT '时段ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    fee_rule_id     BIGINT UNSIGNED NOT NULL COMMENT '收费规则ID（逻辑外键：fee_rule.id）',
    segment_name    VARCHAR(64)     NOT NULL COMMENT '时段名称，如白天/夜间',
    start_time      TIME            NOT NULL COMMENT '时段开始时间',
    end_time        TIME            NOT NULL COMMENT '时段结束时间',
    unit_minutes    INT             NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
    unit_price      DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '时段单价',
    cap_amount      DECIMAL(18,2)       DEFAULT NULL COMMENT '时段封顶金额',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_fee_rule_id (fee_rule_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则时段表';

-- ---------------------------------------------------------------------------
-- 3. 保留旧 billing_rule / billing_rule_version 表（不删除，不迁移）
-- ---------------------------------------------------------------------------
-- 旧表继续保留供现有 BillingEngine 使用，本期不强制迁移数据。
-- 下期 Sprint 完成适配层后统一迁移并删除旧表。
