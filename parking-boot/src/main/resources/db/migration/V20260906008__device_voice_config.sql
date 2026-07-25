-- ============================================================
-- v2.3 设备语音播报配置 —— device 表新增语音开关和模板字段
-- ============================================================
-- 说明：每个相机设备可独立配置是否自动语音播报以及播报模板。
--       {plate} 为车牌号占位符，播报时替换为实际车牌。
-- 幂等：使用 ADD COLUMN IF NOT EXISTS（MySQL 8.0+ 不支持，使用存储过程兜底）。
-- ============================================================

-- 语音播报开关（0=关闭, 1=开启），默认关闭
ALTER TABLE device
    ADD COLUMN voice_enabled TINYINT(1) NOT NULL DEFAULT 0 COMMENT '识别后自动语音播报开关' AFTER gateway;

-- 准入播报模板（如 "{plate},欢迎光临"）
ALTER TABLE device
    ADD COLUMN voice_welcome_template VARCHAR(200) DEFAULT NULL COMMENT '准入语音模板，{plate}=车牌占位符' AFTER voice_enabled;

-- 禁入播报模板（如 "{plate},禁止通行"）
ALTER TABLE device
    ADD COLUMN voice_deny_template VARCHAR(200) DEFAULT NULL COMMENT '禁入语音模板，{plate}=车牌占位符' AFTER voice_welcome_template;
