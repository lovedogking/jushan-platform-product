-- ============================================================
-- v0.8 语音控制能力 —— 更新芊熠相机产品能力列表
-- ============================================================
-- 说明：给已部署的芊熠产品（QY-01、QY-Q3）追加 VOICE_CONTROL 能力。
--       语音通过 rs485 透传 OLM-M1D 0x30/0x31 命令到 LED 屏卡。
--       t_device_product 的 capabilities 为 JSON 数组字符串，
--       本迁移在原数组中追加 "VOICE_CONTROL"。
-- 幂等：UPDATE 命中 brand/model 唯一组合；重复执行结果不变。
-- ============================================================

UPDATE t_device_product
SET capabilities = '["DISPLAY_TEXT","DISPLAY_SAVE","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE","CAPTURE","VOICE_CONTROL"]'
WHERE brand = '芊熠' AND model = 'QY-01';

UPDATE t_device_product
SET capabilities = '["DISPLAY_TEXT","DISPLAY_SAVE","TIME_SYNC","OPEN_GATE","CLOSE_GATE","LOCK_OPEN_GATE","CAPTURE","VOICE_CONTROL"]'
WHERE brand = '芊熠' AND model = 'QY-Q3';
