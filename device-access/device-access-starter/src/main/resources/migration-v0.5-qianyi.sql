-- ============================================================
-- v0.5 芊熠相机接入 — 存量数据库产品种子迁移
-- ============================================================
-- 新增芊熠车牌相机产品（能力：开/关闸、常开、校时、屏显）
-- model 为占位值 QY-01，部署时按实际设备型号（如 S8_2）调整
--
-- 执行方式:
--   mysql -u root -p --default-character-set=utf8mb4 device_access < migration-v0.5-qianyi.sql
-- ============================================================

INSERT IGNORE INTO t_device_product (brand, model, product_name, device_type, protocol, capabilities) VALUES
('芊熠', 'QY-01', '芊熠 QY-01', 'CAMERA', 'MQTT', '["DISPLAY_TEXT","DISPLAY_SAVE","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE"]');
