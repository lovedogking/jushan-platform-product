-- ============================================================
-- Phase 3 E3: 小程序消息通知表
-- 存储支付成功通知等消息，供小程序消息中心展示
-- ============================================================

CREATE TABLE IF NOT EXISTS `mini_message` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花算法）',
    `tenant_id`         BIGINT       NOT NULL COMMENT '所属租户',
    `wx_user_id`        BIGINT       NOT NULL COMMENT '微信用户ID',
    `type`              VARCHAR(32)  NOT NULL DEFAULT 'SYSTEM' COMMENT '消息类型：PAY_SUCCESS-支付成功, SYSTEM-系统通知',
    `title`             VARCHAR(128) NOT NULL COMMENT '消息标题',
    `content`           TEXT         DEFAULT NULL COMMENT '消息内容',
    `related_order_id`  BIGINT       DEFAULT NULL COMMENT '关联订单ID（可跳转）',
    `related_plate`     VARCHAR(32)  DEFAULT NULL COMMENT '关联车牌号',
    `related_amount`    INT          DEFAULT NULL COMMENT '关联金额（分）',
    `is_read`           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否已读：0-未读, 1-已读',
    `read_at`           DATETIME(3)  DEFAULT NULL COMMENT '阅读时间',
    `created_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`        DATETIME(3)  DEFAULT NULL COMMENT '删除时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_msg_user_type` (`wx_user_id`, `type`) USING BTREE,
    KEY `idx_msg_created` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='小程序消息通知表';
