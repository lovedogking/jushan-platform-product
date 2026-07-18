-- 岗亭管理员跨租户设计：sys_admin_account_parking_lot.tenant_id 改为可空
-- 岗亭管理员不再归属单一租户，可通过 parkingLotIds 跨租户分配停车场
ALTER TABLE sys_admin_account_parking_lot
    MODIFY COLUMN tenant_id BIGINT NULL COMMENT '租户 ID（岗亭管理员为 NULL，表示跨租户）';
