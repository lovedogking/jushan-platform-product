-- =============================================================================
-- Flyway 迁移：收费规则主表
-- =============================================================================
-- T34｜收费规则与版本 CRUD
-- 创建收费规则主表，支持多套规则、规则启停和基本信息管理。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 收费规则主表
-- -----------------------------------------------------------------------------
CREATE TABLE billing_rule
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id      BIGINT       NOT NULL COMMENT '所属租户 ID',
    parking_lot_id BIGINT       NOT NULL COMMENT '所属停车场 ID',
    name           VARCHAR(100) NOT NULL COMMENT '规则名称',
    description    VARCHAR(500) DEFAULT '' COMMENT '规则描述',
    rule_type      VARCHAR(20)  NOT NULL DEFAULT 'HOURLY' COMMENT '规则类型：HOURLY-按时长, FIXED-固定金额, NO_FEE-免费',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    is_default     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为默认规则：1-是, 0-否',
    created_by     BIGINT       NOT NULL COMMENT '创建人 ID',
    updated_by     BIGINT       COMMENT '修改人 ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按状态查询',
    UNIQUE KEY uk_parking_lot_name (parking_lot_id, name) COMMENT '同一停车场规则名称唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则主表';

-- -----------------------------------------------------------------------------
-- 2. 收费规则版本表（快照存储，版本不可修改）
-- -----------------------------------------------------------------------------
CREATE TABLE billing_rule_version
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id        BIGINT       NOT NULL COMMENT '所属规则 ID',
    tenant_id      BIGINT       NOT NULL COMMENT '所属租户 ID（冗余字段，加速查询）',
    parking_lot_id BIGINT       NOT NULL COMMENT '所属停车场 ID（冗余字段）',
    version        INT          NOT NULL COMMENT '版本号（递增）',
    is_active      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为当前生效版本：1-是, 0-否',
    -- 计费配置（JSON 存储灵活计费规则）
    config         JSON         NOT NULL COMMENT '计费配置 JSON',
    -- 计费配置摘要（用于快速展示，不解析 JSON）
    config_summary VARCHAR(255) DEFAULT '' COMMENT '计费配置摘要，如"首2小时5元，后每小时3元，封顶30元"',
    -- 各字段明细（方便查询和统计）
    free_minutes   INT          NOT NULL DEFAULT 0 COMMENT '免费时长（分钟）',
    first_period   INT          NOT NULL DEFAULT 0 COMMENT '首时段时长（分钟）',
    first_amount   INT          NOT NULL DEFAULT 0 COMMENT '首时段金额（分）',
    unit_period    INT          NOT NULL DEFAULT 0 COMMENT '续费单位时长（分钟）',
    unit_amount    INT          NOT NULL DEFAULT 0 COMMENT '续费单位金额（分）',
    daily_cap      INT          NOT NULL DEFAULT 0 COMMENT '单日封顶金额（分），0表示不封顶',
    max_amount     INT          NOT NULL DEFAULT 0 COMMENT '最大金额（分），0表示不封顶',
    effective_from DATETIME     COMMENT '生效时间（可预设计费规则生效时间）',
    effective_to   DATETIME     COMMENT '失效时间（空表示永久生效）',
    created_by     BIGINT       NOT NULL COMMENT '创建人 ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_rule_id (rule_id) COMMENT '按规则查询',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_is_active (is_active) COMMENT '按生效状态查询',
    UNIQUE KEY uk_rule_version (rule_id, version) COMMENT '同一规则版本号唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则版本表';

-- -----------------------------------------------------------------------------
-- 3. 规则切换审计表
-- -----------------------------------------------------------------------------
CREATE TABLE billing_rule_switch_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID',
    tenant_id      BIGINT       NOT NULL COMMENT '租户 ID',
    operator_id    BIGINT       NOT NULL COMMENT '操作人 ID',
    operator_name  VARCHAR(100) DEFAULT '' COMMENT '操作人名称',
    before_rule_id BIGINT       COMMENT '切换前规则 ID',
    after_rule_id  BIGINT       NOT NULL COMMENT '切换后规则 ID',
    apply_to_existing TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否影响已在场车辆：1-是, 0-否（仅对新入场生效）',
    reason         VARCHAR(255) DEFAULT '' COMMENT '切换原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则切换审计日志';