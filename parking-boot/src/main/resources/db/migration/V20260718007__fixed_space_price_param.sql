-- 小程序固定车位链路：注入固定车位定价参数种子（全局行）
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, parking_lot_id, created_at, updated_at)
VALUES ('fixed_space.price_per_month_cents', '30000', '固定车位单价（分/月）', '计费设置', 'INT', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);
