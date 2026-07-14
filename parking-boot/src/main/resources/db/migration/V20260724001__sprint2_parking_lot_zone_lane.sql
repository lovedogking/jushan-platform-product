-- =============================================================================
-- Flyway 迁移：Sprint 2 - 车场基础设置（停车场档案、区域管理、通道管理）
-- =============================================================================
-- 对齐 PRD V1.0 和 section_03_ddl.md 规范，补齐 parking_lot 字段，
-- 新建 parking_zone 表，重构 parking_lane 表结构。
-- 所有业务表包含 tenant_id 和 deleted_at，主键使用 BIGINT（Snowflake）。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 重构 parking_lot 表（补齐 PRD 字段，移除策略字段到 access_policy）
-- ---------------------------------------------------------------------------

-- 1.1 新增字段
ALTER TABLE parking_lot
    ADD COLUMN province        VARCHAR(64)   DEFAULT NULL COMMENT '省份' AFTER name,
    ADD COLUMN city            VARCHAR(64)   DEFAULT NULL COMMENT '城市' AFTER province,
    ADD COLUMN district        VARCHAR(64)   DEFAULT NULL COMMENT '区县' AFTER city,
    ADD COLUMN region_type     TINYINT       NOT NULL DEFAULT 1 COMMENT '区域类型：1商场 2写字楼 3住宅小区 4医院 5景区 6交通枢纽' AFTER district,
    ADD COLUMN contact_name    VARCHAR(64)   DEFAULT NULL COMMENT '联系人' AFTER contact_phone,
    ADD COLUMN business_hours  VARCHAR(32)   DEFAULT '00:00-24:00' COMMENT '营业时间' AFTER status,
    ADD COLUMN images          JSON          DEFAULT NULL COMMENT '车场图片URL数组（最多5张）' AFTER business_hours,
    ADD COLUMN version         INT           NOT NULL DEFAULT 0 COMMENT '乐观锁版本号' AFTER images,
    ADD COLUMN deleted_at      DATETIME(3)   DEFAULT NULL COMMENT '软删除时间' AFTER updated_at;

-- 1.2 移除已迁移到 access_policy 的策略字段（如果存在）
-- 注意：这些字段在旧代码中存在，但按 Sprint 2 规范应移除到策略配置表
-- 先检查列是否存在再删除，避免报错
SET @drop_disable_new_entries = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_new_entries'),
    'ALTER TABLE parking_lot DROP COLUMN disable_new_entries',
    'SELECT 1'
);
PREPARE stmt1 FROM @drop_disable_new_entries;
EXECUTE stmt1;
DEALLOCATE PREPARE stmt1;

SET @drop_disable_payment = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_payment'),
    'ALTER TABLE parking_lot DROP COLUMN disable_payment',
    'SELECT 1'
);
PREPARE stmt2 FROM @drop_disable_payment;
EXECUTE stmt2;
DEALLOCATE PREPARE stmt2;

SET @drop_disable_exit = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_exit'),
    'ALTER TABLE parking_lot DROP COLUMN disable_exit',
    'SELECT 1'
);
PREPARE stmt3 FROM @drop_disable_exit;
EXECUTE stmt3;
DEALLOCATE PREPARE stmt3;

SET @drop_disable_auto_gate = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_auto_gate'),
    'ALTER TABLE parking_lot DROP COLUMN disable_auto_gate',
    'SELECT 1'
);
PREPARE stmt4 FROM @drop_disable_auto_gate;
EXECUTE stmt4;
DEALLOCATE PREPARE stmt4;

SET @drop_disable_only_config = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'disable_only_config'),
    'ALTER TABLE parking_lot DROP COLUMN disable_only_config',
    'SELECT 1'
);
PREPARE stmt5 FROM @drop_disable_only_config;
EXECUTE stmt5;
DEALLOCATE PREPARE stmt5;

-- 1.3 移除 current_vehicles 和 remaining_spaces（改为区域汇总 + Redis 实时计算）
SET @drop_current_vehicles = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'current_vehicles'),
    'ALTER TABLE parking_lot DROP COLUMN current_vehicles',
    'SELECT 1'
);
PREPARE stmt6 FROM @drop_current_vehicles;
EXECUTE stmt6;
DEALLOCATE PREPARE stmt6;

