-- V20260903001__device_network_fields.sql
-- Device 表新增网络配置字段，供适配器连接使用

ALTER TABLE device
  ADD COLUMN ip_address VARCHAR(45) NULL COMMENT 'IP地址',
  ADD COLUMN port INT NULL DEFAULT 80 COMMENT '端口',
  ADD COLUMN subnet_mask VARCHAR(45) NULL COMMENT '子网掩码',
  ADD COLUMN gateway VARCHAR(45) NULL COMMENT '网关地址';
