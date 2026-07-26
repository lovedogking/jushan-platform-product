-- =============================================================================
-- Flyway 迁移：parking_lot 增加计费规则绑定
-- =============================================================================

ALTER TABLE parking_lot
    ADD COLUMN fee_rule_id BIGINT UNSIGNED DEFAULT NULL COMMENT '绑定的计费规则ID（逻辑外键：fee_rule.id）' AFTER data_retention_days;
