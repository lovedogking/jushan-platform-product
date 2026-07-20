    -- ═══════════════════════════════════════════════════════════════
-- 停车SaaS平台 — 数据库初始化脚本（parking-system）
-- 数据库: jushan_platform
-- ═══════════════════════════════════════════════════════════════
-- 创建时间: 2026-07-20 16:04 
-- 说明: 本脚本合并了所有 Flyway 迁移文件，按版本顺序排列
--       适用于新建数据库时的全量初始化
--
-- 执行方式:
--   mysql -u root -p --default-character-set=utf8mb4 < init-parking-system.sql
-- ═══════════════════════════════════════════════════════════════

CREATE DATABASE IF NOT EXISTS jushan_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE jushan_platform;

-- ═══════════════════════════════════════════════════════════════
-- 以下为 Flyway 迁移脚本（按版本顺序排列）
-- ═══════════════════════════════════════════════════════════════


-- ============================================================
-- Migration: V20260710001__baseline.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260711002__sys_user_and_login_log.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260711003__sys_role_permission.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：固定角色、权限与角色-权限关联
-- =============================================================================
-- T13｜固定角色、菜单与操作权限
-- 创建角色表、权限表、角色-权限关联表，并初始化 7 个固定角色和对应权限。
-- 第一阶段不支持客户自定义角色和自定义权限组合。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 角色表
-- -----------------------------------------------------------------------------
CREATE TABLE sys_role
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code        VARCHAR(50)  NOT NULL COMMENT '角色编码（唯一标识，如 super_admin）',
    name        VARCHAR(100) NOT NULL COMMENT '角色显示名称',
    description VARCHAR(255) DEFAULT '' COMMENT '角色说明',
    is_fixed    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否固定角色：1-是，0-否（第一阶段全部为固定角色）',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_code (code) COMMENT '角色编码唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

-- -----------------------------------------------------------------------------
-- 权限表
-- -----------------------------------------------------------------------------
CREATE TABLE sys_permission
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    code        VARCHAR(100) NOT NULL COMMENT '权限编码（如 gate:open）',
    name        VARCHAR(100) NOT NULL COMMENT '权限名称',
    description VARCHAR(255) DEFAULT '' COMMENT '权限说明',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_code (code) COMMENT '权限编码唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

