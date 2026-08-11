-- =============================================================================
-- Flyway 迁移：fee_rule 补齐二期计费关键列
-- =============================================================================
-- 需求：补齐首时段分钟、整单封顶、跨天模式、生效方式 4 个字段，
--       使 FeeRule 具备完整 runtime 计算能力。
-- =============================================================================

ALTER TABLE fee_rule
    ADD COLUMN first_period_minutes INT             NOT NULL DEFAULT 0 COMMENT '首时段时长（分钟），0 表示无首时段优惠' AFTER unit_minutes,
    ADD COLUMN max_amount           DECIMAL(18,2)       DEFAULT NULL COMMENT '整单封顶金额（NULL 表示不封顶）' AFTER daily_cap,
    ADD COLUMN cross_day_mode       TINYINT         NOT NULL DEFAULT 1 COMMENT '跨天计费规则：1按自然日分段（每天0点重置） 2连续计费（每24小时一个封顶窗口）' AFTER night_cap,
    ADD COLUMN effect_mode          TINYINT         NOT NULL DEFAULT 1 COMMENT '生效方式：1立即生效 2仅新入场生效 3定时生效' AFTER cross_day_mode;
