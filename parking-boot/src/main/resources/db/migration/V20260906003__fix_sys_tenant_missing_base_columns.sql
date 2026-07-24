-- ---------------------------------------------------------------------------
-- 修复 sys_tenant 与 BaseEntity 公共列不对齐
-- ---------------------------------------------------------------------------
-- 背景：
--   V20260722001 创建 sys_tenant 时未包含 BaseEntity 公共列（tenant_id / deleted_at），
--   V20260723001 为其他 sprint1 表补 deleted_at 时遗漏了本表。
--   SysTenant 实体继承 BaseEntity（含 @TableLogic deletedAt），
--   MyBatis-Plus 查询会带出 tenant_id、deleted_at 列并附加 deleted_at IS NULL 条件，
--   列缺失导致 BadSqlGrammarException。
-- 说明：
--   sys_tenant 为系统级表（租户拦截器忽略表），tenant_id 恒为 NULL。
-- ---------------------------------------------------------------------------

ALTER TABLE sys_tenant
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '租户ID（系统表，恒为NULL）' AFTER status;

ALTER TABLE sys_tenant
    ADD COLUMN deleted_at DATETIME(3) DEFAULT NULL COMMENT '软删除时间' AFTER updated_at;
