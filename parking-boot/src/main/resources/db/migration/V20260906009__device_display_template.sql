-- ============================================================
-- v2.3 设备识别联动配置 —— device 表新增显示屏模板字段
-- ============================================================
-- 说明：配合 voice_enabled/voice_welcome_template/voice_deny_template，
--       新增显示屏模板，识别事件时与语音同步触发。
--       {plate} 为车牌号占位符，\n 分隔多行。
-- 幂等：Flyway 保证不重复执行。
-- ============================================================

-- 准入显示屏模板（如 "{plate}\n欢迎回家"）
ALTER TABLE device
    ADD COLUMN display_welcome_template VARCHAR(200) DEFAULT NULL COMMENT '准入显示屏模板，{plate}=车牌占位符，\\n=换行' AFTER voice_deny_template;

-- 禁入显示屏模板（如 "{plate}\n禁止通行"）
ALTER TABLE device
    ADD COLUMN display_deny_template VARCHAR(200) DEFAULT NULL COMMENT '禁入显示屏模板，{plate}=车牌占位符，\\n=换行' AFTER display_welcome_template;
