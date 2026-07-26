ALTER TABLE device
    ADD COLUMN voice_volume INT DEFAULT 80 COMMENT '语音音量 1-100，默认80',
    ADD COLUMN voice_male TINYINT DEFAULT 0 COMMENT '语音类型 0男声/1女声';
