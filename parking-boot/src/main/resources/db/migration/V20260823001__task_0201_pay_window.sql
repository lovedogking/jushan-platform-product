-- 任务包 2-1：区分出口缴费与提前缴费，支付出场窗口期机制
-- 新增字段：pay_window_deadline（支付窗口截止时间）、pay_scene（支付场景）、
--           recalc_source_order_id（重算来源原订单号）、exit_lane_id（出口车道ID）
ALTER TABLE parking_order
    ADD COLUMN pay_window_deadline DATETIME       NULL COMMENT '支付窗口截止时间（ADVANCE场景，now+窗口期分钟数）',
    ADD COLUMN pay_scene           VARCHAR(20)    NULL COMMENT '支付场景：AT_EXIT=出口缴费 / ADVANCE=提前缴费',
    ADD COLUMN recalc_source_order_id BIGINT      NULL COMMENT '重算来源原订单号（超期重算新订单关联原订单）',
    ADD COLUMN exit_lane_id        BIGINT         NULL COMMENT '出口车道ID（AT_EXIT场景用于开闸）';

-- 窗口期加速查询：PAID + deadline 范围内的订单
CREATE INDEX idx_order_window_deadline ON parking_order (pay_window_deadline, status);
