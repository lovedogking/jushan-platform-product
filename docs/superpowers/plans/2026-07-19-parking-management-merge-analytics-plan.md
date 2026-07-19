# 车场管理合并 & 运营数据页 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 合并车场/车道/设备管理为一个主从列表页（`/admin/parking`），新增运营数据页（`/operation/analytics`），Device 表增加网络字段，清理旧代码和文档。

**Architecture:** 前端主从列表布局（左侧车场列表 + 右侧三 Tab），后端新增统计聚合 API（直接 SQL 查 parking_session），ECharts 图表渲染。

**Tech Stack:** Vue 3 + Ant Design Vue 4 + Pinia + ECharts 5 + TypeScript, Java 21 + Spring Boot 3.x + MyBatis-Plus, Flyway

## Global Constraints

- 金额字段一期始终返回 null，前端显示「即将上线」
- Device 网络字段全部 optional（nullable）
- 不修改构建脚本与 CI 配置
- 删除代码需 clean compile 验证
- Commit 格式: `[GAP-xx] feat/fix: 描述` 或 `[SA-xx] feat: 描述`

---

### Task 1: Flyway 迁移 — Device 表新增网络字段

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260903001__device_network_fields.sql`

**Interfaces:**
- Produces: `device.ip_address VARCHAR(45)`, `device.port INT`, `device.subnet_mask VARCHAR(45)`, `device.gateway VARCHAR(45)`

- [ ] **Step 1: 创建 Flyway 迁移 SQL 文件**

```sql
-- V20260903001__device_network_fields.sql
-- Device 表新增网络配置字段，供适配器连接使用

ALTER TABLE device
  ADD COLUMN ip_address VARCHAR(45) NULL COMMENT 'IP地址',
  ADD COLUMN port INT NULL DEFAULT 80 COMMENT '端口',
  ADD COLUMN subnet_mask VARCHAR(45) NULL COMMENT '子网掩码',
  ADD COLUMN gateway VARCHAR(45) NULL COMMENT '网关地址';
```

- [ ] **Step 2: 提交**

```bash
git add parking-boot/src/main/resources/db/migration/V20260903001__device_network_fields.sql
git commit -m "feat: Device 表新增 IP/端口/子网掩码/网关字段"
```

---

### Task 2: 后端 — Device 实体/DTO/VO 增加网络字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/Device.java`
- Modify: `parking-system/src/main/java/com/jushan/system/dto/CreateDeviceRequest.java`
- Modify: `parking-system/src/main/java/com/jushan/system/dto/UpdateDeviceRequest.java`
- Modify: `parking-system/src/main/java/com/jushan/system/vo/DeviceVO.java`

**Interfaces:**
- Produces: Device 实体新增 `ipAddress`, `port`, `subnetMask`, `gateway` 字段及 getter/setter；DTO/VO 同步新增

- [ ] **Step 1: Device.java 新增字段和 getter/setter**

在 `Device.java` 的 `description` 字段之后、`createdAt` 之前添加：

```java
    /** 设备 IP 地址 */
    private String ipAddress;

    /** 设备端口，默认 80 */
    private Integer port;

    /** 子网掩码 */
    private String subnetMask;

    /** 网关地址 */
    private String gateway;
```

在 getter/setter 区域（`setDescription` 之后、`getCreatedAt` 之前）添加：

```java
    public String getIpAddress() { return ipAddress; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }

    public Integer getPort() { return port; }
    public void setPort(Integer port) { this.port = port; }

    public String getSubnetMask() { return subnetMask; }
    public void setSubnetMask(String subnetMask) { this.subnetMask = subnetMask; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }
```

- [ ] **Step 2: CreateDeviceRequest.java 新增字段**

```java
    /** 设备 IP 地址 */
    private String ipAddress;

    /** 设备端口，默认 80 */
    private Integer port;

    /** 子网掩码 */
    private String subnetMask;

    /** 网关地址 */
    private String gateway;
```

（添加对应的 getter/setter）

- [ ] **Step 3: UpdateDeviceRequest.java 新增字段**

```java
    /** 设备 IP 地址 */
    private String ipAddress;

    /** 设备端口 */
    private Integer port;

    /** 子网掩码 */
    private String subnetMask;

    /** 网关地址 */
    private String gateway;
```

（添加对应的 getter/setter）

- [ ] **Step 4: DeviceVO.java 新增字段**

```java
    /** 设备 IP 地址 */
    private String ipAddress;

    /** 设备端口 */
    private Integer port;

    /** 子网掩码 */
    private String subnetMask;

    /** 网关地址 */
    private String gateway;
```

（添加对应的 getter/setter）

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期：BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/Device.java
git add parking-system/src/main/java/com/jushan/system/dto/CreateDeviceRequest.java
git add parking-system/src/main/java/com/jushan/system/dto/UpdateDeviceRequest.java
git add parking-system/src/main/java/com/jushan/system/vo/DeviceVO.java
git commit -m "feat: Device 实体/DTO/VO 增加网络配置字段"
```

---

### Task 3: 后端 — 运营数据统计接口

**Files:**
- Create: `parking-system/src/main/java/com/jushan/platform/modules/parking/controller/AnalyticsController.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/parking/service/AnalyticsService.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/parking/service/impl/AnalyticsServiceImpl.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/parking/vo/AnalyticsOverviewVO.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/parking/dto/AnalyticsQueryCmd.java`

**Interfaces:**
- Produces: `GET /api/v1/admin/analytics/overview?lotId=&period=today|month|year|custom&startDate=&endDate=`
- Returns: `AnalyticsOverviewVO { entryCount, exitCount, currentInCount, entryTriggerStats, trendData[], revenue }`

- [ ] **Step 1: 创建 AnalyticsQueryCmd**

```java
package com.jushan.platform.modules.parking.dto;

import java.time.LocalDate;

public class AnalyticsQueryCmd {
    /** 停车场ID，0 或 null 表示全部 */
    private Long lotId;
    /** 时间周期：today / month / year / custom */
    private String period;
    /** 自定义起始日期（period=custom 时必填） */
    private LocalDate startDate;
    /** 自定义结束日期（period=custom 时必填） */
    private LocalDate endDate;

    // getter/setter ...
    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }
    public String getPeriod() { return period; }
    public void setPeriod(String period) { this.period = period; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
}
```

- [ ] **Step 2: 创建 AnalyticsOverviewVO**

```java
package com.jushan.platform.modules.parking.vo;

import java.util.List;
import java.util.Map;

public class AnalyticsOverviewVO {
    /** 入场总数 */
    private Long entryCount;
    /** 出场总数 */
    private Long exitCount;
    /** 当前在场车辆数 */
    private Long currentInCount;
    /** 入场触发方式统计: whitelist_auto / manual_open / always_open_period / manual_entry */
    private Map<String, Long> entryTriggerStats;
    /** 车流量趋势数据（按时段聚合） */
    private List<TrendPoint> trendData;
    /** 营收（一期返回 null） */
    private Object revenue;

    public static class TrendPoint {
        private String time;
        private Long entry;
        private Long exit;

        public TrendPoint() {}
        public TrendPoint(String time, Long entry, Long exit) {
            this.time = time; this.entry = entry; this.exit = exit;
        }
        public String getTime() { return time; }
        public void setTime(String time) { this.time = time; }
        public Long getEntry() { return entry; }
        public void setEntry(Long entry) { this.entry = entry; }
        public Long getExit() { return exit; }
        public void setExit(Long exit) { this.exit = exit; }
    }

