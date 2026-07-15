-- =============================================================================
-- Flyway 迁移：为 parking_session 表添加 parking_record_id 列
-- =============================================================================
-- 背景：
--   ParkingSession 实体（parking-system 模块）需要关联到对应的停车记录
--   （parking_record），以支持从 Session 追踪到完整停车链路。
--   该字段在 E2E 联调中发现缺失，本次补加。
-- =============================================================================

-- 使用 information_schema + PREPARE 判断列是否存在后安全执行
SET @add_parking_record_id = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE parking_session ADD COLUMN parking_record_id BIGINT DEFAULT NULL COMMENT ''关联停车记录 ID（逻辑外键：parking_record.id）'' AFTER plate_color',
        'SELECT 1 AS already_exists'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_session'
      AND COLUMN_NAME = 'parking_record_id'
);
PREPARE stmt FROM @add_parking_record_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
