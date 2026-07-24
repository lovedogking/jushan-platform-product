-- ---------------------------------------------------------------------------
-- 修复 device_status_snapshot.tenant_id 非空约束导致平台用户状态查询失败
-- ---------------------------------------------------------------------------
-- 背景：
--   V20260717001 为业务表补 tenant_id 时设为非空。
--   平台用户（super_admin / platform_operator）无租户绑定（tenantId 为 NULL），
--   岗亭监控批量状态查询落快照时 INSERT 不带 tenant_id，
--   触发 "Field 'tenant_id' doesn't have a default value"，
--   外层兜底为「状态查询失败: ERROR」并误报设备离线严重告警。
-- 先例：
--   sys_audit_log.tenant_id 本就可空（平台操作时为空），本迁移对齐同一语义。
-- ---------------------------------------------------------------------------

ALTER TABLE device_status_snapshot
    MODIFY COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '租户ID（平台用户查询时为空）';