-- -----------------------------------------------------------------------------
-- 角色-权限关联表
-- -----------------------------------------------------------------------------
CREATE TABLE sys_role_permission
(
    id              BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    role_code       VARCHAR(50)  NOT NULL COMMENT '角色编码',
    permission_code VARCHAR(100) NOT NULL COMMENT '权限编码',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_role_permission (role_code, permission_code) COMMENT '角色权限唯一索引',
    INDEX idx_role_code (role_code) COMMENT '按角色查询权限',
    INDEX idx_permission_code (permission_code) COMMENT '按权限查询角色'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限关联表';

-- =============================================================================
-- 种子数据：7 个固定角色
-- =============================================================================
INSERT INTO sys_role (code, name, description) VALUES
('super_admin',        '超级管理员',   '全平台、全部客户、全部停车场，拥有所有权限'),
('platform_operator',  '平台运营',     '查看运营状态、协助配置、处理服务请求、查看平台统计'),
('customer_admin',     '客户管理员',   '管理本租户下全部停车场、员工、配置和数据'),
('parking_manager',    '停车场管理员', '管理被授权停车场的基础信息、车道、设备和收费规则'),
('finance',            '财务',         '查看订单、支付、分账、退款和 SaaS 费用数据'),
('device_maintenance', '设备运维',     '查看设备状态、处理告警、执行校时和远程控制'),
('booth_operator',     '岗亭',         '查看实时通行记录、人工放行、人工减免、修改车牌');

-- =============================================================================
-- 种子数据：权限列表（18 个权限码，格式为 module:action）
-- =============================================================================
INSERT INTO sys_permission (code, name, description) VALUES
('user:read',        '查看用户',     '查看平台用户和客户员工信息'),
('user:write',       '管理用户',     '创建、编辑、禁用/启用用户账号'),
('role:read',        '查看角色',     '查看角色和权限配置'),
('tenant:read',      '查看租户',     '查看客户和租户信息'),
('tenant:write',     '管理租户',     '审核客户、配置租户额度和套餐'),
('parking:read',     '查看停车场',   '查看停车场基础信息、车道和设备配置'),
('parking:write',    '管理停车场',   '创建/编辑停车场、车道和设备绑定'),
('parking:disable',  '停用停车场',   '启用/停用停车场（仅超级管理员和客户管理员）'),
('device:read',      '查看设备',     '查看设备状态、心跳和告警信息'),
('device:manage',    '管理设备',     '校时、远程控制和设备告警处理'),
('gate:open',        '开闸',         '执行开闸操作'),
('gate:manual',      '人工放行',     '人工放行、车牌修正、人工减免'),
('record:read',      '查看通行记录', '查看入场/出场通行记录和停车记录'),
('order:read',       '查看订单',     '查看停车订单和支付状态'),
('finance:read',     '查看财务',     '查看订单支付、分账和结算数据'),
('finance:manage',   '管理财务',     '处理退款异常、SaaS 费用配置'),
('fee-rule:read',    '查看收费规则', '查看停车收费规则配置'),
('fee-rule:write',   '管理收费规则', '创建/编辑收费规则版本');

-- =============================================================================
-- 种子数据：角色-权限映射
-- =============================================================================

-- 超级管理员：拥有全部 18 个权限
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('super_admin', 'user:read'),
('super_admin', 'user:write'),
('super_admin', 'role:read'),
('super_admin', 'tenant:read'),
('super_admin', 'tenant:write'),
('super_admin', 'parking:read'),
('super_admin', 'parking:write'),
('super_admin', 'parking:disable'),
('super_admin', 'device:read'),
('super_admin', 'device:manage'),
('super_admin', 'gate:open'),
('super_admin', 'gate:manual'),
('super_admin', 'record:read'),
('super_admin', 'order:read'),
('super_admin', 'finance:read'),
('super_admin', 'finance:manage'),
('super_admin', 'fee-rule:read'),
('super_admin', 'fee-rule:write');

-- 平台运营：查看运营数据、协助配置、处理服务请求
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('platform_operator', 'user:read'),
('platform_operator', 'role:read'),
('platform_operator', 'tenant:read'),
('platform_operator', 'tenant:write'),
('platform_operator', 'parking:read'),
('platform_operator', 'device:read'),
('platform_operator', 'record:read'),
('platform_operator', 'order:read'),
('platform_operator', 'finance:read'),
('platform_operator', 'fee-rule:read');

-- 客户管理员：管理本租户全部资源
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('customer_admin', 'user:read'),
('customer_admin', 'user:write'),
('customer_admin', 'parking:read'),
('customer_admin', 'parking:write'),
('customer_admin', 'parking:disable'),
('customer_admin', 'device:read'),
('customer_admin', 'gate:open'),
('customer_admin', 'record:read'),
('customer_admin', 'order:read'),
('customer_admin', 'finance:read'),
('customer_admin', 'fee-rule:read'),
('customer_admin', 'fee-rule:write');

-- 停车场管理员：管理授权停车场
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('parking_manager', 'parking:read'),
('parking_manager', 'device:read'),
('parking_manager', 'gate:open'),
('parking_manager', 'gate:manual'),
('parking_manager', 'record:read'),
('parking_manager', 'order:read'),
('parking_manager', 'fee-rule:read'),
('parking_manager', 'fee-rule:write');

-- 财务：查看财务数据，处理退款
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('finance', 'record:read'),
('finance', 'order:read'),
('finance', 'finance:read'),
('finance', 'finance:manage'),
('finance', 'fee-rule:read');

-- 设备运维：设备状态、校时、告警处理
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('device_maintenance', 'device:read'),
('device_maintenance', 'device:manage'),
('device_maintenance', 'gate:open'),
('device_maintenance', 'gate:manual'),
('device_maintenance', 'record:read');

-- 岗亭：实时通行、人工放行、车牌修正
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('booth_operator', 'gate:open'),
('booth_operator', 'gate:manual'),
('booth_operator', 'record:read'),
('booth_operator', 'order:read');


-- ============================================================
-- Migration: V20260711004__tenant_and_registration.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260711005__employee_and_parking_lot.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：客户员工与停车场授权
-- =============================================================================
-- T15｜客户员工与停车场授权
-- 创建最小停车场占位表和员工-停车场授权关联表。
-- 停车场完整字段将在 T18 补充。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 停车场占位表（最小字段集，T18 将扩展）
-- -----------------------------------------------------------------------------
CREATE TABLE parking_lot
(
    id         BIGINT      AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id  BIGINT      NOT NULL COMMENT '所属租户 ID',
    name       VARCHAR(128) NOT NULL COMMENT '停车场名称',
    status     VARCHAR(20) NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场表（占位，T18 扩展）';

-- -----------------------------------------------------------------------------
-- 员工-停车场授权关联表
-- -----------------------------------------------------------------------------
CREATE TABLE employee_parking_lot
(
    id             BIGINT   AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    employee_id    BIGINT   NOT NULL COMMENT '员工 ID（sys_user.id）',
    parking_lot_id BIGINT   NOT NULL COMMENT '停车场 ID（parking_lot.id）',
    created_at     DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_employee_parking (employee_id, parking_lot_id) COMMENT '员工停车场唯一索引',
    INDEX idx_employee_id (employee_id) COMMENT '按员工查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='员工-停车场授权关联表';


-- ============================================================
-- Migration: V20260711006__audit_log.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：高风险操作审计日志表
-- =============================================================================
-- T17｜超级管理员代操作与高风险审计
-- 创建 sys_audit_log 表，用于记录超级管理员代操作及其他高风险操作的审计信息。
-- 记录真实操作人、目标租户/停车场、操作前后值、操作结果和原因。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 审计日志表
-- -----------------------------------------------------------------------------
CREATE TABLE sys_audit_log
(
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       DEFAULT NULL COMMENT '目标租户 ID（操作发生时有效的 tenant_id）',
    target_type         VARCHAR(64)  NOT NULL COMMENT '目标类型（如 parking_lot, fee_rule, order 等）',
    target_id           VARCHAR(128) NOT NULL DEFAULT '' COMMENT '目标业务主键（如停车场 ID、订单号等）',
    action              VARCHAR(64)  NOT NULL COMMENT '操作类型（如 proxy_start, proxy_stop, gate_open, fee_adjust 等）',
    operator_id         BIGINT       NOT NULL COMMENT '真实操作人 ID（sys_user.id，总是实际登录用户）',
    operator_name       VARCHAR(128) NOT NULL DEFAULT '' COMMENT '真实操作人登录账号/显示名',
    target_tenant_id    BIGINT       DEFAULT NULL COMMENT '代操作目标租户 ID（仅在代理模式下有效）',
    is_proxy            TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为代操作：1-是，0-否',
    before_value        TEXT         DEFAULT NULL COMMENT '操作前数据（JSON 格式，可选）',
    after_value         TEXT         DEFAULT NULL COMMENT '操作后数据（JSON 格式，可选）',
    result              VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '操作结果：SUCCESS, FAILED, UNCERTAIN',
    fail_reason         VARCHAR(1000) DEFAULT '' COMMENT '失败原因（result=FAILED 时填写）',
    reason              VARCHAR(500) NOT NULL DEFAULT '' COMMENT '操作原因/备注（由操作人填写）',
    client_ip           VARCHAR(45)  NOT NULL DEFAULT '' COMMENT '操作客户端 IP',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按目标租户查询',
    INDEX idx_target (target_type, target_id) COMMENT '按目标类型+目标ID查询',
    INDEX idx_operator_id (operator_id) COMMENT '按操作人查询',
    INDEX idx_is_proxy (is_proxy) COMMENT '按是否代操作查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询',
    INDEX idx_action (action) COMMENT '按操作类型查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='高风险操作审计日志表';


-- ============================================================
-- Migration: V20260711007__parking_lot_full.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：停车场完整字段、容量审计与状态审计
-- =============================================================================
-- T18｜停车场基础信息、状态与容量
-- 扩展 parking_lot 占位表，新增停车场基础信息字段、容量字段、停用策略字段，
-- 并创建容量变更审计表和状态变更审计表。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 扩展 parking_lot 表
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lot
    ADD COLUMN address              VARCHAR(255) DEFAULT '' COMMENT '地址' AFTER name,
    ADD COLUMN contact_phone        VARCHAR(20)  DEFAULT '' COMMENT '联系电话' AFTER address,
    ADD COLUMN longitude            DECIMAL(10,7) DEFAULT NULL COMMENT '经度（预留）' AFTER contact_phone,
    ADD COLUMN latitude             DECIMAL(10,7) DEFAULT NULL COMMENT '纬度（预留）' AFTER longitude,
    ADD COLUMN total_spaces         INT          NOT NULL DEFAULT 0 COMMENT '总车位数' AFTER latitude,
    ADD COLUMN current_vehicles     INT          NOT NULL DEFAULT 0 COMMENT '当前在场车辆数（只读，由停车记录计算）' AFTER total_spaces,
    ADD COLUMN remaining_spaces     INT          NOT NULL DEFAULT 0 COMMENT '剩余车位数（默认 = total_spaces - current_vehicles，允许人工修正）' AFTER current_vehicles,
    ADD COLUMN payment_mode         VARCHAR(20)  NOT NULL DEFAULT 'PLATFORM' COMMENT '支付模式：PLATFORM-平台统一商户, CUSTOMER-客户独立商户' AFTER remaining_spaces,
    ADD COLUMN image_retention_days INT          NOT NULL DEFAULT 30 COMMENT '抓拍图片保存天数' AFTER payment_mode,
    ADD COLUMN data_retention_days  INT          NOT NULL DEFAULT 365 COMMENT '业务数据保存天数' AFTER image_retention_days,
    ADD COLUMN free_exit_minutes    INT          NOT NULL DEFAULT 15 COMMENT '缴费后免费离场时间（分钟）' AFTER data_retention_days,
    ADD COLUMN manual_release_policy VARCHAR(50) NOT NULL DEFAULT 'ADMIN_ONLY' COMMENT '人工放行策略：ADMIN_ONLY-仅管理员, BOOTH_ALLOWED-岗亭可放行' AFTER free_exit_minutes,
    ADD COLUMN offline_policy       VARCHAR(50)  NOT NULL DEFAULT 'ALLOW_ENTRY_EXIT' COMMENT '离线运行策略：ALLOW_ENTRY_EXIT-允许出入, ALLOW_EXIT_ONLY-只出不进, STRICT-禁止通行' AFTER manual_release_policy,
    -- 停用时的保留范围（JSON 或独立字段，此处用独立字段以便查询）
    ADD COLUMN disable_new_entries  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否允许新车入场：1-允许, 0-禁止' AFTER offline_policy,
    ADD COLUMN disable_payment      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否允许缴费：1-允许, 0-禁止' AFTER disable_new_entries,
    ADD COLUMN disable_exit         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否允许出场：1-允许, 0-禁止' AFTER disable_payment,
    ADD COLUMN disable_auto_gate    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '停用时是否保留自动开闸：1-保留, 0-关闭' AFTER disable_exit,
    ADD COLUMN disable_only_config  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否仅限制后台配置：1-是, 0-否' AFTER disable_auto_gate;

-- -----------------------------------------------------------------------------
-- 2. 容量变更审计表
-- -----------------------------------------------------------------------------
CREATE TABLE parking_lot_capacity_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID',
    field_name     VARCHAR(50)  NOT NULL COMMENT '变更字段名（total_spaces / remaining_spaces）',
    before_value   INT          NOT NULL COMMENT '修改前数值',
    after_value    INT          NOT NULL COMMENT '修改后数值',
    operator_id    BIGINT       NOT NULL COMMENT '操作人 ID（sys_user.id）',
    reason         VARCHAR(255) DEFAULT '' COMMENT '修改原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场容量变更审计日志';

-- -----------------------------------------------------------------------------
-- 3. 状态变更审计表
-- -----------------------------------------------------------------------------
CREATE TABLE parking_lot_status_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID',
    before_status  VARCHAR(20)  NOT NULL COMMENT '修改前状态',
    after_status   VARCHAR(20)  NOT NULL COMMENT '修改后状态',
    operator_id    BIGINT       NOT NULL COMMENT '操作人 ID（sys_user.id）',
    reason         VARCHAR(255) DEFAULT '' COMMENT '操作原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场状态变更审计日志';


-- ============================================================
-- Migration: V20260711008__parking_lane.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：车道表（入口、出口与车道模型）
-- =============================================================================
-- T19｜入口、出口与车道模型
-- 创建 parking_lane 表，支持入口、出口、混合车道模型。
-- 车道编码在停车场内唯一。
-- 当前不包含相机/道闸绑定（T20/T21 负责）。
-- =============================================================================

CREATE TABLE parking_lane
(
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id       BIGINT       NOT NULL COMMENT '所属停车场 ID',
    name                 VARCHAR(128) NOT NULL COMMENT '车道名称',
    code                 VARCHAR(64)  NOT NULL COMMENT '车道编码（停车场内唯一）',
    direction            VARCHAR(20)  NOT NULL DEFAULT 'ENTRY' COMMENT '车道方向：ENTRY-入口, EXIT-出口, MIXED-混合',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    is_key_lane          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为关键车道：1-是, 0-否（关键车道离线可能导致停车场不可用）',
    auto_release_policy  VARCHAR(50)  NOT NULL DEFAULT 'MANUAL' COMMENT '自动放行策略：AUTO-自动放行, MANUAL-人工确认, AFTER_PAY-缴费后自动放行',
    description          VARCHAR(255) DEFAULT '' COMMENT '备注/其他业务参数',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_parking_lot_code (parking_lot_id, code) COMMENT '停车场内车道编码唯一',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_direction (direction) COMMENT '按方向查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车道';


-- ============================================================
-- Migration: V20260711009__device_vendor_model.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：设备厂商、型号与平台设备台账
-- =============================================================================
-- T20｜设备厂商、型号与平台设备台账
-- 创建 device_vendor（设备厂商）、device_model（设备型号）、device（设备台账）表。
-- 设备台账通过 parking_lot_id → tenant_id 推导租户范围，保证数据隔离。
-- device_sn 在同厂商内唯一（UNIQUE(vendor_id, device_sn)）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 设备厂商表
-- -----------------------------------------------------------------------------
CREATE TABLE device_vendor
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '厂商名称（如"臻识"）',
    code        VARCHAR(32)  NOT NULL COMMENT '厂商编码（如 ZHENSHI）',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    description VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_vendor_code (code) COMMENT '厂商编码唯一',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备厂商';

-- -----------------------------------------------------------------------------
-- 2. 设备型号表
-- -----------------------------------------------------------------------------
CREATE TABLE device_model
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    vendor_id   BIGINT       NOT NULL COMMENT '所属厂商 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '型号名称（如"C5H"）',
    code        VARCHAR(32)  NOT NULL COMMENT '型号编码（如 C5H）',
    device_type VARCHAR(20)  NOT NULL DEFAULT 'CAMERA' COMMENT '设备类型：CAMERA-相机, GATE-道闸',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    description VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_vendor_model_code (vendor_id, code) COMMENT '同厂商下型号编码唯一',
    INDEX idx_vendor_id (vendor_id) COMMENT '按厂商查询',
    INDEX idx_device_type (device_type) COMMENT '按设备类型查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备型号';

-- -----------------------------------------------------------------------------
-- 3. 平台设备台账表
-- -----------------------------------------------------------------------------
CREATE TABLE device
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键（平台设备 ID，前端引用此 ID）',
    parking_lot_id BIGINT      NOT NULL COMMENT '所属停车场 ID',
    lane_id       BIGINT       DEFAULT NULL COMMENT '绑定车道 ID（T21 负责绑定，当前可为空）',
    vendor_id     BIGINT       NOT NULL COMMENT '设备厂商 ID',
    model_id      BIGINT       NOT NULL COMMENT '设备型号 ID',
    name          VARCHAR(128) NOT NULL COMMENT '设备名称（运营可读）',
    code          VARCHAR(64)  NOT NULL COMMENT '设备业务编码（停车场内唯一）',
    device_sn     VARCHAR(128) NOT NULL COMMENT '厂商设备序列号（可信记录，禁止前端传入）',
    device_type   VARCHAR(20)  NOT NULL DEFAULT 'CAMERA' COMMENT '设备类型：CAMERA-相机, GATE-道闸',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    capabilities  VARCHAR(500) DEFAULT '' COMMENT '设备能力（JSON 或逗号分隔，如 RECOGNIZE,CAPTURE,GATE_OPEN）',
    description   VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_parking_lot_code (parking_lot_id, code) COMMENT '停车场内设备编码唯一',
    UNIQUE INDEX uq_vendor_device_sn (vendor_id, device_sn) COMMENT '同厂商下设备 SN 唯一',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_lane_id (lane_id) COMMENT '按车道查询',
    INDEX idx_vendor_id (vendor_id) COMMENT '按厂商查询',
    INDEX idx_model_id (model_id) COMMENT '按型号查询',
    INDEX idx_device_type (device_type) COMMENT '按设备类型查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台设备台账';

-- -----------------------------------------------------------------------------
-- 4. 种子数据：臻识 C5H（当前唯一真机确认的厂商/型号）
-- -----------------------------------------------------------------------------
INSERT INTO device_vendor (name, code, status, description) VALUES
('臻识', 'ZHENSHI', 'ENABLED', '臻识科技，当前已确认真机厂商'),
('信路通', 'XINLUTONG', 'ENABLED', '信路通，具体型号待联调确认（PENDING_DEVICE_VERIFICATION）');

-- 获取臻识 vendor_id 并插入 C5H 型号
INSERT INTO device_model (vendor_id, name, code, device_type, status, description)
SELECT v.id, 'C5H', 'C5H', 'CAMERA', 'ENABLED', '臻识 C5H 车牌识别相机，当前唯一真机确认型号'
FROM device_vendor v WHERE v.code = 'ZHENSHI';


-- ============================================================
-- Migration: V20260711010__lane_device_binding.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：设备-车道绑定约束与逻辑道闸执行设备
-- =============================================================================
-- T21｜相机、逻辑道闸与车道绑定
-- 1. 添加 device.executor_device_id — GATE 设备的执行相机引用
-- 2. 添加 UNIQUE INDEX uq_lane_device_type — 同一车道每种设备类型最多一台
-- 3. 添加 FK 约束确保 executor_device 存在且类型为 CAMERA（应用层校验）
-- 4. lane_id IS NULL 时 UNIQUE 索引不生效（MySQL NULL 允许重复）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 添加逻辑道闸执行设备字段（执行开闸的相机设备 ID）
--    GATE 设备的 executor_device_id 指向实际控制继电器开闸的 CAMERA 设备
--    CAMERA 设备的 executor_device_id 为 NULL（自己就是执行者）
-- -----------------------------------------------------------------------------
ALTER TABLE device
    ADD COLUMN executor_device_id BIGINT DEFAULT NULL COMMENT '执行设备 ID（GATE 指向实际开闸的 CAMERA，CAMERA 为 NULL 即自身）' AFTER lane_id;

-- -----------------------------------------------------------------------------
-- 2. 同一车道每种设备类型唯一约束
--    仅在有 lane_id 的行生效（MySQL UNIQUE 索引允许多个 NULL）
-- -----------------------------------------------------------------------------
CREATE UNIQUE INDEX uq_lane_device_type ON device (lane_id, device_type);

-- -----------------------------------------------------------------------------
-- 3. 索引
-- -----------------------------------------------------------------------------
CREATE INDEX idx_executor_device_id ON device (executor_device_id);


-- ============================================================
-- Migration: V20260711011__device_status_snapshot.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：设备状态快照表
-- =============================================================================
-- T24｜设备状态查询、快照与轮询
-- 持久化每次对 Device Access 状态查询的结果，支持：
-- 1. 最新状态快照读取（避免每次实时调用 DA）
-- 2. 历史查询记录（审计/排障）
-- 3. 过期状态时间标识（前端可显示"最后查询于 X 秒前"）
-- =============================================================================

CREATE TABLE device_status_snapshot
(
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    device_id           BIGINT       NOT NULL COMMENT '平台设备 ID（关联 device.id）',
    device_sn           VARCHAR(128) NOT NULL COMMENT '查询时使用的厂商序列号（快照冗余，便于排障）',
    online              TINYINT(1)   DEFAULT NULL COMMENT '设备是否在线（DA 返回值）',
    last_online_time    DATETIME     DEFAULT NULL COMMENT '最近在线时间（DA 返回值）',
    gate_status         VARCHAR(64)  DEFAULT NULL COMMENT '道闸杆状态（DA 返回值，仅 GATE 有效）',
    gate_connect_status VARCHAR(64)  DEFAULT NULL COMMENT '道闸连接状态（DA 返回值，仅 GATE 有效）',
    status_description  VARCHAR(255) DEFAULT NULL COMMENT '设备状态描述（DA 返回的 status 字段）',
    query_success       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '本次查询是否成功（1=成功收到 DA 数据）',
    error_code          VARCHAR(32)  DEFAULT NULL COMMENT '失败时的错误码（如 404/503/UNCERTAIN）',
    error_message       VARCHAR(500) DEFAULT NULL COMMENT '失败时的错误消息',
    collected_at        DATETIME     NOT NULL COMMENT '状态采集时间（调用 DA 的时间点）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',

    INDEX idx_device_id (device_id) COMMENT '按设备查询快照',
    INDEX idx_device_id_collected (device_id, collected_at DESC) COMMENT '查询某设备最新快照',
    INDEX idx_query_success (query_success) COMMENT '按查询成功/失败筛选',
    INDEX idx_collected_at (collected_at) COMMENT '按采集时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备状态快照';


-- ============================================================
-- Migration: V20260711012__fix_role_permissions.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：校正角色权限矩阵（FIX-12）
-- =============================================================================
-- 根据需求规格说明书和角色职责，补充以下缺失权限：
--   1. customer_admin 缺少 device:manage（应能管理设备台账）
--   2. parking_manager 缺少 parking:write（应能管理授权停车场的车道/设备）
-- =============================================================================
-- 使用 INSERT IGNORE 确保重复部署安全（已存在则跳过）
-- =============================================================================

-- customer_admin：补充 device:manage（FIX-12-R1）
INSERT IGNORE INTO sys_role_permission (role_code, permission_code, created_at) VALUES
('customer_admin', 'device:manage', NOW());

-- parking_manager：补充 parking:write（FIX-12-R2）
INSERT IGNORE INTO sys_role_permission (role_code, permission_code, created_at) VALUES
('parking_manager', 'parking:write', NOW());


-- ============================================================
-- Migration: V20260711013__credential_status.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：凭据状态 — 强制首次修改密码（FIX-04）
-- =============================================================================
-- 为 sys_user 增加 credential_status 字段，标记默认密码是否已修改。
-- 存量管理员凭据标记为 EXPIRED，生产环境必须通过配置提供初始密码。
-- =============================================================================

-- 新增凭据状态列（默认 ACTIVE 表示无需强制修改）
ALTER TABLE sys_user
    ADD COLUMN credential_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    COMMENT '凭据状态：ACTIVE-正常, EXPIRED-需强制修改密码';

-- 将默认超级管理员的凭据标记为 EXPIRED（固定密码 admin123 已公开）
UPDATE sys_user
SET credential_status = 'EXPIRED'
WHERE username = 'admin'
  AND credential_status = 'ACTIVE';


-- ============================================================
-- Migration: V20260711014__device_verification_status.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：设备厂商/型号验证状态（FIX-15）
-- =============================================================================
-- 为 device_vendor 和 device_model 增加 verification_status 字段，
-- 区分"配置存在"与"真机已验证"，防止未验证型号被标记为已具备冻结能力。
-- V01–V04 真机验证尚未完成，存量种子数据标记为 PENDING。
-- =============================================================================

-- 设备厂商：新增验证状态
ALTER TABLE device_vendor
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT '验证状态：VERIFIED-真机已验证, PENDING-待验证, UNVERIFIED-未验证'
    AFTER status;

-- 设备型号：新增验证状态
ALTER TABLE device_model
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT '验证状态：VERIFIED-真机已验证, PENDING-待验证, UNVERIFIED-未验证'
    AFTER status;

-- 存量种子数据标记为 PENDING（V01–V04 真机验证尚未完成）
UPDATE device_vendor SET verification_status = 'PENDING';
UPDATE device_model SET verification_status = 'PENDING';


-- ============================================================
-- Migration: V20260712015__recognition_event_log.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：识别事件日志表（T28｜平台内部标准识别事件与测试入口）
-- =============================================================================
-- 用途：记录平台内部识别事件（Mock、人工触发、未来 Device Access），
--       提供事件追溯和审计基础。
-- 幂等由 T29 在业务层通过 event_id 唯一约束实现。
-- =============================================================================

CREATE TABLE recognition_event_log
(
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    event_id         VARCHAR(64)  NOT NULL COMMENT '事件唯一 ID（UUID v4）',
    tenant_id        BIGINT       NOT NULL COMMENT '租户 ID（后端从设备推导）',
    parking_lot_id   BIGINT       NOT NULL COMMENT '停车场 ID（后端推导）',
    lane_id          BIGINT       DEFAULT NULL COMMENT '车道 ID',
    device_id        BIGINT       NOT NULL COMMENT '平台设备主键（相机设备）',
    plate_number     VARCHAR(32)  NOT NULL COMMENT '车牌号',
    direction        VARCHAR(20)  NOT NULL COMMENT '方向：ENTRY-入场, EXIT-出场',
    event_time       DATETIME     NOT NULL COMMENT '事件发生时间',
    confidence       INT          DEFAULT NULL COMMENT '识别置信度 0-100',
    image_path       VARCHAR(512) DEFAULT NULL COMMENT '全景图路径占位',
    plate_image_path VARCHAR(512) DEFAULT NULL COMMENT '车牌特写图路径占位',
    source           VARCHAR(32)  NOT NULL COMMENT '事件来源：MANUAL/MOCK/DEVICE_ACCESS',
    raw_data         TEXT         DEFAULT NULL COMMENT '原始数据摘要（调试用，生产可裁剪）',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',

    INDEX idx_event_id (event_id) COMMENT '按事件 ID 查询',
    INDEX idx_tenant_parking (tenant_id, parking_lot_id) COMMENT '租户+停车场数据隔离',
    INDEX idx_parking_lot (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_device_id (device_id) COMMENT '按设备查询',
    INDEX idx_plate_number (plate_number) COMMENT '按车牌查询',
    INDEX idx_direction (direction) COMMENT '按方向查询',
    INDEX idx_event_time (event_time) COMMENT '按时间范围查询',
    INDEX idx_source (source) COMMENT '按来源查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='识别事件日志';


-- ============================================================
-- Migration: V20260712016__recognition_event_log_t29.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：识别事件日志表增强（T29｜识别事件校验、标准化与幂等）
-- =============================================================================
-- 内容：
--   1. 添加处理状态字段（status）— 追踪事件处理生命周期
--   2. 添加失败原因字段（failure_reason）— 记录校验/处理失败详情
--   3. 添加标准化车牌字段（standardized_plate）— 存储标准化后的车牌号
--   4. 添加厂商事件 ID 字段（vendor_event_id）— Device Access 对接时使用
--   5. 在 event_id 上添加 UNIQUE 索引 — 业务层幂等的数据库最终保障
-- =============================================================================

-- 1. 新增字段
ALTER TABLE recognition_event_log
    ADD COLUMN status             VARCHAR(32)  NOT NULL DEFAULT 'RECEIVED'  COMMENT '处理状态：RECEIVED/PROCESSING/PROCESSED/FAILED',
    ADD COLUMN failure_reason     VARCHAR(512) DEFAULT NULL                 COMMENT '失败原因（校验失败、设备不存在等）',
    ADD COLUMN standardized_plate VARCHAR(32)  DEFAULT NULL                 COMMENT '标准化车牌号（去空格、统一大写）',
    ADD COLUMN vendor_event_id    VARCHAR(128) DEFAULT NULL                 COMMENT '厂商原始事件ID（Device Access 对接时使用）';

-- 2. 业务幂等：event_id 唯一约束
--    MySQL 的 UNIQUE INDEX 同时提供唯一约束和查询优化；
--    原有的 idx_event_id (普通索引) 保留兼容（Flyway 不删除已发布迁移的对象）。
ALTER TABLE recognition_event_log
    ADD UNIQUE INDEX uk_event_id (event_id);

-- 3. 新增索引：按状态查询（岗亭实时列表、补偿重试）
ALTER TABLE recognition_event_log
    ADD INDEX idx_status (status);


-- ============================================================
-- Migration: V20260712017__parking_record.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：停车记录表（T30｜入场通行与停车记录主链路）
-- =============================================================================
-- 内容：
--   1. 创建 parking_record 表 — 记录车辆入场、在场状态
--   2. 功能性唯一索引 — 同车同停车场仅一条有效在场记录（数据库级保障）
--   3. 辅助索引 — 按状态/车牌/事件查询
-- =============================================================================

-- 1. 停车记录表
CREATE TABLE parking_record (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL                COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL                COMMENT '停车场 ID',
    lane_id             BIGINT       DEFAULT NULL            COMMENT '入场车道 ID',
    device_id           BIGINT       DEFAULT NULL            COMMENT '入场相机设备 ID',
    standardized_plate  VARCHAR(32)  NOT NULL                COMMENT '标准化车牌号',
    entry_event_id      BIGINT       DEFAULT NULL            COMMENT '关联的入场识别事件 ID（recognition_event_log.id）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PARKING' COMMENT '状态：PARKING-在场, COMPLETED-已完成, CANCELLED-已作废',
    entry_time          DATETIME     NOT NULL                COMMENT '入场时间',
    exit_time           DATETIME     DEFAULT NULL            COMMENT '出场时间',
    fee_rule_version    VARCHAR(64)  DEFAULT NULL            COMMENT '收费规则版本（占位，T34 实现）',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车记录表';

-- 2. 功能性唯一索引：同车同停车场仅允许一条 PARKING 记录
--    利用 MySQL 8 功能索引，仅对 status='PARKING' 的行生效；
--    CASE WHEN 对非 PARKING 行返回 NULL，MySQL 中多个 NULL 不冲突。
CREATE UNIQUE INDEX uk_active_parking
    ON parking_record(parking_lot_id, standardized_plate,
        (CASE WHEN status = 'PARKING' THEN 1 ELSE NULL END));

-- 3. 辅助索引
CREATE INDEX idx_parking_record_lot_status ON parking_record(parking_lot_id, status);
CREATE INDEX idx_parking_record_plate      ON parking_record(standardized_plate);
CREATE INDEX idx_parking_record_entry_event ON parking_record(entry_event_id);


-- ============================================================
-- Migration: V20260712018__billing_rule.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：收费规则主表
-- =============================================================================
-- T34｜收费规则与版本 CRUD
-- 创建收费规则主表，支持多套规则、规则启停和基本信息管理。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 收费规则主表
-- -----------------------------------------------------------------------------
CREATE TABLE billing_rule
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id      BIGINT       NOT NULL COMMENT '所属租户 ID',
    parking_lot_id BIGINT       NOT NULL COMMENT '所属停车场 ID',
    name           VARCHAR(100) NOT NULL COMMENT '规则名称',
    description    VARCHAR(500) DEFAULT '' COMMENT '规则描述',
    rule_type      VARCHAR(20)  NOT NULL DEFAULT 'HOURLY' COMMENT '规则类型：HOURLY-按时长, FIXED-固定金额, NO_FEE-免费',
    status         VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    is_default     TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为默认规则：1-是, 0-否',
    created_by     BIGINT       NOT NULL COMMENT '创建人 ID',
    updated_by     BIGINT       COMMENT '修改人 ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按状态查询',
    UNIQUE KEY uk_parking_lot_name (parking_lot_id, name) COMMENT '同一停车场规则名称唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则主表';

-- -----------------------------------------------------------------------------
-- 2. 收费规则版本表（快照存储，版本不可修改）
-- -----------------------------------------------------------------------------
CREATE TABLE billing_rule_version
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    rule_id        BIGINT       NOT NULL COMMENT '所属规则 ID',
    tenant_id      BIGINT       NOT NULL COMMENT '所属租户 ID（冗余字段，加速查询）',
    parking_lot_id BIGINT       NOT NULL COMMENT '所属停车场 ID（冗余字段）',
    version        INT          NOT NULL COMMENT '版本号（递增）',
    is_active      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为当前生效版本：1-是, 0-否',
    -- 计费配置（JSON 存储灵活计费规则）
    config         JSON         NOT NULL COMMENT '计费配置 JSON',
    -- 计费配置摘要（用于快速展示，不解析 JSON）
    config_summary VARCHAR(255) DEFAULT '' COMMENT '计费配置摘要，如"首2小时5元，后每小时3元，封顶30元"',
    -- 各字段明细（方便查询和统计）
    free_minutes   INT          NOT NULL DEFAULT 0 COMMENT '免费时长（分钟）',
    first_period   INT          NOT NULL DEFAULT 0 COMMENT '首时段时长（分钟）',
    first_amount   INT          NOT NULL DEFAULT 0 COMMENT '首时段金额（分）',
    unit_period    INT          NOT NULL DEFAULT 0 COMMENT '续费单位时长（分钟）',
    unit_amount    INT          NOT NULL DEFAULT 0 COMMENT '续费单位金额（分）',
    daily_cap      INT          NOT NULL DEFAULT 0 COMMENT '单日封顶金额（分），0表示不封顶',
    max_amount     INT          NOT NULL DEFAULT 0 COMMENT '最大金额（分），0表示不封顶',
    effective_from DATETIME     COMMENT '生效时间（可预设计费规则生效时间）',
    effective_to   DATETIME     COMMENT '失效时间（空表示永久生效）',
    created_by     BIGINT       NOT NULL COMMENT '创建人 ID',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_rule_id (rule_id) COMMENT '按规则查询',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_is_active (is_active) COMMENT '按生效状态查询',
    UNIQUE KEY uk_rule_version (rule_id, version) COMMENT '同一规则版本号唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则版本表';

-- -----------------------------------------------------------------------------
-- 3. 规则切换审计表
-- -----------------------------------------------------------------------------
CREATE TABLE billing_rule_switch_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID',
    tenant_id      BIGINT       NOT NULL COMMENT '租户 ID',
    operator_id    BIGINT       NOT NULL COMMENT '操作人 ID',
    operator_name  VARCHAR(100) DEFAULT '' COMMENT '操作人名称',
    before_rule_id BIGINT       COMMENT '切换前规则 ID',
    after_rule_id  BIGINT       NOT NULL COMMENT '切换后规则 ID',
    apply_to_existing TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否影响已在场车辆：1-是, 0-否（仅对新入场生效）',
    reason         VARCHAR(255) DEFAULT '' COMMENT '切换原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则切换审计日志';

-- ============================================================
-- Migration: V20260712019__wx_user_and_vehicle.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：微信用户、车辆与车牌绑定
-- =============================================================================
-- T40｜微信用户、手机号、车辆与车牌绑定
-- 实现微信登录适配、手机号绑定、车辆/车牌管理和绑定验证策略。
-- 前置依赖：T12（平台账号登录）、T16（可信租户上下文）
-- 外部依赖：B05（微信小程序 AppId/Secret）— 使用 Mock 登录完成平台逻辑
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 微信用户表
-- 存储通过微信授权登录的车主账户。
-- 同一微信 OpenId 在系统中唯一，一个微信用户可绑定多个车牌。
-- -----------------------------------------------------------------------------
CREATE TABLE wx_user
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    openid        VARCHAR(128) NOT NULL COMMENT '微信 OpenId（唯一标识）',
    unionid       VARCHAR(128) DEFAULT NULL COMMENT '微信 UnionId（跨应用唯一，可为空）',
    nickname      VARCHAR(100) DEFAULT '' COMMENT '微信昵称（脱敏展示）',
    avatar_url    VARCHAR(500) DEFAULT '' COMMENT '微信头像 URL',
    phone         VARCHAR(20)  DEFAULT NULL COMMENT '绑定手机号（脱敏展示）',
    phone_verified TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '手机号是否已验证：0-未验证, 1-已验证',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, DISABLED-禁用',
    last_login_at DATETIME     DEFAULT NULL COMMENT '最后登录时间',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_openid (openid) COMMENT 'OpenId 唯一索引',
    UNIQUE KEY uk_unionid (unionid) COMMENT 'UnionId 唯一索引（允许 NULL）',
    UNIQUE KEY uk_phone (phone) COMMENT '手机号唯一索引（允许 NULL）',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_created_at (created_at) COMMENT '按创建时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='微信用户表（车主端）';

-- -----------------------------------------------------------------------------
-- 车辆表
-- 存储车主名下的车辆信息。一辆车可被多个微信用户绑定（需配置策略）。
-- -----------------------------------------------------------------------------
CREATE TABLE vehicle
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    vehicle_plate VARCHAR(20)  NOT NULL COMMENT '车牌号（标准格式，如：京A12345）',
    vehicle_type  VARCHAR(20)  NOT NULL DEFAULT 'SMALL' COMMENT '车辆类型：SMALL-小型车, LARGE-大型车, NEW_ENERGY-新能源车, OTHER-其他',
    brand         VARCHAR(100) DEFAULT '' COMMENT '车辆品牌',
    color         VARCHAR(20)  DEFAULT '' COMMENT '车辆颜色',
    owner_name    VARCHAR(100) DEFAULT '' COMMENT '车主姓名（可选，用于月卡等场景）',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, DISABLED-禁用',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_plate (vehicle_plate) COMMENT '车牌号唯一索引',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_created_at (created_at) COMMENT '按创建时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆表';

-- -----------------------------------------------------------------------------
-- 车牌绑定表
-- 建立微信用户与车辆的绑定关系，支持多绑定策略。
-- -----------------------------------------------------------------------------
CREATE TABLE plate_binding
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    wx_user_id      BIGINT       NOT NULL COMMENT '微信用户 ID',
    vehicle_id      BIGINT       NOT NULL COMMENT '车辆 ID',
    binding_type    VARCHAR(20)  NOT NULL DEFAULT 'OWNER' COMMENT '绑定类型：OWNER-车主本人绑定, AUTHORIZED-授权绑定',
    verify_method   VARCHAR(20)  NOT NULL DEFAULT 'PLATE_ONLY' COMMENT '验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核',
    verify_status   VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '验证状态：PENDING-待验证, APPROVED-已通过, REJECTED-已拒绝',
    is_default      TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否默认车牌：0-否, 1-是',
    remark          VARCHAR(500) DEFAULT '' COMMENT '备注/审核说明',
    verified_at     DATETIME     DEFAULT NULL COMMENT '验证通过时间',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_user_vehicle (wx_user_id, vehicle_id) COMMENT '同一用户不能重复绑定同一辆车',
    INDEX idx_wx_user_id (wx_user_id) COMMENT '按微信用户查询',
    INDEX idx_vehicle_id (vehicle_id) COMMENT '按车辆查询',
    INDEX idx_verify_status (verify_status) COMMENT '按验证状态查询',
    INDEX idx_is_default (is_default) COMMENT '按默认车牌查询',
    INDEX idx_created_at (created_at) COMMENT '按创建时间查询',
    CONSTRAINT fk_plate_binding_wx_user FOREIGN KEY (wx_user_id) REFERENCES wx_user (id) ON DELETE CASCADE,
    CONSTRAINT fk_plate_binding_vehicle FOREIGN KEY (vehicle_id) REFERENCES vehicle (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车牌绑定表';

-- -----------------------------------------------------------------------------
-- 车辆绑定策略配置表（按租户/停车场配置）
-- 控制同一车牌是否允许多账号绑定、默认验证方式等。
-- -----------------------------------------------------------------------------
CREATE TABLE binding_policy
(
    id                    BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id             BIGINT       DEFAULT NULL COMMENT '租户 ID（NULL 表示平台默认策略）',
    parking_lot_id        BIGINT       DEFAULT NULL COMMENT '停车场 ID（NULL 表示租户默认策略）',
    multi_account_mode    VARCHAR(20)  NOT NULL DEFAULT 'SINGLE_ACCOUNT' COMMENT '多账号模式：SINGLE_ACCOUNT-单一微信账号绑定, AUTHORIZED_MULTI-主车主授权其他账号, ALLOW_MULTI-允许多账号直接绑定',
    default_verify_method VARCHAR(20)  NOT NULL DEFAULT 'PLATE_ONLY' COMMENT '默认验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核',
    max_bindings_per_user INT          NOT NULL DEFAULT 5 COMMENT '单个用户最大绑定车辆数',
    max_users_per_plate   INT          NOT NULL DEFAULT 1 COMMENT '单个车牌最大绑定用户数',
    allow_change_plate    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否允许更换车牌',
    status                VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-禁用',
    created_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆绑定策略配置表';

-- -----------------------------------------------------------------------------
-- 初始化平台默认绑定策略
-- -----------------------------------------------------------------------------
INSERT INTO binding_policy (tenant_id, parking_lot_id, multi_account_mode, default_verify_method, max_bindings_per_user, max_users_per_plate, allow_change_plate, status)
VALUES (NULL, NULL, 'SINGLE_ACCOUNT', 'PLATE_ONLY', 5, 1, 1, 'ENABLED');

-- ============================================================
-- Migration: V20260712020__device_command_audit.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：设备命令调用审计表（P001）
-- =============================================================================
-- 目标：保留 Device Access v1.0 统一命令模型的审计结构，当前不实现真实开闸调用。
-- 适用：开闸（OPEN_GATE）、关闸（CLOSE_GATE）、抓拍（SNAPSHOT）、校时（SYNC_TIME）等
--       所有向 Device Access 发出的控制命令。
-- 约束：
--   1. device_id 来自平台可信设备记录，device_sn 冗余存储便于排障
--   2. command_id 为 v1.0 命令 ID，当前占位阶段可为空
--   3. previous_command_id 用于人工再次开闸/重试时关联前次命令
--   4. uncertain 显式标记不确定状态，禁止自动重试
-- =============================================================================

CREATE TABLE device_command_audit
(
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    command_id          VARCHAR(64)  DEFAULT NULL COMMENT 'v1.0 命令 ID（全局唯一，占位阶段可为空）',
    tenant_id           BIGINT       DEFAULT NULL COMMENT '目标租户 ID',
    parking_lot_id      BIGINT       NOT NULL COMMENT '目标停车场 ID',
    lane_id             BIGINT       DEFAULT NULL COMMENT '目标车道 ID',
    device_id           BIGINT       NOT NULL COMMENT '平台设备 ID（关联 device.id）',
    device_sn           VARCHAR(128) NOT NULL COMMENT '调用时使用的厂商序列号（可信记录冗余，便于排障）',
    command_type        VARCHAR(32)  NOT NULL COMMENT '命令类型：OPEN_GATE, CLOSE_GATE, SNAPSHOT, REBOOT, SYNC_TIME 等',
    source              VARCHAR(32)  NOT NULL DEFAULT 'MANUAL' COMMENT '操作来源：SYSTEM-系统自动, MANUAL-人工操作, AUTO_EXIT-自动出场, COMPENSATION-补偿',
    operator_id         BIGINT       DEFAULT NULL COMMENT '操作人 ID（sys_user.id；SYSTEM 来源时为 0 或 null）',
    operator_name       VARCHAR(128) DEFAULT '' COMMENT '操作人名称/账号',
    reason              VARCHAR(500) DEFAULT '' COMMENT '操作原因/备注（由操作人或业务填写）',
    previous_command_id VARCHAR(64)  DEFAULT NULL COMMENT '前次命令 ID（人工再次开闸、重试或关联补偿时填写）',
    status              VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '命令状态：PENDING, SUCCESS, FAILED, UNCERTAIN, REJECTED, NOT_IMPLEMENTED',
    uncertain           TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否处于 UNCERTAIN 状态（1=是，0=否）',
    error_code          VARCHAR(32)  DEFAULT NULL COMMENT '错误码（DA 返回或平台错误码）',
    error_message       VARCHAR(500) DEFAULT NULL COMMENT '错误消息',
    request_payload     TEXT         DEFAULT NULL COMMENT '请求报文/上下文（JSON，用于排障和复核）',
    response_payload    TEXT         DEFAULT NULL COMMENT '响应报文/结果（JSON）',
    issued_at           DATETIME     DEFAULT NULL COMMENT '命令发出时间',
    completed_at        DATETIME     DEFAULT NULL COMMENT '命令完成/最终状态确认时间',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',

    INDEX idx_device_id (device_id) COMMENT '按设备查询审计',
    INDEX idx_device_id_created (device_id, created_at DESC) COMMENT '查询某设备最近审计',
    INDEX idx_command_id (command_id) COMMENT '按命令 ID 查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按命令状态筛选',
    INDEX idx_uncertain (uncertain) COMMENT '按 UNCERTAIN 状态筛选',
    INDEX idx_created_at (created_at) COMMENT '按时间范围查询',
    INDEX idx_previous_command_id (previous_command_id) COMMENT '按前次命令 ID 查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备命令调用审计表';


-- ============================================================
-- Migration: V20260713001__duplicate_entry_policy.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：重复入场处理策略（P003）
-- =============================================================================
-- 内容：
--   1. parking_lot 表新增 duplicate_entry_policy 字段
--   2. parking_record 表新增 entry_image_path 字段（UPDATE 策略时保存最新抓拍）
--   3. 创建异常重复入场记录表 duplicate_entry_log（供运营处置）
-- =============================================================================

-- 1. 停车场重复入场策略配置
--    REJECT: 拒绝并记录异常（默认，兼容现有行为）
--    UPDATE: 更新原记录入场时间和抓拍
--    EXCEPTION: 创建异常待处理记录，不阻止入场
ALTER TABLE parking_lot
    ADD COLUMN duplicate_entry_policy VARCHAR(20) NOT NULL DEFAULT 'REJECT'
        COMMENT '重复入场策略：REJECT-拒绝, UPDATE-更新原记录, EXCEPTION-创建异常记录';

-- 2. 停车记录新增入场抓拍路径（UPDATE 策略时保存最新图片）
ALTER TABLE parking_record
    ADD COLUMN entry_image_path VARCHAR(500) DEFAULT NULL COMMENT '入场抓拍图片路径（UPDATE 策略时更新）';

-- 3. 异常重复入场记录表（供运营人员查看和处置）
CREATE TABLE duplicate_entry_log (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL                COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL                COMMENT '停车场 ID',
    lane_id             BIGINT       DEFAULT NULL            COMMENT '车道 ID',
    device_id           BIGINT       DEFAULT NULL            COMMENT '设备 ID',
    standardized_plate  VARCHAR(32)  NOT NULL                COMMENT '标准化车牌号',
    existing_record_id  BIGINT       NOT NULL                COMMENT '关联的已有停车记录 ID',
    strategy            VARCHAR(20)  NOT NULL                COMMENT '执行策略：REJECT/UPDATE/EXCEPTION',
    action              VARCHAR(50)  NOT NULL                COMMENT '执行动作：REJECTED-已拒绝, UPDATED-已更新, EXCEPTION_CREATED-已创建异常',
    reason              VARCHAR(500) DEFAULT NULL            COMMENT '处置说明/原因',
    entry_event_id      BIGINT       DEFAULT NULL            COMMENT '关联的入场识别事件 ID',
    image_path          VARCHAR(500) DEFAULT NULL            COMMENT '本次抓拍图片路径',
    confidence          INT          DEFAULT NULL            COMMENT '识别置信度',
    created_at          DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_duplicate_entry_lot_plate (parking_lot_id, standardized_plate),
    INDEX idx_duplicate_entry_record (existing_record_id),
    INDEX idx_duplicate_entry_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常重复入场记录表';


-- ============================================================
-- Migration: V20260714001__exit_flow_skeleton.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：出口识别与出场流程骨架（P004）
-- =============================================================================
-- 内容：
--   1. 创建 parking_order 表 —— 停车订单骨架，后续 P008 扩展状态机
--   2. 创建 exit_record 表 —— 出场记录，与 parking_record / recognition_event_log 关联
--   3. parking_record 表新增 exit_event_id 字段 —— 出场事件可追溯
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 停车订单表（P004 骨架，P008 将完善状态机、支付单关联、回调幂等）
-- -----------------------------------------------------------------------------
CREATE TABLE parking_order
(
    id               BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id        BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id   BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id BIGINT      NOT NULL COMMENT '关联停车记录 ID',
    plate_number     VARCHAR(32)  NOT NULL COMMENT '车牌号（标准化后）',
    amount_cents     INT          NOT NULL DEFAULT 0 COMMENT '订单金额（分），禁止负值',
    status           VARCHAR(32)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING-待支付, PAID-已支付, COMPLETED-已完成, CANCELLED-已取消',
    pay_time         DATETIME     DEFAULT NULL COMMENT '支付时间',
    exit_time        DATETIME     DEFAULT NULL COMMENT '出场时间（订单完成时）',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_parking_record_id (parking_record_id) COMMENT '按停车记录查询',
    INDEX idx_plate_number (plate_number) COMMENT '按车牌查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车订单表（P004 骨架）';

-- -----------------------------------------------------------------------------
-- 2. 出场记录表
-- -----------------------------------------------------------------------------
CREATE TABLE exit_record
(
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id   BIGINT       DEFAULT NULL COMMENT '关联停车记录 ID（NO_RECORD 时为空）',
    exit_event_id       BIGINT       DEFAULT NULL COMMENT '关联的出场识别事件 ID（recognition_event_log.id）',
    lane_id             BIGINT       DEFAULT NULL COMMENT '出场车道 ID',
    device_id           BIGINT       DEFAULT NULL COMMENT '出场相机设备 ID',
    standardized_plate  VARCHAR(32)  NOT NULL COMMENT '标准化车牌号',
    exit_time           DATETIME     NOT NULL COMMENT '出场时间',
    fee_cents           INT          NOT NULL DEFAULT 0 COMMENT '计算费用（分）',
    paid_cents          INT          NOT NULL DEFAULT 0 COMMENT '已支付金额（分）',
    release_decision    VARCHAR(32)  NOT NULL COMMENT '放行决策：PAID-已支付放行, ZERO_FEE-零元放行, UNAUTHORIZED-授权放行, PENDING_PAYMENT-待支付不放行, NO_RECORD-无在场记录, EXCEPTION-异常',
    order_id            BIGINT       DEFAULT NULL COMMENT '关联订单 ID',
    reason              VARCHAR(500) DEFAULT NULL COMMENT '决策原因/备注',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_exit_record_lot_plate (parking_lot_id, standardized_plate) COMMENT '按停车场+车牌查询',
    INDEX idx_exit_record_record (parking_record_id) COMMENT '按停车记录查询',
    INDEX idx_exit_record_event (exit_event_id) COMMENT '按出场事件查询',
    INDEX idx_exit_record_decision (release_decision) COMMENT '按放行决策查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='出场记录表';

-- -----------------------------------------------------------------------------
-- 3. 停车记录新增出场事件关联
-- -----------------------------------------------------------------------------
ALTER TABLE parking_record
    ADD COLUMN exit_event_id BIGINT DEFAULT NULL COMMENT '关联的出场识别事件 ID（recognition_event_log.id）';


-- ============================================================
-- Migration: V20260715001__billing_rule_recalc_log.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：收费规则切换重新计算审计日志（P006）
-- =============================================================================
-- 当 applyToExisting=true 时，对当前在场停车记录按新规则重新计算费用并记录。
-- =============================================================================

CREATE TABLE billing_rule_recalc_log
(
    id                BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id         BIGINT       NOT NULL COMMENT '租户 ID',
    parking_lot_id    BIGINT       NOT NULL COMMENT '停车场 ID',
    parking_record_id BIGINT       NOT NULL COMMENT '停车记录 ID',
    plate_number      VARCHAR(32)  NOT NULL COMMENT '车牌号',
    rule_version_id   BIGINT       NOT NULL COMMENT '切换后的规则版本 ID',
    fee_cents         INT          NOT NULL DEFAULT 0 COMMENT '重新计算时的费用（分）',
    recalc_time       DATETIME     NOT NULL COMMENT '重新计算时间',
    operator_id       BIGINT       NOT NULL COMMENT '操作人 ID',
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_parking_record_id (parking_record_id) COMMENT '按停车记录查询',
    INDEX idx_rule_version_id (rule_version_id) COMMENT '按规则版本查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则切换重新计算审计日志';


-- ============================================================
-- Migration: V20260716001__monitor_alert.sql
-- ============================================================
CREATE TABLE monitor_alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    tenant_id       BIGINT NOT NULL COMMENT '租户 ID',
    parking_lot_id  BIGINT NOT NULL COMMENT '停车场 ID',
    alert_type      VARCHAR(32) NOT NULL COMMENT '异常类型：DEVICE_OFFLINE-设备离线, LOT_FULL-车位已满, LOT_DISABLED-停车场停用, RECOGNITION_FAIL-识别失败',
    severity        VARCHAR(16) NOT NULL COMMENT '严重程度：WARNING-警告, CRITICAL-严重',
    source_id       VARCHAR(64) COMMENT '关联来源 ID（设备 ID / 事件 ID / 记录 ID）',
    message         VARCHAR(512) NOT NULL COMMENT '异常描述',
    acknowledged    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已确认：0-未确认, 1-已确认',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    acknowledged_at DATETIME COMMENT '确认时间',
    INDEX idx_parking_lot_ack (parking_lot_id, acknowledged),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗亭监控异常提醒表';


-- ============================================================
-- Migration: V20260716002__add_booth_monitor_permission.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：新增岗亭实时监控权限（P005）
-- =============================================================================
-- 为岗亭操作员增加独立的 `booth:monitor` 权限，用于访问：
--   - GET /booth/monitor/snapshot
--   - POST /booth/monitor/devices/refresh
--   - POST /booth/monitor/alerts/{id}/ack
-- 平台管理员、设备运维等即使拥有 record:read 也不能访问岗亭监控接口。
-- =============================================================================

INSERT INTO sys_permission (code, name, description) VALUES
('booth:monitor', '岗亭实时监控', '查看岗亭实时监控页面、接收 WebSocket 推送、确认异常提醒')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('booth_operator', 'booth:monitor')
ON DUPLICATE KEY UPDATE role_code = role_code;


-- ============================================================
-- Migration: V20260717001__add_tenant_id_to_business_tables.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：为业务表补齐 tenant_id 列与索引
-- =============================================================================
-- TASK-0101｜多租户数据隔离基座
-- 为当前缺少 tenant_id 的业务表添加租户字段，并通过关联表回填历史数据。
-- 所有业务表最终 tenant_id 均设为 NOT NULL，确保 MyBatis-Plus 租户拦截器生效。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. device：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE device
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE device d
    JOIN parking_lot pl ON d.parking_lot_id = pl.id
   SET d.tenant_id = pl.tenant_id;

-- 无停车场的孤立设备（理论上不存在）暂归为 NULL，后续业务清理
ALTER TABLE device
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 2. parking_lane：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lane
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE parking_lane l
    JOIN parking_lot pl ON l.parking_lot_id = pl.id
   SET l.tenant_id = pl.tenant_id;

ALTER TABLE parking_lane
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 3. employee_parking_lot：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE employee_parking_lot
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE employee_parking_lot epl
    JOIN parking_lot pl ON epl.parking_lot_id = pl.id
   SET epl.tenant_id = pl.tenant_id;

ALTER TABLE employee_parking_lot
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 4. parking_lot_capacity_log：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lot_capacity_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE parking_lot_capacity_log pcl
    JOIN parking_lot pl ON pcl.parking_lot_id = pl.id
   SET pcl.tenant_id = pl.tenant_id;

ALTER TABLE parking_lot_capacity_log
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 5. parking_lot_status_log：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lot_status_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE parking_lot_status_log psl
    JOIN parking_lot pl ON psl.parking_lot_id = pl.id
   SET psl.tenant_id = pl.tenant_id;

ALTER TABLE parking_lot_status_log
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 6. device_status_snapshot：通过 device_id -> device.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE device_status_snapshot
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE device_status_snapshot dss
    JOIN device d ON dss.device_id = d.id
   SET dss.tenant_id = d.tenant_id;

ALTER TABLE device_status_snapshot
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';


-- ============================================================
-- Migration: V20260717002__add_company_id_to_parking_lot.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：为 parking_lot 补齐公司关联字段
-- =============================================================================
-- TASK-0101｜多租户数据隔离基座
-- 为 parking_lot 添加 company_id / group_id，供后续公司/集团档案功能使用。
-- 初始允许 NULL，V20260718001 负责回填并收紧为 NOT NULL。
-- =============================================================================

ALTER TABLE parking_lot
    ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '所属公司 ID（逻辑外键：company.id）' AFTER tenant_id,
    ADD COLUMN group_id   BIGINT DEFAULT NULL COMMENT '所属集团 ID（逻辑外键：company.id，冗余）' AFTER company_id,
    ADD INDEX idx_company_id (company_id) COMMENT '按公司查询';


-- ============================================================
-- Migration: V20260718001__company_and_parking_lot_company_id.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：公司/集团档案表与停车场所属公司关联
-- =============================================================================
-- TASK-0102｜公司/集团档案管理
-- 1. 创建 company 表，支持集团-子公司-分公司三级树形结构。
-- 2. 为 parking_lot 增加 company_id / group_id 关联字段。
-- 3. 为历史租户与停车场生成默认公司，保证非空约束可落地。
-- 4. 新增公司管理相关权限并映射到固定角色。
-- 5. 清理 parking_lot 中与当前需求文档不符的停用范围冗余字段。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 公司/集团档案表
-- -----------------------------------------------------------------------------
CREATE TABLE company
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '公司 ID',
    tenant_id     BIGINT       NOT NULL COMMENT '所属租户 ID',
    parent_id     BIGINT       DEFAULT NULL COMMENT '上级公司 ID（逻辑外键：company.id，顶级为 NULL）',
    name          VARCHAR(128) NOT NULL COMMENT '公司名称',
    level         TINYINT      NOT NULL DEFAULT 1 COMMENT '公司级别：1-集团, 2-子公司, 3-分公司',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常, 2-暂停, 3-注销',
    sort_order    INT          NOT NULL DEFAULT 0 COMMENT '同级排序',
    path          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '树路径编码（如 /1/12/123/）',
    contact_name  VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
    contact_phone VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
    deleted_at    DATETIME(3)  DEFAULT NULL COMMENT '软删除时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parent_id (parent_id) COMMENT '按上级查询',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询',
    INDEX idx_path (path) COMMENT '按路径查询',
    UNIQUE KEY uk_tenant_name_deleted (tenant_id, name, (CAST(deleted_at IS NULL AS UNSIGNED))) COMMENT '租户内未删除公司名称唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司/集团档案表';

-- -----------------------------------------------------------------------------
-- 2. 停车场新增公司关联字段
-- -----------------------------------------------------------------------------
-- 已由 V20260717002 添加 company_id / group_id，此处仅做回填与约束收紧。

-- -----------------------------------------------------------------------------
-- 3. 历史数据回填：为每个租户生成默认集团，并关联历史停车场
-- -----------------------------------------------------------------------------
-- 3.1 为每个租户插入默认集团（幂等：按租户 ID + 名称软删除唯一键，NULL 视为唯一）
INSERT INTO company (tenant_id, parent_id, name, level, status, sort_order, path, contact_name, contact_phone)
SELECT t.id,
       NULL,
       t.name,
       1,
       1,
       0,
       '/',
       t.contact_person,
       t.contact_phone
FROM tenant t
WHERE t.status != 'REJECTED'
  AND NOT EXISTS (SELECT 1 FROM company c WHERE c.tenant_id = t.id AND c.level = 1 AND c.deleted_at IS NULL);

-- 3.2 修正默认集团公司的 path 为实际公司 ID（插入时无法预知自增 ID）
UPDATE company
SET path = CONCAT('/', id, '/')
WHERE level = 1
  AND parent_id IS NULL
  AND deleted_at IS NULL;

-- 3.3 将历史停车场的 company_id 指向租户默认集团
UPDATE parking_lot pl
    JOIN company c ON c.tenant_id = pl.tenant_id AND c.level = 1 AND c.deleted_at IS NULL
   SET pl.company_id = c.id,
       pl.group_id   = c.id
 WHERE pl.company_id IS NULL;

-- 3.4 移除 company_id 的临时默认值
ALTER TABLE parking_lot
    MODIFY COLUMN company_id BIGINT NOT NULL COMMENT '所属公司 ID（逻辑外键：company.id）';

-- -----------------------------------------------------------------------------
-- 4. 新增公司管理权限并映射到固定角色
-- -----------------------------------------------------------------------------
INSERT INTO sys_permission (code, name, description) VALUES
('company:read',  '查看公司', '查看公司/集团档案'),
('company:write', '管理公司', '创建、编辑公司/集团档案'),
('company:delete','删除公司', '软删除公司/集团档案')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- 超级管理员：拥有全部权限
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('super_admin', 'company:read'),
('super_admin', 'company:write'),
('super_admin', 'company:delete')
ON DUPLICATE KEY UPDATE role_code = role_code;

-- 平台运营：查看与协助配置
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('platform_operator', 'company:read'),
('platform_operator', 'company:write')
ON DUPLICATE KEY UPDATE role_code = role_code;

-- 客户管理员：管理本租户公司
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('customer_admin', 'company:read'),
('customer_admin', 'company:write'),
('customer_admin', 'company:delete')
ON DUPLICATE KEY UPDATE role_code = role_code;

-- -----------------------------------------------------------------------------
-- 5. 清理 parking_lot 冗余字段（与当前 PRD/开发计划不符的停用范围字段）
-- -----------------------------------------------------------------------------
-- 注意：当前代码（ParkingLotService、EntryService、测试）仍在使用以下字段，
-- 因此暂不删除。待 TASK-0102 后续清理工作完成后再统一移除。
--
-- 备份冗余列数据到临时表，便于必要时回查
-- CREATE TABLE IF NOT EXISTS _backup_parking_lot_disable_columns AS
-- SELECT id, disable_new_entries, disable_payment, disable_exit, disable_auto_gate, disable_only_config
-- FROM parking_lot;
--
-- ALTER TABLE parking_lot
--     DROP COLUMN disable_new_entries,
--     DROP COLUMN disable_payment,
--     DROP COLUMN disable_exit,
--     DROP COLUMN disable_auto_gate,
--     DROP COLUMN disable_only_config;


-- ============================================================
-- Migration: V20260721001__task_0101_tenant_id_and_deleted_at.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：TASK-0101 多租户数据隔离基座补齐
-- =============================================================================
-- 为 vehicle、wx_user、plate_binding、sys_user 表添加 tenant_id 和 deleted_at 字段，
-- 确保所有业务表满足多租户隔离与软删除的全局约束。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. vehicle：添加 tenant_id 与 deleted_at
-- ---------------------------------------------------------------------------
ALTER TABLE vehicle
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id,
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';

-- ---------------------------------------------------------------------------
-- 2. wx_user：添加 tenant_id 与 deleted_at
-- ---------------------------------------------------------------------------
ALTER TABLE wx_user
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id,
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';

-- ---------------------------------------------------------------------------
-- 3. plate_binding：添加 tenant_id 与 deleted_at
-- ---------------------------------------------------------------------------
ALTER TABLE plate_binding
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id,
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';

-- ---------------------------------------------------------------------------
-- 4. sys_user：添加 deleted_at（tenant_id 已在 V20260711004 添加）
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';


-- ============================================================
-- Migration: V20260722001__sprint1_tenant_and_company.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260723001__fix_sprint1_deleted_at_and_snowflake.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：修复 Sprint 1 DDL 与实体层不一致问题
-- =============================================================================
-- 问题：
--   1. DDL 使用 is_deleted TINYINT，实体层使用 deletedAt LocalDateTime
--   2. DDL 使用 AUTO_INCREMENT，规范要求 Snowflake 分布式 ID
--   3. BCrypt 哈希格式不兼容（$2b$ → $2a$）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 修正 sys_company：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_company DROP COLUMN is_deleted;
ALTER TABLE sys_company ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER sort_order;
ALTER TABLE sys_company MODIFY COLUMN id BIGINT NOT NULL COMMENT '公司ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 2. 修正 sys_admin_account：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account DROP COLUMN is_deleted;
ALTER TABLE sys_admin_account ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER last_login_time;
ALTER TABLE sys_admin_account MODIFY COLUMN id BIGINT NOT NULL COMMENT '账号ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 3. 修正 sys_custom_role：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_custom_role DROP COLUMN is_deleted;
ALTER TABLE sys_custom_role ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER description;
ALTER TABLE sys_custom_role MODIFY COLUMN id BIGINT NOT NULL COMMENT '角色ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 4. 修正 sys_role_permission：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_role_permission DROP COLUMN is_deleted;
ALTER TABLE sys_role_permission ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER data_scope;
ALTER TABLE sys_role_permission ADD COLUMN updated_at DATETIME(3) DEFAULT NULL COMMENT '更新时间' AFTER deleted_at;
ALTER TABLE sys_role_permission MODIFY COLUMN id BIGINT NOT NULL COMMENT '权限ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 5. 修正 sys_auth_code：新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_auth_code ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER activated_at;
ALTER TABLE sys_auth_code MODIFY COLUMN id BIGINT NOT NULL COMMENT '授权码ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 6. 修正 sys_admin_account_role：新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account_role ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER created_at;
ALTER TABLE sys_admin_account_role ADD COLUMN updated_at DATETIME(3) DEFAULT NULL COMMENT '更新时间' AFTER deleted_at;
ALTER TABLE sys_admin_account_role MODIFY COLUMN id BIGINT NOT NULL COMMENT '关联ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 7. 修正 sys_business_log：新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_business_log ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER error_msg;
ALTER TABLE sys_business_log ADD COLUMN updated_at DATETIME(3) DEFAULT NULL COMMENT '更新时间' AFTER deleted_at;
ALTER TABLE sys_business_log MODIFY COLUMN id BIGINT NOT NULL COMMENT '日志ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 8. 修正 sys_tenant：移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_tenant MODIFY COLUMN id BIGINT NOT NULL COMMENT '租户ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 9. 修正初始化数据：更新 BCrypt 哈希为 Spring Security 兼容格式
-- ---------------------------------------------------------------------------
-- 原哈希 $2b$12$... 是 Node.js bcrypt 变体，Java BCryptPasswordEncoder 默认使用 $2a$ 前缀
-- 使用 Spring Security 的 BCryptPasswordEncoder 重新生成（密码：admin123）
UPDATE sys_admin_account SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lqkkO9LZ3Kz1VxhG6' WHERE id = 1;


-- ============================================================
-- Migration: V20260724001__sprint2_parking_lot_zone_lane.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：Sprint 2 - 车场基础设置（停车场档案、区域管理、通道管理）
-- =============================================================================
-- 对齐 PRD V1.0 和 section_03_ddl.md 规范，补齐 parking_lot 字段，
-- 新建 parking_zone 表，重构 parking_lane 表结构。
-- 所有业务表包含 tenant_id 和 deleted_at，主键使用 BIGINT（Snowflake）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 重构 parking_lot 表（补齐 PRD 字段，移除策略字段到 access_policy）
-- ---------------------------------------------------------------------------

-- 1.1 新增字段
ALTER TABLE parking_lot
    ADD COLUMN province        VARCHAR(64)   DEFAULT NULL COMMENT '省份' AFTER name,
    ADD COLUMN city            VARCHAR(64)   DEFAULT NULL COMMENT '城市' AFTER province,
    ADD COLUMN district        VARCHAR(64)   DEFAULT NULL COMMENT '区县' AFTER city,
    ADD COLUMN region_type     TINYINT       NOT NULL DEFAULT 1 COMMENT '区域类型：1商场 2写字楼 3住宅小区 4医院 5景区 6交通枢纽' AFTER district,
    ADD COLUMN contact_name    VARCHAR(64)   DEFAULT NULL COMMENT '联系人' AFTER contact_phone,
    ADD COLUMN business_hours  VARCHAR(32)   DEFAULT '00:00-24:00' COMMENT '营业时间' AFTER status,
    ADD COLUMN images          JSON          DEFAULT NULL COMMENT '车场图片URL数组（最多5张）' AFTER business_hours,
    ADD COLUMN version         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER images,
    ADD COLUMN deleted_at      DATETIME(3)   DEFAULT NULL COMMENT '软删除时间' AFTER updated_at;

-- 1.2 移除已迁移到 access_policy 的策略字段（如果存在）
-- 注意：这些字段在旧代码中存在，但按 Sprint 2 规范应移除到策略配置表
-- 先检查列是否存在再删除，避免报错
SET @drop_disable_new_entries = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_new_entries'),
    'ALTER TABLE parking_lot DROP COLUMN disable_new_entries',
    'SELECT 1'
);
PREPARE stmt1 FROM @drop_disable_new_entries;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @drop_disable_payment = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_payment'),
    'ALTER TABLE parking_lot DROP COLUMN disable_payment',
    'SELECT 1'
);
PREPARE stmt2 FROM @drop_disable_payment;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET @drop_disable_exit = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_exit'),
    'ALTER TABLE parking_lot DROP COLUMN disable_exit',
    'SELECT 1'
);
PREPARE stmt3 FROM @drop_disable_exit;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

SET @drop_disable_auto_gate = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_auto_gate'),
    'ALTER TABLE parking_lot DROP COLUMN disable_auto_gate',
    'SELECT 1'
);
PREPARE stmt4 FROM @drop_disable_auto_gate;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

SET @drop_disable_only_config = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_only_config'),
    'ALTER TABLE parking_lot DROP COLUMN disable_only_config',
    'SELECT 1'
);
PREPARE stmt5 FROM @drop_disable_only_config;
EXECUTE stmt5;
DEALLOCATE PREPARE stmt5;

-- 1.3 移除 current_vehicles 和 remaining_spaces（改为区域汇总 + Redis 实时计算）
SET @drop_current_vehicles = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'current_vehicles'),
    'ALTER TABLE parking_lot DROP COLUMN current_vehicles',
    'SELECT 1'
);
PREPARE stmt6 FROM @drop_current_vehicles;
EXECUTE stmt6;
DEALLOCATE PREPARE stmt6;

SET @drop_remaining_spaces = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'remaining_spaces'),
    'ALTER TABLE parking_lot DROP COLUMN remaining_spaces',
    'SELECT 1'
);
PREPARE stmt7 FROM @drop_remaining_spaces;
EXECUTE stmt7;
DEALLOCATE PREPARE stmt7;

-- 1.4 修改主键为 BIGINT（移除 AUTO_INCREMENT）
ALTER TABLE parking_lot MODIFY COLUMN id BIGINT NOT NULL COMMENT '车场ID（Snowflake）';

-- 1.5 添加索引
ALTER TABLE parking_lot ADD UNIQUE INDEX uk_tenant_name (tenant_id, name, deleted_at) COMMENT '租户内名称唯一';
ALTER TABLE parking_lot ADD INDEX idx_region_type (region_type) COMMENT '按区域类型查询';
ALTER TABLE parking_lot ADD INDEX idx_deleted_at (deleted_at) COMMENT '软删除查询';

-- ---------------------------------------------------------------------------
-- 2. 新建 parking_zone 区域管理表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parking_zone (
    id              BIGINT UNSIGNED NOT NULL COMMENT '区域ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    name            VARCHAR(128)        NOT NULL COMMENT '区域名称',
    tag             VARCHAR(64)         NOT NULL DEFAULT 'NORMAL' COMMENT '区域标签：NORMAL普通 VIP员工 LOADING装卸 CHARGE充电 支持自定义',
    level           TINYINT             NOT NULL DEFAULT 1 COMMENT '区域等级：1普通 2VIP 3员工',
    fee_rule_id     BIGINT UNSIGNED     DEFAULT NULL COMMENT '收费标准ID（逻辑外键：fee_rule.id）【预留】',
    total_spaces    INT                 NOT NULL DEFAULT 0 COMMENT '车位总数',
    fixed_spaces    INT                 NOT NULL DEFAULT 0 COMMENT '固定车位数',
    temp_spaces     INT                 NOT NULL DEFAULT 0 COMMENT '临停车位数（= total_spaces - fixed_spaces）',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    manager_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '区域负责人ID（逻辑外键：sys_admin_account.id）',
    remark          VARCHAR(512)        DEFAULT NULL COMMENT '备注',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域管理表';

-- ---------------------------------------------------------------------------
-- 3. 重构 parking_lane 通道管理表
-- ---------------------------------------------------------------------------

-- 3.1 先删除旧表（如果存在），因为字段变更较大，重建更简单
-- 注意：生产环境应使用 ALTER TABLE，但开发阶段重建更快
-- 保留旧表数据：先改名备份
RENAME TABLE parking_lane TO parking_lane_backup;

-- 3.2 创建新表
CREATE TABLE parking_lane (
    id              BIGINT UNSIGNED NOT NULL COMMENT '通道ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id         BIGINT UNSIGNED     NOT NULL COMMENT '所属区域ID（逻辑外键：parking_zone.id）',
    lane_no         VARCHAR(32)         NOT NULL COMMENT '通道编号，如 A1、17',
    name            VARCHAR(128)        NOT NULL COMMENT '通道名称，如东大门',
    type            TINYINT             NOT NULL DEFAULT 1 COMMENT '通道类型：1入口 2出口 3双向',
    entry_camera_id BIGINT UNSIGNED     DEFAULT NULL COMMENT '入口相机ID（逻辑外键：device.id）【预留】',
    exit_camera_id  BIGINT UNSIGNED     DEFAULT NULL COMMENT '出口相机ID（逻辑外键：device.id）【预留】',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用 3维护中',
    tide_mode       TINYINT             DEFAULT 0 COMMENT '潮汐模式：0关闭 1早高峰入口 2晚高峰出口',
    camera_mode     TINYINT             DEFAULT 1 COMMENT '相机配置模式：1单相机 2双相机 3主从相机',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_lane_no (tenant_id, lot_id, lane_no, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_zone_id (zone_id),
    KEY idx_entry_camera_id (entry_camera_id),
    KEY idx_exit_camera_id (exit_camera_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通道管理表';

-- 3.3 从备份表迁移数据（字段映射）
-- 旧字段 → 新字段映射：
-- parking_lot_id → lot_id
-- code → lane_no
-- direction → type: ENTRY=1, EXIT=2, MIXED=3
-- status: ENABLED=1, DISABLED=2
-- is_key_lane, auto_release_policy, description 已移除（移到策略配置）
INSERT INTO parking_lane (
    id, tenant_id, lot_id, zone_id, lane_no, name, type, status, 
    tide_mode, camera_mode, version, deleted_at, created_at, updated_at
)
SELECT 
    id,
    tenant_id,
    parking_lot_id AS lot_id,
    0 AS zone_id,  -- 旧数据没有 zone_id，默认设置为 0（后续手动关联）
    code AS lane_no,
    name,
    CASE direction
        WHEN 'ENTRY' THEN 1
        WHEN 'EXIT' THEN 2
        WHEN 'MIXED' THEN 3
        ELSE 1
    END AS type,
    CASE status
        WHEN 'ENABLED' THEN 1
        WHEN 'DISABLED' THEN 2
        ELSE 1
    END AS status,
    0 AS tide_mode,  -- 旧数据无潮汐模式
    1 AS camera_mode,  -- 旧数据默认单相机
    0 AS version,
    NULL AS deleted_at,
    created_at,
    updated_at
FROM parking_lane_backup;

-- 3.4 删除备份表
DROP TABLE parking_lane_backup;

-- ---------------------------------------------------------------------------
-- 4. 初始化数据
-- ---------------------------------------------------------------------------

-- 为每个现有停车场创建默认区域（如果 parking_zone 为空）
-- 这确保每个停车场至少有一个区域，符合 PRD 要求
INSERT INTO parking_zone (id, tenant_id, lot_id, name, tag, level, total_spaces, fixed_spaces, temp_spaces, status, version, created_at, updated_at)
SELECT 
    id + 1000000,  -- 使用偏移避免 ID 冲突（Snowflake 实际由应用生成）
    tenant_id,
    id AS lot_id,
    '默认区域' AS name,
    'NORMAL' AS tag,
    1 AS level,
    total_spaces,
    0 AS fixed_spaces,
    total_spaces AS temp_spaces,
    1 AS status,
    0 AS version,
    created_at,
    updated_at
FROM parking_lot
WHERE deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM parking_zone WHERE parking_zone.lot_id = parking_lot.id);

-- ---------------------------------------------------------------------------
-- 5. 更新 parking_lane 的 zone_id（关联到默认区域）
-- ---------------------------------------------------------------------------
UPDATE parking_lane pl
SET pl.zone_id = (
    SELECT pz.id 
    FROM parking_zone pz 
    WHERE pz.lot_id = pl.lot_id 
    LIMIT 1
)
WHERE pl.zone_id = 0;


-- ============================================================
-- Migration: V20260725001__sprint3_fee_rule.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：Sprint 3 - 收费规则引擎（fee_rule / fee_rule_segment）
-- =============================================================================
-- 对齐 PRD V1.0 和 section_03_ddl.md 规范，新建 fee_rule 表和 fee_rule_segment 表。
-- 所有业务表包含 tenant_id 和 deleted_at，主键使用 BIGINT（Snowflake）。
-- 金额字段使用 DECIMAL(18,2)，禁止 FLOAT/DOUBLE。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 新建 fee_rule（收费规则表）
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fee_rule (
    id                  BIGINT UNSIGNED NOT NULL COMMENT '规则ID（Snowflake）',
    tenant_id           BIGINT          NOT NULL COMMENT '租户ID',
    lot_id              BIGINT UNSIGNED NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id             BIGINT UNSIGNED     DEFAULT NULL COMMENT '适用区域ID（逻辑外键：parking_zone.id，NULL表示车场通用）',
    name                VARCHAR(128)    NOT NULL COMMENT '规则名称',
    billing_mode        TINYINT         NOT NULL DEFAULT 1 COMMENT '计费模式：1按时 2按次 3阶梯 4分时段',
    free_minutes        INT             NOT NULL DEFAULT 0 COMMENT '免费时长（分钟）',
    unit_minutes        INT             NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
    first_period_price  DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '首时段价格',
    subsequent_price    DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '后续单价',
    daily_cap           DECIMAL(18,2)       DEFAULT NULL COMMENT '24小时封顶金额',
    night_cap           DECIMAL(18,2)       DEFAULT NULL COMMENT '夜间封顶金额',
    priority            INT             NOT NULL DEFAULT 0 COMMENT '优先级，数字越大优先级越高',
    status              TINYINT         NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    effective_start     DATETIME(3)         DEFAULT NULL COMMENT '生效开始时间',
    effective_end       DATETIME(3)         DEFAULT NULL COMMENT '生效结束时间',
    holiday_rules       JSON                DEFAULT NULL COMMENT '节假日特殊规则JSON',
    version             INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at          DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_zone_id (zone_id),
    KEY idx_billing_mode (billing_mode),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则表';

-- ---------------------------------------------------------------------------
-- 2. 新建 fee_rule_segment（收费规则时段表）【辅助表】
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS fee_rule_segment (
    id              BIGINT UNSIGNED NOT NULL COMMENT '时段ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    fee_rule_id     BIGINT UNSIGNED NOT NULL COMMENT '收费规则ID（逻辑外键：fee_rule.id）',
    segment_name    VARCHAR(64)     NOT NULL COMMENT '时段名称，如白天/夜间',
    start_time      TIME            NOT NULL COMMENT '时段开始时间',
    end_time        TIME            NOT NULL COMMENT '时段结束时间',
    unit_minutes    INT             NOT NULL DEFAULT 60 COMMENT '计费单位（分钟）',
    unit_price      DECIMAL(18,2)   NOT NULL DEFAULT 0.00 COMMENT '时段单价',
    cap_amount      DECIMAL(18,2)       DEFAULT NULL COMMENT '时段封顶金额',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_fee_rule_id (fee_rule_id),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='收费规则时段表';

-- ---------------------------------------------------------------------------
-- 3. 保留旧 billing_rule / billing_rule_version 表（不删除，不迁移）
-- ---------------------------------------------------------------------------
-- 旧表继续保留供现有 BillingEngine 使用，本期不强制迁移数据。
-- 下期 Sprint 完成适配层后统一迁移并删除旧表。


-- ============================================================
-- Migration: V20260727001__sys_department.sql
-- ============================================================
-- Sprint 4: TASK-0401 部门（组织架构）管理
-- 创建 sys_department 表

CREATE TABLE IF NOT EXISTS sys_department (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '部门ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parent_id       BIGINT          NOT NULL DEFAULT 0 COMMENT '上级部门ID（0表示顶级）',
    name            VARCHAR(50)     NOT NULL COMMENT '部门名称',
    code            VARCHAR(30)     NULL COMMENT '部门编码',
    parking_lot_id  BIGINT          NOT NULL COMMENT '所属停车场ID',
    level           INT             NULL COMMENT '部门级别',
    manager_name    VARCHAR(30)     NULL COMMENT '负责人',
    contact_phone   VARCHAR(20)     NULL COMMENT '联系电话',
    sort_order      INT             NULL DEFAULT 0 COMMENT '排序',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_tenant_id (tenant_id),
    INDEX idx_parent_id (parent_id),
    INDEX idx_parking_lot_id (parking_lot_id),
    INDEX idx_tenant_name (tenant_id, name),
    INDEX idx_tenant_deleted (tenant_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门/组织架构表';


-- ============================================================
-- Migration: V20260727002__sys_vehicle.sql
-- ============================================================
-- Sprint 4: TASK-0402 车辆主表与多类型登记
-- 创建 sys_vehicle 和 sys_vehicle_multi_plate 表

CREATE TABLE IF NOT EXISTS sys_vehicle (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '车辆ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    plate_color     VARCHAR(10)     NULL COMMENT '车牌颜色',
    vehicle_type    VARCHAR(20)     NOT NULL COMMENT '车辆类型：FREE-免费车, MONTHLY-月租车, PREPAID-储值车, VIP-贵宾车, SUPER-超级车牌, BLACKLIST-黑名单',
    owner_name      VARCHAR(30)     NULL COMMENT '车主姓名',
    owner_phone     VARCHAR(20)     NULL COMMENT '车主电话',
    department_id   BIGINT          NULL COMMENT '所属部门ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '所属停车场ID',
    valid_start_date DATE           NULL COMMENT '月卡/固定车有效期开始',
    valid_end_date  DATE            NULL COMMENT '月卡/固定车有效期结束',
    prepaid_balance DECIMAL(18,2)   NULL DEFAULT 0 COMMENT '储值车余额（元）',
    fee_rule_id     BIGINT          NULL COMMENT '月卡/固定车收费标准ID',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-正常, EXPIRED-已过期, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_tenant_plate (tenant_id, plate_number, deleted_at),
    INDEX idx_tenant_type (tenant_id, vehicle_type),
    INDEX idx_department (department_id),
    INDEX idx_parking_lot (parking_lot_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆主表';

CREATE TABLE IF NOT EXISTS sys_vehicle_multi_plate (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '绑定ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '主车辆ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '绑定车牌号（大写）',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-有效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_vehicle (vehicle_id),
    INDEX idx_plate (plate_number),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一位多车绑定表';


-- ============================================================
-- Migration: V20260727003__sys_vehicle_wallet.sql
-- ============================================================
-- Sprint 4: TASK-0403 储值车账户与流水
-- 创建 sys_vehicle_wallet 和 sys_vehicle_wallet_log 表

CREATE TABLE IF NOT EXISTS sys_vehicle_wallet (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '钱包ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID',
    balance         DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '当前余额（元）',
    total_recharge  DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '累计充值金额（元）',
    total_consume   DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '累计消费金额（元）',
    version         INT             NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_vehicle (vehicle_id, deleted_at),
    INDEX idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值车钱包表';

CREATE TABLE IF NOT EXISTS sys_vehicle_wallet_log (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '流水ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID',
    wallet_id       BIGINT          NOT NULL COMMENT '钱包ID',
    log_type        VARCHAR(20)     NOT NULL COMMENT '流水类型：RECHARGE-充值, CONSUME-消费, REFUND-退款, ADJUST-调账',
    amount          DECIMAL(18,2)   NOT NULL COMMENT '变动金额（元，正数增加，负数减少）',
    balance_before  DECIMAL(18,2)   NOT NULL COMMENT '变动前余额（元）',
    balance_after   DECIMAL(18,2)   NOT NULL COMMENT '变动后余额（元）',
    order_id        BIGINT          NULL COMMENT '关联订单ID',
    operator_id     BIGINT          NULL COMMENT '操作人ID',
    operator_name   VARCHAR(30)     NULL COMMENT '操作人姓名',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_wallet (wallet_id),
    INDEX idx_vehicle (vehicle_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_type (log_type),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='储值车钱包流水表';


-- ============================================================
-- Migration: V20260727004__lane_permission.sql
-- ============================================================
-- Sprint 4: TASK-0404 通道权限管理
-- 创建 lane_permission 表，支持车辆/部门级别的通道通行权限配置

CREATE TABLE IF NOT EXISTS lane_permission (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '权限ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    lane_id         BIGINT          NOT NULL COMMENT '通道ID（逻辑外键：parking_lane.id）',
    target_type     VARCHAR(20)     NOT NULL COMMENT '权限目标类型：VEHICLE-车辆, DEPARTMENT-部门',
    target_id       BIGINT          NOT NULL COMMENT '权限目标ID（车辆ID或部门ID）',
    direction       VARCHAR(20)     NOT NULL DEFAULT 'BOTH' COMMENT '允许方向：ENTRY-仅入口, EXIT-仅出口, BOTH-双向',
    valid_start     DATETIME(3)     NULL COMMENT '有效期开始（NULL表示永久）',
    valid_end       DATETIME(3)     NULL COMMENT '有效期结束（NULL表示永久）',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_lane_target (tenant_id, lane_id, target_type, target_id, deleted_at),
    INDEX idx_lane (lane_id),
    INDEX idx_target (target_type, target_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通道权限配置表';


-- ============================================================
-- Migration: V20260727005__vehicle_audit.sql
-- ============================================================
-- Sprint 4: TASK-0407 车辆审核基础流程
-- 创建 vehicle_audit 表，支持车辆登记审核（通过/驳回/待补充）

CREATE TABLE IF NOT EXISTS vehicle_audit (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '审核ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID（逻辑外键：sys_vehicle.id）',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（大写）',
    apply_type      VARCHAR(20)     NOT NULL COMMENT '申请类型：NEW-新登记, UPDATE-信息变更, RENEW-续期',
    apply_reason    VARCHAR(500)    NULL COMMENT '申请原因/备注',
    applicant_id    BIGINT          NULL COMMENT '申请人ID（小程序用户或管理员）',
    applicant_name  VARCHAR(30)     NULL COMMENT '申请人姓名',
    applicant_phone VARCHAR(20)     NULL COMMENT '申请人电话',
    audit_status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核, APPROVED-已通过, REJECTED-已驳回, NEED_INFO-待补充',
    audit_result    VARCHAR(500)    NULL COMMENT '审核结果说明',
    auditor_id      BIGINT          NULL COMMENT '审核人ID',
    auditor_name    VARCHAR(30)     NULL COMMENT '审核人姓名',
    audited_at      DATETIME(3)     NULL COMMENT '审核时间',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_vehicle (vehicle_id),
    INDEX idx_tenant_status (tenant_id, audit_status),
    INDEX idx_plate (plate_number),
    INDEX idx_applicant (applicant_id),
    INDEX idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆审核记录表';


-- ============================================================
-- Migration: V20260728001__access_policy.sql
-- ============================================================
-- Sprint 5: TASK-0501 车辆进出策略配置
-- 创建 access_policy 表，支持键值对存储策略配置

CREATE TABLE IF NOT EXISTS access_policy (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '策略ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    policy_type     VARCHAR(30)     NOT NULL COMMENT '策略类型：ENTRY-入场策略, EXIT-出场策略, BLACKLIST-黑名单策略, VIP-VIP策略',
    policy_key      VARCHAR(50)     NOT NULL COMMENT '策略键，如 allow_entry、auto_release、need_confirm',
    policy_value    VARCHAR(500)    NOT NULL COMMENT '策略值，如 true、false、10（分钟）',
    description     VARCHAR(200)    NULL COMMENT '策略说明',
    sort_order      INT             NOT NULL DEFAULT 0 COMMENT '排序',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_lot_type_key (tenant_id, parking_lot_id, policy_type, policy_key, deleted_at),
    INDEX idx_lot (parking_lot_id),
    INDEX idx_type (policy_type),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆进出策略配置表';


-- ============================================================
-- Migration: V20260728002__parking_space_policy.sql
-- ============================================================
-- Sprint 5: TASK-0502 车位管控策略
-- 创建 parking_space_policy 表，支持余位计算与管控

CREATE TABLE IF NOT EXISTS parking_space_policy (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '策略ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    zone_id         BIGINT          NULL COMMENT '区域ID（NULL表示全场策略）',
    total_spaces    INT             NOT NULL DEFAULT 0 COMMENT '总车位数',
    fixed_spaces    INT             NOT NULL DEFAULT 0 COMMENT '固定车位数（月租/储值等）',
    temp_spaces     INT             NOT NULL DEFAULT 0 COMMENT '临时车位数',
    reserved_spaces INT             NOT NULL DEFAULT 0 COMMENT '预留车位数',
    warning_threshold INT           NOT NULL DEFAULT 10 COMMENT '余位预警阈值',
    full_action     VARCHAR(20)     NOT NULL DEFAULT 'WARN' COMMENT '满位动作：WARN-仅预警, BLOCK-禁止入场, ALLOW_VIP-仅允许VIP/月租',
    status          VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效, DISABLED-已禁用',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    UNIQUE KEY uk_lot_zone (tenant_id, parking_lot_id, zone_id, deleted_at),
    INDEX idx_lot (parking_lot_id),
    INDEX idx_zone (zone_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车位管控策略表';


-- ============================================================
-- Migration: V20260729001__parking_session.sql
-- ============================================================
-- Sprint 6: TASK-0601 在场车辆管理
-- 创建 parking_session 表，记录车辆在场状态

CREATE TABLE IF NOT EXISTS parking_session (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '在场记录ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    lane_id         BIGINT          NULL COMMENT '入场通道ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（大写）',
    plate_color     VARCHAR(10)     NULL COMMENT '车牌颜色',
    vehicle_type    VARCHAR(20)     NULL COMMENT '车辆类型判定结果',
    entry_time      DATETIME(3)     NOT NULL COMMENT '入场时间',
    entry_image     VARCHAR(255)    NULL COMMENT '入场抓拍图片URL',
    entry_operator  BIGINT          NULL COMMENT '入场操作人ID（人工放行时）',
    exit_time       DATETIME(3)     NULL COMMENT '出场时间',
    exit_lane_id    BIGINT          NULL COMMENT '出场通道ID',
    exit_image      VARCHAR(255)    NULL COMMENT '出场抓拍图片URL',
    exit_operator   BIGINT          NULL COMMENT '出场操作人ID',
    status          VARCHAR(20)     NOT NULL DEFAULT 'IN' COMMENT '状态：IN-在场, OUT-已出场, EXCEPTION-异常',
    fee_amount      DECIMAL(18,2)   NULL DEFAULT 0 COMMENT '应收费用（元）',
    paid_amount     DECIMAL(18,2)   NULL DEFAULT 0 COMMENT '已付费用（元）',
    order_id        BIGINT          NULL COMMENT '关联订单ID',
    remark          VARCHAR(200)    NULL COMMENT '备注',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_plate (plate_number),
    INDEX idx_lot_status (parking_lot_id, status),
    INDEX idx_tenant (tenant_id),
    INDEX idx_entry_time (entry_time),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='在场车辆记录表';


-- ============================================================
-- Migration: V20260729002__shift_record.sql
-- ============================================================
-- Sprint 6: TASK-0606 交接班管理基础版
-- 创建 shift_record 表

CREATE TABLE IF NOT EXISTS shift_record (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '交接班记录ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    operator_id     BIGINT          NOT NULL COMMENT '操作员ID',
    operator_name   VARCHAR(30)     NOT NULL COMMENT '操作员姓名',
    shift_type      VARCHAR(20)     NOT NULL COMMENT '班次类型：MORNING-早班, AFTERNOON-中班, NIGHT-晚班',
    start_time      DATETIME(3)     NOT NULL COMMENT '开班时间',
    end_time        DATETIME(3)     NULL COMMENT '交班时间',
    entry_count     INT             NOT NULL DEFAULT 0 COMMENT '本班入场车辆数',
    exit_count      INT             NOT NULL DEFAULT 0 COMMENT '本班出场车辆数',
    fee_amount      DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '本班收费金额（元）',
    cash_amount     DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '现金收费金额（元）',
    online_amount   DECIMAL(18,2)   NOT NULL DEFAULT 0 COMMENT '线上收费金额（元）',
    exception_count INT             NOT NULL DEFAULT 0 COMMENT '异常处理数',
    handover_status VARCHAR(20)     NOT NULL DEFAULT 'OPEN' COMMENT '交接状态：OPEN-开班中, CLOSED-已交班',
    handover_to     BIGINT          NULL COMMENT '接班人ID',
    handover_remark VARCHAR(200)    NULL COMMENT '交接备注',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_lot_time (parking_lot_id, start_time),
    INDEX idx_operator (operator_id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_status (handover_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交接班记录表';


-- ============================================================
-- Migration: V20260730001__visitor_apply.sql
-- ============================================================
-- Sprint 7: TASK-0704 访客预约
-- 创建 visitor_apply 表，支持访客预约申请

CREATE TABLE IF NOT EXISTS visitor_apply (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '预约ID（Snowflake）',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '停车场ID',
    visitor_name    VARCHAR(30)     NOT NULL COMMENT '访客姓名',
    visitor_phone   VARCHAR(20)     NOT NULL COMMENT '访客电话',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '访客车牌号（大写）',
    visit_reason    VARCHAR(200)    NULL COMMENT '来访事由',
    host_name       VARCHAR(30)     NULL COMMENT '被访人姓名',
    host_phone      VARCHAR(20)     NULL COMMENT '被访人电话',
    host_department VARCHAR(50)     NULL COMMENT '被访部门',
    visit_date      DATE            NOT NULL COMMENT '预约来访日期',
    visit_time_start TIME           NULL COMMENT '预约来访开始时间',
    visit_time_end  TIME            NULL COMMENT '预约来访结束时间',
    apply_status    VARCHAR(20)     NOT NULL DEFAULT 'PENDING' COMMENT '申请状态：PENDING-待审核, APPROVED-已通过, REJECTED-已拒绝, CANCELLED-已取消',
    audit_result    VARCHAR(200)    NULL COMMENT '审核结果说明',
    auditor_id      BIGINT          NULL COMMENT '审核人ID',
    audited_at      DATETIME(3)     NULL COMMENT '审核时间',
    applicant_id    BIGINT          NULL COMMENT '申请人ID（小程序用户）',
    created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at      DATETIME(3)     NULL DEFAULT NULL COMMENT '软删除时间（NULL表示未删除）',

    INDEX idx_plate (plate_number),
    INDEX idx_lot_date (parking_lot_id, visit_date),
    INDEX idx_status (apply_status),
    INDEX idx_tenant (tenant_id),
    INDEX idx_applicant (applicant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='访客预约申请表';


-- ============================================================
-- Migration: V20260731001__sprint8_order_payment.sql
-- ============================================================
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



-- ============================================================
-- Migration: V20260801001__sprint9_archive.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：Sprint 9 日志审计与数据归档
-- =============================================================================
-- 内容：
--   1. 创建 archive_data 表 —— 数据归档记录
--   2. 创建 archive_job_log 表 —— 归档任务执行日志
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 数据归档记录表
-- -----------------------------------------------------------------------------
CREATE TABLE archive_data
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    data_type       VARCHAR(32)  NOT NULL COMMENT '数据类型：OPERATION_LOG, ACCESS_LOG, PARKING_RECORD, ORDER, PAY_ORDER',
    source_table    VARCHAR(64)  NOT NULL COMMENT '源表名',
    source_id       BIGINT       NOT NULL COMMENT '源记录 ID',
    archive_batch   VARCHAR(64)  NOT NULL COMMENT '归档批次号',
    archive_path    VARCHAR(512) NOT NULL COMMENT '归档存储路径（文件路径或对象存储 key）',
    archive_size    BIGINT       NOT NULL DEFAULT 0 COMMENT '归档数据大小（字节）',
    record_count    INT          NOT NULL DEFAULT 0 COMMENT '归档记录数',
    start_time      DATETIME     NOT NULL COMMENT '归档数据起始时间',
    end_time       DATETIME     NOT NULL COMMENT '归档数据结束时间',
    status          VARCHAR(32)  NOT NULL DEFAULT 'ARCHIVED' COMMENT '状态：ARCHIVED-已归档, RESTORED-已恢复, DELETED-已删除',
    checksum        VARCHAR(64)  DEFAULT NULL COMMENT '数据校验和（SHA-256）',
    compressed      TINYINT      NOT NULL DEFAULT 1 COMMENT '是否压缩：1-是, 0-否',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME     DEFAULT NULL COMMENT '软删除时间',
    INDEX idx_archive_type_time (data_type, start_time, end_time) COMMENT '按类型+时间查询',
    INDEX idx_archive_batch (archive_batch) COMMENT '按批次查询',
    INDEX idx_archive_status (status, created_at) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='数据归档记录表';

-- -----------------------------------------------------------------------------
-- 2. 归档任务执行日志表
-- -----------------------------------------------------------------------------
CREATE TABLE archive_job_log
(
    id              BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    job_name        VARCHAR(64)  NOT NULL COMMENT '任务名称',
    job_type        VARCHAR(32)  NOT NULL COMMENT '任务类型：AUTO_ARCHIVE-自动归档, MANUAL_ARCHIVE-手动归档, RESTORE-恢复',
    data_type       VARCHAR(32)  NOT NULL COMMENT '数据类型',
    start_time      DATETIME     NOT NULL COMMENT '任务开始时间',
    end_time        DATETIME     DEFAULT NULL COMMENT '任务结束时间',
    record_count    INT          NOT NULL DEFAULT 0 COMMENT '处理记录数',
    success_count   INT          NOT NULL DEFAULT 0 COMMENT '成功记录数',
    fail_count      INT          NOT NULL DEFAULT 0 COMMENT '失败记录数',
    status          VARCHAR(32)  NOT NULL DEFAULT 'RUNNING' COMMENT '状态：RUNNING-运行中, SUCCESS-成功, FAILED-失败, PARTIAL-部分成功',
    error_msg       TEXT         DEFAULT NULL COMMENT '错误信息',
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_job_log_type (job_type, data_type, status) COMMENT '按类型+状态查询',
    INDEX idx_job_log_time (start_time, end_time) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='归档任务执行日志表';



-- ============================================================
-- Migration: V20260802001__fix_super_admin_password.sql
-- ============================================================
-- ---------------------------------------------------------------------------
-- FIX: 修正 super_admin 初始密码哈希
-- ---------------------------------------------------------------------------
-- 背景：V20260723001 迁移中写入的 BCrypt 哈希与注释声明的密码 admin123 不匹配，
--       导致全新数据库启动后 super_admin 无法登录。
-- 修复：使用 Spring Security BCryptPasswordEncoder 重新生成 admin123 的正确哈希。
-- ---------------------------------------------------------------------------

UPDATE sys_admin_account
SET password = '$2a$10$tbnJalEKCDRhYzXwkjINRur42EBSciqq/WZjArDLRHBAl0eLMKI3S'
WHERE id = 1
  AND username = 'super_admin';


-- ============================================================
-- Migration: V20260803001__fix_customer_admin_permissions.sql
-- ============================================================
-- ---------------------------------------------------------------------------
-- FIX: 为 customer_admin 角色补充缺失的菜单权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. 前端菜单要求 company:view / account:view / role:view 权限码才显示对应菜单
--   2. 后端 Controller 也使用这些权限码做接口鉴权
--   3. 但现有迁移中 customer_admin 角色只关联了 company:read / company:write 等旧权限码
--   4. role:view / role:create / role:update 等权限从未插入到 sys_role_permission
-- 修复：
--   1. 统一插入所有缺失的权限码到 sys_role_permission（按 role_id 关联）
--   2. 为 customer_admin 角色（role_code = 'customer_admin'）补充全部所需权限
--   3. 保持幂等：ON DUPLICATE KEY UPDATE
-- ---------------------------------------------------------------------------

-- 插入 customer_admin 所需的所有权限（按 role_id 动态匹配）
-- 使用 MD5 派生确定性正 bigint 作为主键，避免与现有 Snowflake ID 冲突
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'company:view' AS permission_code, 'menu' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'company:create', 'button', 'company'
    UNION ALL SELECT 'company:update', 'button', 'company'
    UNION ALL SELECT 'company:delete', 'button', 'company'
    UNION ALL SELECT 'account:view', 'menu', 'company'
    UNION ALL SELECT 'account:create', 'button', 'company'
    UNION ALL SELECT 'account:update', 'button', 'company'
    UNION ALL SELECT 'account:delete', 'button', 'company'
    UNION ALL SELECT 'role:view', 'menu', 'company'
    UNION ALL SELECT 'role:create', 'button', 'company'
    UNION ALL SELECT 'role:update', 'button', 'company'
    UNION ALL SELECT 'role:delete', 'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);

-- 同时补充 SUPER_ADMIN 的缺失权限（如果还没有的话）。
-- 注：SUPER_ADMIN 实际已拥有通配符 '*' 权限，此段仅为完整性保留。
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    'all' AS data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'company:view' AS permission_code, 'menu' AS permission_type
    UNION ALL SELECT 'company:create', 'button'
    UNION ALL SELECT 'company:update', 'button'
    UNION ALL SELECT 'company:delete', 'button'
    UNION ALL SELECT 'account:view', 'menu'
    UNION ALL SELECT 'account:create', 'button'
    UNION ALL SELECT 'account:update', 'button'
    UNION ALL SELECT 'account:delete', 'button'
    UNION ALL SELECT 'role:view', 'menu'
    UNION ALL SELECT 'role:create', 'button'
    UNION ALL SELECT 'role:update', 'button'
    UNION ALL SELECT 'role:delete', 'button'
) perm
WHERE scr.role_code = 'SUPER_ADMIN'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);


-- ============================================================
-- Migration: V20260804001__backfill_customer_admin_permissions.sql
-- ============================================================
-- ---------------------------------------------------------------------------
-- FIX: 为已存在的 customer_admin 角色补全租户级管理权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. TenantService.resolveCustomerAdminRoleId 在旧代码中创建角色时未初始化权限。
--   2. 已通过审核的租户（如 13562996687）对应的 customer_admin 角色权限为空，
--      导致登录后 JWT 权限列表为空，无法进入车场运营 / 设备运维 / 平台管理。
-- 修复：
--   1. 为所有未删除的 customer_admin 角色插入完整默认权限。
--   2. 使用 role_id + permission_code 的 MD5 派生确定性正 bigint 作为主键，避免冲突。
--   3. 保持幂等：ON DUPLICATE KEY UPDATE 仅更新 permission_type / data_scope。
-- ---------------------------------------------------------------------------

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    -- 企业管理
    SELECT 'company:view' AS permission_code, 'menu' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'company:create', 'button', 'company'
    UNION ALL SELECT 'company:update', 'button', 'company'
    UNION ALL SELECT 'company:delete', 'button', 'company'
    -- 员工账号
    UNION ALL SELECT 'account:view', 'menu', 'company'
    UNION ALL SELECT 'account:create', 'button', 'company'
    UNION ALL SELECT 'account:update', 'button', 'company'
    UNION ALL SELECT 'account:delete', 'button', 'company'
    -- 角色权限
    UNION ALL SELECT 'role:view', 'menu', 'company'
    UNION ALL SELECT 'role:create', 'button', 'company'
    UNION ALL SELECT 'role:update', 'button', 'company'
    UNION ALL SELECT 'role:delete', 'button', 'company'
    -- 停车场
    UNION ALL SELECT 'parking:view', 'menu', 'company'
    UNION ALL SELECT 'parking:read', 'button', 'company'
    UNION ALL SELECT 'parking:write', 'button', 'company'
    UNION ALL SELECT 'parking:update', 'button', 'company'
    UNION ALL SELECT 'parking:delete', 'button', 'company'
    UNION ALL SELECT 'parking:disable', 'button', 'company'
    -- 区域/车道
    UNION ALL SELECT 'lane:view', 'menu', 'company'
    UNION ALL SELECT 'lane:update', 'button', 'company'
    UNION ALL SELECT 'lane:delete', 'button', 'company'
    -- 设备
    UNION ALL SELECT 'device:read', 'menu', 'company'
    UNION ALL SELECT 'device:manage', 'button', 'company'
    -- 收费规则
    UNION ALL SELECT 'fee:read', 'menu', 'company'
    UNION ALL SELECT 'fee:write', 'button', 'company'
    -- 计费记录
    UNION ALL SELECT 'billing:read', 'menu', 'company'
    UNION ALL SELECT 'billing:write', 'button', 'company'
    UNION ALL SELECT 'billing:switch', 'button', 'company'
    -- 停车记录
    UNION ALL SELECT 'record:read', 'menu', 'company'
    -- 岗亭
    UNION ALL SELECT 'booth:view', 'menu', 'company'
    UNION ALL SELECT 'booth:operate', 'button', 'company'
    UNION ALL SELECT 'booth:monitor', 'button', 'company'
    -- 车辆
    UNION ALL SELECT 'vehicle:view', 'menu', 'company'
    UNION ALL SELECT 'vehicle:create', 'button', 'company'
    UNION ALL SELECT 'vehicle:update', 'button', 'company'
    UNION ALL SELECT 'vehicle:delete', 'button', 'company'
    -- 部门
    UNION ALL SELECT 'department:view', 'menu', 'company'
    UNION ALL SELECT 'department:create', 'button', 'company'
    UNION ALL SELECT 'department:update', 'button', 'company'
    UNION ALL SELECT 'department:delete', 'button', 'company'
    -- 用户
    UNION ALL SELECT 'user:read', 'menu', 'company'
    UNION ALL SELECT 'user:write', 'button', 'company'
    -- 租户自身信息
    UNION ALL SELECT 'tenant:read', 'menu', 'company'
    -- 小程序
    UNION ALL SELECT 'miniapp:view', 'menu', 'company'
    UNION ALL SELECT 'miniapp:operate', 'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);


-- ============================================================
-- Migration: V20260805001__revoke_tenant_read_from_customer_admin.sql
-- ============================================================
-- ---------------------------------------------------------------------------
-- FIX: 撤销 customer_admin 的 tenant:read 权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. tenant:read / tenant:write 是平台级权限，允许查看/操作所有租户信息。
--   2. customer_admin 是租户内管理员，不应拥有跨租户查看能力。
--   3. V20260804001 误将 tenant:read 补入 customer_admin 默认权限。
-- 修复：
--   1. 从所有 customer_admin 角色中删除 tenant:read 权限关联。
--   2. 不影响平台级角色（如 SUPER_ADMIN）的 tenant:read。
-- ---------------------------------------------------------------------------

DELETE rp
FROM sys_role_permission rp
INNER JOIN sys_custom_role scr ON rp.role_id = scr.id
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
  AND rp.permission_code = 'tenant:read';


-- ============================================================
-- Migration: V20260806001__add_company_read_write_to_customer_admin.sql
-- ============================================================
-- ---------------------------------------------------------------------------
-- FIX: 为 customer_admin 补全公司管理后端接口所需权限
-- ---------------------------------------------------------------------------
-- 背景：
--   1. 前端菜单/路由使用 company:view，按钮使用 company:create/update/delete。
--   2. 后端 CompanyController 实际使用 company:read（列表/详情/树）和
--      company:write（创建/更新）。
--   3. 已存在的 customer_admin 角色缺少 company:read 和 company:write，
--      导致租户管理员进入公司管理页面即提示无权限，且无法新增/编辑公司。
-- 修复：
--   1. 为所有未删除的 customer_admin 角色补充 company:read 和 company:write。
--   2. 保持幂等：ON DUPLICATE KEY UPDATE。
-- ---------------------------------------------------------------------------

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'company:read' AS permission_code, 'button' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'company:write', 'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);


-- ============================================================
-- Migration: V20260807001__backfill_company_sort_order.sql
-- ============================================================
-- 回填现有公司排序号
-- 问题：历史数据 sort_order 默认为 0，导致列表中多条记录排序相同。
-- 处理：按租户分组，根据创建时间（id 作为兜底）递增分配排序号。

UPDATE company c
  JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY created_at ASC, id ASC) AS rn
    FROM company
    WHERE deleted_at IS NULL
  ) t ON c.id = t.id
SET c.sort_order = t.rn
WHERE c.deleted_at IS NULL;


-- ============================================================
-- Migration: V20260808001__fix_sys_admin_account_unique_index.sql
-- ============================================================
-- 修复软删除账号后无法重新创建同名账号的问题
-- 原唯一索引 (tenant_id, username) 不包含删除标记，导致已软删除的账号仍占用用户名。
-- 通过增加一个用于唯一性校验的派生列（软删除为原时间，未删除为固定时间），
-- 使唯一索引能够区分“未删除”与“已软删除”记录。

ALTER TABLE sys_admin_account
    ADD COLUMN deleted_at_for_uk DATETIME AS (COALESCE(deleted_at, '1970-01-01 00:00:00')) STORED NOT NULL
        COMMENT '用于唯一索引区分软删除记录',
    DROP INDEX uk_tenant_username,
    ADD UNIQUE INDEX uk_tenant_username (tenant_id, username, deleted_at_for_uk);


-- ============================================================
-- Migration: V20260809001__renewal_order_cols.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260809002__vehicle_permissions.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：车辆管理菜单与续费按钮所需权限
-- =============================================================================
-- 背景：
--   1. 前端车辆管理页与“续费”按钮依赖 vehicle:view / vehicle:renew 等权限码
--   2. 后端 SysVehicleController 已使用 vehicle:create/update/delete/view 做鉴权
--   3. 续费接口新增 vehicle:renew 权限码
-- 修复：为 customer_admin / SUPER_ADMIN 角色补齐车辆相关权限（幂等：ON DUPLICATE KEY UPDATE）
-- =============================================================================

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'vehicle:view'   AS permission_code, 'menu'   AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'vehicle:create', 'button', 'company'
    UNION ALL SELECT 'vehicle:update', 'button', 'company'
    UNION ALL SELECT 'vehicle:delete', 'button', 'company'
    UNION ALL SELECT 'vehicle:renew',  'button', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);

INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':', perm.permission_code), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    perm.permission_code,
    perm.permission_type,
    'all' AS data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'vehicle:view'   AS permission_code, 'menu'   AS permission_type
    UNION ALL SELECT 'vehicle:create', 'button'
    UNION ALL SELECT 'vehicle:update', 'button'
    UNION ALL SELECT 'vehicle:delete', 'button'
    UNION ALL SELECT 'vehicle:renew',  'button'
) perm
WHERE scr.role_code = 'SUPER_ADMIN'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);


-- ============================================================
-- Migration: V20260809003__add_deleted_at_to_parking_record.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：为 parking_record 表添加 deleted_at 列（软删除支持）
-- =============================================================================
-- 背景：
--   MyBatis-Plus 逻辑删除拦截器（TenantLineInnerInterceptor）要求所有业务表
--   必须存在 deleted_at 列。parking_record 初始建表时遗漏该列，本次补加。
-- =============================================================================

-- 使用 information_schema + PREPARE 判断列是否存在后安全执行
-- E2E 联调期间该列已被手动添加，故使用条件判断确保幂等
SET @add_deleted_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE parking_record ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT ''软删除时间（NULL 表示未删除）'' AFTER updated_at',
        'SELECT 1 AS already_exists'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_record'
      AND COLUMN_NAME = 'deleted_at'
);
PREPARE stmt FROM @add_deleted_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- Migration: V20260809004__add_parking_record_id_to_parking_session.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：为 parking_session 表添加 parking_record_id 列
-- =============================================================================
-- 背景：
--   ParkingSession 实体（parking-system 模块）需要关联到对应的停车记录
--   （parking_record），以支持从 Session 追踪到完整停车链路。
--   该字段在 E2E 联调中发现缺失，本次补加。
-- =============================================================================

-- 使用 information_schema + PREPARE 判断列是否存在后安全执行
SET @add_parking_record_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE parking_session ADD COLUMN parking_record_id BIGINT DEFAULT NULL COMMENT ''关联停车记录 ID（逻辑外键：parking_record.id）'' AFTER plate_color',
        'SELECT 1 AS already_exists'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_session'
      AND COLUMN_NAME = 'parking_record_id'
);
PREPARE stmt FROM @add_parking_record_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- Migration: V20260815001__extend_parking_session_image_columns.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：延长 parking_session 图片列长度
-- =============================================================================
-- 背景：
--   臻识 C5H 相机的抓拍图片 URL（AliCloud OSS 签名 URL）超过 255 字符，
--   导致 DataTruncation 错误。entry_image 和 exit_image 列需扩展到 1024 字符。
-- =============================================================================

ALTER TABLE parking_session
    MODIFY COLUMN entry_image VARCHAR(1024) DEFAULT NULL COMMENT '入场抓拍图片URL（AliCloud OSS 签名URL，长度可达 1024）',
    MODIFY COLUMN exit_image VARCHAR(1024) DEFAULT NULL COMMENT '出场抓拍图片URL';


-- ============================================================
-- Migration: V20260815002__add_current_vehicles_to_parking_lot.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：为 parking_lot 表添加 current_vehicles 列
-- =============================================================================
-- 背景：
--   Entity 和业务代码已使用 current_vehicles 字段，但 DB 建表时遗漏。
--   该字段用于记录当前在场车辆数，配合 total_spaces 计算剩余车位。
-- =============================================================================

SET @current_vehicles_exists = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE parking_lot ADD COLUMN current_vehicles INT NOT NULL DEFAULT 0 COMMENT ''当前在场车辆数（用于计算剩余车位）'' AFTER total_spaces', 'SELECT 1 AS already_exists')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_lot'
      AND COLUMN_NAME = 'current_vehicles'
);
PREPARE stmt FROM @current_vehicles_exists;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- Migration: V20260815003__fix_utf8_double_encoding.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：修复 UTF-8 双重编码问题
-- =============================================================================
-- 背景：
--   测试数据入库时使用了错误的字符集，导致 UTF-8 编码的汉字字节被再次
--   以 UTF-8 编码存储。例如 "入口" 的 UTF-8 字节 E5 85 A5 E5 8F A3 被
--   当作 Latin-1 字符再次 UTF-8 编码，存入 C3 A5 E2 80 A6 C2 A5 ...
--   这导致前端展示时出现乱码（E2Eå…¥å£é€šé）。
--
-- 修复方式：
--   1. 用 latin1 解读当前字符串，得到原始 UTF-8 字节
--   2. 再以 utf8mb4 编码，恢复正确的中文
-- =============================================================================

-- 修复 parking_lot 表
UPDATE parking_lot
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);

-- 修复 parking_lane 表
UPDATE parking_lane
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);

-- 修复 device 表
UPDATE device
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);

-- 修复 parking_zone 表
UPDATE parking_zone
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);


-- ============================================================
-- Migration: V20260816001__create_mock_payment_tables.sql
-- ============================================================
-- =============================================================================
-- Phase 0 S0-3: 模拟支付系统 — 新建 mock_payment_config / mock_payment_record
-- =============================================================================
-- 设计目的：
-- 1. 彻底切断真实支付（4pyun.com），所有支付走内部模拟流程
-- 2. 按车场独立配置支付超时时间
-- 3. 完整记录模拟支付流水，支持运营端手动标记支付
-- =============================================================================

-- 模拟支付车场级配置
CREATE TABLE `mock_payment_config` (
    `id`               BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
    `parking_lot_id`   BIGINT       NOT NULL COMMENT '车场ID',
    `timeout_minutes`  INT          NOT NULL DEFAULT 15 COMMENT '支付超时时间（分钟）',
    `enabled`          TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用模拟支付',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `deleted_at`       DATETIME     DEFAULT NULL COMMENT '软删除时间',
    UNIQUE KEY `uk_lot` (`parking_lot_id`, `deleted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟支付车场配置';

-- 模拟支付流水记录
CREATE TABLE `mock_payment_record` (
    `id`               BIGINT       NOT NULL PRIMARY KEY COMMENT '雪花ID',
    `tenant_id`        BIGINT       NOT NULL COMMENT '租户ID',
    `parking_lot_id`   BIGINT       NOT NULL COMMENT '车场ID',
    `order_id`         BIGINT       NOT NULL COMMENT '关联订单ID',
    `plate_number`     VARCHAR(20)  NOT NULL COMMENT '车牌号',
    `amount`           DECIMAL(10,2) NOT NULL DEFAULT 0.00 COMMENT '支付金额（元）',
    `status`           TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1=待支付 2=已支付 3=已超时',
    `paid_at`          DATETIME     DEFAULT NULL COMMENT '支付时间',
    `paid_by`          VARCHAR(50)  DEFAULT NULL COMMENT '支付人（用户ID或系统标记）',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX `idx_order_id` (`order_id`),
    INDEX `idx_lot_plate` (`parking_lot_id`, `plate_number`),
    INDEX `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模拟支付流水';

-- 插入默认配置（为现有车场创建初始模拟支付配置）
-- 注意：此 INSERT 依赖于 parking_lot 表已有数据，如果表空则不插入
INSERT IGNORE INTO mock_payment_config (id, tenant_id, parking_lot_id, timeout_minutes, enabled, created_at, updated_at)
SELECT
    FLOOR(RAND() * 9000000000000000000) + 1000000000000000000 as id,
    pl.tenant_id,
    pl.id AS parking_lot_id,
    15 AS timeout_minutes,
    1 AS enabled,
    NOW() AS created_at,
    NOW() AS updated_at
FROM parking_lot pl
WHERE pl.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM mock_payment_config mpc WHERE mpc.parking_lot_id = pl.id AND mpc.deleted_at IS NULL);


-- ============================================================
-- Migration: V20260817001__create_vehicle_renewal_log.sql
-- ============================================================
-- =============================================================================
-- 车辆续费记录表（Phase 1 A1 — 月卡独立管理）
-- =============================================================================
-- 记录每次月卡续费操作的历史，包括续费月数、金额、续费前后有效期、操作人。
-- 与 sys_vehicle 的 valid_end_date 联动：每次续费成功后写入一条记录。
-- =============================================================================

CREATE TABLE IF NOT EXISTS vehicle_renewal_log (
    id              BIGINT          NOT NULL PRIMARY KEY COMMENT '雪花ID',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id  BIGINT          NOT NULL COMMENT '车场ID',
    vehicle_id      BIGINT          NOT NULL COMMENT '车辆ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号',
    order_id        BIGINT          DEFAULT NULL COMMENT '关联续费订单ID',
    renewal_months  INT             NOT NULL COMMENT '续费月数',
    amount_cents    INT             NOT NULL COMMENT '金额（分）',
    old_valid_end   DATE            DEFAULT NULL COMMENT '续费前有效期',
    new_valid_end   DATE            NOT NULL COMMENT '续费后有效期',
    operator_id     BIGINT          NOT NULL COMMENT '操作人ID',
    remark          VARCHAR(255)    DEFAULT NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL COMMENT '更新时间',
    deleted_at      DATETIME        DEFAULT NULL COMMENT '删除时间（软删除）',

    INDEX idx_vehicle   (vehicle_id),
    INDEX idx_plate     (plate_number),
    INDEX idx_lot       (parking_lot_id),
    INDEX idx_operator  (operator_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆续费记录';


-- ============================================================
-- Migration: V20260817002__create_fixed_space_binding.sql
-- ============================================================
-- Phase 1 A2：固定车位绑定关系表
-- 车位号 ↔ 车辆 专属绑定，非绑定车辆占用按临停计费
-- 用于固定车位车辆入场自动放行、出场不生成临停订单

CREATE TABLE fixed_space_binding (
    id BIGINT PRIMARY KEY COMMENT '雪花主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    parking_lot_id BIGINT NOT NULL COMMENT '车场ID',
    zone_id BIGINT DEFAULT NULL COMMENT '区域ID（可为空，不限制到区域）',
    space_no VARCHAR(20) NOT NULL COMMENT '具体车位号',
    vehicle_id BIGINT NOT NULL COMMENT '车辆ID（关联 sys_vehicle.id）',
    valid_start DATE NOT NULL COMMENT '有效期起',
    valid_end DATE NOT NULL COMMENT '有效期止',
    status TINYINT NOT NULL DEFAULT 1 COMMENT '状态：1=生效中, 2=已过期, 3=已注销',
    remark VARCHAR(255) DEFAULT NULL COMMENT '备注',
    created_at DATETIME(3) NOT NULL COMMENT '创建时间',
    updated_at DATETIME(3) NOT NULL COMMENT '更新时间',
    deleted_at DATETIME(3) DEFAULT NULL COMMENT '删除时间',
    UNIQUE KEY uk_space (parking_lot_id, zone_id, space_no, deleted_at),
    UNIQUE KEY uk_vehicle (parking_lot_id, vehicle_id, deleted_at),
    INDEX idx_tenant (tenant_id),
    INDEX idx_plate_via_vehicle (vehicle_id),
    INDEX idx_lot (parking_lot_id),
    INDEX idx_expiring (parking_lot_id, status, valid_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='固定车位绑定关系表';


-- ============================================================
-- Migration: V20260818001__order_admin_index.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：B1 订单中心管理索引优化
-- =============================================================================
-- 内容：
--   1. 添加 order_type + created_at 复合索引 —— 优化订单类型筛选查询
--   2. 添加 plate_number + created_at 复合索引 —— 优化车牌模糊查询后的排序
-- =============================================================================

ALTER TABLE parking_order
    ADD INDEX idx_order_type_created (order_type, created_at) COMMENT '按订单类型+创建时间查询（B1 订单中心）';


-- ============================================================
-- Migration: V20260818002__sys_config_enhance.sql
-- ============================================================
-- =============================================================================
-- A3: 系统参数管理 — 扩展 sys_config 表 + 默认参数注入
-- =============================================================================
-- 本迁移：
-- 1. 新增分组（group_name）、值类型（value_type）、枚举选项（options）字段
-- 2. 注入 Phase 1 A3 定义的全部 8 个系统参数的默认记录
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 扩展 sys_config 结构
-- ---------------------------------------------------------------------------
ALTER TABLE sys_config
    ADD COLUMN group_name VARCHAR(50) NOT NULL DEFAULT 'default' COMMENT '参数分组' AFTER description,
    ADD COLUMN value_type VARCHAR(20) NOT NULL DEFAULT 'STRING' COMMENT '值类型：STRING / INT / BOOLEAN / ENUM' AFTER group_name,
    ADD COLUMN options TEXT COMMENT 'ENUM 类型的可选值，JSON 数组，如 ["1","2","3"]' AFTER value_type;

-- ---------------------------------------------------------------------------
-- 2. 注入默认系统参数
-- ---------------------------------------------------------------------------

-- ===== 基础设置 =====
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('available_space.refresh_interval_minutes', '5', '余位刷新间隔（分钟）', '基础设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('vehicle.bind_limit_per_user', '5', '用户车辆绑定数量上限', '基础设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

-- ===== 计费设置 =====
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('monthly_pass.count_in_available_space', 'false', '月卡是否计入余位', '计费设置', 'BOOLEAN', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('monthly_pass.expiry_reminder_days', '7', '月卡到期提醒天数', '计费设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('booth.fee_reduction_threshold_cents', '50000', '费用减免超 X 元需二次确认（单位：分）', '计费设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

-- ===== 告警设置 =====
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, options, created_at, updated_at)
VALUES ('blacklist.trigger_mode', '1', '黑名单触发模式：1=禁止入场, 2=允许但告警, 3=按类型区分', '告警设置', 'ENUM', '["1","2","3"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('remote_gate.alert_auto_dismiss_seconds', '10', '远程开闸弹窗自动消失秒数', '告警设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

-- ===== 岗亭设置 =====
-- mock_payment.timeout_minutes 已在 mock_payment_config 表中按车场配置，此处仅做全局兜底
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('mock_payment.timeout_minutes', '15', '模拟支付超时（分钟），按车场配置时优先使用 mock_payment_config 表', '岗亭设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);


-- ============================================================
-- Migration: V20260818003__add_remote_gate_permission.sql
-- ============================================================
-- =============================================================================
-- B2: 运营端远程开闸 — 添加 device:remote:open 权限
-- =============================================================================
-- 背景：
--   Phase 1 B2 为运营端提供按车道远程开闸能力。
--   新增 device:remote:open 权限码，控制远程开闸功能的访问。
-- 安全：
--   - 超级管理员（super_admin）默认拥有
--   - 设备维护员（device_maintenance）、岗亭操作员（booth_operator）默认拥有
--   - 租户管理员（customer_admin）默认不拥有，需超管在自定义角色中手动勾选
--   - 固定角色（parking_manager 等）不自动获得此权限
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 插入权限定义（幂等）
-- ---------------------------------------------------------------------------
INSERT INTO sys_permission (code, name, description)
VALUES ('device:remote:open', '远程开闸', '运营端远程开启道闸')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- ---------------------------------------------------------------------------
-- 2. 为 SUPER_ADMIN 自定义角色授权
-- ---------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':device:remote:open'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    'device:remote:open' AS permission_code,
    'button' AS permission_type,
    'all' AS data_scope
FROM sys_custom_role scr
WHERE scr.role_code = 'SUPER_ADMIN'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);


-- ============================================================
-- Migration: V20260818004__add_record_view_permission.sql
-- ============================================================
-- =====================================================
-- Phase 2 D1: 新增通行记录查看权限（record:view）
-- =====================================================

-- 1. 插入权限记录
INSERT INTO sys_permission (code, name, description)
SELECT 'record:view', '通行记录查看', '查看和导出通行记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'record:view');

-- 2. 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(cr.id, ':record:view'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'record:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'record:view');


-- ============================================================
-- Migration: V20260818005__add_exception_record.sql
-- ============================================================
-- =====================================================
-- Phase 2 D2: 新增异常记录表 + 权限
-- =====================================================

-- 1. 创建异常记录表
CREATE TABLE IF NOT EXISTS exception_record (
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id           BIGINT       NOT NULL                COMMENT '租户 ID',
    parking_lot_id      BIGINT       NOT NULL                COMMENT '停车场 ID',
    lane_id             BIGINT       DEFAULT NULL            COMMENT '通道 ID',
    exception_type      VARCHAR(32)  NOT NULL                COMMENT '异常类型：DUP_ENTRY-重复入场, RECOGNITION_FAIL-识别失败, BLACKLIST-黑名单告警, UNPAID_INTERCEPT-未支付拦截',
    plate_number        VARCHAR(32)  DEFAULT NULL            COMMENT '车牌号',
    status              VARCHAR(16)  NOT NULL DEFAULT 'UNHANDLED' COMMENT '处理状态：UNHANDLED-未处理, HANDLED-已处理',
    description         VARCHAR(512) DEFAULT NULL            COMMENT '异常描述',
    handled_at          DATETIME     DEFAULT NULL            COMMENT '处理时间',
    handler             BIGINT       DEFAULT NULL            COMMENT '处理人（sys_user.id）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at          DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at          DATETIME     DEFAULT NULL            COMMENT '逻辑删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='异常记录表（运营端异常管理）';

CREATE INDEX idx_exception_lot_status ON exception_record(parking_lot_id, status);
CREATE INDEX idx_exception_type ON exception_record(exception_type);
CREATE INDEX idx_exception_created_at ON exception_record(created_at);

-- 2. 插入权限记录
INSERT INTO sys_permission (code, name, description)
SELECT 'exception:view', '异常记录查看', '查看和处理异常记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'exception:view');

-- 3. 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(cr.id, ':exception:view'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'exception:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'exception:view');


-- ============================================================
-- Migration: V20260818006__add_manual_gate_columns_and_permission.sql
-- ============================================================
-- =====================================================
-- Phase 2 D3: device_command_audit 补充字段 + 权限
-- =====================================================

-- 1. 添加车牌号字段（手动开闸时记录关联车辆）
ALTER TABLE device_command_audit
    ADD COLUMN plate_number VARCHAR(32) DEFAULT NULL COMMENT '关联车牌号（手动开闸时记录）' AFTER command_type;

-- 2. 添加费用字段（分，手工计费时记录）
ALTER TABLE device_command_audit
    ADD COLUMN fee_cents INT DEFAULT NULL COMMENT '费用（分，手工计费时记录）' AFTER plate_number;

-- 3. 插入 device:audit 权限
INSERT INTO sys_permission (code, name, description)
SELECT 'device:audit', '设备命令审计', '查看设备命令调用审计记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'device:audit');

-- 4. 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(cr.id, ':device:audit'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'device:audit', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'device:audit');


-- ============================================================
-- Migration: V20260818007__add_dashboard_view_permission.sql
-- ============================================================
-- =====================================================
-- Phase 2 D4: 新增仪表盘查看权限（dashboard:view）
-- =====================================================

INSERT INTO sys_permission (code, name, description)
SELECT 'dashboard:view', '仪表盘查看', '查看首页仪表盘数据'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'dashboard:view');

-- 为 SUPER_ADMIN 自定义角色授权
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(cr.id, ':dashboard:view'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    cr.id, 'dashboard:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'dashboard:view');


-- ============================================================
-- Migration: V20260818008__create_proxy_pay_record.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260818009__create_mini_message.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260819001__recreate_remaining_spaces.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：为 parking_lot 表重建 remaining_spaces 列
-- =============================================================================
-- 背景：
--   V20260724001 移除了 remaining_spaces（计划改为区域汇总+Redis 实时计算），
--   但区域汇总方案未落地（ParkingSpacePolicyServiceImpl.calculateRemain 仍为 usedSpaces=0 占位），
--   且旧包 EntryService/ExitService/DashboardMapper 等仍有 remaining_spaces 读写。
--   本期决策（D1-方案A）：重建 remaining_spaces 列，进出场链路继续维护该列，
--   保持与 current_vehicles 同步一致（total_spaces - current_vehicles = remaining_spaces）。
--
-- 一致性校验 SQL（可按需调度执行）：
--   SELECT id, name, total_spaces, current_vehicles, remaining_spaces,
--          (total_spaces - current_vehicles) AS expected_remaining
--   FROM parking_lot
--   WHERE remaining_spaces != (total_spaces - current_vehicles);
-- =============================================================================

SET @remaining_spaces_exists = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE parking_lot ADD COLUMN remaining_spaces INT NOT NULL DEFAULT 0 COMMENT ''剩余车位数（total_spaces - current_vehicles，允许人工修正）'' AFTER current_vehicles', 'SELECT 1 AS already_exists')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_lot'
      AND COLUMN_NAME = 'remaining_spaces'
);
PREPARE stmt FROM @remaining_spaces_exists;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- Migration: V20260820001__task_0101_parking_lot_param.sql
-- ============================================================
-- =============================================================================
-- 任务包 1-1：车场级参数层 —— sys_config 参数分层 + 7 项车场级参数 + mock_payment_config 超时迁移
-- =============================================================================
-- 需求依据：V1.1 3.1.2（ADMIN-002 车场级参数表）、6.1 数据字典（参数层级 1=全局/2=车场级）
--
-- 模型取舍（方案 A）：扩展 sys_config 而非新建表，改动最小、复用实体/元数据列。
--   - 采用 parking_lot_id=0 作为"全局"哨兵（而非 NULL）：MySQL 唯一索引将多个 NULL 视为互异，
--     无法约束全局行唯一，故用 0 哨兵使唯一键 (config_key, parking_lot_id) 正确生效。
--   - param_level：1=全局，2=车场级。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. sys_config 结构扩展（现有 8 行经默认值自动成为全局行 parking_lot_id=0 / param_level=1）
-- ---------------------------------------------------------------------------
ALTER TABLE sys_config
    ADD COLUMN parking_lot_id BIGINT NOT NULL DEFAULT 0 COMMENT '归属车场ID，0=全局' AFTER config_value,
    ADD COLUMN param_level TINYINT NOT NULL DEFAULT 1 COMMENT '参数层级：1=全局，2=车场级' AFTER parking_lot_id;

-- ---------------------------------------------------------------------------
-- 2. 唯一键调整：(config_key) -> (config_key, parking_lot_id)
-- ---------------------------------------------------------------------------
ALTER TABLE sys_config DROP INDEX uk_config_key;
ALTER TABLE sys_config ADD UNIQUE KEY uk_key_lot (config_key, parking_lot_id) COMMENT '同一参数在同一层级唯一';

-- ---------------------------------------------------------------------------
-- 3. 注入 7 项车场级参数的"全局默认行"（可被车场覆盖）
--    - 已存在的 3 项（mock_payment.timeout_minutes / monthly_pass.expiry_reminder_days /
--      monthly_pass.count_in_available_space）来自 V20260818002，此处仅对齐分组/类型。
--    - 新增 4 项（未支付出场 / 欠费再出场 / 识别失败 / 支付出场窗口期）。
-- ---------------------------------------------------------------------------

-- 对齐已有 3 项的分组/类型（幂等）
UPDATE sys_config SET group_name = '岗亭设置', value_type = 'INT'
    WHERE config_key = 'mock_payment.timeout_minutes' AND parking_lot_id = 0;
UPDATE sys_config SET group_name = '计费设置', value_type = 'INT'
    WHERE config_key = 'monthly_pass.expiry_reminder_days' AND parking_lot_id = 0;
UPDATE sys_config SET group_name = '计费设置', value_type = 'BOOLEAN'
    WHERE config_key = 'monthly_pass.count_in_available_space' AND parking_lot_id = 0;

-- 新增 4 项全局默认行
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('pay.exit_window_minutes', '15', 0, 1, '支付后出场窗口期（分钟）', '出场设置', 'INT', NULL, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);

INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('exit.unpaid_strategy', 'BLOCK', 0, 1, '未支付出场策略：BLOCK=拦截, ALLOW_ARREARS=允许欠费放行', '出场设置', 'ENUM', '["BLOCK","ALLOW_ARREARS"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('arrears.reexit_strategy', 'MUST_PAY', 0, 1, '欠费车辆再次出场策略：MUST_PAY=必须补缴, REMIND_ONLY=仅提醒', '出场设置', 'ENUM', '["MUST_PAY","REMIND_ONLY"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('recognition.fail_strategy', 'MANUAL', 0, 1, '识别失败处理策略：MANUAL=人工处理, AUTO_RELEASE=自动放行', '出场设置', 'ENUM', '["MANUAL","AUTO_RELEASE"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);

-- ---------------------------------------------------------------------------
-- 4. mock_payment_config.timeout_minutes 迁移到车场级参数体系
--    仅迁移"非默认(≠15)"的车场覆盖，等于默认的车场继续继承全局 15（生效值不变、避免冗余覆盖行）。
-- ---------------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, created_at, updated_at)
SELECT 'mock_payment.timeout_minutes', CAST(mpc.timeout_minutes AS CHAR), mpc.parking_lot_id, 2,
       '模拟支付超时（分钟）', '岗亭设置', 'INT', NOW(), NOW()
FROM mock_payment_config mpc
WHERE mpc.deleted_at IS NULL
  AND mpc.timeout_minutes IS NOT NULL
  AND mpc.timeout_minutes <> 15
ON DUPLICATE KEY UPDATE config_value = VALUES(config_value), param_level = VALUES(param_level), updated_at = NOW();

-- ---------------------------------------------------------------------------
-- 5. 标记 mock_payment_config.timeout_minutes 列为废弃（读取源已切换至 sys_config 车场级参数）
-- ---------------------------------------------------------------------------
ALTER TABLE mock_payment_config
    MODIFY COLUMN timeout_minutes INT NOT NULL DEFAULT 15
    COMMENT '[已废弃-任务包1-1] 超时改由 sys_config(mock_payment.timeout_minutes) 车场级参数管理，本列仅兼容同步';


-- ============================================================
-- Migration: V20260821001__task_0102_order_status_and_refund.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260822001__billing_effect_type.sql
-- ============================================================
-- =============================================================================
-- 任务包 1-3：计费体系统一 — billing_rule 生效方式（ADMIN-010）
-- =============================================================================
-- 需求依据：V1.1 3.1.10、7.1、确认项 34
--
-- 变更内容：
--   1. billing_rule 增加 effect_type（IMMEDIATE/NEW_ENTRY_ONLY/SCHEDULED）
--      与 effect_time（定时生效时间）
--   2. parking_record 增加 rule_snapshot（JSON，仅存计费必需字段），
--      用于 NEW_ENTRY_ONLY 模式下已在场车辆按入场时规则快照计费
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. billing_rule：增加生效方式与定时生效时间
-- -----------------------------------------------------------------------------
ALTER TABLE billing_rule
    ADD COLUMN effect_type VARCHAR(20) NOT NULL DEFAULT 'IMMEDIATE' COMMENT '生效方式：IMMEDIATE-立即生效（含已在场车辆）, NEW_ENTRY_ONLY-仅新入场生效, SCHEDULED-定时生效' AFTER is_default,
    ADD COLUMN effect_time DATETIME COMMENT '定时生效时间（effect_type=SCHEDULED 时必填）' AFTER effect_type;
CREATE INDEX idx_effect_type ON billing_rule (effect_type);

-- -----------------------------------------------------------------------------
-- 2. parking_record：增加规则快照（仅 NEW_ENTRY_ONLY 时使用）
-- -----------------------------------------------------------------------------
ALTER TABLE parking_record
    ADD COLUMN rule_snapshot JSON COMMENT '入场时的计费规则快照（仅 NEW_ENTRY_ONLY 生效方式下使用，存计费必需字段：ruleType, freeMinutes, firstPeriod, firstAmount, unitPeriod, unitAmount, dailyCap, maxAmount, timeSegments）' AFTER fee_rule_version;


-- ============================================================
-- Migration: V20260823001__task_0201_pay_window.sql
-- ============================================================
-- 任务包 2-1：区分出口缴费与提前缴费，支付出场窗口期机制
-- 新增字段：pay_window_deadline（支付窗口截止时间）、pay_scene（支付场景）、
--           recalc_source_order_id（重算来源原订单号）、exit_lane_id（出口车道ID）
ALTER TABLE parking_order
    ADD COLUMN pay_window_deadline DATETIME       NULL COMMENT '支付窗口截止时间（ADVANCE场景，now+窗口期分钟数）',
    ADD COLUMN pay_scene           VARCHAR(20)    NULL COMMENT '支付场景：AT_EXIT=出口缴费 / ADVANCE=提前缴费',
    ADD COLUMN recalc_source_order_id BIGINT      NULL COMMENT '重算来源原订单号（超期重算新订单关联原订单）',
    ADD COLUMN exit_lane_id        BIGINT         NULL COMMENT '出口车道ID（AT_EXIT场景用于开闸）';

-- 窗口期加速查询：PAID + deadline 范围内的订单
CREATE INDEX idx_order_window_deadline ON parking_order (pay_window_deadline, status);


-- ============================================================
-- Migration: V20260824001__extend_billing_rule_recalc_log.sql
-- ============================================================
-- 扩展 billing_rule_recalc_log 支持超时重算场景
ALTER TABLE billing_rule_recalc_log
    ADD COLUMN original_order_id      BIGINT       NULL COMMENT '原订单 ID（重算来源）' AFTER parking_record_id,
    ADD COLUMN original_amount_cents  INT          NULL COMMENT '原订单金额（分）' AFTER fee_cents,
    ADD COLUMN new_amount_cents       INT          NULL COMMENT '重新计算金额（分）' AFTER original_amount_cents,
    ADD COLUMN trigger_reason         VARCHAR(64)  NULL COMMENT '重算触发原因：EXIT_RESCAN=出场重识别, TIMEOUT_RECALC=超时关单后重算' AFTER new_amount_cents;

CREATE INDEX idx_recalc_original_order_id ON billing_rule_recalc_log(original_order_id);
CREATE INDEX idx_recalc_trigger_reason   ON billing_rule_recalc_log(trigger_reason);

-- 超时重算场景下无需规则版本和操作人，改为可为 NULL
ALTER TABLE billing_rule_recalc_log
    MODIFY COLUMN rule_version_id BIGINT NULL COMMENT '切换后的规则版本 ID（超时重算场景可为 NULL）',
    MODIFY COLUMN operator_id     BIGINT NULL COMMENT '操作人 ID（系统触发可为 NULL）';


-- ============================================================
-- Migration: V20260825001__add_arrears_order_ids.sql
-- ============================================================
-- 新增 arrears_order_ids 字段，存储合并订单关联的欠费订单 ID 列表（JSON 数组）
ALTER TABLE parking_order
    ADD COLUMN arrears_order_ids TEXT NULL COMMENT '关联的欠费订单ID列表（JSON数组），合并计费时记录，如 [101,102]' AFTER recalc_source_order_id;


-- ============================================================
-- Migration: V20260826001__create_monthly_pass.sql
-- ============================================================
-- =============================================================================
-- 任务包 3-1：月卡独立实体 — 建表 + 存量迁移 + vehicle_renewal_log 扩展
-- =============================================================================

-- Step 1: 创建 monthly_pass 表
CREATE TABLE monthly_pass (
    id               BIGINT          NOT NULL PRIMARY KEY COMMENT '主键（Snowflake）',
    tenant_id        BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id   BIGINT          NOT NULL COMMENT '车场ID',
    plate_number     VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    plate_color      VARCHAR(10)     DEFAULT NULL COMMENT '车牌颜色',
    vehicle_type     VARCHAR(20)     DEFAULT NULL COMMENT '车辆类型（小型车/大型车等）',
    valid_start_date DATE            NOT NULL COMMENT '有效期起',
    valid_end_date   DATE            NOT NULL COMMENT '有效期止',
    amount_cents     INT             NOT NULL DEFAULT 0 COMMENT '费用（分）',
    paid_amount_cents INT            NOT NULL DEFAULT 0 COMMENT '实收金额（分）',
    pay_method       VARCHAR(20)     NOT NULL COMMENT '缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER',
    pass_status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '月卡状态：ACTIVE-生效中 / EXPIRED-已过期 / CANCELLED-已注销',
    applicant_id     BIGINT          DEFAULT NULL COMMENT '申请人ID（小程序用户ID）',
    source           VARCHAR(20)     NOT NULL COMMENT '来源：ADMIN-运营端 / MINIAPP-小程序端',
    owner_name       VARCHAR(30)     DEFAULT NULL COMMENT '车主姓名',
    owner_phone      VARCHAR(20)     DEFAULT NULL COMMENT '车主电话',
    remark           VARCHAR(200)    DEFAULT NULL COMMENT '备注',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at       DATETIME(3)     DEFAULT NULL COMMENT '软删除时间',

    INDEX idx_tenant     (tenant_id),
    INDEX idx_plate      (plate_number),
    INDEX idx_lot        (parking_lot_id),
    INDEX idx_status     (pass_status),
    INDEX idx_lot_plate  (parking_lot_id, plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月卡独立实体表';

-- Step 2: 存量数据迁移（sys_vehicle.vehicleType=MONTHLY → monthly_pass）
INSERT INTO monthly_pass (
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    amount_cents, paid_amount_cents, pay_method,
    pass_status, applicant_id, source,
    owner_name, owner_phone, remark,
    created_at, updated_at
)
SELECT
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    0, 0, 'OTHER',
    CASE
        WHEN status = 'ACTIVE' AND valid_end_date >= CURDATE() THEN 'ACTIVE'
        ELSE 'EXPIRED'
    END,
    NULL, 'ADMIN',
    owner_name, owner_phone, remark,
    COALESCE(created_at, NOW()), NOW()
FROM sys_vehicle
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 3: 改写旧 MONTHLY 记录（旧判断口径下线）
UPDATE sys_vehicle
SET vehicle_type = 'FREE',
    status = 'EXPIRED',
    updated_at = NOW()
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 4: vehicle_renewal_log 扩展
ALTER TABLE vehicle_renewal_log
    ADD COLUMN monthly_pass_id BIGINT DEFAULT NULL COMMENT '月卡ID（新体系）' AFTER vehicle_id;


-- ============================================================
-- Migration: V20260827001__add_fixed_space_columns.sql
-- ============================================================
-- 任务包 3-2：固定车位补齐审核流/缴费方式/唯一校验/到期任务
-- 在 fixed_space_binding 表上新增字段与索引

ALTER TABLE fixed_space_binding
    ADD COLUMN pay_method VARCHAR(20) DEFAULT NULL COMMENT '缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER' AFTER valid_end,
    ADD COLUMN paid_amount_cents INT NOT NULL DEFAULT 0 COMMENT '实收金额（分）' AFTER pay_method,
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'PENDING' COMMENT '审核状态：PENDING-待审核 / APPROVED-已通过 / REJECTED-已驳回' AFTER paid_amount_cents,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ADMIN' COMMENT '来源：ADMIN-运营端 / MINIAPP-小程序端' AFTER review_status,
    ADD COLUMN applicant_id BIGINT DEFAULT NULL COMMENT '申请人ID（小程序用户ID；运营端录入为NULL）' AFTER source;

CREATE INDEX idx_fsb_review_status ON fixed_space_binding (review_status);


-- ============================================================
-- Migration: V20260828001__create_vehicle_list.sql
-- ============================================================
-- ============================================================
-- 1. 创建 vehicle_list 表
-- ============================================================
CREATE TABLE IF NOT EXISTS vehicle_list (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    list_type       VARCHAR(16)     NOT NULL COMMENT '名单类型：BLACK / WHITE',
    parking_lot_id  BIGINT          NOT NULL COMMENT '生效车场ID',
    start_date      DATE            NULL COMMENT '有效期开始（NULL=立即生效）',
    end_date        DATE            NULL COMMENT '有效期结束（NULL=永久）',
    trigger_type    VARCHAR(32)     NULL COMMENT '黑名单触发类型：ARREARS / MANAGEMENT / OTHER（白名单为NULL）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / DISABLED',
    remark          VARCHAR(255)    NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME        NULL COMMENT '软删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车辆黑白名单表';

-- 索引
CREATE UNIQUE INDEX uk_lot_plate_type ON vehicle_list (parking_lot_id, plate_number, list_type);
CREATE INDEX idx_plate_number ON vehicle_list (plate_number);
CREATE INDEX idx_parking_lot_id ON vehicle_list (parking_lot_id);
CREATE INDEX idx_list_type_status ON vehicle_list (list_type, status);

-- ============================================================
-- 2. 迁移 AccessPolicy BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    ap.tenant_id,
    UPPER(ap.policy_key) AS plate_number,
    'BLACK'               AS list_type,
    ap.parking_lot_id,
    NULL                  AS start_date,
    NULL                  AS end_date,
    'OTHER'               AS trigger_type,
    'ACTIVE'              AS status,
    CONCAT('迁移自access_policy: ', COALESCE(ap.description, '')) AS remark,
    ap.created_at,
    NOW()
FROM access_policy ap
WHERE ap.policy_type = 'BLACKLIST'
  AND ap.status = 'ACTIVE'
  AND ap.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = ap.parking_lot_id
        AND vl.plate_number  = UPPER(ap.policy_key)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 3. 迁移 sys_vehicle BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'BLACK'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    'OTHER'                AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle BLACKLIST: ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type = 'BLACKLIST'
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 4. 迁移 sys_vehicle VIP/SUPER/FREE → vehicle_list（白名单）
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'WHITE'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    NULL                   AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle ', sv.vehicle_type, ': ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type IN ('VIP', 'SUPER', 'FREE')
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'WHITE'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 5. 插入全局参数默认值
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_mode', 'DENY_ENTRY',
        '黑名单触发模式：DENY_ENTRY-禁止入场 / ALLOW_WITH_ALERT-允许但告警 / BY_TYPE-按类型区分',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_types',
        '[{"code":"ARREARS","label":"欠费类"},{"code":"MANAGEMENT","label":"管理类"},{"code":"OTHER","label":"其他类"}]',
        '黑名单触发类型字典（JSON数组）',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();


-- ============================================================
-- Migration: V20260901001__add_camera_source.sql
-- ============================================================
-- =============================================================================
-- 任务包 3-5：识别事件/停车记录/出场记录新增相机来源标记
-- =============================================================================

ALTER TABLE recognition_event_log
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE parking_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '入场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE exit_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '出场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';


-- ============================================================
-- Migration: V20260902001__phase4_fee_reduce_permission.sql
-- ============================================================
-- Phase 4-1: 新增 fee:reduce 权限（费用减免独立权限码，最小权限原则）
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:reduce', '费用减免', '对收费金额进行减免，需要二次确认超阈值操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:reduce');

-- 为 booth_operator 角色授权（通过 sys_custom_role 查找 role_id，兼容新旧 schema）
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT scr.id, 'fee:reduce', 'button', 'self'
FROM sys_custom_role scr
WHERE scr.role_code = 'booth_operator'
  AND scr.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = scr.id AND srp.permission_code = 'fee:reduce'
  );

-- 为 parking_manager 角色授权
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT scr.id, 'fee:reduce', 'button', 'parking'
FROM sys_custom_role scr
WHERE scr.role_code = 'parking_manager'
  AND scr.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = scr.id AND srp.permission_code = 'fee:reduce'
  );


-- ============================================================
-- Migration: V20260902002__phase4_shift_record_extend.sql
-- ============================================================
-- Phase 4-2: shift_record 交接班扩展
-- 1. 新增 adjust_reason 列（手工校正实收金额原因）
ALTER TABLE shift_record
    ADD COLUMN adjust_reason VARCHAR(200) DEFAULT NULL COMMENT '手工校正实收金额原因' AFTER online_amount;

-- 2. 新增 arrears_count 列（本班产生的欠费订单数）
ALTER TABLE shift_record
    ADD COLUMN arrears_count INT NOT NULL DEFAULT 0 COMMENT '本班产生的欠费订单数' AFTER exception_count;

-- 3. 新增 handover_order_count 列（交接给下一班的未支付/欠费订单数）
ALTER TABLE shift_record
    ADD COLUMN handover_order_count INT NOT NULL DEFAULT 0 COMMENT '交接给下一班的未支付/欠费订单数' AFTER arrears_count;


-- ============================================================
-- Migration: V20260902003__correct_plate_fields.sql
-- ============================================================
-- V20260902003__correct_plate_fields.sql
-- 向 recognition_event_log 添加车牌校正字段

ALTER TABLE recognition_event_log
    ADD COLUMN corrected_plate VARCHAR(20) NULL COMMENT '校正后车牌号' AFTER camera_source,
    ADD COLUMN correction_type VARCHAR(30) NULL COMMENT '校正类型：MANUAL_CORRECTION' AFTER corrected_plate,
    ADD COLUMN corrected_at DATETIME NULL COMMENT '校正操作时间' AFTER correction_type,
    ADD COLUMN corrector_id BIGINT NULL COMMENT '校正人ID（sys_user.id）' AFTER corrected_at;


-- ============================================================
-- Migration: V20260902004__webhook_secret.sql
-- ============================================================
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


-- ============================================================
-- Migration: V20260902005__device_camera_direction_role.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：设备/车道模型扩展 — 识别方向 + 主备关系 + 解除一车道一相机限制
-- =============================================================================
-- 0-3｜ADMIN-004 双向通道 + ADMIN-005 主备/识别方向
-- 1. 移除 uq_lane_device_type 唯一索引（允许同一车道多台同类型设备）
-- 2. 新增 recognition_direction（识别方向）和 camera_role（主备角色）字段
-- 3. 存量单向车道相机数据回填（入口=1, 出口=2，双向=NULL需人工补录）
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 删除唯一索引 uq_lane_device_type（保留 lane_id 普通索引）
--    V20260711010 创建了 UNIQUE INDEX uq_lane_device_type(lane_id, device_type)
--    此索引阻止同一车道绑定多台 CAMERA（无法配置双向双相机或主备双相机）
-- -----------------------------------------------------------------------------
DROP INDEX uq_lane_device_type ON device;

-- -----------------------------------------------------------------------------
-- 2. 新增识别方向字段
--    1=入场(ENTRY), 2=出场(EXIT), NULL=未指定
--    双向通道绑定的相机必须指定识别方向
-- -----------------------------------------------------------------------------
ALTER TABLE device
    ADD COLUMN recognition_direction TINYINT DEFAULT NULL COMMENT '识别方向：1=入场, 2=出场, NULL=未指定（单向通道可推断，双向通道必填）' AFTER device_type,
    ADD COLUMN camera_role TINYINT DEFAULT NULL COMMENT '主备角色：1=主相机, 2=备相机, NULL=单相机模式或无主备' AFTER recognition_direction;

-- -----------------------------------------------------------------------------
-- 3. 索引
-- -----------------------------------------------------------------------------
CREATE INDEX idx_recognition_direction ON device (recognition_direction);
CREATE INDEX idx_camera_role ON device (camera_role);
CREATE INDEX idx_lane_direction_role ON device (lane_id, recognition_direction, camera_role);

-- -----------------------------------------------------------------------------
-- 4. 存量数据回填：单向车道相机 recognition_direction 按车道类型回填
--    入口车道(type=1) → 相机 recognition_direction=1
--    出口车道(type=2) → 相机 recognition_direction=2
--    双向车道(type=3) → 不自动回填（NULL，需人工补录）
-- -----------------------------------------------------------------------------
UPDATE device d
    INNER JOIN parking_lane l ON d.lane_id = l.id AND l.type IN (1, 2)
SET d.recognition_direction = CASE WHEN l.type = 1 THEN 1 WHEN l.type = 2 THEN 2 END
WHERE d.device_type = 'CAMERA' AND d.recognition_direction IS NULL;

-- 回填日志：输出需要人工补录的双向车道相机清单
-- 以下查询可在执行迁移后手动运行以识别需要人工补录的设备：
-- SELECT d.id AS device_id, d.name, d.device_sn, l.id AS lane_id, l.name AS lane_name, l.type AS lane_direction
-- FROM device d INNER JOIN parking_lane l ON d.lane_id = l.id AND l.type = 3
-- WHERE d.device_type = 'CAMERA' AND d.recognition_direction IS NULL;


-- ============================================================
-- Migration: V20260902006__add_temp_plate_flag.sql
-- ============================================================
-- =============================================================================
-- 任务包 3-4：无牌车临时车牌 — 三表新增 temp_plate_flag 字段
-- =============================================================================

ALTER TABLE parking_record
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE parking_order
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE recognition_event_log
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';


-- ============================================================
-- Migration: V20260902007__package_6_2_account_enhance.sql
-- ============================================================
-- =============================================================================
-- 任务包 6-2：账号体系收口 — sys_admin_account 扩展 + 停车场多对多 + 废弃标记
-- =============================================================================
-- 1. sys_admin_account 新增字段
-- 2. sys_admin_account_parking_lot 多对多关联表
-- 3. employee / sys_user / employee_parking_lot 废弃标记

-- ---------------------------------------------------------------------------
-- 1. sys_admin_account 新增字段
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码：0=否，1=是' AFTER lock_until,
    ADD COLUMN allow_fee_reduction TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许费用减免：0=否，1=是（仅岗亭管理员生效）' AFTER must_change_password;

-- 已有 level=3 的停车场管理员兼容：全部设为不强制改密、不允减免
UPDATE sys_admin_account SET must_change_password = 0, allow_fee_reduction = 0 WHERE level = 3;

-- 从 employee 表迁移数据到 sys_admin_account 的辅助：先不做数据迁移，仅建结构
-- （数据迁移在 Part L 中作为单独任务执行，确保先在测试环境验证）

-- ---------------------------------------------------------------------------
-- 2. sys_admin_account_parking_lot 表：管理员 ↔ 停车场 多对多关联
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_admin_account_parking_lot (
    id              BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    admin_account_id BIGINT      NOT NULL COMMENT '管理员账号 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_admin_account (admin_account_id),
    INDEX idx_parking_lot (parking_lot_id),
    UNIQUE KEY uk_account_lot (admin_account_id, parking_lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员账号-停车场关联表（岗亭管理员多车场支持）';

-- ---------------------------------------------------------------------------
-- 3. 标记旧表为废弃（不删表，仅添加注释）
--    注：employee / employee_parking_lot 表暂未创建，仅标记 sys_user
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user COMMENT = '[已废弃-任务包6-2] 旧用户表已合并至 sys_admin_account';


-- ============================================================
-- Migration: V20260902008__package_6_2_review_columns.sql
-- ============================================================
-- =============================================================================
-- 任务包 6-2：月卡/固定车位审核 — monthly_pass 新增 review_status + review_remark
-- 注：fixed_space_binding 已有 review_status 列（FixedSpaceBinding.reviewStatus），
--     本迁移仅补齐 review_remark；支付方式/金额使用现有 pay_method / paid_amount_cents。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. monthly_pass 新增审核字段
-- ---------------------------------------------------------------------------
ALTER TABLE monthly_pass
    ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：PENDING=待审核, APPROVED=已通过, REJECTED=已驳回' AFTER pass_status,
    ADD COLUMN review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注' AFTER review_status;

-- 存量月卡默认已通过
UPDATE monthly_pass SET review_status = 'APPROVED' WHERE review_status IS NULL;

-- ---------------------------------------------------------------------------
-- 2. fixed_space_binding 补齐 review_remark
-- ---------------------------------------------------------------------------
ALTER TABLE fixed_space_binding
    ADD COLUMN review_remark VARCHAR(255) DEFAULT NULL COMMENT '审核备注' AFTER review_status;

-- 存量固定车位 review_status 已有值，确认默认 APPROVED
UPDATE fixed_space_binding SET review_status = 'APPROVED' WHERE review_status IS NULL AND status IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. 插入审核模式全局参数（若未存在）
-- ---------------------------------------------------------------------------
INSERT INTO sys_config (config_key, config_value, parking_lot_id, param_level, description, group_name, value_type, options, created_at, updated_at)
VALUES ('monthly_fixed.review_mode', 'AUTO', 0, 1, '月卡/固定车位审核模式：AUTO=自动通过, MANUAL=人工审核', '计费设置', 'ENUM', '["AUTO","MANUAL"]', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type), options = VALUES(options);


-- ============================================================
-- Migration: V20260902009__report_indexes.sql
-- ============================================================
-- 报表聚合索引（包 6-1：收入/车流量报表）
-- PERF-005: 单表聚合 ≤1s，复杂聚合 ≤3s

-- 收入报表: 按 pay_time + status + parking_lot_id 聚合
-- 覆盖 ReportMapper.sumRevenue / revenueByDay / revenueByMonth / revenueByYear
CREATE INDEX idx_po_pay_time_status_lot
    ON parking_order (pay_time, status, parking_lot_id);

-- 车流量报表: 按 entry_time + parking_lot_id 聚合
CREATE INDEX idx_pr_entry_time_lot
    ON parking_record (entry_time, parking_lot_id);

-- 车流量报表: 按 exit_time + parking_lot_id 聚合
CREATE INDEX idx_pr_exit_time_lot
    ON parking_record (exit_time, parking_lot_id);

-- 车流量报表: 按 entry_time + lane_id 聚合（通道维度）
CREATE INDEX idx_pr_entry_time_lane
    ON parking_record (entry_time, lane_id);


-- ============================================================
-- Migration: V20260902010__add_session_key_to_wx_user.sql
-- ============================================================
-- =============================================================================
-- Flyway 迁移：wx_user 表新增 session_key 字段
-- =============================================================================
-- 微信 session_key 用于旧版 getPhoneNumber 解密（可选）。
-- 仅当表结构无此字段时执行 ALTER。
-- =============================================================================

-- 检查字段是否已存在（Flyway 幂等：使用存储过程安全执行）
-- 如果字段已存在，MySQL 会抛出 1060 Duplicate column，使用条件判断避免
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'wx_user'
  AND COLUMN_NAME = 'session_key';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE wx_user ADD COLUMN session_key VARCHAR(100) COMMENT ''微信会话密钥'' AFTER phone',
    'SELECT ''session_key column already exists, skipping'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;


-- ============================================================
-- Migration: V20260902011__monthly_pass_price_param.sql
-- ============================================================
-- 小程序月卡链路：注入月卡定价参数种子（全局行）
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, parking_lot_id, created_at, updated_at)
VALUES ('monthly_pass.price_per_month_cents', '30000', '月卡单价（分/月）', '计费设置', 'INT', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);


-- ============================================================
-- Migration: V20260902012__fixed_space_price_param.sql
-- ============================================================
-- 小程序固定车位链路：注入固定车位定价参数种子（全局行）
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, parking_lot_id, created_at, updated_at)
VALUES ('fixed_space.price_per_month_cents', '30000', '固定车位单价（分/月）', '计费设置', 'INT', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);


-- ============================================================
-- Migration: V20260902013__update_bind_limit.sql
-- ============================================================
-- 订正车辆绑定上限：从 5 → 3（确认项 84）
UPDATE sys_config SET config_value = '3' WHERE config_key = 'vehicle.bind_limit_per_user' AND config_value = '5';


-- ============================================================
-- Migration: V20260902014__booth_cross_tenant_nullable.sql
-- ============================================================
-- 岗亭管理员跨租户设计：sys_admin_account_parking_lot.tenant_id 改为可空
-- 岗亭管理员不再归属单一租户，可通过 parkingLotIds 跨租户分配停车场
ALTER TABLE sys_admin_account_parking_lot
    MODIFY COLUMN tenant_id BIGINT NULL COMMENT '租户 ID（岗亭管理员为 NULL，表示跨租户）';


-- ============================================================
-- Migration: V20260902015__add_gate_mode_and_entry_trigger.sql
-- ============================================================
-- GAP-01/04: 车道闸机模式 + 会话入场触发方式
-- 一期范围：gate_mode 控制车道自动/常开/常关行为；entry_trigger 记录入场触发来源

ALTER TABLE parking_lane
    ADD COLUMN gate_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO'
    COMMENT '闸机模式: AUTO-自动, ALWAYS_OPEN-常开, ALWAYS_CLOSE-常关'
    AFTER camera_mode;

ALTER TABLE parking_session
    ADD COLUMN entry_trigger VARCHAR(20) NULL
    COMMENT '入场触发方式: whitelist_auto-白名单自动, manual_open-人工放行, always_open_period-常开时段, manual_entry-人工补录'
    AFTER entry_operator;


-- ============================================================
-- Migration: V20260903001__device_network_fields.sql
-- ============================================================
-- V20260903001__device_network_fields.sql
-- Device 表新增网络配置字段，供适配器连接使用

ALTER TABLE device
  ADD COLUMN ip_address VARCHAR(45) NULL COMMENT 'IP地址',
  ADD COLUMN port INT NULL DEFAULT 80 COMMENT '端口',
  ADD COLUMN subnet_mask VARCHAR(45) NULL COMMENT '子网掩码',
  ADD COLUMN gateway VARCHAR(45) NULL COMMENT '网关地址';


-- ============================================================
-- Migration: V20260904001__customer_admin_booth_permissions.sql
-- ============================================================
-- ---------------------------------------------------------------------------
-- V1.5: 为租户管理员（customer_admin）补岗亭监控页所需全部权限码
-- ---------------------------------------------------------------------------
-- 背景：
--   V1.5 起超管与租户管理员均可进入并操作岗亭工作区。
--   岗亭监控页（booth-web monitor）依赖以下权限码：
--     booth:monitor  实时监控快照/设备刷新/告警确认/车牌修正/无牌车
--     booth:operate  车场列表/手动开闸关闸/常开常关/批量开闸/出场收费/交接班
--     booth:view     在场车辆/车辆历史/停车会话查询/交接班记录查询
--     fee:read       查询当前收费规则
--     fee:write      岗亭端临时调整收费规则
--     fee:reduce     费用减免
--     record:read    现场收费记录查询
--   既有迁移已为 customer_admin 补过部分码（V20260804001），此处幂等补齐全量。
-- 写法：
--   1. 权限码字典表 sys_permission 幂等补齐（仅作文档登记，鉴权读 sys_role_permission）。
--   2. sys_role_permission 按 role_id + permission_code 唯一键幂等插入。
-- ---------------------------------------------------------------------------

-- 1. 权限码字典登记（幂等）
INSERT INTO sys_permission (code, name, description)
SELECT 'booth:view', '岗亭查询', '岗亭端在场车辆/车辆历史/会话/交接班查询'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'booth:view');
INSERT INTO sys_permission (code, name, description)
SELECT 'booth:operate', '岗亭操作', '岗亭端手动开闸/关闸/常开常关/人工放行/出场收费'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'booth:operate');
INSERT INTO sys_permission (code, name, description)
SELECT 'booth:monitor', '岗亭实时监控', '查看岗亭实时监控页面、接收 WebSocket 推送、确认异常提醒'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'booth:monitor');
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:read', '查看收费规则', '查询停车场当前生效的收费规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:read');
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:write', '管理收费规则', '创建/编辑收费规则（含岗亭端临时调整）'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:write');
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:reduce', '费用减免', '对收费金额进行减免，需要二次确认超阈值操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:reduce');
INSERT INTO sys_permission (code, name, description)
SELECT 'record:read', '查看通行记录', '查看入场/出场通行记录和停车记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'record:read');

-- 2. 为所有未删除的 customer_admin 角色补齐岗亭权限（幂等）
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT scr.id, perm.permission_code, perm.permission_type, perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'booth:monitor' AS permission_code, 'button' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'booth:operate', 'button', 'company'
    UNION ALL SELECT 'booth:view', 'menu', 'company'
    UNION ALL SELECT 'fee:read', 'menu', 'company'
    UNION ALL SELECT 'fee:write', 'button', 'company'
    UNION ALL SELECT 'fee:reduce', 'button', 'company'
    UNION ALL SELECT 'record:read', 'menu', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = scr.id AND srp.permission_code = perm.permission_code
  );

