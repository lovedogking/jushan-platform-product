-- =============================================================================
-- Flyway 迁移：parking_session 增加计费规则快照列
-- =============================================================================
-- 需求：车辆入场时记录生效的 fee_rule 快照，支持"仅新入场生效"规则。
-- =============================================================================

ALTER TABLE parking_session
    ADD COLUMN fee_rule_id      BIGINT       DEFAULT NULL COMMENT '入场时生效的收费规则ID' AFTER remark,
    ADD COLUMN fee_rule_snapshot JSON        DEFAULT NULL COMMENT '入场时生效的收费规则完整快照（含时段列表）' AFTER fee_rule_id;
