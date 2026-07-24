-- =============================================================================
-- Device Access 数据库初始化
-- =============================================================================
-- Docker 首次启动时通过 init 脚本自动创建
-- Flyway 会在 device-access 启动后自行迁移表结构
-- =============================================================================
CREATE DATABASE IF NOT EXISTS `device_access`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 授予 jushan 用户对 device_access 库的完整权限
GRANT ALL PRIVILEGES ON `device_access`.* TO 'jushan'@'%';
FLUSH PRIVILEGES;
