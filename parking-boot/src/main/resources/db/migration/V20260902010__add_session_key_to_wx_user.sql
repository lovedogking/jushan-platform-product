-- =============================================================================
-- Flyway 迁移：wx_user 表新增 session_key 字段
-- =============================================================================
-- 微信 session_key 用于旧版 getPhoneNumber 解密（可选）。
-- 仅当表结构无此字段时执行 ALTER。
-- =============================================================================

-- 检查字段是否已存在（Flyway 幂等：使用存储过程安全执行）
-- 如果字段已存在，MySQL 会抛出 1060 Duplicate column，使用条件判断避免
SET @col_exists = 0;
SELECT COUNT(*) INTO @col_exists
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'wx_user'
  AND COLUMN_NAME = 'session_key';

SET @sql = IF(@col_exists = 0,
    'ALTER TABLE wx_user ADD COLUMN session_key VARCHAR(100) COMMENT ''微信会话密钥'' AFTER phone',
    'SELECT ''session_key column already exists, skipping'' AS msg');

PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