SET @drop_remaining_spaces = IF(
    EXISTS(SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS 
           WHERE TABLE_NAME = 'parking_lot' AND COLUMN_NAME = 'remaining_spaces'),
    'ALTER TABLE parking_lot DROP COLUMN remaining_spaces',
    'SELECT 1'
);
PREPARE stmt7 FROM @drop_remaining_spaces;
EXECUTE stmt7;
DEALLOCATE PREPARE stmt7;

-- 1.4 修改主键为 BIGINT（移除 AUTO_INCREMENT）
ALTER TABLE parking_lot MODIFY COLUMN id BIGINT NOT NULL COMMENT '车场ID（Snowflake）';

-- 1.5 添加索引
ALTER TABLE parking_lot ADD UNIQUE INDEX uk_tenant_name (tenant_id, name, deleted_at) COMMENT '租户内名称唯一';
ALTER TABLE parking_lot ADD INDEX idx_region_type (region_type) COMMENT '按区域类型查询';
ALTER TABLE parking_lot ADD INDEX idx_deleted_at (deleted_at) COMMENT '软删除查询';

-- ---------------------------------------------------------------------------
-- 2. 新建 parking_zone 区域管理表
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS parking_zone (
    id              BIGINT UNSIGNED NOT NULL COMMENT '区域ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    name            VARCHAR(128)        NOT NULL COMMENT '区域名称',
    tag             VARCHAR(64)         NOT NULL DEFAULT 'NORMAL' COMMENT '区域标签：NORMAL普通 VIP员工 LOADING装卸 CHARGE充电 支持自定义',
    level           TINYINT             NOT NULL DEFAULT 1 COMMENT '区域等级：1普通 2VIP 3员工',
    fee_rule_id     BIGINT UNSIGNED     DEFAULT NULL COMMENT '收费标准ID（逻辑外键：fee_rule.id）【预留】',
    total_spaces    INT                 NOT NULL DEFAULT 0 COMMENT '车位总数',
    fixed_spaces    INT                 NOT NULL DEFAULT 0 COMMENT '固定车位数',
    temp_spaces     INT                 NOT NULL DEFAULT 0 COMMENT '临停车位数（= total_spaces - fixed_spaces）',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用',
    manager_id      BIGINT UNSIGNED     DEFAULT NULL COMMENT '区域负责人ID（逻辑外键：sys_admin_account.id）',
    remark          VARCHAR(512)        DEFAULT NULL COMMENT '备注',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='区域管理表';

-- ---------------------------------------------------------------------------
-- 3. 重构 parking_lane 通道管理表
-- ---------------------------------------------------------------------------

-- 3.1 先删除旧表（如果存在），因为字段变更较大，重建更简单
-- 注意：生产环境应使用 ALTER TABLE，但开发阶段重建更快
-- 保留旧表数据：先改名备份
RENAME TABLE parking_lane TO parking_lane_backup;

-- 3.2 创建新表
CREATE TABLE parking_lane (
    id              BIGINT UNSIGNED NOT NULL COMMENT '通道ID（Snowflake）',
    tenant_id       BIGINT              NOT NULL COMMENT '租户ID',
    lot_id          BIGINT UNSIGNED     NOT NULL COMMENT '所属车场ID（逻辑外键：parking_lot.id）',
    zone_id         BIGINT UNSIGNED     NOT NULL COMMENT '所属区域ID（逻辑外键：parking_zone.id）',
    lane_no         VARCHAR(32)         NOT NULL COMMENT '通道编号，如 A1、17',
    name            VARCHAR(128)        NOT NULL COMMENT '通道名称，如东大门',
    type            TINYINT             NOT NULL DEFAULT 1 COMMENT '通道类型：1入口 2出口 3双向',
    entry_camera_id BIGINT UNSIGNED     DEFAULT NULL COMMENT '入口相机ID（逻辑外键：device.id）【预留】',
    exit_camera_id  BIGINT UNSIGNED     DEFAULT NULL COMMENT '出口相机ID（逻辑外键：device.id）【预留】',
    status          TINYINT             NOT NULL DEFAULT 1 COMMENT '状态：1启用 2禁用 3维护中',
    tide_mode       TINYINT             DEFAULT 0 COMMENT '潮汐模式：0关闭 1早高峰入口 2晚高峰出口',
    camera_mode     TINYINT             DEFAULT 1 COMMENT '相机配置模式：1单相机 2双相机 3主从相机',
    version         INT                 NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
    deleted_at      DATETIME(3)         DEFAULT NULL COMMENT '软删除时间',
    created_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at      DATETIME(3)         NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_lot_lane_no (tenant_id, lot_id, lane_no, deleted_at),
    KEY idx_tenant_id (tenant_id),
    KEY idx_lot_id (lot_id),
    KEY idx_zone_id (zone_id),
    KEY idx_entry_camera_id (entry_camera_id),
    KEY idx_exit_camera_id (exit_camera_id),
    KEY idx_status (status),
    KEY idx_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通道管理表';

-- 3.3 从备份表迁移数据（字段映射）
-- 旧字段 → 新字段映射：
-- parking_lot_id → lot_id
-- code → lane_no
-- direction → type: ENTRY=1, EXIT=2, MIXED=3
-- status: ENABLED=1, DISABLED=2
-- is_key_lane, auto_release_policy, description 已移除（移到策略配置）
INSERT INTO parking_lane (
    id, tenant_id, lot_id, zone_id, lane_no, name, type, status, 
    tide_mode, camera_mode, version, deleted_at, created_at, updated_at
)
SELECT 
    id,
    tenant_id,
    parking_lot_id AS lot_id,
    0 AS zone_id,  -- 旧数据没有 zone_id，默认设置为 0（后续手动关联）
    code AS lane_no,
    name,
    CASE direction
        WHEN 'ENTRY' THEN 1
        WHEN 'EXIT' THEN 2
        WHEN 'MIXED' THEN 3
        ELSE 1
    END AS type,
    CASE status
        WHEN 'ENABLED' THEN 1
        WHEN 'DISABLED' THEN 2
        ELSE 1
    END AS status,
    0 AS tide_mode,  -- 旧数据无潮汐模式
    1 AS camera_mode,  -- 旧数据默认单相机
    0 AS version,
    NULL AS deleted_at,
    created_at,
    updated_at
FROM parking_lane_backup;

-- 3.4 删除备份表
DROP TABLE parking_lane_backup;

-- ---------------------------------------------------------------------------
-- 4. 初始化数据
-- ---------------------------------------------------------------------------

-- 为每个现有停车场创建默认区域（如果 parking_zone 为空）
-- 这确保每个停车场至少有一个区域，符合 PRD 要求
INSERT INTO parking_zone (id, tenant_id, lot_id, name, tag, level, total_spaces, fixed_spaces, temp_spaces, status, version, created_at, updated_at)
SELECT 
    id + 1000000,  -- 使用偏移避免 ID 冲突（Snowflake 实际由应用生成）
    tenant_id,
    id AS lot_id,
    '默认区域' AS name,
    'NORMAL' AS tag,
    1 AS level,
    total_spaces,
    0 AS fixed_spaces,
    total_spaces AS temp_spaces,
    1 AS status,
    0 AS version,
    created_at,
    updated_at
FROM parking_lot
WHERE deleted_at IS NULL
  AND NOT EXISTS (SELECT 1 FROM parking_zone WHERE parking_zone.lot_id = parking_lot.id);

-- ---------------------------------------------------------------------------
-- 5. 更新 parking_lane 的 zone_id（关联到默认区域）
-- ---------------------------------------------------------------------------
UPDATE parking_lane pl
SET pl.zone_id = (
    SELECT pz.id 
    FROM parking_zone pz 
    WHERE pz.lot_id = pl.lot_id 
    LIMIT 1
)
WHERE pl.zone_id = 0;
