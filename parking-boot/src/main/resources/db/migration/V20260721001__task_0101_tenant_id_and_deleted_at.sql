-- =============================================================================
-- Flyway 迁移：TASK-0101 多租户数据隔离基座补齐
-- =============================================================================
-- 为 vehicle、wx_user、plate_binding、sys_user 表添加 tenant_id 和 deleted_at 字段，
-- 确保所有业务表满足多租户隔离与软删除的全局约束。
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. vehicle：添加 tenant_id 与 deleted_at
-- ---------------------------------------------------------------------------
ALTER TABLE vehicle
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id,
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';

-- ---------------------------------------------------------------------------
-- 2. wx_user：添加 tenant_id 与 deleted_at
-- ---------------------------------------------------------------------------
ALTER TABLE wx_user
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id,
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';

-- ---------------------------------------------------------------------------
-- 3. plate_binding：添加 tenant_id 与 deleted_at
-- ---------------------------------------------------------------------------
ALTER TABLE plate_binding
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id,
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询',
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';

-- ---------------------------------------------------------------------------
-- 4. sys_user：添加 deleted_at（tenant_id 已在 V20260711004 添加）
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user
    ADD COLUMN deleted_at DATETIME DEFAULT NULL COMMENT '软删除时间' AFTER updated_at,
    ADD INDEX idx_deleted_at (deleted_at) COMMENT '按软删除查询';
