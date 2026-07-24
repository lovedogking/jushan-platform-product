-- ============================================================
-- Flyway 迁移：型号 code/name 重命名 + capabilities 修正
-- ============================================================
-- 1. 臻识 ZS-C5H → ZS-C5（名称 C5H → C5）
-- 2. 芊熠 QY-Q3  → QY-Q8（名称 Q3  → Q8）
-- 3. capabilities 修正：ZS-C5 增加 KEEP_OPEN/KEEP_CLOSE；QY-Q8 增加 KEEP_OPEN
-- 4. 存量相机能力同步（从型号继承最新 capabilities 的相机也一并更新）
-- ============================================================

-- -----------------------------------------------------------------------------
-- 1. 臻识 ZS-C5H → ZS-C5
-- -----------------------------------------------------------------------------
UPDATE device_model
SET name = 'C5', code = 'ZS-C5',
    capabilities = 'OPEN_GATE,CLOSE_GATE,KEEP_OPEN,KEEP_CLOSE,CAPTURE',
    description = '臻识 C5 车牌识别相机（GPIO 控闸，可开可关可常开常关，含主动抓拍）'
WHERE code = 'ZS-C5H';

-- -----------------------------------------------------------------------------
-- 2. 芊熠 QY-Q3 → QY-Q8
-- -----------------------------------------------------------------------------
UPDATE device_model
SET name = 'Q8', code = 'QY-Q8',
    capabilities = 'OPEN_GATE,KEEP_OPEN,CAPTURE',
    description = '芊熠 Q8 车牌识别相机（MQTT接入；继电器可开闸可常开不可关闸；关闸靠地感）'
WHERE code = 'QY-Q3';

-- -----------------------------------------------------------------------------
-- 3. 存量相机能力同步：型号 capabilities 已变，相机级也同步更新
--    （仅更新 camera 类型、capabilities 与旧型号能力一致的设备）
-- -----------------------------------------------------------------------------
UPDATE device d
    INNER JOIN device_model m ON d.model_id = m.id
SET d.capabilities = m.capabilities
WHERE d.device_type = 'CAMERA'
  AND m.code IN ('ZS-C5', 'QY-Q8');
