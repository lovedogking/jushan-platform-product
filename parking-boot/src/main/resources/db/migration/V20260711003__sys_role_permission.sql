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
