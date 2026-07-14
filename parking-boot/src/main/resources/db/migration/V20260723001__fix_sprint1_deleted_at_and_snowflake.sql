-- =============================================================================
-- Flyway 迁移：修复 Sprint 1 DDL 与实体层不一致问题
-- =============================================================================
-- 问题：
--   1. DDL 使用 is_deleted TINYINT，实体层使用 deletedAt LocalDateTime
--   2. DDL 使用 AUTO_INCREMENT，规范要求 Snowflake 分布式 ID
--   3. BCrypt 哈希格式不兼容（$2b$ → $2a$）
-- =============================================================================

-- ---------------------------------------------------------------------------
-- 1. 修正 sys_company：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_company DROP COLUMN is_deleted;
ALTER TABLE sys_company ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER sort_order;
ALTER TABLE sys_company MODIFY COLUMN id BIGINT NOT NULL COMMENT '公司ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 2. 修正 sys_admin_account：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account DROP COLUMN is_deleted;
ALTER TABLE sys_admin_account ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER last_login_time;
ALTER TABLE sys_admin_account MODIFY COLUMN id BIGINT NOT NULL COMMENT '账号ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 3. 修正 sys_custom_role：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_custom_role DROP COLUMN is_deleted;
ALTER TABLE sys_custom_role ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER description;
ALTER TABLE sys_custom_role MODIFY COLUMN id BIGINT NOT NULL COMMENT '角色ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 4. 修正 sys_role_permission：删除 is_deleted，新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_role_permission DROP COLUMN is_deleted;
ALTER TABLE sys_role_permission ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER data_scope;
ALTER TABLE sys_role_permission ADD COLUMN updated_at DATETIME(3) DEFAULT NULL COMMENT '更新时间' AFTER deleted_at;
ALTER TABLE sys_role_permission MODIFY COLUMN id BIGINT NOT NULL COMMENT '权限ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 5. 修正 sys_auth_code：新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_auth_code ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER activated_at;
ALTER TABLE sys_auth_code MODIFY COLUMN id BIGINT NOT NULL COMMENT '授权码ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 6. 修正 sys_admin_account_role：新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_admin_account_role ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER created_at;
ALTER TABLE sys_admin_account_role ADD COLUMN updated_at DATETIME(3) DEFAULT NULL COMMENT '更新时间' AFTER deleted_at;
ALTER TABLE sys_admin_account_role MODIFY COLUMN id BIGINT NOT NULL COMMENT '关联ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 7. 修正 sys_business_log：新增 deleted_at，移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_business_log ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER error_msg;
ALTER TABLE sys_business_log ADD COLUMN updated_at DATETIME(3) DEFAULT NULL COMMENT '更新时间' AFTER deleted_at;
ALTER TABLE sys_business_log MODIFY COLUMN id BIGINT NOT NULL COMMENT '日志ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 8. 修正 sys_tenant：移除 AUTO_INCREMENT
-- ---------------------------------------------------------------------------
ALTER TABLE sys_tenant MODIFY COLUMN id BIGINT NOT NULL COMMENT '租户ID（Snowflake）';

-- ---------------------------------------------------------------------------
-- 9. 修正初始化数据：更新 BCrypt 哈希为 Spring Security 兼容格式
-- ---------------------------------------------------------------------------
-- 原哈希 $2b$12$... 是 Node.js bcrypt 变体，Java BCryptPasswordEncoder 默认使用 $2a$ 前缀
-- 使用 Spring Security 的 BCryptPasswordEncoder 重新生成（密码：admin123）
UPDATE sys_admin_account SET password = '$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lqkkO9LZ3Kz1VxhG6' WHERE id = 1;
