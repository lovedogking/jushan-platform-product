-- 回填现有公司排序号
-- 问题：历史数据 sort_order 默认为 0，导致列表中多条记录排序相同。
-- 处理：按租户分组，根据创建时间（id 作为兜底）递增分配排序号。

UPDATE company c
  JOIN (
    SELECT id,
           ROW_NUMBER() OVER (PARTITION BY tenant_id ORDER BY created_at ASC, id ASC) AS rn
    FROM company
    WHERE deleted_at IS NULL
  ) t ON c.id = t.id
SET c.sort_order = t.rn
WHERE c.deleted_at IS NULL;
