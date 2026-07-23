-- ============================================================
-- v0.7 主动抓拍能力 —— 更新芊熠相机产品能力列表
-- ============================================================
-- 背景：岗亭端人工放行弹窗增加「抓拍」按钮，需要相机产品声明 CAPTURE 能力。
-- 本迁移为已部署的芊熠产品（QY-01）追加 CAPTURE 能力。
-- 说明：t_device_product 的 capabilities 为 JSON 数组字符串，本迁移在原数组中追加
--       "CAPTURE"；如果产品不存在则忽略。
-- 执行方式:
--   mysql -u root -p --default-character-set=utf8mb4 device_access < migration-v0.7-capture.sql
-- 注意：device-access 已启用 Flyway，启动时会自动执行
--       db/migration/V20260724001__add_capture_capability.sql；本文件保留作为手动回退/参考。
-- ============================================================

UPDATE t_device_product
SET capabilities = '["DISPLAY_TEXT","DISPLAY_SAVE","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE","CAPTURE"]'
WHERE brand = '芊熠' AND model = 'QY-01';
