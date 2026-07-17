-- ============================================
-- Device Access v0.2 → v0.3 数据库迁移
-- ============================================

-- Phase 1: 创建产品目录表
CREATE TABLE IF NOT EXISTS t_device_product (
    id           BIGINT       NOT NULL AUTO_INCREMENT  COMMENT '主键',
    brand        VARCHAR(32)  NOT NULL                 COMMENT '品牌',
    model        VARCHAR(32)  NOT NULL                 COMMENT '型号',
    product_name VARCHAR(128) NOT NULL                 COMMENT '产品全称',
    device_type  VARCHAR(16)  NOT NULL                 COMMENT '设备类型',
    protocol     VARCHAR(16)  NOT NULL DEFAULT 'MQTT'  COMMENT '通信协议',
    capabilities VARCHAR(500) NOT NULL DEFAULT '[]'    COMMENT '设备能力列表（JSON数组）',
    remark       VARCHAR(256) DEFAULT ''               COMMENT '备注',
    create_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_product (brand, model)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备产品目录表';

-- Phase 2: 初始化产品数据（从现有 t_device 提取，使用 LEFT JOIN 避免 collation 冲突）
INSERT INTO t_device_product (brand, model, product_name, device_type, protocol)
SELECT DISTINCT
    IFNULL(d.brand, 'ZHENSHI') COLLATE utf8mb4_unicode_ci,
    IFNULL(d.model, 'C5H') COLLATE utf8mb4_unicode_ci,
    CONCAT(IFNULL(d.brand,'ZHENSHI'), ' ', IFNULL(d.model,'C5H')) COLLATE utf8mb4_unicode_ci,
    'CAMERA',
    'MQTT'
FROM t_device d
LEFT JOIN t_device_product p ON p.brand COLLATE utf8mb4_unicode_ci = IFNULL(d.brand, 'ZHENSHI')
  AND p.model COLLATE utf8mb4_unicode_ci = IFNULL(d.model, 'C5H')
WHERE p.id IS NULL;

-- Phase 2.5: 为已迁移的 ZHENSHI/臻识 C5H 补齐能力列表
UPDATE t_device_product
SET capabilities = '["DISPLAY_TEXT","DISPLAY_SAVE","PERIPHERAL_CONTROL","TIME_SYNC"]'
WHERE (brand = 'ZHENSHI' OR brand = '臻识') AND model = 'C5H';

-- Phase 3: t_device 新增列
ALTER TABLE t_device
    ADD COLUMN product_id      BIGINT      DEFAULT NULL COMMENT 'FK→t_device_product.id',
    ADD COLUMN direction       VARCHAR(16) DEFAULT NULL COMMENT '设备方向',
    ADD COLUMN display_enabled TINYINT     NOT NULL DEFAULT 1 COMMENT '显示屏启用: 0-关闭, 1-启用',
    ADD COLUMN display_mode    VARCHAR(16) NOT NULL DEFAULT 'TWO_LINE' COMMENT '显示屏模式: TWO_LINE / FOUR_LINE';

-- Phase 4: 数据升级 — 关联 product_id
UPDATE t_device d
JOIN t_device_product p
    ON p.brand COLLATE utf8mb4_unicode_ci = IFNULL(d.brand, 'ZHENSHI')
   AND p.model COLLATE utf8mb4_unicode_ci = IFNULL(d.model, 'C5H')
SET d.product_id = p.id
WHERE d.product_id IS NULL;

-- Phase 5: 设置 product_id 非空
ALTER TABLE t_device
    MODIFY product_id BIGINT NOT NULL COMMENT 'FK→t_device_product.id',
    ADD INDEX idx_product_id (product_id);

-- Phase 6: 删除旧列
ALTER TABLE t_device
    DROP COLUMN brand,
    DROP COLUMN model;

-- Phase 7: 创建设备关系表
CREATE TABLE IF NOT EXISTS t_device_relation (
    id                BIGINT      NOT NULL AUTO_INCREMENT  COMMENT '主键',
    source_device_id  VARCHAR(64) NOT NULL                 COMMENT '关系源设备ID',
    target_device_id  VARCHAR(64) NOT NULL                 COMMENT '关系目标设备ID',
    relation_type     VARCHAR(32) NOT NULL                 COMMENT '关系类型',
    enabled           TINYINT(1)  NOT NULL DEFAULT 1       COMMENT '是否启用',
    remark            VARCHAR(256) DEFAULT ''               COMMENT '备注',
    create_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time       DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_relation (relation_type, source_device_id, target_device_id),
    INDEX idx_source (source_device_id, relation_type),
    INDEX idx_target (target_device_id, relation_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='设备关系表';
