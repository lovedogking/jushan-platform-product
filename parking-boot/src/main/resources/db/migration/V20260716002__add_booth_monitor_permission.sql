-- =============================================================================
-- Flyway 迁移：新增岗亭实时监控权限（P005）
-- =============================================================================
-- 为岗亭操作员增加独立的 `booth:monitor` 权限，用于访问：
--   - GET /booth/monitor/snapshot
--   - POST /booth/monitor/devices/refresh
--   - POST /booth/monitor/alerts/{id}/ack
-- 平台管理员、设备运维等即使拥有 record:read 也不能访问岗亭监控接口。
-- =============================================================================

INSERT INTO sys_permission (code, name, description) VALUES
('booth:monitor', '岗亭实时监控', '查看岗亭实时监控页面、接收 WebSocket 推送、确认异常提醒')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('booth_operator', 'booth:monitor')
ON DUPLICATE KEY UPDATE role_code = role_code;
