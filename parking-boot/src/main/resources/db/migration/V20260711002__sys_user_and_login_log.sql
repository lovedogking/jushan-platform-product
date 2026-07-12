-- =============================================================================
-- Flyway 迁移：平台账号与登录日志
-- =============================================================================
-- T12｜平台账号登录、退出与会话
-- 创建系统用户表和登录日志表，并插入默认超级管理员。
-- 密码安全：所有密码使用 BCrypt 哈希存储，明文禁止写入数据库。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 系统用户表
-- -----------------------------------------------------------------------------
CREATE TABLE sys_user
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    username      VARCHAR(64)  NOT NULL COMMENT '登录账号',
    password_hash VARCHAR(255) NOT NULL COMMENT 'BCrypt 密码哈希',
    display_name  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '显示名称',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '账号状态：ENABLED-启用, DISABLED-禁用, LOCKED-锁定',
    roles         VARCHAR(500) NOT NULL DEFAULT '' COMMENT '角色列表（JSON 数组，如 ["super_admin"]）',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username) COMMENT '账号唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- -----------------------------------------------------------------------------
-- 登录日志表
-- -----------------------------------------------------------------------------
CREATE TABLE sys_login_log
(
    id           BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    user_id      BIGINT       DEFAULT NULL COMMENT '用户 ID（登录失败可能为 NULL）',
    username     VARCHAR(64)  NOT NULL COMMENT '登录账号',
    ip           VARCHAR(64)  DEFAULT '' COMMENT '登录 IP',
    user_agent   VARCHAR(512) DEFAULT '' COMMENT '浏览器 User-Agent',
    result       VARCHAR(20)  NOT NULL COMMENT '登录结果：SUCCESS-成功, FAIL_BAD_CREDENTIALS-密码错误, FAIL_DISABLED-账号禁用, FAIL_RATE_LIMIT-频繁限制',
    fail_reason  VARCHAR(255) DEFAULT '' COMMENT '失败原因简述（不含密码）',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_username (username) COMMENT '按账号查询登录日志',
    INDEX idx_result (result) COMMENT '按结果查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

-- -----------------------------------------------------------------------------
-- 默认超级管理员
-- 密码：admin123（BCrypt 哈希），首次部署后应立即修改。
-- 角色为平台超级管理员，可访问全部客户和停车场数据。
-- 后续可通过 T13 固定角色模块细化权限粒度。
-- -----------------------------------------------------------------------------
INSERT INTO sys_user (username, password_hash, display_name, status, roles)
VALUES ('admin',
        '$2b$10$zp.gM6IpIAC95Pcq2vDSeeR3.cdKRr.5wvBjs7XwNdWlSAl1IMqna',
        '超级管理员',
        'ENABLED',
        '["super_admin"]');
