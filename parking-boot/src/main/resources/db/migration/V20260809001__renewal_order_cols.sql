-- =============================================================================
-- Flyway 迁移：月卡/固定车续费闭环支撑字段
-- =============================================================================
-- 内容：
--   1. parking_order 增加 ref_id / renewal_months / operator_id 三列
--      - ref_id：关联业务主键（MONTH_RENEW 订单时为 vehicle_id）
--      - renewal_months：续费月数（用于支付成功后回算有效期）
--      - operator_id：发起续费的管理员（sys_user.id），用于审计归属
-- =============================================================================

ALTER TABLE parking_order
    ADD COLUMN ref_id BIGINT DEFAULT NULL COMMENT '关联业务ID（月卡续费订单时为 vehicle_id）',
    ADD COLUMN renewal_months INT DEFAULT NULL COMMENT '续费月数（月卡续费订单）',
    ADD COLUMN operator_id BIGINT DEFAULT NULL COMMENT '发起续费的操作人ID（sys_user.id）',
    ADD INDEX idx_order_ref (ref_id, order_type) COMMENT '按业务ID+订单类型查询续费订单';
