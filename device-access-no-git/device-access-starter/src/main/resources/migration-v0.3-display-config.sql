-- ============================================================
-- v0.3 显示屏配置持久化 — 存量数据库迁移
-- ============================================================
-- 新增 display_enabled / display_mode 字段到 t_device 表
-- 用于持久化显示屏启用状态和布局模式
--
-- 执行方式:
--   mysql -u root -p --default-character-set=utf8mb4 device_access < migration-v0.3-display-config.sql
-- ============================================================

ALTER TABLE t_device
    ADD COLUMN display_enabled TINYINT     NOT NULL DEFAULT 1         COMMENT '显示屏启用: 0-关闭, 1-启用'          AFTER last_online_time,
    ADD COLUMN display_mode   VARCHAR(16) NOT NULL DEFAULT 'TWO_LINE' COMMENT '显示屏模式: TWO_LINE / FOUR_LINE' AFTER display_enabled;
