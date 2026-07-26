ALTER TABLE device
    ADD COLUMN display_text_color INT DEFAULT 0 COMMENT '文字颜色 0白/1红/2蓝/3绿',
    ADD COLUMN display_rotate_mode INT DEFAULT 0 COMMENT '翻转方向 0正常/1上下翻转',
    ADD COLUMN display_brightness INT DEFAULT 3 COMMENT '亮度 0熄屏-5最亮',
    ADD COLUMN display_volume INT DEFAULT 3 COMMENT '音量 0静音-5最大';
