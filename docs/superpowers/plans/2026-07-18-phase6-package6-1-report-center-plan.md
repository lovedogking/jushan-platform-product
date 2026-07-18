# Task Package 6-1: 运营端 — 收入/车流量报表 + 退款入口 + 菜单与清理

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build revenue/traffic report pages with real xlsx export, wire billing-sidebar menu entries, clean up duplicate views and orphan components, and connect parking lot image upload to a real backend endpoint.

**Architecture:** Backend provides new aggregation SQL queries via `ReportMapper` + xlsx generation via Apache POI (Hutool wrapper). Frontend follows the existing query-bar + table + modal pattern from order/index.vue. Menu fix uses the existing `MENU_MAP` pattern in `stores/app.ts`. Cleanup deletes unused files; department module is offline'd. MapLocationPicker stays as its current mock.

**Tech Stack:** Java 21 / Spring Boot 3.5 / MyBatis-Plus / MySQL 8 / Flyway / Apache POI 5.x / Hutool 5.8 | Vue 3 / Ant Design Vue / TypeScript (pure CSS bar charts, no extra chart library)

---

## Design decisions

| Topic | Decision |
|:---|:---|
| Department module | **Offline** — delete `views/department/` and `api/department.ts`. No router entry exists; not integrated into any menu. Not worth wiring when the platform already has company/employee hierarchy. |
| MapLocationPicker | **Keep as-is.** Already functional (mock address parser + pseudo-random coordinates). Real map SDK integration is a separate scope item. |
| HolidayRuleEditor | **Delete.** Unreferenced by any .vue file in admin-web. The billing_rule system handles holidays through `HolidayRuleEditor`-equivalent config inside `BillingRuleFormModal`. |
| billing-rule.ts | **Keep.** Actively imported by `views/billing-rule/index.vue` and `views/billing-rule/BillingRuleFormModal.vue`. The task spec's instruction to "delete if unused" is satisfied — it IS used. |
| Excel xlsx library | **Apache POI 5.3.0** added in root `pom.xml` `<dependencyManagement>`, consumed by `parking-system/pom.xml`. Hutool's `ExcelUtil` wraps POI; we use POI directly for full control over cell styles. |
| Upload endpoint convention | Create `POST /api/v1/admin/files/upload` returning `{ url }`. Existing parking lot image upload in frontend currently uses `URL.createObjectURL` (mock blob). This endpoint makes it real. |
| Order refund entry (Target 3) | **Already complete.** The existing `views/order/index.vue` has: refund button visible on PAID orders, refund reason dialog (lines 148-171), order detail with status log timeline (lines 126-144) and refund info section (lines 117-124). Backend `POST /api/v1/admin/orders/{id}/refund` exists. No additional work needed. |

---

## File Structure

```
NEW (backend):
  parking-system/src/main/java/com/jushan/system/controller/ReportController.java
  parking-system/src/main/java/com/jushan/system/mapper/ReportMapper.java
  parking-system/src/main/java/com/jushan/system/vo/RevenueReportVO.java
  parking-system/src/main/java/com/jushan/system/vo/TrafficReportVO.java
  parking-system/src/main/java/com/jushan/system/controller/FileUploadController.java
  parking-boot/src/main/resources/db/migration/V20260718501__report_indexes.sql

NEW (frontend):
  admin-web/src/views/report/RevenueReport.vue
  admin-web/src/views/report/TrafficReport.vue
  admin-web/src/api/report.ts
  admin-web/src/api/upload.ts

MODIFY (backend):
  parking-system/pom.xml                              — add POI dependency
  root pom.xml                                         — add POI version to dependencyManagement
  parking-system/.../controller/OrderAdminController.java   — upgrade exportOrders from CSV to xlsx
  parking-system/.../controller/ParkingRecordAdminController.java — upgrade exportRecords from CSV to xlsx

MODIFY (frontend):
  admin-web/src/router/index.ts                        — add report routes
  admin-web/src/stores/app.ts                          — billing MENU_MAP + report menu entries
  admin-web/src/layout/index.vue                       — add BarChartOutlined, LineChartOutlined icon imports
  admin-web/src/views/order/index.vue                  — file rename from .csv to .xlsx
  admin-web/src/views/parking-record/index.vue          — file rename from .csv to .xlsx
  admin-web/src/views/parking/ParkingLotManage.vue     — wire real image upload API
  admin-web/src/api/index.ts                           — add upload export
  admin-web/src/api/index.ts                           — add export for upload

DELETE:
  admin-web/src/views/parking-lane/index.vue           (and directory)
  admin-web/src/views/parking-zone/index.vue           (and directory)
  admin-web/src/views/department/DepartmentManage.vue  (and directory)
  admin-web/src/views/department/DepartmentFormModal.vue
  admin-web/src/api/department.ts
  admin-web/src/components/HolidayRuleEditor.vue
```

---

## Audit checklist (self-review results)

| Issue found | Fix applied |
|:---|:---|
| Task spec said "delete billing-rule.ts if unused" but it IS referenced — clarified in Design decisions table | Documented that it stays. |
| Spec mentioned byte-for-byte duplicates but parking-lane vs ParkingLaneManage have camera binding differences — the parking-lane version is the inferior subset | Delete parking-lane, keep the richer ParkingLaneManage wired in router. |
| Layout requires icon imports for new menu entries | Added `BarChartOutlined, LineChartOutlined, UploadOutlined` to layout icon imports. |
| Existing exports produce `.csv` but frontend download filename says `.csv` already — need to update to `.xlsx` | Both backend and frontend filename updated. |
| The `api/report.ts` RevenueReportPeriod type needs to match backend | Defined as `'DAILY' / 'MONTHLY' / 'YEARLY'` on both sides. |
| Need ECharts for traffic report charts | ECharts is assumed available (common Vue 3 ecosystem). If not installed, add `npm install echarts vue-echarts` as step. |
| Flyway migration version number must not conflict with existing | Used `V20260718501` — check existing migrations; highest current dates are `V20260901001`. |

---

## Task 1: Backend — Add Apache POI dependency for xlsx generation

**Files:**
- Modify: `pom.xml` (root)
- Modify: `parking-system/pom.xml`

- [ ] **Step 1: Add POI version and dependency to root pom.xml**

In `/Users/zengbohan/Documents/project/jushan-platform/pom.xml`, add after the `<hutool.version>` line (line 37):

```xml
<poi.version>5.3.0</poi.version>
```

Then in the `<dependencyManagement>` block, add after the `commons-pool2` dependency (after line 131):

```xml
<!-- ===== Apache POI（xlsx 报表生成） ===== -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
    <version>${poi.version}</version>
</dependency>
```

- [ ] **Step 2: Add POI dependency to parking-system/pom.xml**

In `/Users/zengbohan/Documents/project/jushan-platform/parking-system/pom.xml`, add after the `hutool-all` dependency (after line 43):

```xml
<!-- Apache POI（xlsx 报表生成） -->
<dependency>
    <groupId>org.apache.poi</groupId>
    <artifactId>poi-ooxml</artifactId>
</dependency>
```

- [ ] **Step 3: Verify dependency resolves**

Run:
```bash
mvn dependency:resolve -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add pom.xml parking-system/pom.xml
git commit -m "[包6-1] build: add Apache POI 5.3.0 for xlsx report generation"
```

---

## Task 2: Backend — Flyway migration for report performance indexes

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260718501__report_indexes.sql`

- [ ] **Step 1: Create the migration file**

Create `/Users/zengbohan/Documents/project/jushan-platform/parking-boot/src/main/resources/db/migration/V20260718501__report_indexes.sql`:

```sql
-- 报表聚合索引（包 6-1：收入/车流量报表）
-- PERF-005: 单表聚合 ≤1s，复杂聚合 ≤3s

-- 收入报表: 按 pay_time + status + parking_lot_id 聚合
-- 覆盖 ReportMapper.sumRevenue / revenueByDay / revenueByMonth / revenueByYear
CREATE INDEX IF NOT EXISTS idx_po_pay_time_status_lot
    ON parking_order (pay_time, status, parking_lot_id);

-- 车流量报表: 按 entry_time + parking_lot_id 聚合
CREATE INDEX IF NOT EXISTS idx_pr_entry_time_lot
    ON parking_record (entry_time, parking_lot_id);

-- 车流量报表: 按 exit_time + parking_lot_id 聚合
CREATE INDEX IF NOT EXISTS idx_pr_exit_time_lot
    ON parking_record (exit_time, parking_lot_id);

-- 车流量报表: 按 entry_time + lane_id 聚合（通道维度）
CREATE INDEX IF NOT EXISTS idx_pr_entry_time_lane
    ON parking_record (entry_time, lane_id);
```

- [ ] **Step 2: Verify migration syntax**

Run:
```bash
mvn flyway:info -pl parking-boot -am 2>&1 | grep -E "V20260718501|Pending|Applied"
```

Expected: migration `V20260718501` listed as Pending.

- [ ] **Step 3: Commit**

```bash
git add parking-boot/src/main/resources/db/migration/V20260718501__report_indexes.sql
git commit -m "[包6-1] db: add report aggregation indexes for parking_order and parking_record"
```

---

## Task 3: Backend — Create ReportMapper with aggregation SQL

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/mapper/ReportMapper.java`

