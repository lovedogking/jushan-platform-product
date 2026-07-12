-- =============================================================================
-- Flyway 迁移：设备状态快照表
-- =============================================================================
-- T24｜设备状态查询、快照与轮询
-- 持久化每次对 Device Access 状态查询的结果，支持：
-- 1. 最新状态快照读取（避免每次实时调用 DA）
-- 2. 历史查询记录（审计/排障）
-- 3. 过期状态时间标识（前端可显示"最后查询于 X 秒前"）
-- =============================================================================

CREATE TABLE device_status_snapshot
(
    id                  BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    device_id           BIGINT       NOT NULL COMMENT '平台设备 ID（关联 device.id）',
    device_sn           VARCHAR(128) NOT NULL COMMENT '查询时使用的厂商序列号（快照冗余，便于排障）',
    online              TINYINT(1)   DEFAULT NULL COMMENT '设备是否在线（DA 返回值）',
    last_online_time    DATETIME     DEFAULT NULL COMMENT '最近在线时间（DA 返回值）',
    gate_status         VARCHAR(64)  DEFAULT NULL COMMENT '道闸杆状态（DA 返回值，仅 GATE 有效）',
    gate_connect_status VARCHAR(64)  DEFAULT NULL COMMENT '道闸连接状态（DA 返回值，仅 GATE 有效）',
    status_description  VARCHAR(255) DEFAULT NULL COMMENT '设备状态描述（DA 返回的 status 字段）',
    query_success       TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '本次查询是否成功（1=成功收到 DA 数据）',
    error_code          VARCHAR(32)  DEFAULT NULL COMMENT '失败时的错误码（如 404/503/UNCERTAIN）',
    error_message       VARCHAR(500) DEFAULT NULL COMMENT '失败时的错误消息',
    collected_at        DATETIME     NOT NULL COMMENT '状态采集时间（调用 DA 的时间点）',
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',

    INDEX idx_device_id (device_id) COMMENT '按设备查询快照',
    INDEX idx_device_id_collected (device_id, collected_at DESC) COMMENT '查询某设备最新快照',
    INDEX idx_query_success (query_success) COMMENT '按查询成功/失败筛选',
    INDEX idx_collected_at (collected_at) COMMENT '按采集时间范围查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备状态快照';
