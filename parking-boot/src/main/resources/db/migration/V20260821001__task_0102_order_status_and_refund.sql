-- =============================================================================
-- 任务包 1-2：ParkingOrder 状态机扩展（预订单/欠费中）+ 模拟退款闭环 + 状态流转日志
-- =============================================================================
-- 需求依据：V1.1 7.3、3.1.11（ADMIN-011）、附录 10.2 状态机、6.1 订单状态字典
--
-- 变更内容：
--   1. parking_order 增加退款信息列（退款原因/时间/操作人）
--   2. 新建 order_status_log 状态流转日志表（订单号/源状态/目标状态/触发源/操作人/时间）
--   3. 状态枚举扩展说明：status 列（VARCHAR(32)）新增 PRE_ORDER（预订单）、ARREARS（欠费中），
--      无需变更列类型，仅更新注释。
--
-- 存量数据处理（不订正，理由）：
--   存量 PENDING_PAY/PAID 订单不受影响，无需数据订正：
--   - 新增的 PRE_ORDER 仅由"入场"新链路产生；存量订单均由旧"出场建单"链路产生，
--     其起点为 PENDING_PAY/COMPLETED，与新状态机的下游状态完全兼容；
--   - 出场链路对"查不到预订单"的存量在场记录保留原建单逻辑（兼容期处理），流转合法；
--   - 退款列可空，存量订单默认 NULL，不影响既有查询与支付流程。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. parking_order 退款信息列
-- -----------------------------------------------------------------------------
ALTER TABLE parking_order
    ADD COLUMN refund_reason      VARCHAR(255) DEFAULT NULL COMMENT '退款原因（模拟退款必填）',
    ADD COLUMN refund_time        DATETIME     DEFAULT NULL COMMENT '退款时间',
    ADD COLUMN refund_operator_id BIGINT       DEFAULT NULL COMMENT '退款操作人（sys_user.id）';

-- status 枚举补充（仅注释，列类型不变）：
--   PRE_ORDER-预订单, PENDING_PAY-待支付, PAYING-支付中, PAID-已支付, COMPLETED-已完成,
--   CANCELLED-已取消, PAY_FAILED-支付失败, ARREARS-欠费中, REFUNDING-退款中, REFUNDED-已退款

-- -----------------------------------------------------------------------------
-- 2. 订单状态流转日志表
-- -----------------------------------------------------------------------------
CREATE TABLE order_status_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id      BIGINT       NOT NULL COMMENT '租户 ID（从订单推导）',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID（从订单推导）',
    order_id       BIGINT       NOT NULL COMMENT '关联停车订单 ID',
    order_no       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '订单号',
    from_status    VARCHAR(32)  DEFAULT NULL COMMENT '源状态（创建时为空）',
    to_status      VARCHAR(32)  NOT NULL COMMENT '目标状态',
    trigger_source VARCHAR(16)  NOT NULL COMMENT '触发源：SYSTEM-系统, USER-用户, BOOTH-岗亭, TIMER-定时任务',
    operator_id    BIGINT       DEFAULT NULL COMMENT '操作人 ID（系统/定时任务为空）',
    operator_name  VARCHAR(64)  DEFAULT NULL COMMENT '操作人名称/标记',
    remark         VARCHAR(255) DEFAULT NULL COMMENT '备注（如退款原因）',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '流转时间',
    INDEX idx_osl_order (order_id, created_at) COMMENT '按订单查询流转历史',
    INDEX idx_osl_lot (parking_lot_id, created_at) COMMENT '按车场查询',
    INDEX idx_osl_order_no (order_no) COMMENT '按订单号查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单状态流转日志';
