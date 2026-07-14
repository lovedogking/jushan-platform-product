-- =============================================================================
-- Flyway 迁移：Sprint 8 订单支付与 P云对接基础表
-- =============================================================================
-- 内容：
--   1. 扩展 parking_order 表 —— 完善状态机、支付单关联、订单号、幂等键
--   2. 创建 pay_order 表 —— 支付流水记录（P云回调幂等）
--   3. 创建 pay_merchant_config 表 —— P云商户配置
--   4. 创建 pay_settlement_record 表 —— 对账结算记录
--   5. 创建 parking_record_sync_log 表 —— P云停车记录同步日志
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 扩展 parking_order 表（状态机完善）
-- -----------------------------------------------------------------------------
ALTER TABLE parking_order
    ADD COLUMN order_no VARCHAR(64) NOT NULL DEFAULT '' COMMENT '订单号 O{lotId}{yyyyMMdd}{6位序号}',
    ADD COLUMN order_type VARCHAR(32) NOT NULL DEFAULT 'PARKING' COMMENT '订单类型：PARKING-停车, MONTH_RENEW-月卡续费, VISITOR-访客, TOP_UP-充值',
    ADD COLUMN discount_amount INT NOT NULL DEFAULT 0 COMMENT '优惠金额（分）',
    ADD COLUMN points_discount INT NOT NULL DEFAULT 0 COMMENT '积分抵扣金额（分）',
    ADD COLUMN payable_amount INT NOT NULL DEFAULT 0 COMMENT '应付金额（分）',
    ADD COLUMN paid_amount INT NOT NULL DEFAULT 0 COMMENT '已支付金额（分）',
    ADD COLUMN pay_channel VARCHAR(32) DEFAULT NULL COMMENT '支付渠道：PYUN-P云, WECHAT-微信, ALIPAY-支付宝, CASH-现金, BALANCE-余额',
    ADD COLUMN pay_serial VARCHAR(64) DEFAULT NULL COMMENT 'P云支付流水号',
    ADD COLUMN idempotency_key VARCHAR(64) DEFAULT NULL COMMENT '幂等键 X-Idempotency-Key',
    ADD COLUMN expired_at DATETIME DEFAULT NULL COMMENT '订单过期时间',
    ADD COLUMN version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间',
    ADD UNIQUE KEY uk_order_no (order_no) COMMENT '订单号唯一索引',
    ADD UNIQUE KEY uk_idempotency_key (idempotency_key) COMMENT '幂等键唯一索引',
    ADD INDEX idx_order_status (status, created_at) COMMENT '按状态+时间查询',
    ADD INDEX idx_order_pay_serial (pay_serial) COMMENT '按支付流水查询',
    ADD INDEX idx_order_expired (expired_at, status) COMMENT '过期订单扫描';

-- 更新状态枚举注释（PENDING_PAY-待支付, PAYING-支付中, PAID-已支付, COMPLETED-已完成, CANCELLED-已取消, PAY_FAILED-支付失败, REFUNDING-退款中, REFUNDED-已退款）

-- -----------------------------------------------------------------------------
-- 2. 支付流水表（P云回调幂等）
-- -----------------------------------------------------------------------------
CREATE TABLE pay_order
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    order_id        BIGINT       NOT NULL COMMENT '关联停车订单 ID',
    pay_order_no    VARCHAR(64)  NOT NULL COMMENT '支付请求单号',
    pay_serial      VARCHAR(64)  DEFAULT NULL COMMENT 'P云支付流水号',
    refund_order_no VARCHAR(64)  DEFAULT NULL COMMENT '退款请求单号',
    refund_serial   VARCHAR(64)  DEFAULT NULL COMMENT 'P云退款流水号',
    pay_channel     VARCHAR(32)  NOT NULL COMMENT '支付渠道：PYUN, WECHAT, ALIPAY, CASH, BALANCE',
    pay_amount      INT          NOT NULL DEFAULT 0 COMMENT '支付金额（分）',
    refund_amount   INT          NOT NULL DEFAULT 0 COMMENT '退款金额（分）',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待支付, SUCCESS-成功, FAILED-失败, REFUNDED-已退款',
    payer_open_id   VARCHAR(64)  DEFAULT NULL COMMENT '付款方 OpenID',
    pay_scene       VARCHAR(32)  DEFAULT NULL COMMENT '支付场景：MINI_APP-小程序, H5-H5页面, POS-收银台, BOOTH-岗亭',
    trade_no        VARCHAR(64)  DEFAULT NULL COMMENT '第三方支付渠道交易单号',
    trade_time      DATETIME     DEFAULT NULL COMMENT '交易时间',
    fee_cents       INT          NOT NULL DEFAULT 0 COMMENT '手续费（分）',
    notify_raw      TEXT         DEFAULT NULL COMMENT '回调原始报文（JSON）',
    idempotency_key VARCHAR(64)  DEFAULT NULL COMMENT '幂等键',
    version         INT          NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME     DEFAULT NULL COMMENT '软删除时间',
    UNIQUE KEY uk_pay_serial (pay_serial) COMMENT 'P云流水唯一（幂等）',
    UNIQUE KEY uk_pay_order_no (pay_order_no) COMMENT '支付单号唯一',
    INDEX idx_pay_order_order_id (order_id) COMMENT '按订单查询',
    INDEX idx_pay_order_status (status, created_at) COMMENT '按状态查询',
    INDEX idx_pay_order_idempotency (idempotency_key) COMMENT '幂等查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='支付流水表';

