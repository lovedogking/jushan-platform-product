-- =============================================================================
-- 任务包 6-2：月卡/固定车位审核 — monthly_pass 新增 review_status + review_remark
-- 注：fixed_space_binding 已有 review_status 列（FixedSpaceBinding.reviewStatus），
--     本迁移仅补齐 review_remark；支付方式/金额使用现有 pay_method / paid_amount_cents。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. monthly_pass 新增审核字段
-- ---------------------------------------------------------------------------
ALTER TABLE monthly_pass
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING=待审核, APPROVED=已通过, REJECTED=已驳回' AFTER pass_status,
    ADD COLUMN review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注' AFTER review_status;

-- 存量月卡默认已通过
UPDATE monthly_pass SET review_status = 'APPROVED' WHERE review_status IS NULL;

-- ---------------------------------------------------------------------------
-- 2. fixed_space_binding 补齐 review_remark
-- ---------------------------------------------------------------------------
ALTER TABLE fixed_space_binding
    ADD COLUMN review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注' AFTER review_status;

-- 存量固定车位 review_status 已有值，确认默认 APPROVED
UPDATE fixed_space_binding SET review_status = 'APPROVED' WHERE review_status IS NULL AND status IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. 插入审核模式全局参数（若未存在）
-- ---------------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('monthly_fixed.review_mode', 'AUTO', 0, 1, '月卡/固定车位审核模式：AUTO=自动通过, MANUAL=人工审核', '计费设置', 'ENUM', '["AUTO","MANUAL"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);
