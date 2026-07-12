-- =============================================================================
-- Flyway 迁移：停车场完整字段、容量审计与状态审计
-- =============================================================================
-- T18｜停车场基础信息、状态与容量
-- 扩展 parking_lot 占位表，新增停车场基础信息字段、容量字段、停用策略字段，
-- 并创建容量变更审计表和状态变更审计表。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 扩展 parking_lot 表
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lot
    ADD COLUMN address              VARCHAR(255) DEFAULT '' COMMENT '地址' AFTER name,
    ADD COLUMN contact_phone        VARCHAR(20)  DEFAULT '' COMMENT '联系电话' AFTER address,
    ADD COLUMN longitude            DECIMAL(10,7) DEFAULT NULL COMMENT '经度（预留）' AFTER contact_phone,
    ADD COLUMN latitude             DECIMAL(10,7) DEFAULT NULL COMMENT '纬度（预留）' AFTER longitude,
    ADD COLUMN total_spaces         INT          NOT NULL DEFAULT 0 COMMENT '总车位数' AFTER latitude,
    ADD COLUMN current_vehicles     INT          NOT NULL DEFAULT 0 COMMENT '当前在场车辆数（只读，由停车记录计算）' AFTER total_spaces,
    ADD COLUMN remaining_spaces     INT          NOT NULL DEFAULT 0 COMMENT '剩余车位数（默认 = total_spaces - current_vehicles，允许人工修正）' AFTER current_vehicles,
    ADD COLUMN payment_mode         VARCHAR(20)  NOT NULL DEFAULT 'PLATFORM' COMMENT '支付模式：PLATFORM-平台统一商户, CUSTOMER-客户独立商户' AFTER remaining_spaces,
    ADD COLUMN image_retention_days INT          NOT NULL DEFAULT 30 COMMENT '抓拍图片保存天数' AFTER payment_mode,
    ADD COLUMN data_retention_days  INT          NOT NULL DEFAULT 365 COMMENT '业务数据保存天数' AFTER image_retention_days,
    ADD COLUMN free_exit_minutes    INT          NOT NULL DEFAULT 15 COMMENT '缴费后免费离场时间（分钟）' AFTER data_retention_days,
    ADD COLUMN manual_release_policy VARCHAR(50) NOT NULL DEFAULT 'ADMIN_ONLY' COMMENT '人工放行策略：ADMIN_ONLY-仅管理员, BOOTH_ALLOWED-岗亭可放行' AFTER free_exit_minutes,
    ADD COLUMN offline_policy       VARCHAR(50)  NOT NULL DEFAULT 'ALLOW_ENTRY_EXIT' COMMENT '离线运行策略：ALLOW_ENTRY_EXIT-允许出入, ALLOW_EXIT_ONLY-只出不进, STRICT-禁止通行' AFTER manual_release_policy,
    -- 停用时的保留范围（JSON 或独立字段，此处用独立字段以便查询）
    ADD COLUMN disable_new_entries  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否允许新车入场：1-允许, 0-禁止' AFTER offline_policy,
    ADD COLUMN disable_payment      TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否允许缴费：1-允许, 0-禁止' AFTER disable_new_entries,
    ADD COLUMN disable_exit         TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否允许出场：1-允许, 0-禁止' AFTER disable_payment,
    ADD COLUMN disable_auto_gate    TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '停用时是否保留自动开闸：1-保留, 0-关闭' AFTER disable_exit,
    ADD COLUMN disable_only_config  TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '停用时是否仅限制后台配置：1-是, 0-否' AFTER disable_auto_gate;

-- -----------------------------------------------------------------------------
-- 2. 容量变更审计表
-- -----------------------------------------------------------------------------
CREATE TABLE parking_lot_capacity_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID',
    field_name     VARCHAR(50)  NOT NULL COMMENT '变更字段名（total_spaces / remaining_spaces）',
    before_value   INT          NOT NULL COMMENT '修改前数值',
    after_value    INT          NOT NULL COMMENT '修改后数值',
    operator_id    BIGINT       NOT NULL COMMENT '操作人 ID（sys_user.id）',
    reason         VARCHAR(255) DEFAULT '' COMMENT '修改原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场容量变更审计日志';

-- -----------------------------------------------------------------------------
-- 3. 状态变更审计表
-- -----------------------------------------------------------------------------
CREATE TABLE parking_lot_status_log
(
    id             BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id BIGINT       NOT NULL COMMENT '停车场 ID',
    before_status  VARCHAR(20)  NOT NULL COMMENT '修改前状态',
    after_status   VARCHAR(20)  NOT NULL COMMENT '修改后状态',
    operator_id    BIGINT       NOT NULL COMMENT '操作人 ID（sys_user.id）',
    reason         VARCHAR(255) DEFAULT '' COMMENT '操作原因',
    created_at     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_created_at (created_at) COMMENT '按时间查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='停车场状态变更审计日志';
