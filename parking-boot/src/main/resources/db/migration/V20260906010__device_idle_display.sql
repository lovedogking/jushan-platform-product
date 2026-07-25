-- ============================================================
-- v2.4 设备待机显示屏文字 + 显示时长
-- ============================================================
ALTER TABLE device
    ADD COLUMN display_idle_text VARCHAR(200) DEFAULT NULL COMMENT '待机默认显示文字，识别联动结束后恢复，{plate}=车牌占位符' AFTER display_deny_template;

ALTER TABLE device
    ADD COLUMN display_duration_sec INT NOT NULL DEFAULT 5 COMMENT '识别联动显示停留秒数，0=不自动恢复' AFTER display_idle_text;