    // getter/setter ...
    public Long getEntryCount() { return entryCount; }
    public void setEntryCount(Long entryCount) { this.entryCount = entryCount; }
    public Long getExitCount() { return exitCount; }
    public void setExitCount(Long exitCount) { this.exitCount = exitCount; }
    public Long getCurrentInCount() { return currentInCount; }
    public void setCurrentInCount(Long currentInCount) { this.currentInCount = currentInCount; }
    public Map<String, Long> getEntryTriggerStats() { return entryTriggerStats; }
    public void setEntryTriggerStats(Map<String, Long> entryTriggerStats) { this.entryTriggerStats = entryTriggerStats; }
    public List<TrendPoint> getTrendData() { return trendData; }
    public void setTrendData(List<TrendPoint> trendData) { this.trendData = trendData; }
    public Object getRevenue() { return revenue; }
    public void setRevenue(Object revenue) { this.revenue = revenue; }
}
```

- [ ] **Step 3: 创建 AnalyticsService 接口**

```java
package com.jushan.platform.modules.parking.service;

import com.jushan.platform.modules.parking.dto.AnalyticsQueryCmd;
import com.jushan.platform.modules.parking.vo.AnalyticsOverviewVO;

public interface AnalyticsService {
    AnalyticsOverviewVO getOverview(AnalyticsQueryCmd cmd);
}
```

- [ ] **Step 4: 创建 AnalyticsServiceImpl**

```java
package com.jushan.platform.modules.parking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.platform.modules.parking.dto.AnalyticsQueryCmd;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.parking.service.AnalyticsService;
import com.jushan.platform.modules.parking.vo.AnalyticsOverviewVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalyticsServiceImpl implements AnalyticsService {

    private final ParkingSessionMapper parkingSessionMapper;

    public AnalyticsServiceImpl(ParkingSessionMapper parkingSessionMapper) {
        this.parkingSessionMapper = parkingSessionMapper;
    }

    @Override
    public AnalyticsOverviewVO getOverview(AnalyticsQueryCmd cmd) {
        AnalyticsOverviewVO vo = new AnalyticsOverviewVO();
        LocalDateTime[] range = resolveTimeRange(cmd);

        // 入场总数（entry_time 在范围内）
        Long entryCount = countByEntryTimeRange(range[0], range[1], cmd.getLotId());
        vo.setEntryCount(entryCount);

        // 出场总数（exit_time 在范围内）
        Long exitCount = countByExitTimeRange(range[0], range[1], cmd.getLotId());
        vo.setExitCount(exitCount);

        // 当前在场
        Long currentInCount = countCurrentIn(cmd.getLotId());
        vo.setCurrentInCount(currentInCount);

        // 入场触发方式统计
        Map<String, Long> triggerStats = countByEntryTrigger(range[0], range[1], cmd.getLotId());
        vo.setEntryTriggerStats(triggerStats);

        // 趋势数据
        List<AnalyticsOverviewVO.TrendPoint> trend = buildTrend(range[0], range[1], cmd);
        vo.setTrendData(trend);

        // 营收一期占位
        vo.setRevenue(null);

        return vo;
    }

    private LocalDateTime[] resolveTimeRange(AnalyticsQueryCmd cmd) {
        LocalDate today = LocalDate.now();
        LocalDateTime start, end;
        switch (cmd.getPeriod() != null ? cmd.getPeriod() : "today") {
            case "today":
                start = today.atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "month":
                start = today.withDayOfMonth(1).atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "year":
                start = today.withDayOfYear(1).atStartOfDay();
                end = today.atTime(LocalTime.MAX);
                break;
            case "custom":
                start = cmd.getStartDate() != null ? cmd.getStartDate().atStartOfDay() : today.atStartOfDay();
                end = (cmd.getEndDate() != null ? cmd.getEndDate() : today).atTime(LocalTime.MAX);
                break;
            default:
                start = today.atStartOfDay();
                end = today.atTime(LocalTime.MAX);
        }
        return new LocalDateTime[]{start, end};
    }

    private Long countByEntryTimeRange(LocalDateTime start, LocalDateTime end, Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.between(ParkingSession::getEntryTime, start, end);
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        return parkingSessionMapper.selectCount(qw);
    }

    private Long countByExitTimeRange(LocalDateTime start, LocalDateTime end, Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.between(ParkingSession::getExitTime, start, end);
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        return parkingSessionMapper.selectCount(qw);
    }

    private Long countCurrentIn(Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.eq(ParkingSession::getStatus, "IN");
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        return parkingSessionMapper.selectCount(qw);
    }

    private Map<String, Long> countByEntryTrigger(LocalDateTime start, LocalDateTime end, Long lotId) {
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.between(ParkingSession::getEntryTime, start, end);
        qw.isNotNull(ParkingSession::getEntryTrigger);
        if (lotId != null && lotId > 0) qw.eq(ParkingSession::getParkingLotId, lotId);
        List<ParkingSession> sessions = parkingSessionMapper.selectList(qw);
        Map<String, Long> stats = new LinkedHashMap<>();
        stats.put("whitelist_auto", 0L);
        stats.put("manual_open", 0L);
        stats.put("always_open_period", 0L);
        stats.put("manual_entry", 0L);
        for (ParkingSession s : sessions) {
            String trigger = s.getEntryTrigger();
            if (trigger != null) {
                stats.merge(trigger, 1L, Long::sum);
            }
        }
        return stats;
    }

    private List<AnalyticsOverviewVO.TrendPoint> buildTrend(LocalDateTime start, LocalDateTime end, AnalyticsQueryCmd cmd) {
        String period = cmd.getPeriod() != null ? cmd.getPeriod() : "today";
        ChronoUnit unit;
        DateTimeFormatter fmt;
        if ("today".equals(period)) {
            unit = ChronoUnit.HOURS;
            fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
        } else if ("month".equals(period)) {
            unit = ChronoUnit.DAYS;
            fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        } else {
            unit = ChronoUnit.MONTHS;
            fmt = DateTimeFormatter.ofPattern("yyyy-MM");
        }

        // 查询范围内所有 session
        LambdaQueryWrapper<ParkingSession> qw = new LambdaQueryWrapper<>();
        qw.and(w -> w.between(ParkingSession::getEntryTime, start, end)
                .or().between(ParkingSession::getExitTime, start, end));
        if (cmd.getLotId() != null && cmd.getLotId() > 0) qw.eq(ParkingSession::getParkingLotId, cmd.getLotId());
        List<ParkingSession> sessions = parkingSessionMapper.selectList(qw);

        // 构建时段列表
        List<LocalDateTime> slots = new ArrayList<>();
        LocalDateTime cursor = start.truncatedTo(unit);
        while (!cursor.isAfter(end)) {
            slots.add(cursor);
            cursor = cursor.plus(1, unit);
        }

        return slots.stream().map(slot -> {
            LocalDateTime slotEnd = slot.plus(1, unit);
            long entry = sessions.stream().filter(s -> {
                LocalDateTime et = s.getEntryTime();
                return et != null && !et.isBefore(slot) && et.isBefore(slotEnd);
            }).count();
            long exit = sessions.stream().filter(s -> {
                LocalDateTime xt = s.getExitTime();
                return xt != null && !xt.isBefore(slot) && xt.isBefore(slotEnd);
            }).count();
            return new AnalyticsOverviewVO.TrendPoint(slot.format(fmt), entry, exit);
        }).collect(Collectors.toList());
    }
}
```

- [ ] **Step 5: 创建 AnalyticsController**

```java
package com.jushan.platform.modules.parking.controller;

