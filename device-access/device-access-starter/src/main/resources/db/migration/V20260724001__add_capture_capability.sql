-- ============================================================
-- v0.7 主动抓拍能力 —— 更新芊熠相机产品能力列表
-- ============================================================
-- Flyway 版本：device-access 首次启用 Flyway 后的首批迁移之一。
-- 说明：给已部署的芊熠产品（QY-01）追加 CAPTURE 能力。
--       t_device_product 的 capabilities 为 JSON 数组字符串，
--       本迁移在原数组中追加 "CAPTURE"。
-- 幂等：UPDATE 命中 brand/model 唯一组合；重复执行结果不变。
-- ============================================================

UPDATE t_device_product
SET capabilities = '["DISPLAY_TEXT","DISPLAY_SAVE","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE","CAPTURE"]'
WHERE brand = '芊熠' AND model = 'QY-01';
