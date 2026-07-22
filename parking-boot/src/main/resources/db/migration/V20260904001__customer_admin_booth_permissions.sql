-- ---------------------------------------------------------------------------
-- V1.5: 为租户管理员（customer_admin）补岗亭监控页所需全部权限码
-- ---------------------------------------------------------------------------
-- 背景：
--   V1.5 起超管与租户管理员均可进入并操作岗亭工作区。
--   岗亭监控页（booth-web monitor）依赖以下权限码：
--     booth:monitor  实时监控快照/设备刷新/告警确认/车牌修正/无牌车
--     booth:operate  车场列表/手动开闸关闸/常开常关/批量开闸/出场收费/交接班
--     booth:view     在场车辆/车辆历史/停车会话查询/交接班记录查询
--     fee:read       查询当前收费规则
--     fee:write      岗亭端临时调整收费规则
--     fee:reduce     费用减免
--     record:read    现场收费记录查询
--   既有迁移已为 customer_admin 补过部分码（V20260804001），此处幂等补齐全量。
-- 写法：
--   1. 权限码字典表 sys_permission 幂等补齐（仅作文档登记，鉴权读 sys_role_permission）。
--   2. sys_role_permission 按 role_id + permission_code 唯一键幂等插入。
-- ---------------------------------------------------------------------------

-- 1. 权限码字典登记（幂等）
INSERT INTO sys_permission (code, name, description)
SELECT 'booth:view', '岗亭查询', '岗亭端在场车辆/车辆历史/会话/交接班查询'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'booth:view');
INSERT INTO sys_permission (code, name, description)
SELECT 'booth:operate', '岗亭操作', '岗亭端手动开闸/关闸/常开常关/人工放行/出场收费'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'booth:operate');
INSERT INTO sys_permission (code, name, description)
SELECT 'booth:monitor', '岗亭实时监控', '查看岗亭实时监控页面、接收 WebSocket 推送、确认异常提醒'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'booth:monitor');
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:read', '查看收费规则', '查询停车场当前生效的收费规则'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:read');
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:write', '管理收费规则', '创建/编辑收费规则（含岗亭端临时调整）'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:write');
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:reduce', '费用减免', '对收费金额进行减免，需要二次确认超阈值操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:reduce');
INSERT INTO sys_permission (code, name, description)
SELECT 'record:read', '查看通行记录', '查看入场/出场通行记录和停车记录'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'record:read');

-- 2. 为所有未删除的 customer_admin 角色补齐岗亭权限（幂等）
INSERT INTO sys_role_permission (role_id, permission_code, permission_type, data_scope)
SELECT scr.id, perm.permission_code, perm.permission_type, perm.data_scope
FROM sys_custom_role scr
CROSS JOIN (
    SELECT 'booth:monitor' AS permission_code, 'button' AS permission_type, 'company' AS data_scope
    UNION ALL SELECT 'booth:operate', 'button', 'company'
    UNION ALL SELECT 'booth:view', 'menu', 'company'
    UNION ALL SELECT 'fee:read', 'menu', 'company'
    UNION ALL SELECT 'fee:write', 'button', 'company'
    UNION ALL SELECT 'fee:reduce', 'button', 'company'
    UNION ALL SELECT 'record:read', 'menu', 'company'
) perm
WHERE scr.role_code = 'customer_admin'
  AND scr.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission srp
      WHERE srp.role_id = scr.id AND srp.permission_code = perm.permission_code
  );
