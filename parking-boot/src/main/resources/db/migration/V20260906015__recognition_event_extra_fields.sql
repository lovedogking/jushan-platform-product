ALTER TABLE recognition_event_log
    ADD COLUMN body_color INT NULL COMMENT '车身颜色（臻识编码 0-12/255未知）',
    ADD COLUMN car_logo VARCHAR(64) NULL COMMENT '车标品牌',
    ADD COLUMN confidence INT NULL COMMENT '识别可信度 1-100';