import com.jushan.common.Result;
import com.jushan.framework.auth.RequirePermission;
import com.jushan.framework.auth.TenantContext;
import com.jushan.platform.modules.parking.dto.AnalyticsQueryCmd;
import com.jushan.platform.modules.parking.service.AnalyticsService;
import com.jushan.platform.modules.parking.vo.AnalyticsOverviewVO;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    @RequirePermission("parking:read")
    public Result<AnalyticsOverviewVO> getOverview(
            @RequestParam(required = false) Long lotId,
            @RequestParam(defaultValue = "today") String period,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        AnalyticsQueryCmd cmd = new AnalyticsQueryCmd();
        cmd.setLotId(lotId);
        cmd.setPeriod(period);
        if (startDate != null) cmd.setStartDate(java.time.LocalDate.parse(startDate));
        if (endDate != null) cmd.setEndDate(java.time.LocalDate.parse(endDate));
        return Result.success(analyticsService.getOverview(cmd));
    }
}
```

- [ ] **Step 6: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期：BUILD SUCCESS。如 ParkingSessionMapper 不存在，根据实际 Mapper 路径调整。

- [ ] **Step 7: 提交**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/parking/controller/AnalyticsController.java
git add parking-system/src/main/java/com/jushan/platform/modules/parking/service/AnalyticsService.java
git add parking-system/src/main/java/com/jushan/platform/modules/parking/service/impl/AnalyticsServiceImpl.java
git add parking-system/src/main/java/com/jushan/platform/modules/parking/vo/AnalyticsOverviewVO.java
git add parking-system/src/main/java/com/jushan/platform/modules/parking/dto/AnalyticsQueryCmd.java
git commit -m "feat: 新增运营数据统计接口 /api/v1/admin/analytics/overview"
```

---

### Task 4: 前端 — 安装 ECharts

**Files:**
- Modify: `booth-web/package.json`

- [ ] **Step 1: 安装 ECharts**

```bash
cd booth-web && npm install echarts --save
```

- [ ] **Step 2: 验证安装**

```bash
ls node_modules/echarts/package.json
```

预期：文件存在

- [ ] **Step 3: 提交**

```bash
git add booth-web/package.json booth-web/package-lock.json
git commit -m "chore: 安装 ECharts 图表库"
```

---

### Task 5: 前端 — 更新 API 类型定义

**Files:**
- Modify: `booth-web/src/api/parking-manage.ts`

- [ ] **Step 1: DeviceVO 新增网络字段**

在 `DeviceVO` 接口的 `code` 字段后添加：

```typescript
  ipAddress?: string
  port?: number
  subnetMask?: string
  gateway?: string
```

- [ ] **Step 2: DeviceCreateCmd 新增网络字段**

在 `DeviceCreateCmd` 接口中添加：

```typescript
  ipAddress?: string
  port?: number
  subnetMask?: string
  gateway?: string
```

- [ ] **Step 3: DeviceUpdateCmd 新增网络字段**

在 `DeviceUpdateCmd` 接口中添加：

```typescript
  ipAddress?: string
  port?: number
  subnetMask?: string
  gateway?: string
```

- [ ] **Step 4: 新增 Analytics 类型和接口**

在 `parking-manage.ts` 文件末尾追加：

```typescript
// ============ Analytics (运营数据) ============
export interface TrendPoint {
  time: string
  entry: number
  exit: number
}

export interface AnalyticsOverviewVO {
  entryCount: number
  exitCount: number
  currentInCount: number
  entryTriggerStats: Record<string, number>
  trendData: TrendPoint[]
  revenue: any
}

export interface AnalyticsQueryParams {
  lotId?: number
  period: 'today' | 'month' | 'year' | 'custom'
  startDate?: string
  endDate?: string
}

export function getAnalyticsOverview(params: AnalyticsQueryParams) {
  return request.get<AnalyticsOverviewVO>('/v1/admin/analytics/overview', params)
}
```

- [ ] **Step 5: 提交**

```bash
git add booth-web/src/api/parking-manage.ts
git commit -m "feat: API 类型增加设备网络字段和运营数据接口"
```

---

### Task 6: 前端 — 删除旧页面和路由

**Files:**
- Delete: `booth-web/src/views/operation/ParkingLots.vue`
- Delete: `booth-web/src/views/operation/Lanes.vue`
- Delete: `booth-web/src/views/operation/Devices.vue`
- Modify: `booth-web/src/router/index.ts`
- Modify: `booth-web/src/layout/AdminLayout.vue`

- [ ] **Step 1: 删除旧页面文件**

```bash
rm booth-web/src/views/operation/ParkingLots.vue
rm booth-web/src/views/operation/Lanes.vue
rm booth-web/src/views/operation/Devices.vue
```

- [ ] **Step 2: 更新路由 — 替换三个旧路由为一个新路由**

在 `router/index.ts` 中，将 `/admin` 的 children 中三条旧路由：

```typescript
      {
        path: 'parking-lots',
        name: 'AdminParkingLots',
        component: () => import('@/views/operation/ParkingLots.vue'),
        meta: { title: '车场管理' },
      },
      {
        path: 'lanes',
        name: 'AdminLanes',
        component: () => import('@/views/operation/Lanes.vue'),
        meta: { title: '车道管理' },
      },
      {
        path: 'devices',
        name: 'AdminDevices',
        component: () => import('@/views/operation/Devices.vue'),
        meta: { title: '设备管理' },
      },
```

替换为：

```typescript
      {
        path: 'parking',
        name: 'AdminParking',
        component: () => import('@/views/operation/ParkingManage.vue'),
        meta: { title: '车场管理' },
      },
```

同时在 OperationLayout 的 children 中新增 analytics 路由（放在 dashboard 之后）：

```typescript
      {
        path: 'analytics',
        name: 'OperationAnalytics',
        component: () => import('@/views/operation/Analytics.vue'),
        meta: { title: '运营数据' },
      },
```

- [ ] **Step 3: 更新 AdminLayout 菜单 — 只保留一个「车场管理」入口**

将 `AdminLayout.vue` 的菜单区域替换为：

```vue
        <a-menu-item key="/admin/accounts">
          <template #icon><UserOutlined /></template>
          <span>账号管理 (AD-01)</span>
        </a-menu-item>
        <a-menu-item key="/admin/parking">
          <template #icon><HomeOutlined /></template>
          <span>车场管理 (SA-01)</span>
        </a-menu-item>
```

同时移除不再使用的 icon 导入（`BranchesOutlined`, `ToolOutlined`）：

```typescript
import { UserOutlined, HomeOutlined, LogoutOutlined } from '@ant-design/icons-vue'
```

- [ ] **Step 4: 更新 OperationLayout 菜单 — 新增「运营数据」入口**

在 `OperationLayout.vue` 的菜单中，dashboard 之后添加：

```vue
        <a-menu-item key="/operation/analytics">
          <template #icon><BarChartOutlined /></template>
          <span>运营数据</span>
        </a-menu-item>
```

同时新增 icon 导入：

```typescript
import { DashboardOutlined, CarOutlined, FileTextOutlined, BarChartOutlined, LogoutOutlined } from '@ant-design/icons-vue'
```

- [ ] **Step 5: 提交**

```bash
git add booth-web/src/router/index.ts booth-web/src/layout/AdminLayout.vue booth-web/src/layout/OperationLayout.vue
git add booth-web/src/views/operation/ParkingLots.vue booth-web/src/views/operation/Lanes.vue booth-web/src/views/operation/Devices.vue
git commit -m "feat: 删除旧三页面，合并为车场管理主从页，新增运营数据路由"
```

---

