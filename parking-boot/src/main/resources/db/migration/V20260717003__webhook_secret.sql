-- =============================================================================
-- SEC-005 / COMM-002: Device Webhook HMAC 签名校验 — webhook_secret 表
-- =============================================================================
-- 每个停车场配置一个 webhook 签名密钥，用于验证 Device Access 推送的 Webhook
-- 请求的 HMAC-SHA256 签名。密钥由平台管理员统一配置，不出库。
-- =============================================================================

CREATE TABLE webhook_secret
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT      NOT NULL COMMENT '停车场 ID',
    secret        VARCHAR(256) NOT NULL COMMENT 'HMAC-SHA256 签名密钥（加密存储）',
    description   VARCHAR(500) DEFAULT '' COMMENT '密钥说明',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-启用，0-停用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_parking_lot_id (parking_lot_id) COMMENT '停车场唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Webhook 签名密钥表';
