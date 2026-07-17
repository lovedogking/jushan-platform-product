-- =============================================================================
-- 任务包 3-4：无牌车临时车牌 — 三表新增 temp_plate_flag 字段
-- =============================================================================

ALTER TABLE parking_record
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE parking_order
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE recognition_event_log
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';
