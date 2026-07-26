-- =============================================================================
-- Flyway 迁移：计费规则增加车辆适用维度
-- =============================================================================
-- 需求：计费规则增加车辆类型（临时车/月租车/储值车/免费车/黑名单）
--       和车牌颜色（蓝牌/绿牌/黄牌/黑牌/白牌）两个适用维度，以及规则描述字段
-- =============================================================================

ALTER TABLE fee_rule
    ADD COLUMN vehicle_type VARCHAR(32)  DEFAULT NULL COMMENT '适用车辆类型（逗号分隔，如 TEMP,MONTHLY），NULL 表示所有类型' AFTER billing_mode,
    ADD COLUMN plate_color  VARCHAR(32)  DEFAULT NULL COMMENT '适用车牌颜色（逗号分隔，如 BLUE,GREEN），NULL 表示所有颜色' AFTER vehicle_type,
    ADD COLUMN description  VARCHAR(256) DEFAULT NULL COMMENT '规则描述' AFTER name;
