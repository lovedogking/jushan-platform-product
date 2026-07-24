-- =============================================================================
-- Flyway 迁移：设备厂商、型号与平台设备台账
-- =============================================================================
-- T20｜设备厂商、型号与平台设备台账
-- 创建 device_vendor（设备厂商）、device_model（设备型号）、device（设备台账）表。
-- 设备台账通过 parking_lot_id → tenant_id 推导租户范围，保证数据隔离。
-- device_sn 在同厂商内唯一（UNIQUE(vendor_id, device_sn)）。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 设备厂商表
-- -----------------------------------------------------------------------------
CREATE TABLE device_vendor
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '厂商名称（如"臻识"）',
    code        VARCHAR(32)  NOT NULL COMMENT '厂商编码（如 ZHENSHI）',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    description VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_vendor_code (code) COMMENT '厂商编码唯一',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备厂商';

-- -----------------------------------------------------------------------------
-- 2. 设备型号表
-- -----------------------------------------------------------------------------
CREATE TABLE device_model
(
    id          BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    vendor_id   BIGINT       NOT NULL COMMENT '所属厂商 ID',
    name        VARCHAR(64)  NOT NULL COMMENT '型号名称（如"C5H"）',
    code        VARCHAR(32)  NOT NULL COMMENT '型号编码（如 C5H）',
    device_type VARCHAR(20)  NOT NULL DEFAULT 'CAMERA' COMMENT '设备类型：CAMERA-相机, GATE-道闸',
    status      VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    description VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_vendor_model_code (vendor_id, code) COMMENT '同厂商下型号编码唯一',
    INDEX idx_vendor_id (vendor_id) COMMENT '按厂商查询',
    INDEX idx_device_type (device_type) COMMENT '按设备类型查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备型号';

-- -----------------------------------------------------------------------------
-- 3. 平台设备台账表
-- -----------------------------------------------------------------------------
CREATE TABLE device
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键（平台设备 ID，前端引用此 ID）',
    parking_lot_id BIGINT      NOT NULL COMMENT '所属停车场 ID',
    lane_id       BIGINT       DEFAULT NULL COMMENT '绑定车道 ID（T21 负责绑定，当前可为空）',
    vendor_id     BIGINT       NOT NULL COMMENT '设备厂商 ID',
    model_id      BIGINT       NOT NULL COMMENT '设备型号 ID',
    name          VARCHAR(128) NOT NULL COMMENT '设备名称（运营可读）',
    code          VARCHAR(64)  NOT NULL COMMENT '设备业务编码（停车场内唯一）',
    device_sn     VARCHAR(128) NOT NULL COMMENT '厂商设备序列号（可信记录，禁止前端传入）',
    device_type   VARCHAR(20)  NOT NULL DEFAULT 'CAMERA' COMMENT '设备类型：CAMERA-相机, GATE-道闸',
    status        VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    capabilities  VARCHAR(500) DEFAULT '' COMMENT '设备能力（JSON 或逗号分隔，如 RECOGNIZE,CAPTURE,GATE_OPEN）',
    description   VARCHAR(255) DEFAULT '' COMMENT '备注',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_parking_lot_code (parking_lot_id, code) COMMENT '停车场内设备编码唯一',
    UNIQUE INDEX uq_vendor_device_sn (vendor_id, device_sn) COMMENT '同厂商下设备 SN 唯一',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_lane_id (lane_id) COMMENT '按车道查询',
    INDEX idx_vendor_id (vendor_id) COMMENT '按厂商查询',
    INDEX idx_model_id (model_id) COMMENT '按型号查询',
    INDEX idx_device_type (device_type) COMMENT '按设备类型查询',
    INDEX idx_status (status) COMMENT '按状态查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='平台设备台账';

-- -----------------------------------------------------------------------------
-- 4. 种子数据：臻识 C5（当前唯一真机确认的厂商/型号）
-- -----------------------------------------------------------------------------
INSERT INTO device_vendor (name, code, status, description) VALUES
('臻识', 'ZHENSHI', 'ENABLED', '臻识科技，当前已确认真机厂商'),
('信路通', 'XINLUTONG', 'ENABLED', '信路通，具体型号待联调确认（PENDING_DEVICE_VERIFICATION）');

-- 获取臻识 vendor_id 并插入 C5 型号
INSERT INTO device_model (vendor_id, name, code, device_type, status, description)
SELECT v.id, 'C5', 'C5', 'CAMERA', 'ENABLED', '臻识 C5 车牌识别相机，当前唯一真机确认型号'
FROM device_vendor v WHERE v.code = 'ZHENSHI';
