-- 修复软删除账号后无法重新创建同名账号的问题
-- 原唯一索引 (tenant_id, username) 不包含删除标记，导致已软删除的账号仍占用用户名。
-- 通过增加一个用于唯一性校验的派生列（软删除为原时间，未删除为固定时间），
-- 使唯一索引能够区分“未删除”与“已软删除”记录。

ALTER TABLE sys_admin_account
    ADD COLUMN deleted_at_for_uk DATETIME AS (COALESCE(deleted_at, '1970-01-01 00:00:00')) STORED NOT NULL
        COMMENT '用于唯一索引区分软删除记录',
    DROP INDEX uk_tenant_username,
    ADD UNIQUE INDEX uk_tenant_username (tenant_id, username, deleted_at_for_uk);
