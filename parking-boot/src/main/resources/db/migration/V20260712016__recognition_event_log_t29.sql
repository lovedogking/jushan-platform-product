-- =============================================================================
-- Flyway 迁移：识别事件日志表增强（T29｜识别事件校验、标准化与幂等）
-- =============================================================================
-- 内容：
--   1. 添加处理状态字段（status）— 追踪事件处理生命周期
--   2. 添加失败原因字段（failure_reason）— 记录校验/处理失败详情
--   3. 添加标准化车牌字段（standardized_plate）— 存储标准化后的车牌号
--   4. 添加厂商事件 ID 字段（vendor_event_id）— Device Access 对接时使用
--   5. 在 event_id 上添加 UNIQUE 索引 — 业务层幂等的数据库最终保障
-- =============================================================================

-- 1. 新增字段
ALTER TABLE recognition_event_log
    ADD COLUMN status             VARCHAR(32)  NOT NULL DEFAULT 'RECEIVED'  COMMENT '处理状态：RECEIVED/PROCESSING/PROCESSED/FAILED',
    ADD COLUMN failure_reason     VARCHAR(512) DEFAULT NULL                 COMMENT '失败原因（校验失败、设备不存在等）',
    ADD COLUMN standardized_plate VARCHAR(32)  DEFAULT NULL                 COMMENT '标准化车牌号（去空格、统一大写）',
    ADD COLUMN vendor_event_id    VARCHAR(128) DEFAULT NULL                 COMMENT '厂商原始事件ID（Device Access 对接时使用）';

-- 2. 业务幂等：event_id 唯一约束
--    MySQL 的 UNIQUE INDEX 同时提供唯一约束和查询优化；
--    原有的 idx_event_id (普通索引) 保留兼容（Flyway 不删除已发布迁移的对象）。
ALTER TABLE recognition_event_log
    ADD UNIQUE INDEX uk_event_id (event_id);

-- 3. 新增索引：按状态查询（岗亭实时列表、补偿重试）
ALTER TABLE recognition_event_log
    ADD INDEX idx_status (status);
