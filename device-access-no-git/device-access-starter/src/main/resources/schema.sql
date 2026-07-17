-- Device Access v0.3 初始化 DDL
-- 数据库: device_access
-- 手工执行时请使用 --default-character-set=utf8mb4，否则中文会乱码
--   mysql -u root -p --default-character-set=utf8mb4 < schema.sql

CREATE DATABASE IF NOT EXISTS device_access
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE device_access;

-- ═══════════════════════════════════════════
-- 设备产品目录表
-- ═══════════════════════════════════════════

CREATE TABLE IF NOT EXISTS t_device_product (
    id           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    brand        VARCHAR(32)  NOT NULL                 COMMENT '品牌',
    model        VARCHAR(32)  NOT NULL                 COMMENT '型号',
    product_name VARCHAR(128) NOT NULL                 COMMENT '产品全称（品牌 + 型号）',
    device_type  VARCHAR(16)  NOT NULL                 COMMENT '设备类型: CAMERA / DISPLAY',
    protocol     VARCHAR(16)  NOT NULL DEFAULT 'MQTT'  COMMENT '通信协议: MQTT / RS485 / NONE',
    capabilities VARCHAR(500) NOT NULL DEFAULT '[]'    COMMENT '设备能力列表（JSON数组），如 ["DISPLAY_TEXT","DISPLAY_SAVE"]',
    remark       VARCHAR(256) DEFAULT ''               COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product (brand, model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备产品目录表';

-- 预设产品数据
INSERT IGNORE INTO t_device_product (brand, model, product_name, device_type, protocol, capabilities) VALUES
('ZHENSHI', 'C5H',    'ZHENSHI C5H',        'CAMERA',  'MQTT',  '["DISPLAY_TEXT","DISPLAY_SAVE","DISPLAY_CONFIG","VOICE_CONTROL","DISPLAY_ENHANCED","PERIPHERAL_CONTROL","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE"]'),
('臻识',   'C6H',    '臻识 C6H',           'CAMERA',  'MQTT',  '["DISPLAY_TEXT","DISPLAY_SAVE","DISPLAY_CONFIG","VOICE_CONTROL","DISPLAY_ENHANCED","PERIPHERAL_CONTROL","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE"]'),
('信路通',  'XLT-01', '信路通 XLT-01',      'CAMERA',  'MQTT',  '["DISPLAY_TEXT","DISPLAY_SAVE","DISPLAY_CONFIG","VOICE_CONTROL","DISPLAY_ENHANCED","PERIPHERAL_CONTROL","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE"]'),
('通用',   'LED-01', 'LED显示屏 960x64',    'DISPLAY', 'RS485', '[]');

-- ═══════════════════════════════════════════
-- 设备表
-- ═══════════════════════════════════════════

CREATE TABLE IF NOT EXISTS t_device (
    id                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    device_id          VARCHAR(64)  NOT NULL                 COMMENT '设备唯一标识（序列号/MAC）',
    device_name        VARCHAR(128) DEFAULT ''               COMMENT '设备名称',
    product_id         BIGINT       NOT NULL                 COMMENT 'FK → t_device_product.id',
    direction          VARCHAR(16)  DEFAULT NULL             COMMENT '设备方向: ENTRANCE/EXIT/BIDIRECTIONAL',
    platform_device_id VARCHAR(64)  DEFAULT NULL             COMMENT '平台设备ID',
    tenant_id          VARCHAR(64)  DEFAULT NULL             COMMENT '租户ID',
    parking_lot_id     VARCHAR(64)  DEFAULT NULL             COMMENT '停车场ID',
    lane_id            VARCHAR(64)  DEFAULT NULL             COMMENT '车道ID',
    status             VARCHAR(16)  NOT NULL DEFAULT 'OFFLINE' COMMENT '状态: ONLINE/OFFLINE（系统维护，禁止手动修改）',
    last_online_time   DATETIME     DEFAULT NULL              COMMENT '最后在线时间',
    display_enabled    TINYINT      NOT NULL DEFAULT 1       COMMENT '显示屏启用: 0-关闭, 1-启用',
    display_mode       VARCHAR(16)  NOT NULL DEFAULT 'TWO_LINE' COMMENT '显示屏模式: TWO_LINE / FOUR_LINE',
    remark             VARCHAR(256) DEFAULT ''               COMMENT '备注',
    deleted            TINYINT      NOT NULL DEFAULT 0       COMMENT '逻辑删除: 0-未删除, 1-已删除',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_id (device_id),
    INDEX idx_product_id (product_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

-- ═══════════════════════════════════════════
-- 设备命令执行日志表
-- ═══════════════════════════════════════════

CREATE TABLE IF NOT EXISTS t_device_command_log (
    id                 BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    device_id          VARCHAR(64)  NOT NULL                 COMMENT '设备唯一标识',
    brand              VARCHAR(32)  DEFAULT NULL             COMMENT '设备品牌',
    command_type       VARCHAR(32)  NOT NULL                 COMMENT '命令类型: OPEN_GATE / CLOSE_GATE / SYNC_TIME / DISPLAY_TEXT / PERIPHERAL_CONTROL / DISPLAY_SAVE',
    plate_no           VARCHAR(32)  DEFAULT NULL             COMMENT '关联车牌号',
    success            TINYINT(1)   NOT NULL                 COMMENT '是否成功: 0-失败, 1-成功',
    device_code        INT          DEFAULT NULL             COMMENT '设备返回码',
    message            VARCHAR(256) DEFAULT NULL             COMMENT '执行结果描述',
    platform_device_id VARCHAR(64)  DEFAULT NULL             COMMENT '平台设备ID',
    tenant_id          VARCHAR(64)  DEFAULT NULL             COMMENT '租户ID',
    parking_lot_id     VARCHAR(64)  DEFAULT NULL             COMMENT '停车场ID',
    lane_id            VARCHAR(64)  DEFAULT NULL             COMMENT '车道ID',
    request_time       DATETIME     NOT NULL                 COMMENT '请求时间',
    response_time      DATETIME     DEFAULT NULL             COMMENT '响应时间',
    create_time        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_device_id    (device_id),
    INDEX idx_command_type (command_type),
    INDEX idx_request_time (request_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备命令执行日志表';

-- ═══════════════════════════════════════════
-- 事件发件箱表（v0.4 增强：Webhook 持久化 + 定时重试）
-- ═══════════════════════════════════════════

CREATE TABLE IF NOT EXISTS t_event_outbox (
    id          BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    event_id    VARCHAR(64)  NOT NULL                 COMMENT '事件唯一标识',
    event_type  VARCHAR(32)  NOT NULL                 COMMENT '事件类型: PLATE_RECOGNIZED',
    payload     TEXT         NOT NULL                 COMMENT '事件 JSON 报文',
    status      VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/SENT/FAILED',
    retry_count INT          NOT NULL DEFAULT 0       COMMENT '已重试次数',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    last_retry_at DATETIME   DEFAULT NULL             COMMENT '最后重试时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    INDEX idx_status_created (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='事件发件箱表';
