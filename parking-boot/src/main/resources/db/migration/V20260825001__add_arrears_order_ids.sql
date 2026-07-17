-- 新增 arrears_order_ids 字段，存储合并订单关联的欠费订单 ID 列表（JSON 数组）
ALTER TABLE parking_order
    ADD COLUMN arrears_order_ids TEXT NULL COMMENT '关联的欠费订单ID列表（JSON数组），合并计费时记录，如 [101,102]' AFTER recalc_source_order_id;