### Task 7: 前端 — 创建车场管理合并页（ParkingManage.vue）

**Files:**
- Create: `booth-web/src/views/operation/ParkingManage.vue`

**Interfaces:**
- Consumes: `getParkingLots`, `createParkingLot`, `updateParkingLot`, `deleteParkingLot`, `updateParkingLotStatus` from `parking-manage.ts`
- Consumes: `getParkingLanes`, `createParkingLane`, `updateParkingLane`, `deleteParkingLane` from `parking-manage.ts`
- Consumes: `getDevices`, `createDevice`, `updateDevice`, `updateDeviceStatus`, `bindDeviceLane`, `unbindDeviceLane`, `getDeviceVendors`, `getDeviceModels` from `parking-manage.ts`

- [ ] **Step 1: 创建主组件结构**

```vue
<template>
  <div class="parking-manage">
    <a-row :gutter="16">
      <!-- 左侧：车场列表 -->
      <a-col :span="6">
        <div class="lot-sidebar">
          <a-input-search
            v-model:value="searchKeyword"
            placeholder="搜索车场..."
            @search="fetchParkingLots"
            style="margin-bottom: 12px"
          />
          <a-list
            :loading="lotLoading"
            :data-source="parkingLots"
            size="small"
          >
            <template #renderItem="{ item }">
              <a-list-item
                :class="['lot-item', { active: selectedLot?.id === item.id }]"
                @click="selectLot(item)"
              >
                <a-list-item-meta>
                  <template #title>
                    <span>{{ item.name }}</span>
                    <a-tag :color="item.status === 'ENABLED' ? 'green' : 'red'" style="margin-left: 8px">
                      {{ item.status === 'ENABLED' ? '启用' : '停用' }}
                    </a-tag>
                  </template>
                  <template #description>
                    {{ item.totalSpaces }} 车位 · {{ item.address || '未填写地址' }}
                  </template>
                </a-list-item-meta>
              </a-list-item>
            </template>
          </a-list>
          <a-button type="dashed" block @click="showCreateLotModal" style="margin-top: 8px">
            <PlusOutlined /> 新增车场
          </a-button>
        </div>
      </a-col>

      <!-- 右侧：Tab 详情 -->
      <a-col :span="18">
        <div v-if="!selectedLot" class="empty-state">
          <a-empty description="请从左侧选择一个车场查看详情" />
        </div>
        <a-tabs v-else v-model:activeKey="activeTab">
          <a-tab-pane key="basic" tab="基本信息">
            <LotBasicInfo :lot="selectedLot" @updated="handleLotUpdated" />
          </a-tab-pane>
          <a-tab-pane key="lane" tab="车道管理">
            <LaneManager :lot-id="selectedLot.id" />
          </a-tab-pane>
          <a-tab-pane key="device" tab="设备管理">
            <DeviceManager :lot-id="selectedLot.id" />
          </a-tab-pane>
        </a-tabs>
      </a-col>
    </a-row>

    <!-- 新增车场弹窗 -->
    <a-modal v-model:open="createLotVisible" title="新增车场" @ok="handleCreateLot" :confirm-loading="createLotLoading">
      <a-form :model="createLotForm" layout="vertical">
        <a-form-item label="车场名称" required>
          <a-input v-model:value="createLotForm.name" placeholder="请输入车场名称" />
        </a-form-item>
        <a-form-item label="总车位数">
          <a-input-number v-model:value="createLotForm.totalSpaces" :min="1" style="width: 100%" />
        </a-form-item>
        <a-form-item label="负责人姓名">
          <a-input v-model:value="createLotForm.contactName" placeholder="请输入负责人姓名" />
        </a-form-item>
        <a-form-item label="联系电话">
          <a-input v-model:value="createLotForm.contactPhone" placeholder="请输入联系电话" />
        </a-form-item>
        <a-form-item label="详细地址">
          <a-textarea v-model:value="createLotForm.address" placeholder="请输入详细地址" :rows="2" />
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, reactive } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getParkingLots, createParkingLot,
  type ParkingLotVO, type ParkingLotCreateCmd
} from '@/api/parking-manage'
import LotBasicInfo from './components/LotBasicInfo.vue'
import LaneManager from './components/LaneManager.vue'
import DeviceManager from './components/DeviceManager.vue'

const searchKeyword = ref('')
const parkingLots = ref<ParkingLotVO[]>([])
const lotLoading = ref(false)
const selectedLot = ref<ParkingLotVO | null>(null)
const activeTab = ref('basic')

// 新增车场
const createLotVisible = ref(false)
const createLotLoading = ref(false)
const createLotForm = reactive<ParkingLotCreateCmd & { contactName?: string; contactPhone?: string }>({
  companyId: 0,
  name: '',
  address: '',
  totalSpaces: undefined,
  contactName: '',
  contactPhone: '',
})

async function fetchParkingLots() {
  lotLoading.value = true
  try {
    const res = await getParkingLots({ page: 1, size: 100, keyword: searchKeyword.value || undefined })
    parkingLots.value = res.records
  } finally {
    lotLoading.value = false
  }
}

function selectLot(lot: ParkingLotVO) {
  selectedLot.value = lot
  activeTab.value = 'basic'
}

function showCreateLotModal() {
  createLotForm.name = ''
  createLotForm.address = ''
  createLotForm.totalSpaces = undefined
  createLotForm.contactName = ''
  createLotForm.contactPhone = ''
  createLotVisible.value = true
}

async function handleCreateLot() {
  if (!createLotForm.name.trim()) {
    message.warning('请输入车场名称')
    return
  }
  createLotLoading.value = true
  try {
    await createParkingLot({
      companyId: createLotForm.companyId,
      name: createLotForm.name,
      address: createLotForm.address,
      totalSpaces: createLotForm.totalSpaces,
    })
    message.success('车场创建成功')
    createLotVisible.value = false
    await fetchParkingLots()
  } finally {
    createLotLoading.value = false
  }
}

function handleLotUpdated(lot: ParkingLotVO) {
  selectedLot.value = lot
  fetchParkingLots()
}

onMounted(() => {
  fetchParkingLots()
})
</script>

<style lang="scss" scoped>
.parking-manage { height: 100%; }
.lot-sidebar { border-right: 1px solid #f0f0f0; padding-right: 8px; height: 100%; overflow-y: auto; }
.lot-item { cursor: pointer; transition: background 0.2s; }
.lot-item:hover { background: #f5f5f5; }
.lot-item.active { background: #e6f7ff; border-left: 3px solid #1890ff; }
.empty-state { display: flex; align-items: center; justify-content: center; height: 400px; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add booth-web/src/views/operation/ParkingManage.vue
git commit -m "feat: 创建车场管理合并页主框架（左侧列表 + 右侧三Tab）"
```

---

### Task 8: 前端 — 创建基本信息子组件（LotBasicInfo.vue）

**Files:**
- Create: `booth-web/src/views/operation/components/LotBasicInfo.vue`

- [ ] **Step 1: 创建组件**

