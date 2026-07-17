-- =============================================================================
-- Flyway 迁移：为 parking_lot 表重建 remaining_spaces 列
-- =============================================================================
-- 背景：
--   V20260724001 移除了 remaining_spaces（计划改为区域汇总+Redis 实时计算），
--   但区域汇总方案未落地（ParkingSpacePolicyServiceImpl.calculateRemain 仍为 usedSpaces=0 占位），
--   且旧包 EntryService/ExitService/DashboardMapper 等仍有 remaining_spaces 读写。
--   本期决策（D1-方案A）：重建 remaining_spaces 列，进出场链路继续维护该列，
--   保持与 current_vehicles 同步一致（total_spaces - current_vehicles = remaining_spaces）。
--
-- 一致性校验 SQL（可按需调度执行）：
--   SELECT id, name, total_spaces, current_vehicles, remaining_spaces,
--          (total_spaces - current_vehicles) AS expected_remaining
--   FROM parking_lot
--   WHERE remaining_spaces != (total_spaces - current_vehicles);
-- =============================================================================

SET @remaining_spaces_exists = (
    SELECT IF(COUNT(*) = 0, 'ALTER TABLE parking_lot ADD COLUMN remaining_spaces INT NOT NULL DEFAULT 0 COMMENT ''剩余车位数（total_spaces - current_vehicles，允许人工修正）'' AFTER current_vehicles', 'SELECT 1 AS already_exists')
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'parking_lot'
      AND COLUMN_NAME = 'remaining_spaces'
);
PREPARE stmt FROM @remaining_spaces_exists;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
