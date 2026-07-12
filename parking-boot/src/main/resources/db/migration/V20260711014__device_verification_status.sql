-- =============================================================================
-- Flyway 迁移：设备厂商/型号验证状态（FIX-15）
-- =============================================================================
-- 为 device_vendor 和 device_model 增加 verification_status 字段，
-- 区分"配置存在"与"真机已验证"，防止未验证型号被标记为已具备冻结能力。
-- V01–V04 真机验证尚未完成，存量种子数据标记为 PENDING。
-- =============================================================================

-- 设备厂商：新增验证状态
ALTER TABLE device_vendor
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT '验证状态：VERIFIED-真机已验证, PENDING-待验证, UNVERIFIED-未验证'
    AFTER status;

-- 设备型号：新增验证状态
ALTER TABLE device_model
    ADD COLUMN verification_status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
    COMMENT '验证状态：VERIFIED-真机已验证, PENDING-待验证, UNVERIFIED-未验证'
    AFTER status;

-- 存量种子数据标记为 PENDING（V01–V04 真机验证尚未完成）
UPDATE device_vendor SET verification_status = 'PENDING';
UPDATE device_model SET verification_status = 'PENDING';