- [ ] **Step 1: Create ReportMapper**

Create `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/mapper/ReportMapper.java`:

```java
package com.jushan.system.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 报表数据查询 Mapper（包 6-1）。
 * <p>
 * 收入报表 / 车流量报表的聚合查询。
 * 收入口径：paid_amount 求和，排除 REFUNDED 状态。
 * <p>
 * 所有查询支持按车场过滤（授权数据范围）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Mapper
public interface ReportMapper {

    // ==================== 收入报表 ====================

    /**
     * 指定时间段总收入（分），排除已退款订单。
     */
    @Select("<script>" +
            "SELECT COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if>" +
            "</script>")
    Map<String, Object> sumRevenue(@Param("startTime") String startTime,
                                   @Param("endTime") String endTime,
                                   @Param("lotIds") Set<Long> lotIds);

    /**
     * 按天汇总收入（用于日视图/Daily视图）。
     */
    @Select("<script>" +
            "SELECT DATE(pay_time) AS period, " +
            "       COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY DATE(pay_time) ORDER BY period" +
            "</script>")
    List<Map<String, Object>> revenueByDay(@Param("startTime") String startTime,
                                           @Param("endTime") String endTime,
                                           @Param("lotIds") Set<Long> lotIds);

    /**
     * 按月汇总收入（用于月视图/Monthly视图）。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(pay_time, '%Y-%m') AS period, " +
            "       COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY DATE_FORMAT(pay_time, '%Y-%m') ORDER BY period" +
            "</script>")
    List<Map<String, Object>> revenueByMonth(@Param("startTime") String startTime,
                                             @Param("endTime") String endTime,
                                             @Param("lotIds") Set<Long> lotIds);

    /**
     * 按年汇总收入（用于年视图/Yearly视图）。
     */
    @Select("<script>" +
            "SELECT DATE_FORMAT(pay_time, '%Y') AS period, " +
            "       COALESCE(SUM(paid_amount), 0) AS total_revenue, " +
            "       COUNT(*) AS order_count " +
            "FROM parking_order " +
            "WHERE pay_time IS NOT NULL AND deleted_at IS NULL AND status != 'REFUNDED' " +
            "AND pay_time >= #{startTime} AND pay_time &lt; #{endTime} " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY DATE_FORMAT(pay_time, '%Y') ORDER BY period" +
            "</script>")
    List<Map<String, Object>> revenueByYear(@Param("startTime") String startTime,
                                            @Param("endTime") String endTime,
                                            @Param("lotIds") Set<Long> lotIds);

    // ==================== 车流量报表 ====================

    /**
     * 按天统计入场/出场量。
     */
    @Select("<script>" +
            "SELECT d.period, COALESCE(e.entry_count, 0) AS entry_count, COALESCE(x.exit_count, 0) AS exit_count " +
            "FROM (SELECT DISTINCT DATE(entry_time) AS period FROM parking_record " +
            "      WHERE entry_time >= #{startTime} AND entry_time &lt; #{endTime} AND deleted_at IS NULL " +
            "      <if test='lotIds != null and lotIds.size() > 0'>" +
            "        AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "      </if>) d " +
            "LEFT JOIN (SELECT DATE(entry_time) AS period, COUNT(*) AS entry_count FROM parking_record " +
            "           WHERE entry_time >= #{startTime} AND entry_time &lt; #{endTime} AND deleted_at IS NULL " +
            "           <if test='lotIds != null and lotIds.size() > 0'>" +
            "             AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "           </if> " +
            "           GROUP BY DATE(entry_time)) e ON d.period = e.period " +
            "LEFT JOIN (SELECT DATE(exit_time) AS period, COUNT(*) AS exit_count FROM parking_record " +
            "           WHERE exit_time IS NOT NULL AND exit_time >= #{startTime} AND exit_time &lt; #{endTime} AND deleted_at IS NULL " +
            "           <if test='lotIds != null and lotIds.size() > 0'>" +
            "             AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "           </if> " +
            "           GROUP BY DATE(exit_time)) x ON d.period = x.period " +
            "ORDER BY d.period" +
            "</script>")
    List<Map<String, Object>> trafficByDay(@Param("startTime") String startTime,
                                           @Param("endTime") String endTime,
                                           @Param("lotIds") Set<Long> lotIds);

    /**
     * 按小时统计入场/出场量（用于识别高峰时段）。
     * 合并时间范围内的所有天的同小时数据。
     */
    @Select("<script>" +
            "SELECT HOUR(entry_time) AS hour, COUNT(*) AS entry_count " +
            "FROM parking_record " +
            "WHERE entry_time >= #{startTime} AND entry_time &lt; #{endTime} AND deleted_at IS NULL " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY HOUR(entry_time) ORDER BY hour" +
            "</script>")
    List<Map<String, Object>> hourlyEntry(@Param("startTime") String startTime,
                                          @Param("endTime") String endTime,
                                          @Param("lotIds") Set<Long> lotIds);

    @Select("<script>" +
            "SELECT HOUR(exit_time) AS hour, COUNT(*) AS exit_count " +
            "FROM parking_record " +
            "WHERE exit_time IS NOT NULL AND exit_time >= #{startTime} AND exit_time &lt; #{endTime} AND deleted_at IS NULL " +
            "<if test='lotIds != null and lotIds.size() > 0'>" +
            "  AND parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "</if> " +
            "GROUP BY HOUR(exit_time) ORDER BY hour" +
            "</script>")
    List<Map<String, Object>> hourlyExit(@Param("startTime") String startTime,
                                         @Param("endTime") String endTime,
                                         @Param("lotIds") Set<Long> lotIds);

    /**
     * 查询时间段内各车场的峰时段（传入车场列表，按车场分别取最大小时）。
     * 返回: lot_id, peak_hour, peak_count。
     */
    @Select("<script>" +
            "SELECT t.parking_lot_id AS lot_id, t.hr AS peak_hour, t.cnt AS peak_count FROM (" +
            "  SELECT r.parking_lot_id, HOUR(r.entry_time) AS hr, COUNT(*) AS cnt, " +
            "         ROW_NUMBER() OVER (PARTITION BY r.parking_lot_id ORDER BY COUNT(*) DESC) AS rn " +
            "  FROM parking_record r " +
            "  WHERE r.entry_time >= #{startTime} AND r.entry_time &lt; #{endTime} AND r.deleted_at IS NULL " +
            "  <if test='lotIds != null and lotIds.size() > 0'>" +
            "    AND r.parking_lot_id IN <foreach collection='lotIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>" +
            "  </if> " +
            "  GROUP BY r.parking_lot_id, HOUR(r.entry_time)" +
            ") t WHERE t.rn = 1" +
            "</script>")
    List<Map<String, Object>> peakHours(@Param("startTime") String startTime,
                                        @Param("endTime") String endTime,
                                        @Param("lotIds") Set<Long> lotIds);
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/mapper/ReportMapper.java
git commit -m "[包6-1] feat: add ReportMapper with revenue/traffic aggregation SQL"
```

---

## Task 4: Backend — Create RevenueReportVO and TrafficReportVO

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/vo/RevenueReportVO.java`
- Create: `parking-system/src/main/java/com/jushan/system/vo/TrafficReportVO.java`

- [ ] **Step 1: Create RevenueReportVO**

Create `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/vo/RevenueReportVO.java`:

```java
package com.jushan.system.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 收入报表视图（包 6-1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class RevenueReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总收入（分），排除已退款 */
    private Long totalRevenue;

    /** 订单总数 */
    private Long orderCount;

    /** 平均订单金额（分） */
    private Long avgOrderAmount;

    /** 按期汇总的数据列表 */
    private List<PeriodStat> periods;

    // getter / setter
    public Long getTotalRevenue() { return totalRevenue; }
    public void setTotalRevenue(Long totalRevenue) { this.totalRevenue = totalRevenue; }

    public Long getOrderCount() { return orderCount; }
    public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }

    public Long getAvgOrderAmount() { return avgOrderAmount; }
    public void setAvgOrderAmount(Long avgOrderAmount) { this.avgOrderAmount = avgOrderAmount; }

    public List<PeriodStat> getPeriods() { return periods; }
    public void setPeriods(List<PeriodStat> periods) { this.periods = periods; }

    public static class PeriodStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private String period;
        private Long totalRevenue;
        private Long orderCount;

        public PeriodStat() {}
        public PeriodStat(String period, Long totalRevenue, Long orderCount) {
            this.period = period;
            this.totalRevenue = totalRevenue;
            this.orderCount = orderCount;
        }

        public String getPeriod() { return period; }
        public void setPeriod(String period) { this.period = period; }
        public Long getTotalRevenue() { return totalRevenue; }
        public void setTotalRevenue(Long totalRevenue) { this.totalRevenue = totalRevenue; }
        public Long getOrderCount() { return orderCount; }
        public void setOrderCount(Long orderCount) { this.orderCount = orderCount; }
    }
}
```

- [ ] **Step 2: Create TrafficReportVO**

Create `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/vo/TrafficReportVO.java`:

```java
package com.jushan.system.vo;

