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