```vue
<template>
  <div class="lot-basic-info">
    <a-form :model="form" layout="vertical" style="max-width: 600px">
      <a-form-item label="车场名称">
        <a-input v-model:value="form.name" />
      </a-form-item>
      <a-form-item label="总车位数">
        <a-input-number v-model:value="form.totalSpaces" :min="0" style="width: 100%" />
      </a-form-item>
      <a-form-item label="负责人姓名">
        <a-input v-model:value="form.contactName" placeholder="请输入负责人姓名" />
      </a-form-item>
      <a-form-item label="联系电话">
        <a-input v-model:value="form.contactPhone" placeholder="请输入联系电话" />
      </a-form-item>
      <a-form-item label="详细地址">
        <a-textarea v-model:value="form.address" placeholder="请输入详细地址" :rows="2" />
      </a-form-item>
      <a-form-item label="状态">
        <a-switch
          :checked="form.status === 'ENABLED'"
          checked-children="启用"
          un-checked-children="停用"
          @change="handleStatusChange"
        />
      </a-form-item>
      <a-form-item>
        <a-button type="primary" :loading="saving" @click="handleSave">保存</a-button>
      </a-form-item>
    </a-form>
  </div>
</template>

<script setup lang="ts">
import { reactive, watch } from 'vue'
import { message } from 'ant-design-vue'
import { updateParkingLot, updateParkingLotStatus, type ParkingLotVO } from '@/api/parking-manage'

const props = defineProps<{ lot: ParkingLotVO }>()
const emit = defineEmits<{ updated: [lot: ParkingLotVO] }>()

const saving = ref(false)

const form = reactive({
  name: '',
  address: '',
  totalSpaces: 0,
  contactName: '',
  contactPhone: '',
  status: 'ENABLED' as string,
})

watch(() => props.lot, (lot) => {
  if (lot) {
    form.name = lot.name || ''
    form.address = lot.address || ''
    form.totalSpaces = lot.totalSpaces || 0
    form.contactName = lot.contactName || ''
    form.contactPhone = lot.contactPhone || ''
    form.status = lot.status
  }
}, { immediate: true })

async function handleSave() {
  saving.value = true
  try {
    const updated = await updateParkingLot(props.lot.id, {
      name: form.name,
      address: form.address,
      totalSpaces: form.totalSpaces,
    })
    message.success('保存成功')
    emit('updated', updated)
  } finally {
    saving.value = false
  }
}

async function handleStatusChange(checked: boolean) {
  const action = checked ? 'ENABLED' : 'DISABLED'
  try {
    await updateParkingLotStatus(props.lot.id, action)
    form.status = action
    message.success(checked ? '车场已启用' : '车场已停用')
    emit('updated', { ...props.lot, status: action })
  } catch {
    form.status = props.lot.status // 恢复
  }
}
</script>
```

需要补充 `import { ref } from 'vue'`：

```typescript
import { reactive, ref, watch } from 'vue'
```

- [ ] **Step 2: 提交**

```bash
git add booth-web/src/views/operation/components/LotBasicInfo.vue
git commit -m "feat: 车场基本信息编辑组件"
```

---

### Task 9: 前端 — 创建车道管理子组件（LaneManager.vue）

**Files:**
- Create: `booth-web/src/views/operation/components/LaneManager.vue`

- [ ] **Step 1: 创建组件（含设备配置）**

```vue
<template>
  <div class="lane-manager">
    <div class="toolbar">
      <span class="title">车道列表</span>
      <a-button type="primary" size="small" @click="showCreateModal"><PlusOutlined /> 新增车道</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="lanes"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'type'">
          <a-tag>{{ record.typeLabel }}</a-tag>
        </template>
        <template v-if="column.key === 'gateMode'">
          <a-tag :color="gateModeColor(record.gateMode)">{{ record.gateModeLabel }}</a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 1 ? 'green' : 'red'">{{ record.status === 1 ? '启用' : '停用' }}</a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
          <a-popconfirm title="确定删除此车道？" @confirm="handleDelete(record.id)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑车道弹窗 -->
    <a-modal
      v-model:open="modalVisible"
      :title="editingLane ? '编辑车道' : '新增车道'"
      @ok="handleSave"
      :confirm-loading="saving"
      width="640px"
    >
      <a-form :model="laneForm" layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="车道编号" required>
              <a-input v-model:value="laneForm.laneNo" placeholder="如 A1" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="车道名称" required>
              <a-input v-model:value="laneForm.name" placeholder="如 东门入口" />
            </a-form-item>
          </a-col>
        </a-row>
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="出入口类型" required>
              <a-select v-model:value="laneForm.type" :options="directionOptions" @change="onDirectionChange" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="闸机模式">
              <a-select v-model:value="laneForm.gateMode" :options="gateModeOptions" />
            </a-form-item>
          </a-col>
        </a-row>

        <!-- 设备配置区域 -->
        <a-divider>设备配置</a-divider>
        <a-tabs v-model:activeKey="deviceTab" v-if="laneForm.type !== undefined">
          <!-- 入口相机（入口和双向都显示） -->
          <a-tab-pane
            v-if="laneForm.type === 1 || laneForm.type === 3"
            key="entry"
            tab="入口相机"
          >
            <DeviceFormFields v-model="entryDeviceForm" />
          </a-tab-pane>
          <!-- 出口相机（出口和双向都显示） -->
          <a-tab-pane
            v-if="laneForm.type === 2 || laneForm.type === 3"
            key="exit"
            tab="出口相机"
          >
            <DeviceFormFields v-model="exitDeviceForm" />
          </a-tab-pane>
        </a-tabs>
        <div v-else style="color: #999; text-align: center; padding: 20px;">
          请先选择出入口类型
        </div>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getParkingLanes, createParkingLane, updateParkingLane, deleteParkingLane,
  createDevice, type ParkingLaneVO
} from '@/api/parking-manage'
import DeviceFormFields from './DeviceFormFields.vue'
import type { DeviceFormData } from './DeviceFormFields.vue'

const props = defineProps<{ lotId: number }>()

const lanes = ref<ParkingLaneVO[]>([])
const loading = ref(false)
const modalVisible = ref(false)
const saving = ref(false)
const editingLane = ref<ParkingLaneVO | null>(null)
const deviceTab = ref('entry')

const directionOptions = [
  { value: 1, label: '入口' },
  { value: 2, label: '出口' },
  { value: 3, label: '双向' },
]

const gateModeOptions = [
  { value: 'AUTO', label: '自动 (AUTO)' },
  { value: 'ALWAYS_OPEN', label: '常开 (ALWAYS_OPEN)' },
  { value: 'ALWAYS_CLOSE', label: '常关 (ALWAYS_CLOSE)' },
]

const laneForm = reactive({
  name: '',
  laneNo: '',
  type: undefined as number | undefined,
  gateMode: 'AUTO' as string,
})

// 设备表单数据（使用 ref 兼容 DeviceFormFields 的 defineModel）
const entryDeviceForm = ref<DeviceFormData>(getDefaultDeviceForm(1))
const exitDeviceForm = ref<DeviceFormData>(getDefaultDeviceForm(2))

function getDefaultDeviceForm(direction: number) {
  return {
    name: direction === 1 ? '入口相机' : '出口相机',
    deviceSn: '',
    vendorId: undefined as number | undefined,
    modelId: undefined as number | undefined,
    ipAddress: '',
    port: 80,
    subnetMask: '',
    gateway: '',
    deviceType: 'CAMERA',
    recognitionDirection: direction,
  }
}

function gateModeColor(mode: string) {
  const map: Record<string, string> = { AUTO: 'blue', ALWAYS_OPEN: 'green', ALWAYS_CLOSE: 'orange' }
  return map[mode] || 'default'
}

const columns = [
  { title: '车道编号', dataIndex: 'laneNo', key: 'laneNo' },
  { title: '车道名称', dataIndex: 'name', key: 'name' },
  { title: '类型', key: 'type' },
  { title: '闸机模式', key: 'gateMode' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 150 },
]

async function fetchLanes() {
  loading.value = true
  try {
    const res = await getParkingLanes({ page: 1, size: 100, parkingLotId: props.lotId })
    lanes.value = res.records
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editingLane.value = null
  laneForm.name = ''
  laneForm.laneNo = ''
  laneForm.type = undefined
  laneForm.gateMode = 'AUTO'
  deviceTab.value = 'entry'
  resetDeviceForms()
  modalVisible.value = true
}

function showEditModal(lane: ParkingLaneVO) {
  editingLane.value = lane
  laneForm.name = lane.name
  laneForm.laneNo = lane.laneNo
  laneForm.type = lane.type
  laneForm.gateMode = lane.gateMode
  deviceTab.value = lane.type === 2 ? 'exit' : 'entry'
  resetDeviceForms()
  modalVisible.value = true
}

function resetDeviceForms() {
  entryDeviceForm.value = getDefaultDeviceForm(1)
  exitDeviceForm.value = getDefaultDeviceForm(2)
}

function onDirectionChange() {
  if (laneForm.type === 2) {
    deviceTab.value = 'exit'
  } else {
    deviceTab.value = 'entry'
  }
}

async function handleSave() {
  if (!laneForm.name.trim() || !laneForm.laneNo.trim() || laneForm.type === undefined) {
    message.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    let laneId: number
    if (editingLane.value) {
      const updated = await updateParkingLane(editingLane.value.id, {
        name: laneForm.name,
        laneNo: laneForm.laneNo,
        type: laneForm.type,
        gateMode: laneForm.gateMode,
      })
      laneId = updated.id
    } else {
      const created = await createParkingLane({
        lotId: props.lotId,
        name: laneForm.name,
        laneNo: laneForm.laneNo,
        type: laneForm.type,
        gateMode: laneForm.gateMode,
      })
      laneId = created.id
    }

    // 仅新建车道时同时创建设备（编辑时不处理设备）
    const isNew = !editingLane.value
    if (isNew) {
      if (laneForm.type === 1 || laneForm.type === 3) {
        await createDeviceForLane(entryDeviceForm.value, laneId, props.lotId)
      }
      if (laneForm.type === 2 || laneForm.type === 3) {
        await createDeviceForLane(exitDeviceForm.value, laneId, props.lotId)
      }
    }

    message.success(editingLane.value ? '车道更新成功' : '车道创建成功')
    modalVisible.value = false
    await fetchLanes()
  } finally {
    saving.value = false
  }
}

async function createDeviceForLane(form: any, laneId: number, lotId: number) {
  if (!form.deviceSn.trim() && !form.name.trim()) return
  await createDevice({
    parkingLotId: lotId,
    vendorId: form.vendorId || 0,
    modelId: form.modelId || 0,
    name: form.name,
    code: form.deviceSn,
    deviceSn: form.deviceSn,
    deviceType: 'CAMERA',
    laneId,
    ipAddress: form.ipAddress,
    port: form.port,
    subnetMask: form.subnetMask,
    gateway: form.gateway,
  })
}

async function handleDelete(id: number) {
  await deleteParkingLane(id)
  message.success('车道已删除')
  await fetchLanes()
}

onMounted(() => fetchLanes())
</script>

<style lang="scss" scoped>
.lane-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add booth-web/src/views/operation/components/LaneManager.vue
git commit -m "feat: 车道管理子组件（含动态设备配置表单）"
```

