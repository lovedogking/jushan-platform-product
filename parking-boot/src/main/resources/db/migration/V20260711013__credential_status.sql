-- =============================================================================
-- Flyway 迁移：凭据状态 — 强制首次修改密码（FIX-04）
-- =============================================================================
-- 为 sys_user 增加 credential_status 字段，标记默认密码是否已修改。
-- 存量管理员凭据标记为 EXPIRED，生产环境必须通过配置提供初始密码。
-- =============================================================================

-- 新增凭据状态列（默认 ACTIVE 表示无需强制修改）
ALTER TABLE sys_user
    ADD COLUMN credential_status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
    COMMENT '凭据状态：ACTIVE-正常, EXPIRED-需强制修改密码';

-- 将默认超级管理员的凭据标记为 EXPIRED（固定密码 admin123 已公开）
UPDATE sys_user
SET credential_status = 'EXPIRED'
WHERE username = 'admin'
  AND credential_status = 'ACTIVE';
