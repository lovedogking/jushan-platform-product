-- 订正车辆绑定上限：从 5 → 3（确认项 84）
UPDATE sys_config SET config_value = '3' WHERE config_key = 'vehicle.bind_limit_per_user' AND config_value = '5';
