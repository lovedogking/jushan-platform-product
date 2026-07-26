ALTER TABLE device
    ADD COLUMN voice_release_template VARCHAR(512) NULL COMMENT '手动放行语音模板（支持占位符：{plate}车牌号, {type}车辆类型）'
    AFTER voice_deny_template;
