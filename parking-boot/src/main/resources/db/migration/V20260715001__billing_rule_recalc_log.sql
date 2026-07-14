-- =============================================================================
-- Flyway 迁移：收费规则切换重新计算审计日志（P006）
-- =============================================================================
-- 当 applyToExisting=true 时，对当前在场停车记录按新规则重新计算费用并记录。
-- =============================================================================

CREATE TABLE billing_rule_recalc_log
(
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id         BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id    BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id BIGINT       NOT NULL COMMENT '停车记录 ID',
    plate_number      VARCHAR(32)  NOT NULL COMMENT '车牌号',
    rule_version_id   BIGINT       NOT NULL COMMENT '切换后的规则版本 ID',
    fee_cents         INT          NOT NULL DEFAULT 0 COMMENT '重新计算时的费用（分）',
    recalc_time       DATETIME     NOT NULL COMMENT '重新计算时间',
    operator_id       BIGINT       NOT NULL COMMENT '操作人 ID',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_parking_record_id (parking_record_id) COMMENT '按停车记录查询',
    INDEX idx_rule_version_id (rule_version_id) COMMENT '按规则版本查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则切换重新计算审计日志';
