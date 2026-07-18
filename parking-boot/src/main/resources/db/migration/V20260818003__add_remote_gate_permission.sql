-- =============================================================================
-- B2: 运营端远程开闸 — 添加 device:remote:open 权限
-- =============================================================================
-- 背景：
--   Phase 1 B2 为运营端提供按车道远程开闸能力。
--   新增 device:remote:open 权限码，控制远程开闸功能的访问。
-- 安全：
--   - 超级管理员（super_admin）默认拥有
--   - 设备维护员（device_maintenance）、岗亭操作员（booth_operator）默认拥有
--   - 租户管理员（customer_admin）默认不拥有，需超管在自定义角色中手动勾选
--   - 固定角色（parking_manager 等）不自动获得此权限
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 插入权限定义（幂等）
-- ---------------------------------------------------------------------------
INSERT INTO sys_permission (code, name, description)
VALUES ('device:remote:open', '远程开闸', '运营端远程开启道闸')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- ---------------------------------------------------------------------------
-- 2. 为 SUPER_ADMIN 自定义角色授权
-- ---------------------------------------------------------------------------
INSERT INTO sys_role_permission (id, role_id, permission_code, permission_type, data_scope)
SELECT
    CONV(SUBSTRING(SHA2(CONCAT(scr.id, ':device:remote:open'), 256), 1, 16), 16, 10) % 9223372036854775807 AS id,
    scr.id,
    'device:remote:open' AS permission_code,
    'button' AS permission_type,
    'all' AS data_scope
FROM sys_custom_role scr
WHERE scr.role_code = 'SUPER_ADMIN'
  AND scr.deleted_at IS NULL
ON DUPLICATE KEY UPDATE permission_type = VALUES(permission_type), data_scope = VALUES(data_scope);
