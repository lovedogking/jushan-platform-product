-- 报表聚合索引（包 6-1：收入/车流量报表）
-- PERF-005: 单表聚合 ≤1s，复杂聚合 ≤3s

-- 收入报表: 按 pay_time + status + parking_lot_id 聚合
-- 覆盖 ReportMapper.sumRevenue / revenueByDay / revenueByMonth / revenueByYear
CREATE INDEX idx_po_pay_time_status_lot
    ON parking_order (pay_time, status, parking_lot_id);

-- 车流量报表: 按 entry_time + parking_lot_id 聚合
CREATE INDEX idx_pr_entry_time_lot
    ON parking_record (entry_time, parking_lot_id);

-- 车流量报表: 按 exit_time + parking_lot_id 聚合
CREATE INDEX idx_pr_exit_time_lot
    ON parking_record (exit_time, parking_lot_id);

-- 车流量报表: 按 entry_time + lane_id 聚合（通道维度）
CREATE INDEX idx_pr_entry_time_lane
    ON parking_record (entry_time, lane_id);
