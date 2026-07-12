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