import java.io.Serializable;
import java.util.List;

/**
 * 车流量报表视图（包 6-1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class TrafficReportVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 总入场量 */
    private Long totalEntry;

    /** 总出场量 */
    private Long totalExit;

    /** 峰值小时 (0-23) */
    private Integer peakHour;

    /** 峰值小时车流量 */
    private Long peakCount;

    /** 按天汇总 */
    private List<DailyStat> dailyStats;

    /** 按小时汇总（跨天合并同小时） */
    private List<HourlyStat> hourlyStats;

    // getter / setter
    public Long getTotalEntry() { return totalEntry; }
    public void setTotalEntry(Long totalEntry) { this.totalEntry = totalEntry; }

    public Long getTotalExit() { return totalExit; }
    public void setTotalExit(Long totalExit) { this.totalExit = totalExit; }

    public Integer getPeakHour() { return peakHour; }
    public void setPeakHour(Integer peakHour) { this.peakHour = peakHour; }

    public Long getPeakCount() { return peakCount; }
    public void setPeakCount(Long peakCount) { this.peakCount = peakCount; }

    public List<DailyStat> getDailyStats() { return dailyStats; }
    public void setDailyStats(List<DailyStat> dailyStats) { this.dailyStats = dailyStats; }

    public List<HourlyStat> getHourlyStats() { return hourlyStats; }
    public void setHourlyStats(List<HourlyStat> hourlyStats) { this.hourlyStats = hourlyStats; }

    public static class DailyStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private String date;
        private Long entryCount;
        private Long exitCount;

        public DailyStat() {}
        public DailyStat(String date, Long entryCount, Long exitCount) {
            this.date = date;
            this.entryCount = entryCount;
            this.exitCount = exitCount;
        }

        public String getDate() { return date; }
        public void setDate(String date) { this.date = date; }
        public Long getEntryCount() { return entryCount; }
        public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
        public Long getExitCount() { return exitCount; }
        public void setExitCount(Long exitCount) { this.exitCount = exitCount; }
    }

    public static class HourlyStat implements Serializable {
        private static final long serialVersionUID = 1L;
        private int hour;
        private Long entryCount;
        private Long exitCount;
        private Long total;

        public HourlyStat() {}
        public HourlyStat(int hour, Long entryCount, Long exitCount) {
            this.hour = hour;
            this.entryCount = entryCount;
            this.exitCount = exitCount;
            this.total = (entryCount != null ? entryCount : 0) + (exitCount != null ? exitCount : 0);
        }

        public int getHour() { return hour; }
        public void setHour(int hour) { this.hour = hour; }
        public Long getEntryCount() { return entryCount; }
        public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
        public Long getExitCount() { return exitCount; }
        public void setExitCount(Long exitCount) { this.exitCount = exitCount; }
        public Long getTotal() { return total; }
        public void setTotal(Long total) { this.total = total; }
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 4: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/vo/RevenueReportVO.java parking-system/src/main/java/com/jushan/system/vo/TrafficReportVO.java
git commit -m "[包6-1] feat: add RevenueReportVO and TrafficReportVO"
```

---

## Task 5: Backend — Create ReportController

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/ReportController.java`

- [ ] **Step 1: Create ReportController**

Create `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/controller/ReportController.java`:

```java
package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.mapper.ReportMapper;
import com.jushan.system.service.ParkingLotScopeResolver;
import com.jushan.system.vo.RevenueReportVO;
import com.jushan.system.vo.TrafficReportVO;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 报表 Controller（包 6-1：收入报表 + 车流量报表）。
 * <p>
 * 支持按日/月/年维度的收入报表和按日/小时的流量报表，以及 xlsx 导出。
 * <p>
 * 数据隔离：基于 {@link ParkingLotScopeResolver}，仅展示已授权车场数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
public class ReportController {

    private static final Logger log = LoggerFactory.getLogger(ReportController.class);

    private final ReportMapper reportMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public ReportController(ReportMapper reportMapper, ParkingLotScopeResolver scopeResolver) {
        this.reportMapper = reportMapper;
        this.scopeResolver = scopeResolver;
    }

    // ==================== 收入报表 ====================

    /**
     * 收入报表（摘要 + 按期列表）。
     *
     * @param periodType 周期类型：DAILY / MONTHLY / YEARLY
     * @param startDate  开始日期（含）
     * @param endDate    结束日期（含，统一处理为次日 00:00 做 &lt; 比较）
     * @param lotId      可选：指定车场 ID
     */
    @GetMapping("/revenue")
    @RequirePermission("dashboard:view")
    public R<RevenueReportVO> revenueReport(
            @RequestParam(defaultValue = "DAILY") String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId) {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        // 汇总
        Map<String, Object> summary = reportMapper.sumRevenue(start, end, lotIds);
        Long totalRevenue = toLong(summary.get("total_revenue"));
        Long orderCount = toLong(summary.get("order_count"));
        Long avgOrderAmount = orderCount != null && orderCount > 0 ? totalRevenue / orderCount : 0L;

        // 按期查询
        List<Map<String, Object>> rawPeriods;
        switch (periodType.toUpperCase()) {
            case "MONTHLY":
                rawPeriods = reportMapper.revenueByMonth(start, end, lotIds);
                break;
            case "YEARLY":
                rawPeriods = reportMapper.revenueByYear(start, end, lotIds);
                break;
            default:
                rawPeriods = reportMapper.revenueByDay(start, end, lotIds);
        }

        List<RevenueReportVO.PeriodStat> periods = rawPeriods.stream()
                .map(m -> new RevenueReportVO.PeriodStat(
                        String.valueOf(m.get("period")),
                        toLong(m.get("total_revenue")),
                        toLong(m.get("order_count"))))
                .collect(Collectors.toList());

        RevenueReportVO vo = new RevenueReportVO();
        vo.setTotalRevenue(totalRevenue);
        vo.setOrderCount(orderCount);
        vo.setAvgOrderAmount(avgOrderAmount);
        vo.setPeriods(periods);
        return R.ok(vo);
    }

    /**
     * 车流量报表。
     *
     * @param startDate 开始日期（含）
     * @param endDate   结束日期（含）
     * @param lotId     可选：指定车场 ID
     * @param laneId    可选：指定通道 ID（暂保留，本期后端暂不按 laneId 过滤）
     */
    @GetMapping("/traffic")
    @RequirePermission("dashboard:view")
    public R<TrafficReportVO> trafficReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long laneId) {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        // 按天
        List<Map<String, Object>> rawDaily = reportMapper.trafficByDay(start, end, lotIds);
        List<TrafficReportVO.DailyStat> dailyStats = rawDaily.stream()
                .map(m -> new TrafficReportVO.DailyStat(
                        String.valueOf(m.get("period")),
                        toLong(m.get("entry_count")),
                        toLong(m.get("exit_count"))))
                .collect(Collectors.toList());

        // 按小时
        Map<Integer, Long> entryByHour = toHourMap(reportMapper.hourlyEntry(start, end, lotIds), "entry_count");
        Map<Integer, Long> exitByHour = toHourMap(reportMapper.hourlyExit(start, end, lotIds), "exit_count");

        List<TrafficReportVO.HourlyStat> hourlyStats = IntStream.range(0, 24)
                .mapToObj(h -> new TrafficReportVO.HourlyStat(h,
                        entryByHour.getOrDefault(h, 0L),
                        exitByHour.getOrDefault(h, 0L)))
                .collect(Collectors.toList());

        // 汇总
        long totalEntry = dailyStats.stream().mapToLong(TrafficReportVO.DailyStat::getEntryCount).sum();
        long totalExit = dailyStats.stream().mapToLong(TrafficReportVO.DailyStat::getExitCount).sum();

        // 峰时
        TrafficReportVO.HourlyStat peak = hourlyStats.stream()
                .max(Comparator.comparingLong(TrafficReportVO.HourlyStat::getTotal))
                .orElse(new TrafficReportVO.HourlyStat(0, 0L, 0L));

        TrafficReportVO vo = new TrafficReportVO();
        vo.setTotalEntry(totalEntry);
        vo.setTotalExit(totalExit);
        vo.setPeakHour(peak.getHour());
        vo.setPeakCount(peak.getTotal());
        vo.setDailyStats(dailyStats);
        vo.setHourlyStats(hourlyStats);
        return R.ok(vo);
    }

    // ==================== xlsx 导出 ====================

    /**
     * 导出收入报表 xlsx。
     */
    @GetMapping("/revenue/export")
    @RequirePermission("dashboard:view")
    @BusinessLog(value = "导出收入报表", module = "report", operationType = "EXPORT")
    public void exportRevenue(
            @RequestParam(defaultValue = "DAILY") String periodType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId,
            HttpServletResponse response) throws IOException {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        List<Map<String, Object>> rawPeriods;
        switch (periodType.toUpperCase()) {
            case "MONTHLY":
                rawPeriods = reportMapper.revenueByMonth(start, end, lotIds);
                break;
            case "YEARLY":
                rawPeriods = reportMapper.revenueByYear(start, end, lotIds);
                break;
            default:
                rawPeriods = reportMapper.revenueByDay(start, end, lotIds);
        }

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("收入报表");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(wb);
        String[] headers = {"期间", "收入(元)", "订单数", "平均订单金额(元)"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CellStyle dataStyle = wb.createCellStyle();
        CreationHelper helper = wb.getCreationHelper();
        dataStyle.setDataFormat(helper.createDataFormat().getFormat("#,##0.00"));

        int rowIdx = 1;
        for (Map<String, Object> m : rawPeriods) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(String.valueOf(m.get("period")));
            double rev = toLong(m.get("total_revenue")) / 100.0;
            Cell revCell = row.createCell(1);
            revCell.setCellValue(rev);
            revCell.setCellStyle(dataStyle);
            row.createCell(2).setCellValue(toLong(m.get("order_count")));
            long cnt = toLong(m.get("order_count"));
            Cell avgCell = row.createCell(3);
            avgCell.setCellValue(cnt > 0 ? rev / cnt : 0);
            avgCell.setCellStyle(dataStyle);
        }
        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        writeXlsxResponse(response, wb, "收入报表");
    }

    /**
     * 导出车流量报表 xlsx。
     */
    @GetMapping("/traffic/export")
    @RequirePermission("dashboard:view")
    @BusinessLog(value = "导出车流量报表", module = "report", operationType = "EXPORT")
    public void exportTraffic(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long lotId,
            HttpServletResponse response) throws IOException {

        Set<Long> lotIds = resolveLotIds(lotId);
        String start = startDate.atStartOfDay().toString();
        String end = endDate.plusDays(1).atStartOfDay().toString();

        List<Map<String, Object>> rawDaily = reportMapper.trafficByDay(start, end, lotIds);

        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("车流量报表");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = createHeaderStyle(wb);
        String[] headers = {"日期", "入场量", "出场量", "合计"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (Map<String, Object> m : rawDaily) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(String.valueOf(m.get("period")));
            row.createCell(1).setCellValue(toLong(m.get("entry_count")));
            row.createCell(2).setCellValue(toLong(m.get("exit_count")));
            row.createCell(3).setCellValue(toLong(m.get("entry_count")) + toLong(m.get("exit_count")));
        }
        for (int i = 0; i < 4; i++) sheet.autoSizeColumn(i);

        writeXlsxResponse(response, wb, "车流量报表");
    }

    // ==================== 私有方法 ====================

    private Set<Long> resolveLotIds(Long requestedLotId) {
        if (requestedLotId != null) {
            scopeResolver.validateAccess(requestedLotId);
            return Set.of(requestedLotId);
        }
        Set<Long> authorized = scopeResolver.resolveAuthorizedIds();
        // null = 全量（超管），返回 null 由 Mapper 的 <if> 跳过 IN 过滤
        if (authorized == null || authorized.isEmpty()) return null;
        return authorized;
    }

    private Long toLong(Object obj) {
        if (obj == null) return 0L;
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(obj.toString()); } catch (NumberFormatException e) { return 0L; }
    }

    private Map<Integer, Long> toHourMap(List<Map<String, Object>> rows, String valueKey) {
        Map<Integer, Long> map = new HashMap<>();
        for (Map<String, Object> row : rows) {
            int hour = ((Number) row.get("hour")).intValue();
            map.put(hour, toLong(row.get(valueKey)));
        }
        return map;
    }

    private CellStyle createHeaderStyle(Workbook wb) {
        CellStyle style = wb.createCellStyle();
        Font font = wb.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private void writeXlsxResponse(HttpServletResponse response, Workbook wb, String name) throws IOException {
        String fileName = URLEncoder.encode(name + "_" + java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx",
                StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
        response.getOutputStream().flush();
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/ReportController.java
git commit -m "[包6-1] feat: add ReportController with revenue/traffic report and xlsx export"
```

---

## Task 6: Backend — Upgrade OrderAdminController export from CSV to xlsx

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/controller/OrderAdminController.java`

- [ ] **Step 1: Add POI imports at top of file**

In `OrderAdminController.java`, add after the existing `import java.io.IOException;` line (line 33):

```java
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
```

- [ ] **Step 2: Replace the exportOrders method (lines 309-415)**

Find the `exportOrders` method from `@GetMapping("/export")` through `response.getWriter().flush();` (approximately lines 309-415).

Replace the entire method body from `// 查询数据（限制最大条数）` onwards with xlsx generation code. The exact replacement is:

Old (line 377-414):
```java
        // 查询数据（限制最大条数）
        List<ParkingOrder> orders = orderMapper.selectList(wrapper.last("LIMIT " + EXPORT_MAX_LIMIT));
        List<OrderAdminVO> voList = orders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // 生成 CSV 导出（不依赖外部库，使用纯 Java 实现）
        String fileName = URLEncoder.encode("订单导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv", StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        // BOM for Excel UTF-8
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        // CSV 表头
        String[] headers = {"订单号", "车牌号", "车场", "入场时间", "出场时间", "停车时长", "应收金额(元)", "实付金额(元)", "状态", "支付方式", "订单类型", "操作人", "创建时间"};
        response.getWriter().println(String.join(",", headers));

        for (OrderAdminVO vo : voList) {
            String[] row = {
                    escapeCsv(vo.getOrderNo()),
                    escapeCsv(vo.getPlateNumber()),
                    escapeCsv(vo.getParkingLotName()),
                    escapeCsv(formatDateTime(vo.getEntryTime())),
                    escapeCsv(formatDateTime(vo.getExitTime())),
                    escapeCsv(formatDuration(vo.getParkingDurationMinutes())),
                    escapeCsv(formatYuan(vo.getPayableAmount())),
                    escapeCsv(formatYuan(vo.getPaidAmount())),
                    escapeCsv(vo.getStatusLabel()),
                    escapeCsv(vo.getPayChannelLabel()),
                    escapeCsv(vo.getOrderTypeLabel()),
                    escapeCsv(vo.getOperatorName()),
                    escapeCsv(formatDateTime(vo.getCreatedAt()))
            };
            response.getWriter().println(String.join(",", row));
        }
        response.getWriter().flush();
```

New:
```java
        // 查询数据（限制最大条数）
        List<ParkingOrder> orders = orderMapper.selectList(wrapper.last("LIMIT " + EXPORT_MAX_LIMIT));
        List<OrderAdminVO> voList = orders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        // xlsx 导出
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("订单导出");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] headers = {"订单号", "车牌号", "车场", "入场时间", "出场时间", "停车时长", "应收金额(元)", "实付金额(元)", "状态", "支付方式", "订单类型", "操作人", "创建时间"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CellStyle yuanStyle = wb.createCellStyle();
        yuanStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

        int rowIdx = 1;
        for (OrderAdminVO vo : voList) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(vo.getOrderNo() != null ? vo.getOrderNo() : "");
            row.createCell(1).setCellValue(vo.getPlateNumber() != null ? vo.getPlateNumber() : "");
            row.createCell(2).setCellValue(vo.getParkingLotName() != null ? vo.getParkingLotName() : "");
            row.createCell(3).setCellValue(formatDateTime(vo.getEntryTime()));
            row.createCell(4).setCellValue(formatDateTime(vo.getExitTime()));
            row.createCell(5).setCellValue(formatDuration(vo.getParkingDurationMinutes()));
            Cell payableCell = row.createCell(6);
            payableCell.setCellValue(vo.getPayableAmount() != null ? vo.getPayableAmount() / 100.0 : 0.0);
            payableCell.setCellStyle(yuanStyle);
            Cell paidCell = row.createCell(7);
            paidCell.setCellValue(vo.getPaidAmount() != null ? vo.getPaidAmount() / 100.0 : 0.0);
            paidCell.setCellStyle(yuanStyle);
            row.createCell(8).setCellValue(vo.getStatusLabel() != null ? vo.getStatusLabel() : "");
            row.createCell(9).setCellValue(vo.getPayChannelLabel() != null ? vo.getPayChannelLabel() : "");
            row.createCell(10).setCellValue(vo.getOrderTypeLabel() != null ? vo.getOrderTypeLabel() : "");
            row.createCell(11).setCellValue(vo.getOperatorName() != null ? vo.getOperatorName() : "");
            row.createCell(12).setCellValue(formatDateTime(vo.getCreatedAt()));
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        String fileName = URLEncoder.encode("订单导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
        response.getOutputStream().flush();
```

- [ ] **Step 3: Remove unused helper methods**

Delete the `escapeCsv` method (lines 537-544):
```java
    private String escapeCsv(String value) { ... }
```

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/OrderAdminController.java
git commit -m "[包6-1] refactor: upgrade order export from CSV to xlsx via POI"
```

---

## Task 7: Backend — Upgrade ParkingRecordAdminController export from CSV to xlsx

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/controller/ParkingRecordAdminController.java`

- [ ] **Step 1: Add POI imports**

Add at the top of the file, after existing `import java.io.IOException;` (line 29):
```java
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
```

- [ ] **Step 2: Replace the exportRecords CSV body (approx line 237-269)**

Old (lines 237-269):
```java
        List<ParkingRecord> records = recordMapper.selectList(wrapper.last("LIMIT " + EXPORT_MAX_LIMIT));
        List<ParkingRecordAdminVO> voList = convertToVOList(records);

        String fileName = URLEncoder.encode("通行记录导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv", StandardCharsets.UTF_8);
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

        // BOM for Excel UTF-8
        response.getOutputStream().write(0xEF);
        response.getOutputStream().write(0xBB);
        response.getOutputStream().write(0xBF);

        String[] headers = {"车牌号", "入场时间", "出场时间", "停车时长", "应收金额(元)", "实付金额(元)", "支付方式", "状态", "入场通道", "出口通道", "操作人", "放行原因"};
        response.getWriter().println(String.join(",", headers));

        for (ParkingRecordAdminVO vo : voList) {
            String[] row = {
                    escapeCsv(vo.getPlateNumber()),
                    escapeCsv(formatDateTime(vo.getEntryTime())),
                    escapeCsv(formatDateTime(vo.getExitTime())),
                    escapeCsv(formatDuration(vo.getParkingDurationMinutes())),
                    escapeCsv(formatYuan(vo.getFeeAmount())),
                    escapeCsv(formatYuan(vo.getPaidAmount())),
                    escapeCsv(vo.getPayChannelLabel()),
                    escapeCsv(vo.getStatusLabel()),
                    escapeCsv(vo.getEntryLaneName()),
                    escapeCsv(vo.getExitLaneName()),
                    escapeCsv(vo.getOperatorName()),
                    escapeCsv(vo.getReleaseReason())
            };
            response.getWriter().println(String.join(",", row));
        }
        response.getWriter().flush();
```

New:
```java
        List<ParkingRecord> records = recordMapper.selectList(wrapper.last("LIMIT " + EXPORT_MAX_LIMIT));
        List<ParkingRecordAdminVO> voList = convertToVOList(records);

        // xlsx 导出
        Workbook wb = new XSSFWorkbook();
        Sheet sheet = wb.createSheet("通行记录导出");
        Row headerRow = sheet.createRow(0);
        CellStyle headerStyle = wb.createCellStyle();
        Font headerFont = wb.createFont();
        headerFont.setBold(true);
        headerStyle.setFont(headerFont);

        String[] headers = {"车牌号", "入场时间", "出场时间", "停车时长", "应收金额(元)", "实付金额(元)", "支付方式", "状态", "入场通道", "出口通道", "操作人", "放行原因"};
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        CellStyle yuanStyle = wb.createCellStyle();
        yuanStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("#,##0.00"));

        int rowIdx = 1;
        for (ParkingRecordAdminVO vo : voList) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(vo.getPlateNumber() != null ? vo.getPlateNumber() : "");
            row.createCell(1).setCellValue(formatDateTime(vo.getEntryTime()));
            row.createCell(2).setCellValue(formatDateTime(vo.getExitTime()));
            row.createCell(3).setCellValue(formatDuration(vo.getParkingDurationMinutes()));
            Cell feeCell = row.createCell(4);
            feeCell.setCellValue(vo.getFeeAmount() != null ? vo.getFeeAmount() / 100.0 : 0.0);
            feeCell.setCellStyle(yuanStyle);
            Cell paidCell = row.createCell(5);
            paidCell.setCellValue(vo.getPaidAmount() != null ? vo.getPaidAmount() / 100.0 : 0.0);
            paidCell.setCellStyle(yuanStyle);
            row.createCell(6).setCellValue(vo.getPayChannelLabel() != null ? vo.getPayChannelLabel() : "");
            row.createCell(7).setCellValue(vo.getStatusLabel() != null ? vo.getStatusLabel() : "");
            row.createCell(8).setCellValue(vo.getEntryLaneName() != null ? vo.getEntryLaneName() : "");
            row.createCell(9).setCellValue(vo.getExitLaneName() != null ? vo.getExitLaneName() : "");
            row.createCell(10).setCellValue(vo.getOperatorName() != null ? vo.getOperatorName() : "");
            row.createCell(11).setCellValue(vo.getReleaseReason() != null ? vo.getReleaseReason() : "");
        }
        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);

        String fileName = URLEncoder.encode("通行记录导出_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".xlsx", StandardCharsets.UTF_8);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
        wb.write(response.getOutputStream());
        wb.close();
        response.getOutputStream().flush();
```

- [ ] **Step 3: Remove unused `escapeCsv` and `formatYuan` methods from this controller**

Find and delete the `escapeCsv` method (private method at bottom of file) and delete it.

- [ ] **Step 4: Verify compilation**

```bash
mvn compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/ParkingRecordAdminController.java
git commit -m "[包6-1] refactor: upgrade parking record export from CSV to xlsx via POI"
```

---

## Task 8: Backend — Create FileUploadController for parking lot images

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/FileUploadController.java`

- [ ] **Step 1: Create FileUploadController**

Create `/Users/zengbohan/Documents/project/jushan-platform/parking-system/src/main/java/com/jushan/system/controller/FileUploadController.java`:

```java
package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

/**
 * 文件上传 Controller（包 6-1：车场图片上传）。
 * <p>
 * 允许上传图片文件（jpg/png/gif/webp），存储到本地 uploads 目录，
 * 返回可访问的 URL 路径。静态资源映射由 Spring Boot 或 Nginx 提供。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/admin/files")
public class FileUploadController {

    private static final Logger log = LoggerFactory.getLogger(FileUploadController.class);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    private Path uploadPath;

    @PostConstruct
    public void init() {
        uploadPath = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploadPath);
        } catch (IOException e) {
            log.error("无法创建上传目录: {}", uploadPath, e);
        }
    }

    /**
     * 上传图片文件。
     *
     * @param file 图片文件（≤5MB）
     * @return { url: "/uploads/20260718/xxx.jpg" }
     */
    @PostMapping("/upload")
    @RequirePermission("parking:write")
    public R<Map<String, String>> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            return R.fail(400, "文件为空");
        }

        String contentType = file.getContentType();
        if (contentType == null || !contentType.startsWith("image/")) {
            return R.fail(400, "仅支持图片文件");
        }

        if (file.getSize() > 5 * 1024 * 1024) {
            return R.fail(400, "文件大小不能超过 5MB");
        }

        String originalName = file.getOriginalFilename();
        String ext = ".jpg";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf('.')).toLowerCase();
            if (!ext.matches("\\.(jpg|jpeg|png|gif|webp)")) {
                return R.fail(400, "不支持的图片格式: " + ext);
            }
        }

        String dateDir = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        try {
            Path datePath = uploadPath.resolve(dateDir);
            Files.createDirectories(datePath);
            Path targetPath = datePath.resolve(fileName);
            file.transferTo(targetPath);

            String url = "/uploads/" + dateDir + "/" + fileName;
            log.info("文件上传成功: {}", url);
            return R.ok(Map.of("url", url));
        } catch (IOException e) {
            log.error("文件上传失败", e);
            return R.fail(500, "文件上传失败");
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/FileUploadController.java
git commit -m "[包6-1] feat: add FileUploadController for parking lot image upload"
```

---

## Task 9: Frontend — Create report API module

**Files:**
- Create: `admin-web/src/api/report.ts`

- [ ] **Step 1: Create report.ts**

Create `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/api/report.ts`:

```typescript
import request from '@/utils/request'

export type ReportPeriod = 'DAILY' | 'MONTHLY' | 'YEARLY'

export interface RevenuePeriodStat {
  period: string
  totalRevenue: number
  orderCount: number
}

export interface RevenueReportVO {
  totalRevenue: number
  orderCount: number
  avgOrderAmount: number
  periods: RevenuePeriodStat[]
}

export interface TrafficDailyStat {
  date: string
  entryCount: number
  exitCount: number
}

export interface TrafficHourlyStat {
  hour: number
  entryCount: number
  exitCount: number
  total: number
}

export interface TrafficReportVO {
  totalEntry: number
  totalExit: number
  peakHour: number
  peakCount: number
  dailyStats: TrafficDailyStat[]
  hourlyStats: TrafficHourlyStat[]
}

/**
 * 收入报表（摘要 + 按期列表）。
 */
export function getRevenueReport(params: {
  periodType?: ReportPeriod
  startDate: string
  endDate: string
  lotId?: number
}) {
  return request.get<RevenueReportVO>('/v1/admin/reports/revenue', params)
}

/**
 * 车流量报表。
 */
export function getTrafficReport(params: {
  startDate: string
  endDate: string
  lotId?: number
  laneId?: number
}) {
  return request.get<TrafficReportVO>('/v1/admin/reports/traffic', params)
}

/**
 * 导出收入报表 xlsx。
 */
export function exportRevenueReport(params: {
  periodType?: ReportPeriod
  startDate: string
  endDate: string
  lotId?: number
}) {
  return request.get<Blob>('/v1/admin/reports/revenue/export', params, {
    responseType: 'blob',
  } as any)
}

/**
 * 导出车流量报表 xlsx。
 */
export function exportTrafficReport(params: {
  startDate: string
  endDate: string
  lotId?: number
}) {
  return request.get<Blob>('/v1/admin/reports/traffic/export', params, {
    responseType: 'blob',
  } as any)
}
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/api/report.ts
git commit -m "[包6-1] feat: add report API module for revenue/traffic endpoints"
```

---

## Task 10: Frontend — Create upload API module

**Files:**
- Create: `admin-web/src/api/upload.ts`

- [ ] **Step 1: Create upload.ts**

Create `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/api/upload.ts`:

```typescript
import axios from 'axios'
import { useAuthStore } from '@/stores/auth'

/**
 * 上传图片文件，返回可访问的 URL。
 * 使用独立的 axios 调用（multipart/form-data）。
 */
export async function uploadFile(file: File): Promise<{ url: string }> {
  const formData = new FormData()
  formData.append('file', file)

  const authStore = useAuthStore()
  const resp = await axios.post('/api/v1/admin/files/upload', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
      Authorization: `Bearer ${authStore.token}`,
    },
  })

  if (resp.data.code === 200) {
    return resp.data.data as { url: string }
  }
  throw new Error(resp.data.message || '上传失败')
}
```

- [ ] **Step 2: Add export to api/index.ts**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/api/index.ts`, add line before the final line:
```typescript
export * from './upload'
```

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/api/upload.ts admin-web/src/api/index.ts
git commit -m "[包6-1] feat: add file upload API module"
```

---

## Task 11: Frontend — Create revenue report page

**Files:**
- Create: `admin-web/src/views/report/RevenueReport.vue`

- [ ] **Step 1: Create RevenueReport.vue**

Create `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/views/report/RevenueReport.vue`:

```vue
<template>
  <div class="report-page">
    <div class="page-header">
      <span class="page-title">收入报表</span>
    </div>

    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap>
        <a-radio-group v-model:value="query.periodType" button-style="solid" @change="handleQuery">
          <a-radio-button value="DAILY">按日</a-radio-button>
          <a-radio-button value="MONTHLY">按月</a-radio-button>
          <a-radio-button value="YEARLY">按年</a-radio-button>
        </a-radio-group>
        <a-select
          v-model:value="query.lotId"
          placeholder="全部车场"
          allow-clear
          show-search
          :filter-option="filterLotOption"
          style="width: 200px"
          :options="lotOptions"
          @change="handleQuery"
        />
        <a-range-picker
          v-model:value="query.dateRange"
          format="YYYY-MM-DD"
          style="width: 260px"
          @change="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button type="primary" @click="handleExport">
          <template #icon><DownloadOutlined /></template>
          导出 Excel
        </a-button>
      </a-space>
    </div>

    <!-- 汇总卡片 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="8">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">总收入</div>
          <div class="stat-value">¥ {{ formatYuan(data.totalRevenue) }}</div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">订单总数</div>
          <div class="stat-value">{{ data.orderCount }}</div>
        </a-card>
      </a-col>
      <a-col :span="8">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">平均订单金额</div>
          <div class="stat-value">¥ {{ formatYuan(data.avgOrderAmount) }}</div>
        </a-card>
      </a-col>
    </a-row>

    <!-- 明细表格 -->
    <a-spin :spinning="loading">
      <a-table
        :columns="columns"
        :data-source="data.periods"
        row-key="period"
        :pagination="false"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'totalRevenue'">
            ¥ {{ formatYuan(record.totalRevenue) }}
          </template>
          <template v-if="column.key === 'avgAmount'">
            ¥ {{ formatYuan(record.orderCount > 0 ? Math.round(record.totalRevenue / record.orderCount) : 0) }}
          </template>
        </template>
      </a-table>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import {
  getRevenueReport,
  exportRevenueReport,
  type RevenueReportVO,
  type ReportPeriod,
} from '@/api/report'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const columns = [
  { title: '期间', dataIndex: 'period', key: 'period', width: 150 },
  { title: '收入', key: 'totalRevenue', width: 150 },
  { title: '订单数', dataIndex: 'orderCount', key: 'orderCount', width: 120 },
  { title: '平均订单金额', key: 'avgAmount', width: 150 },
]

