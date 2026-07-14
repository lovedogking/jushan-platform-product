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
