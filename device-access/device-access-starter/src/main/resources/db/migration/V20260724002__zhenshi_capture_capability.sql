-- ============================================================
-- v0.7.1 臻识相机补 CAPTURE 能力
-- ============================================================
-- 背景：V20260724001 仅为芊熠 QY-01 追加 CAPTURE 能力，遗漏了臻识相机。
--       ZhenshiDeviceCoordinator.capture 已实现（MQTT snapshot 两段式）；
--       平台侧型号种子（parking V20260906002）已声明臻识相机具备 CAPTURE 能力，
--       本迁移对齐 device-access 侧产品能力声明。
-- 说明：capabilities 为 JSON 数组字符串，幂等追加 "CAPTURE"（已存在则跳过）。
-- ============================================================

UPDATE t_device_product
SET capabilities = JSON_ARRAY_APPEND(capabilities, '$', 'CAPTURE')
WHERE brand IN ('ZHENSHI', '臻识')
  AND JSON_VALID(capabilities)
  AND JSON_CONTAINS(capabilities, '"CAPTURE"') = 0;
