-- =============================================================================
-- Flyway 迁移：为业务表补齐 tenant_id 列与索引
-- =============================================================================
-- TASK-0101｜多租户数据隔离基座
-- 为当前缺少 tenant_id 的业务表添加租户字段，并通过关联表回填历史数据。
-- 所有业务表最终 tenant_id 均设为 NOT NULL，确保 MyBatis-Plus 租户拦截器生效。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. device：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE device
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE device d
    JOIN parking_lot pl ON d.parking_lot_id = pl.id
   SET d.tenant_id = pl.tenant_id;

-- 无停车场的孤立设备（理论上不存在）暂归为 NULL，后续业务清理
ALTER TABLE device
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 2. parking_lane：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lane
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE parking_lane l
    JOIN parking_lot pl ON l.parking_lot_id = pl.id
   SET l.tenant_id = pl.tenant_id;

ALTER TABLE parking_lane
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 3. employee_parking_lot：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE employee_parking_lot
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE employee_parking_lot epl
    JOIN parking_lot pl ON epl.parking_lot_id = pl.id
   SET epl.tenant_id = pl.tenant_id;

ALTER TABLE employee_parking_lot
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 4. parking_lot_capacity_log：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lot_capacity_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE parking_lot_capacity_log pcl
    JOIN parking_lot pl ON pcl.parking_lot_id = pl.id
   SET pcl.tenant_id = pl.tenant_id;

ALTER TABLE parking_lot_capacity_log
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 5. parking_lot_status_log：通过 parking_lot_id -> parking_lot.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE parking_lot_status_log
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE parking_lot_status_log psl
    JOIN parking_lot pl ON psl.parking_lot_id = pl.id
   SET psl.tenant_id = pl.tenant_id;

ALTER TABLE parking_lot_status_log
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';

-- -----------------------------------------------------------------------------
-- 6. device_status_snapshot：通过 device_id -> device.tenant_id 推导租户
-- -----------------------------------------------------------------------------
ALTER TABLE device_status_snapshot
    ADD COLUMN tenant_id BIGINT DEFAULT NULL COMMENT '所属租户 ID' AFTER id;

UPDATE device_status_snapshot dss
    JOIN device d ON dss.device_id = d.id
   SET dss.tenant_id = d.tenant_id;

ALTER TABLE device_status_snapshot
    MODIFY COLUMN tenant_id BIGINT NOT NULL COMMENT '所属租户 ID',
    ADD INDEX idx_tenant_id (tenant_id) COMMENT '按租户查询';
