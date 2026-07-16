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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异常记录表（运营端异常管理）';

CREATE INDEX idx_exception_lot_status ON exception_record(parking_lot_id, status);
CREATE INDEX idx_exception_type ON exception_record(exception_type);
CREATE INDEX idx_exception_created_at ON exception_record(created_at);

-- 2. 插入权限记录
INSERT INTO sys_permission (code, name, description, type)
SELECT 'exception:view', '异常记录查看', '查看和处理异常记录', 'OPERATION'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'exception:view');

-- 3. 授权给固定角色
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT role_code, 'exception:view'
FROM (SELECT 'super_admin' AS role_code UNION ALL
      SELECT 'customer_admin' UNION ALL
      SELECT 'parking_manager' UNION ALL
      SELECT 'device_maintenance') rp
WHERE NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_code = rp.role_code AND srp.permission_code = 'exception:view');

-- 4. 授权给自定义角色中的 SUPER_ADMIN
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT cr.id, 'exception:view', 'OPERATION', 'ALL'
FROM sys_custom_role cr
WHERE cr.role_code = 'SUPER_ADMIN' AND cr.deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission srp
                  WHERE srp.role_id = cr.id AND srp.permission_code = 'exception:view');
