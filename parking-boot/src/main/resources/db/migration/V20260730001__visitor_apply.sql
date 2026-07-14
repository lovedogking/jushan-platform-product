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
