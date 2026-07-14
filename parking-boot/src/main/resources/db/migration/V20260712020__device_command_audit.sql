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