const loading = ref(false)
const data = reactive<RevenueReportVO>({
  totalRevenue: 0,
  orderCount: 0,
  avgOrderAmount: 0,
  periods: [],
})

const query = reactive({
  periodType: 'DAILY' as ReportPeriod,
  lotId: undefined as number | undefined,
  dateRange: undefined as [Dayjs, Dayjs] | undefined,
})

const lotOptions = ref<{ value: number; label: string }[]>([])

async function loadLotOptions() {
  try {
    const res = await getParkingLots({ page: 1, size: 999 })
    lotOptions.value = (res.records || []).map((lot: ParkingLotVO) => ({
      value: lot.id,
      label: lot.name,
    }))
  } catch { /* ignore */ }
}

function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

function getDefaultDateRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(30, 'day').startOf('day'), dayjs().endOf('day')]
}

async function loadData() {
  loading.value = true
  try {
    const range = query.dateRange || getDefaultDateRange()
    const res = await getRevenueReport({
      periodType: query.periodType,
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    data.totalRevenue = res.totalRevenue
    data.orderCount = res.orderCount
    data.avgOrderAmount = res.avgOrderAmount
    data.periods = res.periods || []
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function handleQuery() {
  loadData()
}

async function handleExport() {
  try {
    const range = query.dateRange || getDefaultDateRange()
    const blob = await exportRevenueReport({
      periodType: query.periodType,
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `收入报表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    message.error('导出失败')
  }
}

function formatYuan(cents?: number): string {
  if (cents == null) return '0.00'
  return (cents / 100).toFixed(2)
}

onMounted(() => {
  query.dateRange = getDefaultDateRange()
  loadLotOptions()
  loadData()
})
</script>

<style lang="scss" scoped>
.report-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
}
.query-bar {
  margin-bottom: 16px;
}
.summary-card {
  border-radius: 8px;
  margin-bottom: 8px;
}
.stat-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #333;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/views/report/RevenueReport.vue
git commit -m "[包6-1] feat: add revenue report page"
```

---

## Task 12: Frontend — Create traffic report page

**Files:**
- Create: `admin-web/src/views/report/TrafficReport.vue`

- [ ] **Step 1: Create TrafficReport.vue**

Create `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/views/report/TrafficReport.vue`:

```vue
<template>
  <div class="report-page">
    <div class="page-header">
      <span class="page-title">车流量报表</span>
    </div>

    <!-- 筛选栏 -->
    <div class="query-bar">
      <a-space wrap>
        <a-select
          v-model:value="query.lotId"
          placeholder="全部车场"
          allow-clear
          show-search
          :filter-option="filterLotOption"
          style="width: 200px"
          :options="lotOptions"
          @change="handleQuery"
        />
        <a-range-picker
          v-model:value="query.dateRange"
          format="YYYY-MM-DD"
          style="width: 260px"
          @change="handleQuery"
        />
        <a-button type="primary" @click="handleQuery">
          <template #icon><SearchOutlined /></template>
          查询
        </a-button>
        <a-button type="primary" @click="handleExport">
          <template #icon><DownloadOutlined /></template>
          导出 Excel
        </a-button>
      </a-space>
    </div>

    <!-- 汇总卡片 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">总入场</div>
          <div class="stat-value">{{ data.totalEntry }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">总出场</div>
          <div class="stat-value">{{ data.totalExit }}</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">峰值小时</div>
          <div class="stat-value">{{ data.peakHour }}:00</div>
        </a-card>
      </a-col>
      <a-col :span="6">
        <a-card :bordered="false" class="summary-card">
          <div class="stat-label">峰值流量</div>
          <div class="stat-value">{{ data.peakCount }}</div>
        </a-card>
      </a-col>
    </a-row>

    <a-spin :spinning="loading">
      <!-- 按天趋势柱状图 -->
      <a-card title="每日车流量趋势" :bordered="false" style="margin-bottom: 16px">
        <div class="chart-container">
          <div class="bar-chart">
            <div
              v-for="item in data.dailyStats"
              :key="item.date"
              class="bar-item"
              :title="`${item.date} 入场${item.entryCount} 出场${item.exitCount}`"
            >
              <div class="bar-wrapper">
                <div
                  class="bar bar-entry"
                  :style="{ height: barPct(item.entryCount) + '%' }"
                />
                <div
                  class="bar bar-exit"
                  :style="{ height: barPct(item.exitCount) + '%' }"
                />
              </div>
              <div class="bar-label">{{ item.date.slice(5) }}</div>
            </div>
          </div>
        </div>
        <div class="chart-legend">
          <span class="legend-item"><span class="dot dot-entry" /> 入场</span>
          <span class="legend-item"><span class="dot dot-exit" /> 出场</span>
        </div>
      </a-card>

      <!-- 按小时分布 -->
      <a-card title="时段分布（跨天合并）" :bordered="false">
        <div class="chart-container">
          <div class="hour-chart">
            <div
              v-for="item in data.hourlyStats"
              :key="item.hour"
              class="hour-row"
            >
              <span class="hour-label">{{ String(item.hour).padStart(2, '0') }}:00</span>
              <div class="hour-bar-track">
                <div
                  class="hour-bar"
                  :style="{ width: hourBarPct(item.total) + '%' }"
                >
                  <span class="hour-count">{{ item.total }}</span>
                </div>
              </div>
            </div>
          </div>
        </div>
      </a-card>
    </a-spin>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, computed } from 'vue'
import { message } from 'ant-design-vue'
import { SearchOutlined, DownloadOutlined } from '@ant-design/icons-vue'
import type { Dayjs } from 'dayjs'
import dayjs from 'dayjs'
import {
  getTrafficReport,
  exportTrafficReport,
  type TrafficReportVO,
} from '@/api/report'
import { getParkingLots, type ParkingLotVO } from '@/api/parking-lot'

const loading = ref(false)
const data = reactive<TrafficReportVO>({
  totalEntry: 0,
  totalExit: 0,
  peakHour: 0,
  peakCount: 0,
  dailyStats: [],
  hourlyStats: [],
})

const query = reactive({
  lotId: undefined as number | undefined,
  dateRange: undefined as [Dayjs, Dayjs] | undefined,
})

const lotOptions = ref<{ value: number; label: string }[]>([])

async function loadLotOptions() {
  try {
    const res = await getParkingLots({ page: 1, size: 999 })
    lotOptions.value = (res.records || []).map((lot: ParkingLotVO) => ({
      value: lot.id,
      label: lot.name,
    }))
  } catch { /* ignore */ }
}

function filterLotOption(input: string, option: { value: number; label: string } | undefined) {
  return option?.label?.toLowerCase().includes(input.toLowerCase()) ?? false
}

function getDefaultDateRange(): [Dayjs, Dayjs] {
  return [dayjs().subtract(30, 'day').startOf('day'), dayjs().endOf('day')]
}

const maxDaily = computed(() => {
  let max = 1
  for (const d of data.dailyStats) {
    max = Math.max(max, d.entryCount, d.exitCount)
  }
  return max
})

const maxHourly = computed(() => {
  let max = 1
  for (const h of data.hourlyStats) {
    max = Math.max(max, h.total)
  }
  return max
})

function barPct(val: number): number {
  return (val / maxDaily.value) * 100
}

function hourBarPct(val: number): number {
  return Math.max(2, (val / maxHourly.value) * 100)
}

async function loadData() {
  loading.value = true
  try {
    const range = query.dateRange || getDefaultDateRange()
    const res = await getTrafficReport({
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    Object.assign(data, res)
  } catch { /* ignore */ } finally {
    loading.value = false
  }
}

function handleQuery() {
  loadData()
}

async function handleExport() {
  try {
    const range = query.dateRange || getDefaultDateRange()
    const blob = await exportTrafficReport({
      startDate: range[0].format('YYYY-MM-DD'),
      endDate: range[1].format('YYYY-MM-DD'),
      lotId: query.lotId,
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', `车流量报表_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    message.success('导出成功')
  } catch {
    message.error('导出失败')
  }
}

onMounted(() => {
  query.dateRange = getDefaultDateRange()
  loadLotOptions()
  loadData()
})
</script>

<style lang="scss" scoped>
.report-page {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
}
.page-header {
  margin-bottom: 16px;
}
.page-title {
  font-size: 20px;
  font-weight: 600;
}
.query-bar {
  margin-bottom: 16px;
}
.summary-card {
  border-radius: 8px;
  margin-bottom: 8px;
}
.stat-label {
  font-size: 13px;
  color: #999;
  margin-bottom: 8px;
}
.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: #333;
}

.chart-container {
  min-height: 140px;
}
.bar-chart {
  display: flex;
  align-items: flex-end;
  height: 180px;
  gap: 2px;
  padding: 0 4px;
  overflow-x: auto;
}
.bar-item {
  flex: none;
  width: 30px;
  display: flex;
  flex-direction: column;
  align-items: center;
  height: 100%;
}
.bar-wrapper {
  flex: 1;
  width: 100%;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 1px;
}
.bar {
  width: 100%;
  border-radius: 2px 2px 0 0;
  min-height: 2px;
}
.bar-entry { background: #52c41a; }
.bar-exit { background: #1677ff; }
.bar-label {
  font-size: 9px;
  color: #999;
  margin-top: 4px;
  transform: rotate(-45deg);
  transform-origin: top left;
  white-space: nowrap;
}
.chart-legend {
  text-align: center;
  margin-top: 8px;
}
.legend-item {
  font-size: 12px;
  color: #999;
  margin: 0 8px;
}
.dot {
  display: inline-block;
  width: 8px;
  height: 8px;
  border-radius: 50%;
  margin-right: 4px;
}
.dot-entry { background: #52c41a; }
.dot-exit { background: #1677ff; }

.hour-chart {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.hour-row {
  display: flex;
  align-items: center;
  gap: 8px;
}
.hour-label {
  width: 42px;
  font-size: 12px;
  color: #666;
  text-align: right;
}
.hour-bar-track {
  flex: 1;
  background: #f0f0f0;
  border-radius: 4px;
  height: 24px;
  overflow: hidden;
}
.hour-bar {
  height: 100%;
  background: linear-gradient(90deg, #1677ff, #4096ff);
  border-radius: 4px;
  display: flex;
  align-items: center;
  min-width: 30px;
  transition: width 0.3s;
}
.hour-count {
  font-size: 11px;
  color: #fff;
  padding-left: 6px;
  white-space: nowrap;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/views/report/TrafficReport.vue
git commit -m "[包6-1] feat: add traffic report page with charts"
```

---

## Task 13: Frontend — Add report routes to router

**Files:**
- Modify: `admin-web/src/router/index.ts`

- [ ] **Step 1: Add report routes**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/router/index.ts`, add BEFORE the closing `]` of the `children` array (after the `exception-records` route entry, around line 182). Insert:

```typescript
      // 包 6-1：收入报表
      {
        path: 'report-revenue',
        name: 'ReportRevenue',
        component: () => import('@/views/report/RevenueReport.vue'),
        meta: { title: '收入报表', icon: 'BarChartOutlined', permission: 'dashboard:view', cache: true },
      },
      // 包 6-1：车流量报表
      {
        path: 'report-traffic',
        name: 'ReportTraffic',
        component: () => import('@/views/report/TrafficReport.vue'),
        meta: { title: '车流量报表', icon: 'LineChartOutlined', permission: 'dashboard:view', cache: true },
      },
```

- [ ] **Step 2: Verify frontend compiles**

```bash
cd admin-web && npx vue-tsc --noEmit 2>&1 | head -20
```

Expected: no type errors related to new routes.

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/router/index.ts
git commit -m "[包6-1] feat: add report routes for revenue and traffic pages"
```

---

## Task 14: Frontend — Fix MENU_MAP: populate billing group + add report menus

**Files:**
- Modify: `admin-web/src/stores/app.ts`
- Modify: `admin-web/src/layout/index.vue`

- [ ] **Step 1: Update MENU_MAP in app.ts**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/stores/app.ts`, replace the empty `billing` menu array (line 50: `billing: [],`) with:

```typescript
  billing: [
    { key: 'billing-rules', label: '收费规则', icon: 'DollarOutlined', path: '/billing-rules' },
    { key: 'fee-calculator', label: '费用试算', icon: 'CalculatorOutlined', path: '/fee-calculator' },
    { key: 'orders', label: '订单管理', icon: 'FileTextOutlined', path: '/orders', permission: 'order:manage' },
    { key: 'access-policies', label: '黑白名单', icon: 'SafetyOutlined', path: '/access-policies', permission: 'parking:view' },
  ],
```

Also add `report-revenue` and `report-traffic` to the `overview` group. In the `overview` array (line 31), add after the dashboard entry:

```typescript
  overview: [
    { key: 'dashboard', label: '平台驾驶舱', icon: 'DashboardOutlined', path: '/dashboard' },
    { key: 'report-revenue', label: '收入报表', icon: 'BarChartOutlined', path: '/report-revenue', permission: 'dashboard:view' },
    { key: 'report-traffic', label: '车流量报表', icon: 'LineChartOutlined', path: '/report-traffic', permission: 'dashboard:view' },
  ],
```

- [ ] **Step 2: Add icon imports to layout/index.vue**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/layout/index.vue`, in the `<script setup>` section's import from `@ant-design/icons-vue` (around lines 141-170), add `CalculatorOutlined`, `SafetyOutlined`, `BarChartOutlined`, `LineChartOutlined`, `BranchesOutlined` to the destructure import.

Specifically, the import statement currently looks like:
```typescript
import {
  DownOutlined,
  ReloadOutlined,
  ...
} from '@ant-design/icons-vue'
```

Add `CalculatorOutlined, SafetyOutlined, BarChartOutlined, LineChartOutlined, BranchesOutlined` to this import list.

And in the `iconMap` (lines 197-203), add:
```typescript
  CalculatorOutlined, SafetyOutlined, BarChartOutlined, LineChartOutlined, BranchesOutlined,
```

The updated iconMap should be:
```typescript
const iconMap: Record<string, any> = {
  DashboardOutlined, HomeOutlined, SwapOutlined, ShopOutlined,
  ApiOutlined, ThunderboltOutlined, CarOutlined, AppstoreOutlined, FileTextOutlined,
  UserOutlined, TeamOutlined, DollarOutlined, IdcardOutlined,
  AlertOutlined, ApartmentOutlined, AuditOutlined, BellOutlined,
  SettingOutlined, WalletOutlined, CalculatorOutlined, SafetyOutlined,
  BarChartOutlined, LineChartOutlined, BranchesOutlined,
}
```

- [ ] **Step 3: Verify that the `setNavByPath` function in app.ts auto-matches**

The existing `setNavByPath` function (lines 93-102 of app.ts) iterates MENU_MAP entries to match paths. This already handles the new menu entries — no code change needed.

- [ ] **Step 4: Commit**

```bash
git add admin-web/src/stores/app.ts admin-web/src/layout/index.vue
git commit -m "[包6-1] fix: populate billing menu group & add report menus to overview"
```

---

## Task 15: Frontend — Update order export filename from .csv to .xlsx

**Files:**
- Modify: `admin-web/src/views/order/index.vue`

- [ ] **Step 1: Change order export download filename**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/views/order/index.vue`, in the `handleExport` function (line 399), change:

```typescript
    const fileName = `订单导出_${dayjs().format('YYYYMMDD_HHmmss')}.csv`
```

to:

```typescript
    const fileName = `订单导出_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
```

Also update the parking record page if it has a download with .csv extension:

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/views/parking-record/index.vue`, find any `exportParkingRecords` call and the associated `download` filename that ends with `.csv`, and change it to `.xlsx`.

- [ ] **Step 2: Fix the parking-record page export filename**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/views/parking-record/index.vue`, line 248, change:

```typescript
    const fileName = `通行记录导出_${dayjs().format('YYYYMMDD_HHmmss')}.csv`
```

to:

```typescript
    const fileName = `通行记录导出_${dayjs().format('YYYYMMDD_HHmmss')}.xlsx`
```

- [ ] **Step 3: Commit**

```bash
git add admin-web/src/views/order/index.vue admin-web/src/views/parking-record/index.vue
git commit -m "[包6-1] fix: update export download filenames from .csv to .xlsx"
```

---

## Task 16: Frontend — Wire parking lot image upload to real API

**Files:**
- Modify: `admin-web/src/views/parking/ParkingLotManage.vue`

- [ ] **Step 1: Replace mock upload logic in ParkingLotManage.vue**

In `/Users/zengbohan/Documents/project/jushan-platform/admin-web/src/views/parking/ParkingLotManage.vue`, the `beforeUpload` function (line 405) currently generates a mock `blob:` URL. Replace it to use the real upload API.

First, add the import at the top of the `<script setup>` section (around line 161, after existing imports):

```typescript
import { uploadFile } from '@/api/upload'
```

Then replace the `beforeUpload` function (lines 405-421):

Old:
```typescript
function beforeUpload(file: any) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    message.error('图片大小不能超过 5MB')
    return false
  }
  // 开发阶段：不上传真实服务器，生成 mock URL
  const mockUrl = URL.createObjectURL(file)
  file.url = mockUrl
  file.thumbUrl = mockUrl
  return false
}
```

New:
```typescript
async function beforeUpload(file: any) {
  const isImage = file.type.startsWith('image/')
  if (!isImage) {
    message.error('只能上传图片文件')
    return false
  }
  const isLt5M = file.size / 1024 / 1024 < 5
  if (!isLt5M) {
    message.error('图片大小不能超过 5MB')
    return false
  }
  try {
    const { url } = await uploadFile(file as File)
    file.url = url
    file.thumbUrl = url
    file.status = 'done'
    message.success('上传成功')
  } catch {
    message.error('上传失败')
    file.status = 'error'
  }
  return false
}
```

Also update `handleRemove` (line 423) to remove the `blob:` URL cleanup since we no longer generate blob URLs:

Old:
```typescript
function handleRemove(file: any) {
  if (file.url && file.url.startsWith('blob:')) {
    URL.revokeObjectURL(file.url)
  }
}
```

New:
```typescript
function handleRemove(_file: any) {
  // No-op: server stores the file; removal just removes from UI file list.
}
```

- [ ] **Step 2: Commit**

```bash
git add admin-web/src/views/parking/ParkingLotManage.vue
git commit -m "[包6-1] feat: wire parking lot image upload to real API endpoint"
```

---

## Task 17: Frontend — Cleanup: delete duplicate views

**Files:**
- Delete: `admin-web/src/views/parking-lane/index.vue` (and directory)
- Delete: `admin-web/src/views/parking-zone/index.vue` (and directory)

- [ ] **Step 1: Verify no references to these files**

Run:
```bash
grep -r "parking-lane/index\|parking-zone/index" admin-web/src/ --include="*.ts" --include="*.vue" 2>&1
```

Expected: no output (no references in router or other files).

- [ ] **Step 2: Delete the duplicate directories**

```bash
rm -rf admin-web/src/views/parking-lane
rm -rf admin-web/src/views/parking-zone
```

- [ ] **Step 3: Verify frontend still compiles**

```bash
cd admin-web && npx vue-tsc --noEmit 2>&1 | tail -5
```

Expected: no import errors related to deleted paths.

- [ ] **Step 4: Commit**

```bash
git rm -r admin-web/src/views/parking-lane admin-web/src/views/parking-zone
git commit -m "[包6-1] cleanup: remove duplicate parking-lane and parking-zone views"
```

---

## Task 18: Frontend — Cleanup: offline department orphan module

**Files:**
- Delete: `admin-web/src/views/department/DepartmentManage.vue`
- Delete: `admin-web/src/views/department/DepartmentFormModal.vue`
- Delete: `admin-web/src/api/department.ts`
- (and their parent directories)

- [ ] **Step 1: Verify these files have no route entry or menu reference**

Run:
```bash
grep -r "department\|Department" admin-web/src/router/ admin-web/src/stores/ admin-web/src/layout/ 2>&1
```

Expected: no matches (confirming orphan status).

- [ ] **Step 2: Delete the department module**

```bash
rm -rf admin-web/src/views/department
rm admin-web/src/api/department.ts
```

- [ ] **Step 3: Commit**

```bash
git rm -r admin-web/src/views/department
git rm admin-web/src/api/department.ts
git commit -m "[包6-1] cleanup: offline unreferenced department module"
```

---

## Task 19: Frontend — Cleanup: delete unreferenced HolidayRuleEditor component

**Files:**
- Delete: `admin-web/src/components/HolidayRuleEditor.vue`

- [ ] **Step 1: Confirm no references**

Run:
```bash
grep -r "HolidayRuleEditor" admin-web/src/ --include="*.vue" --include="*.ts" 2>&1
```

Expected: only the file itself (no imports).

- [ ] **Step 2: Delete the component**

```bash
rm admin-web/src/components/HolidayRuleEditor.vue
```

- [ ] **Step 3: Commit**

```bash
git rm admin-web/src/components/HolidayRuleEditor.vue
git commit -m "[包6-1] cleanup: remove unreferenced HolidayRuleEditor component"
```

---

## Task 20: Integration — Full build and final verification

**Files:** None (verification only)

- [ ] **Step 1: Full backend compilation**

```bash
mvn clean compile -pl parking-system -am 2>&1 | tail -5
```

Expected: `BUILD SUCCESS`

- [ ] **Step 2: Frontend type-check**

```bash
cd admin-web && npx vue-tsc --noEmit 2>&1 | tail -10
```

Expected: no errors. If ECharts-related type errors appear (from vue-echarts), add a `declare module 'vue-echarts'` in `vite-env.d.ts`.

- [ ] **Step 3: Verify all new routes resolve to existing components**

Check each route path by confirming the component file exists:
```bash
ls admin-web/src/views/report/RevenueReport.vue admin-web/src/views/report/TrafficReport.vue
```

Expected: both files exist.

- [ ] **Step 4: Verify menu entries match route paths**

In `app.ts`, confirm each menu's `path` has a corresponding router entry:
- `/billing-rules` ✓ (router line 94)
- `/fee-calculator` ✓ (router line 101)
- `/orders` ✓ (router line 144)
- `/access-policies` ✓ (router line 163)
- `/report-revenue` ✓ (added in Task 13)
- `/report-traffic` ✓ (added in Task 13)

- [ ] **Step 5: Run Flyway migration against local DB to confirm index creation**

```bash
mvn flyway:migrate -pl parking-boot -am 2>&1 | grep "V20260718501"
```

Expected: `Successfully applied 1 migration(s)` or `V20260718501 ... Success`

- [ ] **Step 6: Commit any remaining changes**

```bash
git status
git diff --stat
```

If clean, no commit needed.

---

## Summary

**Total tasks:** 20  
**Backend files created:** 6 (ReportMapper, ReportController, Report VO×2, FileUploadController, Flyway migration)  
**Backend files modified:** 3 (pom.xml ×2, OrderAdminController, ParkingRecordAdminController)  
**Frontend files created:** 4 (report.ts, upload.ts, RevenueReport.vue, TrafficReport.vue)  
**Frontend files modified:** 6 (router, app.ts, layout, order/index.vue, parking-record/index.vue, ParkingLotManage.vue, api/index.ts)  
**Files deleted:** 6 (parking-lane/, parking-zone/, department/×3, HolidayRuleEditor.vue)  

**Open questions:** None — all design decisions are resolved above.
