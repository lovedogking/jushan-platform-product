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