-- -----------------------------------------------------------------------------
-- 3. P云商户配置表
-- -----------------------------------------------------------------------------
CREATE TABLE pay_merchant_config
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    app_id          VARCHAR(64)  NOT NULL COMMENT 'P云应用ID',
    app_secret      VARCHAR(128) NOT NULL COMMENT 'P云应用密钥（加密存储）',
    merchant_no     VARCHAR(64)  NOT NULL COMMENT 'P云商户号',
    park_uuid       VARCHAR(64)  DEFAULT NULL COMMENT 'P云停车场UUID',
    notify_url      VARCHAR(256) DEFAULT NULL COMMENT '支付回调地址',
    callback_url    VARCHAR(256) DEFAULT NULL COMMENT '支付成功前端回调地址',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-禁用',
    env             VARCHAR(32)  NOT NULL DEFAULT 'PROD' COMMENT '环境：PROD-生产, SANDBOX-沙箱',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME     DEFAULT NULL COMMENT '软删除时间',
    UNIQUE KEY uk_merchant_lot (tenant_id, parking_lot_id, deleted_at) COMMENT '每个停车场只能有一个商户配置',
    INDEX idx_merchant_app_id (app_id) COMMENT '按应用ID查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='P云商户配置表';

-- -----------------------------------------------------------------------------
-- 4. 对账结算记录表
-- -----------------------------------------------------------------------------
CREATE TABLE pay_settlement_record
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    serial          VARCHAR(64)  NOT NULL COMMENT '结算流水号',
    subject         VARCHAR(256) DEFAULT NULL COMMENT '结算主题',
    start_time      DATETIME     NOT NULL COMMENT '结算开始时间',
    end_time        DATETIME     NOT NULL COMMENT '结算结束时间',
    trade_count     INT          NOT NULL DEFAULT 0 COMMENT '交易笔数',
    total_value     INT          NOT NULL DEFAULT 0 COMMENT '总交易金额（分）',
    settle_value    INT          NOT NULL DEFAULT 0 COMMENT '最终结算金额（分）',
    service_value   INT          NOT NULL DEFAULT 0 COMMENT '手续费（分）',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-未清算, SETTLED-已清算',
    type            VARCHAR(32)  NOT NULL DEFAULT 'NORMAL' COMMENT '类型：NORMAL-正常, SUPPLEMENT-补款, DEDUCTION-扣款, REFUND-退款',
    transfer_status VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '划账状态：PENDING-未划账, TRANSFERRED-已划账, EXCEPTION-异常',
    sync_raw        TEXT         DEFAULT NULL COMMENT '同步原始报文',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME     DEFAULT NULL COMMENT '软删除时间',
    UNIQUE KEY uk_settlement_serial (serial) COMMENT '结算流水唯一',
    INDEX idx_settlement_lot_time (parking_lot_id, start_time, end_time) COMMENT '按车场+时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='对账结算记录表';

-- -----------------------------------------------------------------------------
-- 5. P云停车记录同步日志
-- -----------------------------------------------------------------------------
CREATE TABLE parking_record_sync_log
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id BIGINT     NOT NULL COMMENT '关联停车记录 ID',
    sync_type       VARCHAR(32)  NOT NULL COMMENT '同步类型：ENTER-入场, LEAVE-离场, UPDATE-更新',
    park_uuid       VARCHAR(64)  NOT NULL COMMENT 'P云停车场UUID',
    parking_serial  VARCHAR(64)  NOT NULL COMMENT '停车流水号',
    plate           VARCHAR(32)  NOT NULL COMMENT '车牌号',
    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待同步, SUCCESS-成功, FAILED-失败, RETRYING-重试中',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '重试次数',
    response_code   VARCHAR(32)  DEFAULT NULL COMMENT 'P云响应码',
    response_msg    VARCHAR(500) DEFAULT NULL COMMENT 'P云响应消息',
    request_raw     TEXT         DEFAULT NULL COMMENT '请求原始报文',
    response_raw    TEXT         DEFAULT NULL COMMENT '响应原始报文',
    synced_at       DATETIME     DEFAULT NULL COMMENT '同步成功时间',
    next_retry_at   DATETIME     DEFAULT NULL COMMENT '下次重试时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME     DEFAULT NULL COMMENT '软删除时间',
    INDEX idx_sync_log_record (parking_record_id, sync_type) COMMENT '按记录+类型查询',
    INDEX idx_sync_log_status (status, next_retry_at) COMMENT '待重试扫描',
    INDEX idx_sync_log_parking_serial (parking_serial) COMMENT '按流水号查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='P云停车记录同步日志';

