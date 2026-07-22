-- ============================================================
-- 芊熠（Qianyi）设备厂商/型号种子数据
-- ============================================================
-- 背景：芊熠车牌相机经 device-access v0.5（MQTT 协议适配）+ v0.6（抓拍图片
--       独立上传）接入平台。本迁移在平台设备台账中登记厂商与型号，
--       供运营后台创建设备时选择（前端厂商/型号下拉动态加载，无需前端改动）。
-- 说明：型号 code 暂以 QY-01 占位（与 device-access t_device_product 种子一致），
--       真机联调确认实际型号（如 S8_2）后可再增补或修正。
-- ============================================================

INSERT INTO device_vendor (name, code, status, description) VALUES
('芊熠', 'QIANYI', 'ENABLED', '芊熠智能，MQTT 协议接入（v0.5），抓拍图片独立上传（v0.6）');

INSERT INTO device_model (vendor_id, name, code, device_type, status, description)
SELECT v.id, 'QY-01', 'QY-01', 'CAMERA', 'ENABLED', '芊熠车牌识别相机（占位型号，真机确认后修正）'
FROM device_vendor v WHERE v.code = 'QIANYI';
