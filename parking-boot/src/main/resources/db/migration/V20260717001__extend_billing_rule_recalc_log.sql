-- 扩展 billing_rule_recalc_log 支持超时重算场景
ALTER TABLE billing_rule_recalc_log
    ADD COLUMN original_order_id      BIGINT       NULL COMMENT '原订单 ID（重算来源）' AFTER parking_record_id,
    ADD COLUMN original_amount_cents  INT          NULL COMMENT '原订单金额（分）' AFTER fee_cents,
    ADD COLUMN new_amount_cents       INT          NULL COMMENT '重新计算金额（分）' AFTER original_amount_cents,
    ADD COLUMN trigger_reason         VARCHAR(64)  NULL COMMENT '重算触发原因：EXIT_RESCAN=出场重识别, TIMEOUT_RECALC=超时关单后重算' AFTER new_amount_cents;

CREATE INDEX idx_recalc_original_order_id ON billing_rule_recalc_log(original_order_id);
CREATE INDEX idx_recalc_trigger_reason   ON billing_rule_recalc_log(trigger_reason);

-- 超时重算场景下无需规则版本和操作人，改为可为 NULL
ALTER TABLE billing_rule_recalc_log
    MODIFY COLUMN rule_version_id BIGINT NULL COMMENT '切换后的规则版本 ID（超时重算场景可为 NULL）',
    MODIFY COLUMN operator_id     BIGINT NULL COMMENT '操作人 ID（系统触发可为 NULL）';
