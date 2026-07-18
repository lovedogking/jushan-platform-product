-- =============================================================================
-- Flyway 迁移：Sprint 1 - 租户体系与平台底座重构
-- =============================================================================
-- 删除旧表后按新 DDL 重建，对齐任务规范要求。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 删除旧表（如果存在）
-- ---------------------------------------------------------------------------
DROP TABLE IF EXISTS sys_business_log;
DROP TABLE IF EXISTS sys_auth_code;
DROP TABLE IF EXISTS sys_admin_account_role;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_custom_role;
DROP TABLE IF EXISTS sys_admin_account;
DROP TABLE IF EXISTS sys_company;
DROP TABLE IF EXISTS sys_tenant;

-- ---------------------------------------------------------------------------
-- 2. sys_tenant - 租户主表（系统表，不受租户拦截器过滤）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_tenant (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT '租户ID',
    name VARCHAR(100) NOT NULL COMMENT '租户名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '租户编码',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1正常 0禁用',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code)
) COMMENT='租户主表' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 3. sys_company - 公司/集团档案表（业务表，tenant_id 隔离）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_company (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    parent_id BIGINT NOT NULL DEFAULT 0 COMMENT '上级公司ID，0表示顶级集团',
    name VARCHAR(100) NOT NULL COMMENT '公司名称',
    code VARCHAR(50) COMMENT '公司编码',
    level TINYINT NOT NULL COMMENT '级别：1集团 2公司 3分公司',
    contact_name VARCHAR(50) COMMENT '联系人',
    contact_phone VARCHAR(20) COMMENT '联系电话',
    address VARCHAR(255) COMMENT '详细地址',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序',
    is_deleted TINYINT NOT NULL DEFAULT 0 COMMENT '软删除',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_parent_id (parent_id),
    UNIQUE KEY uk_tenant_name (tenant_id, name),
    INDEX idx_level (level)
) COMMENT='公司/集团档案' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 4. sys_admin_account - 管理员账号表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_admin_account (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT COMMENT '租户ID，平台级管理员可为空',
    company_id BIGINT COMMENT '所属公司ID',
    lot_id BIGINT COMMENT '所属车场ID，三级管理员必填',
    username VARCHAR(50) NOT NULL COMMENT '登录账号',
    password VARCHAR(255) NOT NULL COMMENT 'BCrypt 加密密码',
    real_name VARCHAR(50) COMMENT '真实姓名',
    phone VARCHAR(20) COMMENT '手机号',
    email VARCHAR(100) COMMENT '邮箱',
    level TINYINT NOT NULL COMMENT '级别：1一级 2二级 3三级',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1启用 0禁用 2锁定',
    login_fail_count TINYINT NOT NULL DEFAULT 0 COMMENT '连续登录失败次数',
    lock_until DATETIME COMMENT '锁定截止时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    UNIQUE KEY uk_tenant_username (tenant_id, username),
    INDEX idx_company_id (company_id),
    INDEX idx_lot_id (lot_id),
    INDEX idx_level (level)
) COMMENT='管理员账号' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 5. sys_custom_role - 自定义角色表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_custom_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    role_name VARCHAR(50) NOT NULL COMMENT '角色名称',
    role_code VARCHAR(50) NOT NULL COMMENT '角色编码',
    description VARCHAR(255) COMMENT '描述',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    UNIQUE KEY uk_tenant_code (tenant_id, role_code)
) COMMENT='自定义角色' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 6. sys_role_permission - 角色权限矩阵表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_role_permission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限码，如 company:view, company:create',
    permission_type VARCHAR(20) NOT NULL COMMENT '类型：menu/button/data',
    data_scope VARCHAR(20) COMMENT '数据范围：all/company/self/parking',
    is_deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_role_id (role_id),
    UNIQUE KEY uk_role_permission (role_id, permission_code)
) COMMENT='角色权限矩阵' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 7. sys_admin_account_role - 账号角色关联表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_admin_account_role (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    admin_account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_account_role (admin_account_id, role_id),
    INDEX idx_account_id (admin_account_id),
    INDEX idx_role_id (role_id)
) COMMENT='账号角色关联' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 8. sys_auth_code - 授权码表（系统级，但激活后绑定 tenant_id）
-- ---------------------------------------------------------------------------
CREATE TABLE sys_auth_code (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '授权码，格式 XXXX-XXXX-XXXX-XXXX',
    tenant_id BIGINT COMMENT '激活后绑定的租户ID',
    max_parking_count INT NOT NULL DEFAULT 1 COMMENT '可开通车场数量',
    valid_start DATE NOT NULL COMMENT '有效期开始',
    valid_end DATE NOT NULL COMMENT '有效期结束',
    used_count INT NOT NULL DEFAULT 0 COMMENT '已使用次数',
    max_use_count INT NOT NULL DEFAULT 1 COMMENT '最大使用次数',
    version_type VARCHAR(20) NOT NULL COMMENT '功能版本：基础版/标准版/高级版',
    status TINYINT NOT NULL DEFAULT 0 COMMENT '状态：0未使用 1已激活 2已过期 3已禁用',
    activated_by BIGINT COMMENT '激活人ID',
    activated_at DATETIME COMMENT '激活时间',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_code (code),
    INDEX idx_status (status),
    INDEX idx_tenant_id (tenant_id)
) COMMENT='车场开通授权码' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 9. sys_business_log - 业务操作日志表
-- ---------------------------------------------------------------------------
CREATE TABLE sys_business_log (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    operator_id BIGINT COMMENT '操作人ID',
    operator_name VARCHAR(50) COMMENT '操作人姓名',
    ip VARCHAR(50) COMMENT '操作IP',
    operation_type VARCHAR(50) NOT NULL COMMENT '操作类型：CREATE/UPDATE/DELETE/LOGIN/RESET_PASSWORD 等',
    operation_object VARCHAR(100) COMMENT '操作对象：Company/AdminAccount/AuthCode 等',
    object_id VARCHAR(100) COMMENT '对象ID',
    before_value JSON COMMENT '变更前完整值（JSON）',
    after_value JSON COMMENT '变更后完整值（JSON）',
    result TINYINT NOT NULL DEFAULT 1 COMMENT '结果：1成功 0失败',
    error_msg TEXT COMMENT '错误信息',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_tenant_id (tenant_id),
    INDEX idx_operator_id (operator_id),
    INDEX idx_operation_type (operation_type),
    INDEX idx_created_at (created_at)
) COMMENT='业务操作日志' ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------------
-- 10. 初始化数据
-- ---------------------------------------------------------------------------

