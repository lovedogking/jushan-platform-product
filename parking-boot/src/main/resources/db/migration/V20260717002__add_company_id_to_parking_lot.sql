-- =============================================================================
-- Flyway 迁移：为 parking_lot 补齐公司关联字段
-- =============================================================================
-- TASK-0101｜多租户数据隔离基座
-- 为 parking_lot 添加 company_id / group_id，供后续公司/集团档案功能使用。
-- 初始允许 NULL，V20260718001 负责回填并收紧为 NOT NULL。
-- =============================================================================

ALTER TABLE parking_lot
    ADD COLUMN company_id BIGINT DEFAULT NULL COMMENT '所属公司 ID（逻辑外键：company.id）' AFTER tenant_id,
    ADD COLUMN group_id   BIGINT DEFAULT NULL COMMENT '所属集团 ID（逻辑外键：company.id，冗余）' AFTER company_id,
    ADD INDEX idx_company_id (company_id) COMMENT '按公司查询';
