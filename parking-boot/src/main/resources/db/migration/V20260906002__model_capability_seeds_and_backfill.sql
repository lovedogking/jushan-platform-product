-- ============================================================
-- Flyway 迁移：型号级能力种子 + 存量数据治理
-- ============================================================
-- 1. 新建/更新型号记录：芊熠 Q8、臻识 C5 的能力标记
-- 2. 存量芊熠相机能力纠错（去掉不该有的 CLOSE_GATE/KEEP_OPEN）
-- 3. 存量相机能力补漏：未设 capabilities 的相机从型号默认继承
-- 4. 双向车道 direction 未补录清单（输出告警，不自动修正 — 需人工判定）
-- ============================================================

-- -----------------------------------------------------------------------------
-- 1. 芊熠（Qianyi）型号种子：新建 Q8，替换占位 QY-01
-- -----------------------------------------------------------------------------
-- 停用占位型号 QY-01
UPDATE device_model SET status = 'DISABLED', description = '已替换为 Q8 型号'
WHERE code = 'QY-01' AND status = 'ENABLED';

-- 新建 Q8 型号（可开闸/可常开/可抓拍/不可关闸）
INSERT INTO device_model (vendor_id, name, code, device_type, status, capabilities, description)
SELECT v.id, 'Q8', 'QY-Q8', 'CAMERA', 'ENABLED', 'OPEN_GATE,KEEP_OPEN,CAPTURE',
       '芊熠 Q8 车牌识别相机（MQTT接入；继电器可开闸可常开不可关闸；关闸靠地感）'
FROM device_vendor v WHERE v.code = 'QIANYI'
AND NOT EXISTS (SELECT 1 FROM device_model WHERE code = 'QY-Q8');

-- -----------------------------------------------------------------------------
-- 2. 臻识（Zhenshi）型号种子：新建 C5（GPIO 控闸，可开可关可常开常关）
-- -----------------------------------------------------------------------------
INSERT INTO device_model (vendor_id, name, code, device_type, status, capabilities, description)
SELECT v.id, 'C5', 'ZS-C5', 'CAMERA', 'ENABLED', 'OPEN_GATE,CLOSE_GATE,KEEP_OPEN,KEEP_CLOSE,CAPTURE',
       '臻识 C5 车牌识别相机（GPIO 控闸，可开可关可常开常关，含主动抓拍）'
FROM device_vendor v WHERE v.code = 'ZHENSHI'
AND NOT EXISTS (SELECT 1 FROM device_model WHERE code = 'ZS-C5');

-- -----------------------------------------------------------------------------
-- 3. 存量芊熠相机能力纠错（去掉型号级无能力标记）
--    目标：型号为芊熠（vendor=QIANYI）的相机，capabilities 统一为型号默认
-- -----------------------------------------------------------------------------
UPDATE device d
    INNER JOIN device_model m ON d.model_id = m.id
    INNER JOIN device_vendor v ON m.vendor_id = v.id
SET d.capabilities = m.capabilities
WHERE v.code = 'QIANYI'
  AND d.device_type = 'CAMERA'
  AND d.capabilities IS NOT NULL
  AND d.capabilities != COALESCE(m.capabilities, '');

-- -----------------------------------------------------------------------------
-- 4. 存量相机能力补漏：未设 capabilities 的相机从型号默认继承
-- -----------------------------------------------------------------------------
UPDATE device d
    INNER JOIN device_model m ON d.model_id = m.id
SET d.capabilities = m.capabilities
WHERE d.device_type = 'CAMERA'
  AND (d.capabilities IS NULL OR d.capabilities = '')
  AND m.capabilities IS NOT NULL
  AND m.capabilities != '';

-- -----------------------------------------------------------------------------
-- 5. 双向车道 direction 未补录清单（仅输出提示，不自动修正）
--    运维需人工确认每条车道相机的实际识别方向后补录
-- -----------------------------------------------------------------------------
-- 以下查询可在执行迁移后手动运行以识别需要人工补录的相机：
-- SELECT d.id AS device_id, d.name, d.device_sn, l.id AS lane_id, l.name AS lane_name, l.type
-- FROM device d
--     INNER JOIN parking_lane l ON d.lane_id = l.id AND l.type = 3
-- WHERE d.device_type = 'CAMERA' AND d.recognition_direction IS NULL;
