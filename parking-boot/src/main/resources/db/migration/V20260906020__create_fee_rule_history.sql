-- =============================================================================
-- Flyway 迁移：收费规则版本历史表
-- =============================================================================
-- 需求：记录 FeeRule 每次修改前的完整快照，支持历史版本查看与回退。
-- =============================================================================

CREATE TABLE IF NOT EXISTS fee_rule_history (
    id              BIGINT UNSIGNED NOT NULL COMMENT '历史版本ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    fee_rule_id     BIGINT UNSIGNED NOT NULL COMMENT '当前规则ID（逻辑外键：fee_rule.id）',
    version_no      INT             NOT NULL COMMENT '历史版本号，同一规则下自增',
    snapshot_json   JSON            NOT NULL COMMENT '规则完整快照（含时段列表）',
    effective_from  DATETIME(3)     NOT NULL COMMENT '该版本生效开始时间',
    effective_to    DATETIME(3)         DEFAULT NULL COMMENT '该版本失效时间（被新版本替换时填充）',
    created_by      BIGINT              DEFAULT NULL COMMENT '修改人ID',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_fee_rule_id (fee_rule_id),
    KEY idx_version_no (fee_rule_id, version_no),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则版本历史表';
