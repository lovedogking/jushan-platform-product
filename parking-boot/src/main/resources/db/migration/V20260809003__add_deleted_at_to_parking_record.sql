-- =============================================================================
-- Flyway 迁移：为 parking_record 表添加 deleted_at 列（软删除支持）
-- =============================================================================
-- 背景：
--   MyBatis-Plus 逻辑删除拦截器（TenantLineInnerInterceptor）要求所有业务表
--   必须存在 deleted_at 列。parking_record 初始建表时遗漏该列，本次补加。
-- =============================================================================

-- 使用 information_schema + PREPARE 判断列是否存在后安全执行
-- E2E 联调期间该列已被手动添加，故使用条件判断确保幂等
SET @add_deleted_at = (
    SELECT IF(
        COUNT(*) = 0,
        'ALTER TABLE parking_record ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT ''软删除时间（NULL 表示未删除）'' AFTER updated_at',
        'SELECT 1 AS already_exists'
    )
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_record'
      AND COLUMN_NAME = 'deleted_at'
);
PREPARE stmt FROM @add_deleted_at;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
