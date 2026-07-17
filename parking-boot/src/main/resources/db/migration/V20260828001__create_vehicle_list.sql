-- ============================================================
-- 1. 创建 vehicle_list 表
-- ============================================================
CREATE TABLE IF NOT EXISTS vehicle_list (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    list_type       VARCHAR(16)     NOT NULL COMMENT '名单类型：BLACK / WHITE',
    parking_lot_id  BIGINT          NOT NULL COMMENT '生效车场ID',
    start_date      DATE            NULL COMMENT '有效期开始（NULL=立即生效）',
    end_date        DATE            NULL COMMENT '有效期结束（NULL=永久）',
    trigger_type    VARCHAR(32)     NULL COMMENT '黑名单触发类型：ARREARS / MANAGEMENT / OTHER（白名单为NULL）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE / EXPIRED / DISABLED',
    remark          VARCHAR(255)    NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME        NULL COMMENT '软删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆黑白名单表';

-- 索引
CREATE UNIQUE INDEX uk_lot_plate_type ON vehicle_list (parking_lot_id, plate_number, list_type);
CREATE INDEX idx_plate_number ON vehicle_list (plate_number);
CREATE INDEX idx_parking_lot_id ON vehicle_list (parking_lot_id);
CREATE INDEX idx_list_type_status ON vehicle_list (list_type, status);

-- ============================================================
-- 2. 迁移 AccessPolicy BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    ap.tenant_id,
    UPPER(ap.policy_key) AS plate_number,
    'BLACK'               AS list_type,
    ap.parking_lot_id,
    NULL                  AS start_date,
    NULL                  AS end_date,
    'OTHER'               AS trigger_type,
    'ACTIVE'              AS status,
    CONCAT('迁移自access_policy: ', COALESCE(ap.description, '')) AS remark,
    ap.created_at,
    NOW()
FROM access_policy ap
WHERE ap.policy_type = 'BLACKLIST'
  AND ap.status = 'ACTIVE'
  AND ap.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = ap.parking_lot_id
        AND vl.plate_number  = UPPER(ap.policy_key)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 3. 迁移 sys_vehicle BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'BLACK'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    'OTHER'                AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle BLACKLIST: ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type = 'BLACKLIST'
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 4. 迁移 sys_vehicle VIP/SUPER/FREE → vehicle_list（白名单）
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'WHITE'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    NULL                   AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle ', sv.vehicle_type, ': ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type IN ('VIP', 'SUPER', 'FREE')
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'WHITE'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 5. 插入全局参数默认值
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_mode', 'DENY_ENTRY',
        '黑名单触发模式：DENY_ENTRY-禁止入场 / ALLOW_WITH_ALERT-允许但告警 / BY_TYPE-按类型区分',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_types',
        '[{"code":"ARREARS","label":"欠费类"},{"code":"MANAGEMENT","label":"管理类"},{"code":"OTHER","label":"其他类"}]',
        '黑名单触发类型字典（JSON数组）',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