-- 插入默认租户
INSERT INTO sys_tenant (id, name, code, status) VALUES (1, '默认租户', 'DEFAULT_TENANT', 1);

-- 插入超级管理员账号（密码：admin123，BCrypt 加密）
INSERT INTO sys_admin_account (id, tenant_id, username, password, real_name, level, status)
VALUES (1, NULL, 'super_admin', '$2b$12$.jOUKmSqerkcaXyru4c0nu14cRhlJ3zoORVN8CPK0FXC/9XDNA0ku', '超级管理员', 1, 1);

-- 插入基础角色
INSERT INTO sys_custom_role (id, tenant_id, role_name, role_code, description)
VALUES 
    (1, 1, '公司管理员', 'COMPANY_ADMIN', '管理公司及下属车场'),
    (2, 1, '车场管理员', 'PARKING_ADMIN', '管理单个车场');

-- 插入基础权限
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
VALUES 
    -- 公司管理员权限
    (1, 'company:view', 'menu', 'company'),
    (1, 'company:create', 'button', 'company'),
    (1, 'company:update', 'button', 'company'),
    (1, 'company:delete', 'button', 'company'),
    (1, 'account:view', 'menu', 'company'),
    (1, 'account:create', 'button', 'company'),
    (1, 'account:update', 'button', 'company'),
    (1, 'account:delete', 'button', 'company'),
    -- 车场管理员权限
    (2, 'company:view', 'menu', 'self'),
    (2, 'account:view', 'menu', 'self');

-- 平台超级管理员角色（tenant_id=1 作为系统角色容器，实际 super_admin 账号 tenant_id 为 NULL）
INSERT INTO sys_custom_role (id, tenant_id, role_name, role_code, description)
VALUES (3, 1, '超级管理员', 'SUPER_ADMIN', '平台超级管理员，拥有所有权限');

-- 为 super_admin 账号绑定超级管理员角色
INSERT INTO sys_admin_account_role (admin_account_id, role_id) VALUES (1, 3);

-- 超级管理员持有通配权限（PermissionAspect 将 '*' 识别为拥有所有权限）
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
VALUES (3, '*', 'all', 'all');
