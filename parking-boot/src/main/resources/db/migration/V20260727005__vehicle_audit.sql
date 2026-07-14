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
