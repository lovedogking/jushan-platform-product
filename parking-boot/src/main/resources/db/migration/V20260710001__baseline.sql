-- =============================================================================
-- Flyway 基线迁移：系统基础设施
-- =============================================================================
-- 本迁移是数据库第一个版本，仅包含平台级基础元数据表。
-- 业务表（租户、停车场、设备、订单、支付等）在后续任务中逐版本创建。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 系统配置表
-- 用于存储平台级 key-value 配置项，如系统参数、开关、阈值等。
-- 后续业务模块可通过此表管理动态配置，无需重启应用。
-- -----------------------------------------------------------------------------
CREATE TABLE sys_config
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    config_key  VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT        COMMENT '配置值',
    description VARCHAR(500) DEFAULT '' COMMENT '配置说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key) COMMENT '配置键唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
