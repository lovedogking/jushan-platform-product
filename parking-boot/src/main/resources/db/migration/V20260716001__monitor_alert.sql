CREATE TABLE monitor_alert (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    tenant_id       BIGINT NOT NULL COMMENT '租户 ID',
    parking_lot_id  BIGINT NOT NULL COMMENT '停车场 ID',
    alert_type      VARCHAR(32) NOT NULL COMMENT '异常类型：DEVICE_OFFLINE-设备离线, LOT_FULL-车位已满, LOT_DISABLED-停车场停用, RECOGNITION_FAIL-识别失败',
    severity        VARCHAR(16) NOT NULL COMMENT '严重程度：WARNING-警告, CRITICAL-严重',
    source_id       VARCHAR(64) COMMENT '关联来源 ID（设备 ID / 事件 ID / 记录 ID）',
    message         VARCHAR(512) NOT NULL COMMENT '异常描述',
    acknowledged    TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否已确认：0-未确认, 1-已确认',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    acknowledged_at DATETIME COMMENT '确认时间',
    INDEX idx_parking_lot_ack (parking_lot_id, acknowledged),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗亭监控异常提醒表';
