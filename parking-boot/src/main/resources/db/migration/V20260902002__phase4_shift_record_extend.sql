-- Phase 4-2: shift_record 交接班扩展
-- 1. 新增 adjust_reason 列（手工校正实收金额原因）
ALTER TABLE shift_record
    ADD COLUMN IF NOT EXISTS adjust_reason VARCHAR(200) DEFAULT NULL COMMENT '手工校正实收金额原因' AFTER online_amount;

-- 2. 新增 arrears_count 列（本班产生的欠费订单数）
ALTER TABLE shift_record
    ADD COLUMN IF NOT EXISTS arrears_count INT NOT NULL DEFAULT 0 COMMENT '本班产生的欠费订单数' AFTER exception_count;

-- 3. 新增 handover_order_count 列（交接给下一班的未支付/欠费订单数）
ALTER TABLE shift_record
    ADD COLUMN IF NOT EXISTS handover_order_count INT NOT NULL DEFAULT 0 COMMENT '交接给下一班的未支付/欠费订单数' AFTER arrears_count;
