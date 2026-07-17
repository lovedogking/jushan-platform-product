-- =============================================================================
-- 任务包 3-5：识别事件/停车记录/出场记录新增相机来源标记
-- =============================================================================

ALTER TABLE recognition_event_log
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE parking_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '入场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE exit_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '出场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';
