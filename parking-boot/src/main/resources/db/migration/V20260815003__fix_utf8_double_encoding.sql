-- =============================================================================
-- Flyway 迁移：修复 UTF-8 双重编码问题
-- =============================================================================
-- 背景：
--   测试数据入库时使用了错误的字符集，导致 UTF-8 编码的汉字字节被再次
--   以 UTF-8 编码存储。例如 "入口" 的 UTF-8 字节 E5 85 A5 E5 8F A3 被
--   当作 Latin-1 字符再次 UTF-8 编码，存入 C3 A5 E2 80 A6 C2 A5 ...
--   这导致前端展示时出现乱码（E2Eå…¥å£é€šé）。
--
-- 修复方式：
--   1. 用 latin1 解读当前字符串，得到原始 UTF-8 字节
--   2. 再以 utf8mb4 编码，恢复正确的中文
-- =============================================================================

-- 修复 parking_lot 表
UPDATE parking_lot
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);

-- 修复 parking_lane 表
UPDATE parking_lane
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);

-- 修复 device 表
UPDATE device
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);

-- 修复 parking_zone 表
UPDATE parking_zone
SET name = CONVERT(CAST(CONVERT(name USING latin1) AS BINARY) USING utf8mb4)
WHERE HEX(name) LIKE '%C3%' AND name != CONVERT(name USING ascii);