---

### Task 10: 前端 — 创建设备表单子组件（DeviceFormFields.vue）

**Files:**
- Create: `booth-web/src/views/operation/components/DeviceFormFields.vue`

- [ ] **Step 1: 创建组件**

```vue
<template>
  <div class="device-form-fields">
    <a-row :gutter="16">
      <a-col :span="12">
        <a-form-item label="设备名称">
          <a-input v-model:value="model.name" placeholder="如 东门入口相机" />
        </a-form-item>
      </a-col>
      <a-col :span="12">
        <a-form-item label="相机序列号">
          <a-input v-model:value="model.deviceSn" placeholder="厂商序列号" />
        </a-form-item>
      </a-col>
    </a-row>
    <a-row :gutter="16">
      <a-col :span="12">
        <a-form-item label="设备厂商">
          <a-select v-model:value="model.vendorId" :options="vendorOptions" placeholder="选择厂商" />
        </a-form-item>
      </a-col>
      <a-col :span="12">
        <a-form-item label="设备型号">
          <a-select v-model:value="model.modelId" :options="modelOptions" placeholder="如 臻识 C5" />
        </a-form-item>
      </a-col>
    </a-row>
    <a-divider style="margin: 8px 0">网络配置</a-divider>
    <a-row :gutter="16">
      <a-col :span="8">
        <a-form-item label="IP 地址">
          <a-input v-model:value="model.ipAddress" placeholder="192.168.1.100" />
        </a-form-item>
      </a-col>
      <a-col :span="4">
        <a-form-item label="端口">
          <a-input-number v-model:value="model.port" :min="1" :max="65535" style="width: 100%" />
        </a-form-item>
      </a-col>
      <a-col :span="6">
        <a-form-item label="子网掩码">
          <a-input v-model:value="model.subnetMask" placeholder="255.255.255.0" />
        </a-form-item>
      </a-col>
      <a-col :span="6">
        <a-form-item label="网关地址">
          <a-input v-model:value="model.gateway" placeholder="192.168.1.1" />
        </a-form-item>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { getDeviceVendors, getDeviceModels, type DeviceVendor, type DeviceModel } from '@/api/parking-manage'

export interface DeviceFormData {
  name: string
  deviceSn: string
  vendorId?: number
  modelId?: number
  ipAddress: string
  port: number
  subnetMask: string
  gateway: string
  deviceType: string
  recognitionDirection: number
}

// Vue 3.4+ defineModel — 双向绑定自动同步，避免 deep watch 循环
const model = defineModel<DeviceFormData>({ required: true })

const vendors = ref<DeviceVendor[]>([])
const models = ref<DeviceModel[]>([])

const vendorOptions = computed(() => vendors.value.map(v => ({ value: v.id, label: v.name })))
const modelOptions = computed(() => models.value.map(m => ({ value: m.id, label: m.name })))

onMounted(async () => {
  vendors.value = await getDeviceVendors()
  models.value = await getDeviceModels()
})
</script>
```

- [ ] **Step 2: 提交**

```bash
git add booth-web/src/views/operation/components/DeviceFormFields.vue
git commit -m "feat: 设备表单子组件（含网络配置字段）"
```

---

### Task 11: 前端 — 创建设备管理子组件（DeviceManager.vue）

**Files:**
- Create: `booth-web/src/views/operation/components/DeviceManager.vue`

- [ ] **Step 1: 创建组件**

