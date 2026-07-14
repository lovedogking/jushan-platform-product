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
