-- =============================================================================
-- Flyway 迁移：客户注册、审核与租户创建
-- =============================================================================
-- T14｜客户注册、审核、启停与租户创建
-- 创建租户表、租户审核日志表，并在 sys_user 新增 tenant_id 列。
-- 客户注册 → 创建待审核租户 + 待审核管理员账号 → 总后台审核 → 启用/拒绝。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 租户表
-- -----------------------------------------------------------------------------
CREATE TABLE tenant
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name            VARCHAR(128) NOT NULL COMMENT '企业名称',
    contact_person  VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '联系人',
    contact_phone   VARCHAR(20)  NOT NULL COMMENT '联系电话',
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING_REVIEW' COMMENT '状态：PENDING_REVIEW-待审核, ENABLED-已启用, DISABLED-已禁用, REJECTED-已拒绝',
    admin_user_id   BIGINT       DEFAULT NULL COMMENT '关联 sys_user 的管理员账号 ID',
    max_parking_lots INT         NOT NULL DEFAULT 3 COMMENT '最大停车场数量',
    max_devices     INT          NOT NULL DEFAULT 10 COMMENT '最大设备数量',
    max_employees   INT          NOT NULL DEFAULT 20 COMMENT '最大员工账号数量',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_name (name) COMMENT '企业名称唯一索引',
    UNIQUE KEY uk_contact_phone (contact_phone) COMMENT '联系电话唯一索引',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_admin_user_id (admin_user_id) COMMENT '按管理员账号查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户表（客户/商户）';

-- -----------------------------------------------------------------------------
-- 租户审核日志表
-- -----------------------------------------------------------------------------
CREATE TABLE tenant_audit_log
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id     BIGINT       NOT NULL COMMENT '租户 ID',
    action        VARCHAR(20)  NOT NULL COMMENT '操作类型：APPROVED-审核通过, REJECTED-审核拒绝, ENABLED-启用, DISABLED-禁用',
    operator_id   BIGINT       NOT NULL COMMENT '操作人 ID（sys_user.id，超级管理员或平台运营）',
    reason        VARCHAR(500) NOT NULL DEFAULT '' COMMENT '操作原因/备注',
    before_status VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '操作前状态',
    after_status  VARCHAR(20)  NOT NULL DEFAULT '' COMMENT '操作后状态',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_operator_id (operator_id) COMMENT '按操作人查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='租户审核日志表';

-- -----------------------------------------------------------------------------
-- sys_user 新增 tenant_id 列（平台用户为 NULL，客户员工关联所属租户）
-- -----------------------------------------------------------------------------
ALTER TABLE sys_user
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID（平台用户为 NULL）' AFTER id,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询用户';
