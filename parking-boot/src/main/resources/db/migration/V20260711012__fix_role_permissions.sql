-- =============================================================================
-- Flyway 迁移：校正角色权限矩阵（FIX-12）
-- =============================================================================
-- 根据需求规格说明书和角色职责，补充以下缺失权限：
--   1. customer_admin 缺少 device:manage（应能管理设备台账）
--   2. parking_manager 缺少 parking:write（应能管理授权停车场的车道/设备）
-- =============================================================================
-- 使用 INSERT IGNORE 确保重复部署安全（已存在则跳过）
-- =============================================================================

-- customer_admin：补充 device:manage（FIX-12-R1）
INSERT IGNORE INTO sys_role_permission (role_code, permission_code, created_at) VALUES
('customer_admin', 'device:manage', NOW());

-- parking_manager：补充 parking:write（FIX-12-R2）
INSERT IGNORE INTO sys_role_permission (role_code, permission_code, created_at) VALUES
('parking_manager', 'parking:write', NOW());
