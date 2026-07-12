-- =============================================================================
-- Flyway 迁移：车道表（入口、出口与车道模型）
-- =============================================================================
-- T19｜入口、出口与车道模型
-- 创建 parking_lane 表，支持入口、出口、混合车道模型。
-- 车道编码在停车场内唯一。
-- 当前不包含相机/道闸绑定（T20/T21 负责）。
-- =============================================================================

CREATE TABLE parking_lane
(
    id                   BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    parking_lot_id       BIGINT       NOT NULL COMMENT '所属停车场 ID',
    name                 VARCHAR(128) NOT NULL COMMENT '车道名称',
    code                 VARCHAR(64)  NOT NULL COMMENT '车道编码（停车场内唯一）',
    direction            VARCHAR(20)  NOT NULL DEFAULT 'ENTRY' COMMENT '车道方向：ENTRY-入口, EXIT-出口, MIXED-混合',
    status               VARCHAR(20)  NOT NULL DEFAULT 'ENABLED' COMMENT '状态：ENABLED-启用, DISABLED-停用',
    is_key_lane          TINYINT(1)   NOT NULL DEFAULT 0 COMMENT '是否为关键车道：1-是, 0-否（关键车道离线可能导致停车场不可用）',
    auto_release_policy  VARCHAR(50)  NOT NULL DEFAULT 'MANUAL' COMMENT '自动放行策略：AUTO-自动放行, MANUAL-人工确认, AFTER_PAY-缴费后自动放行',
    description          VARCHAR(255) DEFAULT '' COMMENT '备注/其他业务参数',
    created_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    UNIQUE INDEX uq_parking_lot_code (parking_lot_id, code) COMMENT '停车场内车道编码唯一',
    INDEX idx_parking_lot_id (parking_lot_id) COMMENT '按停车场查询',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_direction (direction) COMMENT '按方向查询'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='车道';
