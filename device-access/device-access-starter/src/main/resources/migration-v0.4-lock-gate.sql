-- ============================================================
-- v0.4 锁闸功能 — 存量数据库能力迁移
-- ============================================================
-- 为 ZHENSHI C5H / 臻识 C6H / 信路通 XLT-01 产品添加锁闸能力
--
-- 执行方式:
--   mysql -u root -p --default-character-set=utf8mb4 device_access < migration-v0.4-lock-gate.sql
-- ============================================================

UPDATE t_device_product
SET capabilities = '["DISPLAY_TEXT","DISPLAY_SAVE","DISPLAY_CONFIG","VOICE_CONTROL","DISPLAY_ENHANCED","PERIPHERAL_CONTROL","TIME_SYNC","OPEN_GATE","CLOSE_GATE","VIDEO_STREAM","LOCK_OPEN_GATE","LOCK_CLOSE_GATE"]'
WHERE brand IN ('ZHENSHI', '臻识', '信路通');
