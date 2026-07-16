-- =============================================================================
-- Flyway 迁移：为 parking_lot 表添加 current_vehicles 列
-- =============================================================================
-- 背景：
--   Entity 和业务代码已使用 current_vehicles 字段，但 DB 建表时遗漏。
--   该字段用于记录当前在场车辆数，配合 total_spaces 计算剩余车位。
-- =============================================================================

SET @current_vehicles_exists = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE parking_lot ADD COLUMN current_vehicles INT NOT NULL DEFAULT 0 COMMENT ''当前在场车辆数（用于计算剩余车位）'' AFTER total_spaces', 'SELECT 1 AS already_exists')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_lot'
      AND COLUMN_NAME = 'current_vehicles'
);
PREPARE stmt FROM @current_vehicles_exists;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