```vue
<template>
  <div class="device-manager">
    <div class="toolbar">
      <span class="title">设备列表</span>
      <a-button type="primary" size="small" @click="showCreateModal"><PlusOutlined /> 新增设备</a-button>
    </div>
    <a-table
      :columns="columns"
      :data-source="devices"
      :loading="loading"
      :pagination="false"
      row-key="id"
      size="small"
    >
      <template #bodyCell="{ column, record }">
        <template v-if="column.key === 'deviceType'">
          <a-tag :color="record.deviceType === 'CAMERA' ? 'blue' : 'orange'">
            {{ record.deviceType === 'CAMERA' ? '相机' : '闸机' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'status'">
          <a-tag :color="record.status === 'ENABLED' ? 'green' : 'red'">
            {{ record.status === 'ENABLED' ? '启用' : '停用' }}
          </a-tag>
        </template>
        <template v-if="column.key === 'action'">
          <a-button type="link" size="small" @click="showEditModal(record)">编辑</a-button>
          <a-popconfirm title="确定删除此设备？" @confirm="handleDelete(record.id)">
            <a-button type="link" size="small" danger>删除</a-button>
          </a-popconfirm>
        </template>
      </template>
    </a-table>

    <!-- 新增/编辑设备弹窗 -->
    <a-modal v-model:open="modalVisible" :title="editing ? '编辑设备' : '新增设备'" @ok="handleSave" :confirm-loading="saving" width="640px">
      <a-form layout="vertical">
        <a-row :gutter="16">
          <a-col :span="12">
            <a-form-item label="设备名称" required>
              <a-input v-model:value="deviceForm.name" />
            </a-form-item>
          </a-col>
          <a-col :span="12">
            <a-form-item label="相机序列号" required>
              <a-input v-model:value="deviceForm.deviceSn" />
            </a-form-item>
          </a-col>
        </a-row>
        <DeviceFormFields v-model="deviceFormFields" />
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { PlusOutlined } from '@ant-design/icons-vue'
import { message } from 'ant-design-vue'
import {
  getDevices, createDevice, updateDevice, updateDeviceStatus, deleteDevice,
  type DeviceVO
} from '@/api/parking-manage'
import DeviceFormFields, { type DeviceFormData } from './DeviceFormFields.vue'

const props = defineProps<{ lotId: number }>()

const devices = ref<DeviceVO[]>([])
const loading = ref(false)
const modalVisible = ref(false)
const saving = ref(false)
const editing = ref<DeviceVO | null>(null)

const deviceForm = reactive({
  name: '',
  deviceSn: '',
  vendorId: undefined as number | undefined,
  modelId: undefined as number | undefined,
})

const deviceFormFields = ref<DeviceFormData>({
  name: '',
  deviceSn: '',
  vendorId: undefined,
  modelId: undefined,
  ipAddress: '',
  port: 80,
  subnetMask: '',
  gateway: '',
  deviceType: 'CAMERA',
  recognitionDirection: 1,
})

const columns = [
  { title: '设备名称', dataIndex: 'name', key: 'name' },
  { title: '类型', key: 'deviceType' },
  { title: '序列号', dataIndex: 'deviceSn', key: 'deviceSn' },
  { title: '绑定车道', dataIndex: 'laneName', key: 'laneName' },
  { title: '状态', key: 'status' },
  { title: '操作', key: 'action', width: 150 },
]

async function fetchDevices() {
  loading.value = true
  try {
    const res = await getDevices({ page: 1, size: 100, parkingLotId: props.lotId })
    devices.value = res.records
  } finally {
    loading.value = false
  }
}

function showCreateModal() {
  editing.value = null
  deviceForm.name = ''
  deviceForm.deviceSn = ''
  deviceForm.vendorId = undefined
  deviceForm.modelId = undefined
  Object.assign(deviceFormFields.value, {
    name: '', deviceSn: '', vendorId: undefined, modelId: undefined,
    ipAddress: '', port: 80, subnetMask: '', gateway: '',
    deviceType: 'CAMERA', recognitionDirection: 1,
  })
  modalVisible.value = true
}

function showEditModal(device: DeviceVO) {
  editing.value = device
  deviceForm.name = device.name
  deviceForm.deviceSn = device.deviceSn
  modalVisible.value = true
}

async function handleSave() {
  if (!deviceForm.name.trim() || !deviceForm.deviceSn.trim()) {
    message.warning('请填写必填项')
    return
  }
  saving.value = true
  try {
    if (editing.value) {
      await updateDevice(editing.value.id, {
        name: deviceForm.name,
        ipAddress: deviceFormFields.value.ipAddress,
        port: deviceFormFields.value.port,
        subnetMask: deviceFormFields.value.subnetMask,
        gateway: deviceFormFields.value.gateway,
      })
      message.success('设备更新成功')
    } else {
      await createDevice({
        parkingLotId: props.lotId,
        vendorId: deviceFormFields.value.vendorId || 0,
        modelId: deviceFormFields.value.modelId || 0,
        name: deviceForm.name,
        code: deviceForm.deviceSn,
        deviceSn: deviceForm.deviceSn,
        deviceType: 'CAMERA',
        ipAddress: deviceFormFields.value.ipAddress,
        port: deviceFormFields.value.port,
        subnetMask: deviceFormFields.value.subnetMask,
        gateway: deviceFormFields.value.gateway,
      })
      message.success('设备创建成功')
    }
    modalVisible.value = false
    await fetchDevices()
  } finally {
    saving.value = false
  }
}

async function handleDelete(id: number) {
  // 先禁用再提示（后端无 physical delete for device）
  await updateDeviceStatus(id, 'DISABLED')
  message.success('设备已停用')
  await fetchDevices()
}

onMounted(() => fetchDevices())
</script>

<style lang="scss" scoped>
.device-manager { }
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.toolbar .title { font-weight: 600; font-size: 14px; }
</style>
```

- [ ] **Step 2: 提交**

```bash
git add booth-web/src/views/operation/components/DeviceManager.vue
git commit -m "feat: 设备管理子组件（列表 + 新增/编辑弹窗）"
```

---

### Task 12: 前端 — 创建运营数据页（Analytics.vue）

**Files:**
- Create: `booth-web/src/views/operation/Analytics.vue`
- Create: `booth-web/src/composables/useChart.ts`

- [ ] **Step 1: 创建 ECharts composable**

```typescript
// booth-web/src/composables/useChart.ts
import * as echarts from 'echarts'
import { ref, onMounted, onBeforeUnmount, watch, type Ref } from 'vue'

export function useChart(
  containerRef: Ref<HTMLElement | null>,
  optionsFn: () => echarts.EChartsOption
) {
  const chart = ref<echarts.ECharts | null>(null)

  function initChart() {
    if (!containerRef.value) return
    chart.value = echarts.init(containerRef.value)
    chart.value.setOption(optionsFn())
  }

  function resize() {
    chart.value?.resize()
  }

  function updateChart() {
    chart.value?.setOption(optionsFn(), true)
  }

  onMounted(() => {
    initChart()
    window.addEventListener('resize', resize)
  })

  onBeforeUnmount(() => {
    window.removeEventListener('resize', resize)
    chart.value?.dispose()
  })

  return { chart, updateChart }
}
```

- [ ] **Step 2: 创建 Analytics.vue**

