ALTER TABLE device
    ADD COLUMN voice_entry_welcome_template VARCHAR(512) NULL COMMENT '入场欢迎语音模板',
    ADD COLUMN voice_exit_welcome_template VARCHAR(512) NULL COMMENT '出场欢送语音模板',
    ADD COLUMN display_entry_welcome_template VARCHAR(512) NULL COMMENT '入场欢迎显示模板',
    ADD COLUMN display_exit_welcome_template VARCHAR(512) NULL COMMENT '出场欢送显示模板';
