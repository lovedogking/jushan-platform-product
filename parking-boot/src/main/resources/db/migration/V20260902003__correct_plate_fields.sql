-- V20260902003__correct_plate_fields.sql
-- 向 recognition_event_log 添加车牌校正字段

ALTER TABLE recognition_event_log
    ADD COLUMN corrected_plate VARCHAR(20) NULL COMMENT '校正后车牌号' AFTER camera_source,
    ADD COLUMN correction_type VARCHAR(30) NULL COMMENT '校正类型：MANUAL_CORRECTION' AFTER corrected_plate,
    ADD COLUMN corrected_at DATETIME NULL COMMENT '校正操作时间' AFTER correction_type,
    ADD COLUMN corrector_id BIGINT NULL COMMENT '校正人ID（sys_user.id）' AFTER corrected_at;
