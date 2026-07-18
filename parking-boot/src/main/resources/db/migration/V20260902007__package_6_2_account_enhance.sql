-- =============================================================================
-- 任务包 6-2：账号体系收口 — sys_admin_account 扩展 + 停车场多对多 + 废弃标记
-- =============================================================================
-- 1. sys_admin_account 新增字段
-- 2. sys_admin_account_parking_lot 多对多关联表
-- 3. employee / sys_user / employee_parking_lot 废弃标记

-- ---------------------------------------------------------------------------
-- 1. sys_admin_account 新增字段
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account
    ADD COLUMN must_change_password TINYINT NOT NULL DEFAULT 0 COMMENT '是否必须修改密码：0=否，1=是' AFTER lock_until,
    ADD COLUMN allow_fee_reduction TINYINT NOT NULL DEFAULT 0 COMMENT '是否允许费用减免：0=否，1=是（仅岗亭管理员生效）' AFTER must_change_password;

-- 已有 level=3 的停车场管理员兼容：全部设为不强制改密、不允减免
UPDATE sys_admin_account SET must_change_password = 0, allow_fee_reduction = 0 WHERE level = 3;

-- 从 employee 表迁移数据到 sys_admin_account 的辅助：先不做数据迁移，仅建结构
-- （数据迁移在 Part L 中作为单独任务执行，确保先在测试环境验证）

-- ---------------------------------------------------------------------------
-- 2. sys_admin_account_parking_lot 表：管理员 ↔ 停车场 多对多关联
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_admin_account_parking_lot (
    id              BIGINT       NOT NULL COMMENT '主键（雪花 ID）',
    tenant_id       BIGINT       NOT NULL COMMENT '租户 ID',
    admin_account_id BIGINT      NOT NULL COMMENT '管理员账号 ID',
    parking_lot_id  BIGINT       NOT NULL COMMENT '停车场 ID',
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (id),
    INDEX idx_tenant (tenant_id),
    INDEX idx_admin_account (admin_account_id),
    INDEX idx_parking_lot (parking_lot_id),
    UNIQUE KEY uk_account_lot (admin_account_id, parking_lot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员账号-停车场关联表（岗亭管理员多车场支持）';

-- ---------------------------------------------------------------------------
-- 3. 标记旧表为废弃（不删表，仅添加注释）
--    注：employee / employee_parking_lot 表暂未创建，仅标记 sys_user
-- ---------------------------------------------------------------------------
ALTER TABLE sys_user COMMENT = '[已废弃-任务包6-2] 旧用户表已合并至 sys_admin_account';
