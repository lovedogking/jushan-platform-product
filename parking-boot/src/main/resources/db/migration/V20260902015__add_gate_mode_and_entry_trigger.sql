-- GAP-01/04: 车道闸机模式 + 会话入场触发方式
-- 一期范围：gate_mode 控制车道自动/常开/常关行为；entry_trigger 记录入场触发来源

ALTER TABLE parking_lane
    ADD COLUMN gate_mode VARCHAR(20) NOT NULL DEFAULT 'AUTO'
    COMMENT '闸机模式: AUTO-自动, ALWAYS_OPEN-常开, ALWAYS_CLOSE-常关'
    AFTER camera_mode;

ALTER TABLE parking_session
    ADD COLUMN entry_trigger VARCHAR(20) NULL
    COMMENT '入场触发方式: whitelist_auto-白名单自动, manual_open-人工放行, always_open_period-常开时段, manual_entry-人工补录'
    AFTER entry_operator;
