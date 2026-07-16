-- ============================================================
-- Phase 3 E1: 代理支付记录表
-- 记录代缴人替车主支付停车费的行为
-- ============================================================

CREATE TABLE IF NOT EXISTS `proxy_pay_record` (
    `id`             BIGINT       NOT NULL COMMENT '主键（雪花算法）',
    `tenant_id`      BIGINT       NOT NULL COMMENT '所属租户',
    `parking_lot_id` BIGINT       NOT NULL COMMENT '车场ID',
    `order_id`       BIGINT       NOT NULL COMMENT '停车订单ID',
    `record_id`      BIGINT       NOT NULL COMMENT '停车记录ID',
    `plate_number`   VARCHAR(32)  NOT NULL COMMENT '车牌号',
    `payer_id`       BIGINT       NOT NULL COMMENT '代缴人用户ID（wx_user.id）',
    `payer_name`     VARCHAR(128) DEFAULT NULL COMMENT '代缴人昵称',
    `owner_id`       BIGINT       DEFAULT NULL COMMENT '车主用户ID（wx_user.id，可为NULL表示未注册车主）',
    `owner_name`     VARCHAR(128) DEFAULT NULL COMMENT '车主昵称',
    `amount_cents`   INT          NOT NULL DEFAULT 0 COMMENT '代缴金额（分）',
    `status`         VARCHAR(32)  NOT NULL DEFAULT 'COMPLETED' COMMENT '状态：COMPLETED-已完成',
    `pay_serial`     VARCHAR(128) DEFAULT NULL COMMENT '支付流水号',
    `remark`         VARCHAR(256) DEFAULT NULL COMMENT '备注',
    `created_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    `updated_at`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    `deleted_at`     DATETIME(3)  DEFAULT NULL COMMENT '删除时间',
    PRIMARY KEY (`id`) USING BTREE,
    KEY `idx_proxy_order` (`order_id`) USING BTREE,
    KEY `idx_proxy_plate` (`plate_number`) USING BTREE,
    KEY `idx_proxy_payer` (`payer_id`) USING BTREE,
    KEY `idx_proxy_created` (`created_at`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='代理支付记录表';
