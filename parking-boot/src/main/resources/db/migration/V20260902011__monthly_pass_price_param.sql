-- 小程序月卡链路：注入月卡定价参数种子（全局行）
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, parking_lot_id, created_at, updated_at)
VALUES ('monthly_pass.price_per_month_cents', '30000', '月卡单价（分/月）', '计费设置', 'INT', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);