```vue
<template>
  <div class="analytics-page">
    <!-- 筛选栏 -->
    <a-card size="small" style="margin-bottom: 16px">
      <a-row :gutter="16" align="middle">
        <a-col :span="6">
          <a-select
            v-model:value="query.lotId"
            :options="lotOptions"
            placeholder="选择停车场"
            style="width: 100%"
            @change="fetchData"
          />
        </a-col>
        <a-col :span="10">
          <a-radio-group v-model:value="query.period" button-style="solid" @change="onPeriodChange">
            <a-radio-button value="today">今日</a-radio-button>
            <a-radio-button value="month">本月</a-radio-button>
            <a-radio-button value="year">今年</a-radio-button>
            <a-radio-button value="custom">自定义</a-radio-button>
          </a-radio-group>
        </a-col>
        <a-col :span="8" v-if="query.period === 'custom'">
          <a-range-picker v-model:value="customRange" @change="onCustomRangeChange" style="width: 100%" />
        </a-col>
      </a-row>
    </a-card>

    <!-- KPI 卡片 -->
    <a-row :gutter="16" style="margin-bottom: 16px">
      <a-col :span="4">
        <a-statistic title="入场总数" :value="data?.entryCount ?? '-'" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="出场总数" :value="data?.exitCount ?? '-'" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="当前在场" :value="data?.currentInCount ?? '-'" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="实收总额" value="即将上线" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="临停营收" value="即将上线" />
      </a-col>
      <a-col :span="4">
        <a-statistic title="固定车营收" value="即将上线" />
      </a-col>
    </a-row>

    <!-- 图表区 -->
    <a-row :gutter="16">
      <a-col :span="14">
        <a-card title="车流量趋势" size="small">
          <div ref="trendChartRef" style="height: 320px" />
        </a-card>
      </a-col>
      <a-col :span="10">
        <a-card title="入场方式分布" size="small">
          <div ref="pieChartRef" style="height: 320px" />
        </a-card>
      </a-col>
    </a-row>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, watch, nextTick } from 'vue'
import { message } from 'ant-design-vue'
import dayjs, { type Dayjs } from 'dayjs'
import * as echarts from 'echarts'
import { getAnalyticsOverview, getParkingLots, type AnalyticsOverviewVO, type ParkingLotVO } from '@/api/parking-manage'

const data = ref<AnalyticsOverviewVO | null>(null)

// 车场下拉
const lotOptions = ref<{ value: number | undefined; label: string }[]>([])
async function loadLotOptions() {
  const res = await getParkingLots({ page: 1, size: 200 })
  const options = [{ value: undefined, label: '全部车场' }]
  res.records.forEach((lot: ParkingLotVO) => options.push({ value: lot.id, label: lot.name }))
  lotOptions.value = options
}

const query = reactive({
  lotId: undefined as number | undefined,
  period: 'today' as string,
  startDate: undefined as string | undefined,
  endDate: undefined as string | undefined,
})

const customRange = ref<[Dayjs, Dayjs] | null>(null)

function onPeriodChange() {
  if (query.period !== 'custom') {
    customRange.value = null
    query.startDate = undefined
    query.endDate = undefined
    fetchData()
  }
}

function onCustomRangeChange() {
  if (customRange.value) {
    query.startDate = customRange.value[0].format('YYYY-MM-DD')
    query.endDate = customRange.value[1].format('YYYY-MM-DD')
    fetchData()
  }
}

// ECharts refs
const trendChartRef = ref<HTMLElement | null>(null)
const pieChartRef = ref<HTMLElement | null>(null)
let trendChart: echarts.ECharts | null = null
let pieChart: echarts.ECharts | null = null

async function fetchData() {
  try {
    data.value = await getAnalyticsOverview({
      lotId: query.lotId,
      period: query.period as any,
      startDate: query.startDate,
      endDate: query.endDate,
    })
    await nextTick()
    renderTrendChart()
    renderPieChart()
  } catch {
    message.error('加载数据失败')
  }
}

function renderTrendChart() {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const d = data.value
  if (!d) return
  trendChart.setOption({
    tooltip: { trigger: 'axis' },
    legend: { data: ['入场', '出场'] },
    xAxis: { type: 'category', data: d.trendData.map((t: any) => t.time) },
    yAxis: { type: 'value' },
    series: [
      { name: '入场', type: 'line', data: d.trendData.map((t: any) => t.entry), smooth: true, color: '#1890ff' },
      { name: '出场', type: 'line', data: d.trendData.map((t: any) => t.exit), smooth: true, color: '#52c41a' },
    ],
  }, true)
}

function renderPieChart() {
  if (!pieChartRef.value) return
  if (!pieChart) pieChart = echarts.init(pieChartRef.value)
  const d = data.value
  if (!d) return
  const stats = d.entryTriggerStats || {}
  const pieData = [
    { name: '白名单自动', value: stats.whitelist_auto || 0 },
    { name: '人工放行', value: stats.manual_open || 0 },
    { name: '常开时段', value: stats.always_open_period || 0 },
    { name: '手动补录', value: stats.manual_entry || 0 },
  ].filter(item => item.value > 0)
  pieChart.setOption({
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie', radius: ['40%', '70%'],
      data: pieData,
      label: { formatter: '{b}\n{d}%' },
      color: ['#1890ff', '#faad14', '#52c41a', '#bfbfbf'],
    }],
  }, true)
}

function handleResize() {
  trendChart?.resize()
  pieChart?.resize()
}

onMounted(async () => {
  await loadLotOptions()
  await fetchData()
  window.addEventListener('resize', handleResize)
})
</script>

<style lang="scss" scoped>
.analytics-page { }
</style>
```

需要补充清理逻辑：

```typescript
import { onBeforeUnmount } from 'vue'

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})
```

- [ ] **Step 3: 提交**

```bash
git add booth-web/src/views/operation/Analytics.vue booth-web/src/composables/useChart.ts
git commit -m "feat: 运营数据页（KPI 卡片 + ECharts 折线图/饼图）"
```

---

### Task 13: 清理旧文档

**Files:**
- Delete: `docs/平台开发/差距分析报告_v1.1.md` (已在 git status 显示为已删除)
- Delete: `docs/平台开发/开发任务提示词包_v1.2.md` (已删除)
- Delete: `docs/平台开发/需求规格说明书_v1.2.md` (已删除)
- Check: 是否有其他 V1.2 残留引用

- [ ] **Step 1: 确认旧文档已删除**

```bash
ls docs/平台开发/ 2>/dev/null || echo "目录已空或不存在"
```

- [ ] **Step 2: 如有残留 V1.2 文档，删除**

```bash
find docs/ -name "*v1.2*" -o -name "*V1.2*" 2>/dev/null
```

- [ ] **Step 3: 提交**

```bash
git add docs/
git commit -m "chore: 清理旧 V1.2 文档"
```

---

### Task 14: 重写 AGENTS.md 和 CLAUDE.md

**Files:**
- Modify: `AGENTS.md`
- Modify: `CLAUDE.md`

- [ ] **Step 1: 更新 CLAUDE.md**

保持简洁，只保留引用：

```markdown
# CLAUDE.md — 停车SaaS系统

@AGENTS.md

## Claude Code 特有指令
- 使用 Plan 模式处理复杂任务（>3 个文件修改）
```

- [ ] **Step 2: 更新 AGENTS.md**

在现有 AGENTS.md 中更新功能清单部分，反映合并后的结构：

1. 在「三、功能编号」的 SA 区域，将 SA-01/02/03 合并：
   - SA-01: 车场管理（含车场/车道/设备统一配置页面）
   - 新增 SA-06: 运营数据分析

2. 在「具体功能清单」中：
   - SA-01 改为：车场管理（统一页面：车场信息 + 车道配置 + 设备绑定，左侧车场列表右侧三 Tab）
   - 删除独立的 SA-02、SA-03 条目
   - 新增 SA-06：运营数据分析（车流量统计、入场方式分布、KPI 卡片、ECharts 图表）

3. 更新「八、技术栈与规范」中 booth-web 依赖：
   - 新增 ECharts 5.x

4. 新增验收标准 AC-14 至 AC-22（从设计文档复制）

- [ ] **Step 3: 提交**

```bash
git add AGENTS.md CLAUDE.md
git commit -m "docs: 重写 AGENTS.md/CLAUDE.md，合并 SA-01/02/03，新增 SA-06"
```

---

### Task 15: 全量编译验证

- [ ] **Step 1: 后端编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：BUILD SUCCESS

- [ ] **Step 2: 前端 type-check + build**

```bash
cd booth-web && npm run build
```

预期：构建成功，无 TypeScript 错误

- [ ] **Step 3: 如有错误，逐一修复后重新验证，然后提交**

```bash
git add -A
git commit -m "fix: 编译和类型错误修复"
```
