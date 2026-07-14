-- =============================================================================
-- Flyway 迁移：公司/集团档案表与停车场所属公司关联
-- =============================================================================
-- TASK-0102｜公司/集团档案管理
-- 1. 创建 company 表，支持集团-子公司-分公司三级树形结构。
-- 2. 为 parking_lot 增加 company_id / group_id 关联字段。
-- 3. 为历史租户与停车场生成默认公司，保证非空约束可落地。
-- 4. 新增公司管理相关权限并映射到固定角色。
-- 5. 清理 parking_lot 中与当前需求文档不符的停用范围冗余字段。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. 公司/集团档案表
-- -----------------------------------------------------------------------------
CREATE TABLE company
(
    id            BIGINT       AUTO_INCREMENT PRIMARY KEY COMMENT '公司 ID',
    tenant_id     BIGINT       NOT NULL COMMENT '所属租户 ID',
    parent_id     BIGINT       DEFAULT NULL COMMENT '上级公司 ID（逻辑外键：company.id，顶级为 NULL）',
    name          VARCHAR(128) NOT NULL COMMENT '公司名称',
    level         TINYINT      NOT NULL DEFAULT 1 COMMENT '公司级别：1-集团, 2-子公司, 3-分公司',
    status        TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1-正常, 2-暂停, 3-注销',
    sort_order    INT          NOT NULL DEFAULT 0 COMMENT '同级排序',
    path          VARCHAR(512) NOT NULL DEFAULT '' COMMENT '树路径编码（如 /1/12/123/）',
    contact_name  VARCHAR(64)  DEFAULT NULL COMMENT '联系人',
    contact_phone VARCHAR(32)  DEFAULT NULL COMMENT '联系电话',
    deleted_at    DATETIME(3)  DEFAULT NULL COMMENT '软删除时间',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    INDEX idx_parent_id (parent_id) COMMENT '按上级查询',
    INDEX idx_status (status) COMMENT '按状态查询',
    INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询',
    INDEX idx_path (path) COMMENT '按路径查询',
    UNIQUE KEY uk_tenant_name_deleted (tenant_id, name, (CAST(deleted_at IS NULL AS UNSIGNED))) COMMENT '租户内未删除公司名称唯一'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公司/集团档案表';

-- -----------------------------------------------------------------------------
-- 2. 停车场新增公司关联字段
-- -----------------------------------------------------------------------------
-- 已由 V20260717002 添加 company_id / group_id，此处仅做回填与约束收紧。

-- -----------------------------------------------------------------------------
-- 3. 历史数据回填：为每个租户生成默认集团，并关联历史停车场
-- -----------------------------------------------------------------------------
-- 3.1 为每个租户插入默认集团（幂等：按租户 ID + 名称软删除唯一键，NULL 视为唯一）
INSERT INTO company (tenant_id, parent_id, name, level, status, sort_order, path, contact_name, contact_phone)
SELECT t.id,
       NULL,
       t.name,
       1,
       1,
       0,
       '/',
       t.contact_person,
       t.contact_phone
FROM tenant t
WHERE t.status != 'REJECTED'
  AND NOT EXISTS (SELECT 1 FROM company c WHERE c.tenant_id = t.id AND c.level = 1 AND c.deleted_at IS NULL);

-- 3.2 修正默认集团公司的 path 为实际公司 ID（插入时无法预知自增 ID）
UPDATE company
SET path = CONCAT('/', id, '/')
WHERE level = 1
  AND parent_id IS NULL
  AND deleted_at IS NULL;

-- 3.3 将历史停车场的 company_id 指向租户默认集团
UPDATE parking_lot pl
    JOIN company c ON c.tenant_id = pl.tenant_id AND c.level = 1 AND c.deleted_at IS NULL
   SET pl.company_id = c.id,
       pl.group_id   = c.id
 WHERE pl.company_id IS NULL;

-- 3.4 移除 company_id 的临时默认值
ALTER TABLE parking_lot
    MODIFY COLUMN company_id BIGINT NOT NULL COMMENT '所属公司 ID（逻辑外键：company.id）';

-- -----------------------------------------------------------------------------
-- 4. 新增公司管理权限并映射到固定角色
-- -----------------------------------------------------------------------------
INSERT INTO sys_permission (code, name, description) VALUES
('company:read',  '查看公司', '查看公司/集团档案'),
('company:write', '管理公司', '创建、编辑公司/集团档案'),
('company:delete','删除公司', '软删除公司/集团档案')
ON DUPLICATE KEY UPDATE name = VALUES(name), description = VALUES(description);

-- 超级管理员：拥有全部权限
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('super_admin', 'company:read'),
('super_admin', 'company:write'),
('super_admin', 'company:delete')
ON DUPLICATE KEY UPDATE role_code = role_code;

-- 平台运营：查看与协助配置
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('platform_operator', 'company:read'),
('platform_operator', 'company:write')
ON DUPLICATE KEY UPDATE role_code = role_code;

-- 客户管理员：管理本租户公司
INSERT INTO sys_role_permission (role_code, permission_code) VALUES
('customer_admin', 'company:read'),
('customer_admin', 'company:write'),
('customer_admin', 'company:delete')
ON DUPLICATE KEY UPDATE role_code = role_code;

-- -----------------------------------------------------------------------------
-- 5. 清理 parking_lot 冗余字段（与当前 PRD/开发计划不符的停用范围字段）
-- -----------------------------------------------------------------------------
-- 注意：当前代码（ParkingLotService、EntryService、测试）仍在使用以下字段，
-- 因此暂不删除。待 TASK-0102 后续清理工作完成后再统一移除。
--
-- 备份冗余列数据到临时表，便于必要时回查
-- CREATE TABLE IF NOT EXISTS _backup_parking_lot_disable_columns AS
-- SELECT id, disable_new_entries, disable_payment, disable_exit, disable_auto_gate, disable_only_config
-- FROM parking_lot;
--
-- ALTER TABLE parking_lot
--     DROP COLUMN disable_new_entries,
--     DROP COLUMN disable_payment,
--     DROP COLUMN disable_exit,
--     DROP COLUMN disable_auto_gate,
--     DROP COLUMN disable_only_config;
