-- =============================================================================
-- 任务包 1-3：计费体系统一 — billing_rule 生效方式（ADMIN-010）
-- =============================================================================
-- 需求依据：V1.1 3.1.10、7.1、确认项 34
--
-- 变更内容：
--   1. billing_rule 增加 effect_type（IMMEDIATE/NEW_ENTRY_ONLY/SCHEDULED）
--      与 effect_time（定时生效时间）
--   2. parking_record 增加 rule_snapshot（JSON，仅存计费必需字段），
--      用于 NEW_ENTRY_ONLY 模式下已在场车辆按入场时规则快照计费
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. billing_rule：增加生效方式与定时生效时间
-- -----------------------------------------------------------------------------
ALTER TABLE billing_rule
    ADD COLUMN effect_type VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE' COMMENT '生效方式：IMMEDIATE-立即生效（含已在场车辆）, NEW_ENTRY_ONLY-仅新入场生效, SCHEDULED-定时生效' AFTER is_default,
    ADD COLUMN effect_time DATETIME COMMENT '定时生效时间（effect_type=SCHEDULED 时必填）' AFTER effect_type;
CREATE INDEX idx_effect_type ON billing_rule (effect_type);

-- -----------------------------------------------------------------------------
-- 2. parking_record：增加规则快照（仅 NEW_ENTRY_ONLY 时使用）
-- -----------------------------------------------------------------------------
ALTER TABLE parking_record
    ADD COLUMN rule_snapshot JSON COMMENT '入场时的计费规则快照（仅 NEW_ENTRY_ONLY 生效方式下使用，存计费必需字段：ruleType, freeMinutes, firstPeriod, firstAmount, unitPeriod, unitAmount, dailyCap, maxAmount, timeSegments）' AFTER fee_rule_version;
