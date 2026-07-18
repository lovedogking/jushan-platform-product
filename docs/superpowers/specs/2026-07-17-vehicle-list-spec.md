# 黑白名单独立实体设计规格说明书（任务包 3-3）

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| **文档名称** | 黑白名单独立实体设计规格说明书 |
| **版本号** | V1.0 |
| **编写日期** | 2026-07-17 |
| **对应任务包** | 3-3：黑白名单独立实体 + 互斥 + 类型字典 + 有效期 |
| **需求依据** | 需求规格说明书 V1.2 §3.1.8（ADMIN-008）、§7.4-3/4、确认项 11/32 |
| **前置依赖** | 任务包 3-1 月卡独立实体（monthly_pass 已上线）；任务包 3-2 固定车位独立实体（fixed_space 已上线） |

---

## 1. Goal

新建 `vehicle_list` 独立实体表，将当前散落在 `AccessPolicy`（`policy_type=BLACKLIST`）和 `sys_vehicle`（`vehicle_type=BLACKLIST / VIP / SUPER / FREE`）中的黑白名单数据统一管理。实现同车场同车牌黑白名单互斥校验、黑名单触发类型字典（欠费类 / 管理类 / 其他类）、有效期过期机制、以及进出场判定链路的完整切换（白名单自动放行不计费、黑名单按全局参数 `blacklist.trigger_mode` 执行禁止入场 / 允许但告警 / 按类型区分三种模式）。存量数据通过 Flyway 迁移脚本一次性迁入新表，旧口径（`sys_vehicle.vehicle_type` 黑白名单相关常量、`AccessPolicy` 黑白名单策略）在判定链路中下线但保留代码与数据不删除。

---

## 2. Non-Goals

- **不做优惠券/积分联动**：黑白名单车辆不参与优惠券/积分系统，入场判定链路不调用任何优惠券服务。
- **不做超时自动拉黑功能**：`OverstayBlacklistScheduler` 二期评估，本期仅将其默认关闭（配置项 `jushan.overstay-blacklist.enabled` 改为 `false`）并添加 `@ConditionalOnProperty` 开关注解，代码主体保留不删不改。
- **不做黑名单跨车场同步**：黑名单/白名单仅在指定的单个 `parking_lot_id` 生效，不做批量车场分配或全局名单。
- **不做名单变更审批流**：运营端增删改直接生效，不设审批状态。
- **不做名单变更通知（短信/推送）**：名单变更仅影响进出场判定，不主动通知车主。
- **不改造小程序端**：本期仅运营端（admin-web）名单管理页改造和小程序端进出场判定链路适配，小程序用户端无新增黑白名单相关界面。

---

## 3. Context

### 3.1 现状问题

当前黑白名单数据散布在两条路径中，维护口径不统一且存在数据冲突：

```
现状架构：
  黑名单：
    ├── AccessPolicy (policy_type=BLACKLIST) → policy_key=车牌, policy_value=备注
    └── sys_vehicle (vehicle_type=BLACKLIST)  → 同表含有效期/状态
  白名单：
    └── sys_vehicle (vehicle_type=VIP/SUPER/FREE) → 与月租/储值/固定车位混在同表
  判定链路：
    VehicleTypeDecisionServiceImpl.applyPriorityChain()
    → sys_vehicle.vehicleType  switch-case
    → AccessPolicy  BLACKLIST 无判定读取（仅存储，判定仅靠 sys_vehicle）
```

**问题清单**：
1. **无互斥**：同一车牌可在 `AccessPolicy` 为黑名单同时 `sys_vehicle.vehicleType=VIP` 为白名单，判定链路仅读取 `sys_vehicle`，`AccessPolicy` 黑名单实际未生效。
2. **无触发类型**：黑名单统一按"禁止入场"处理，无欠费类/管理类/其他类的区分能力，岗亭无法获知黑名单原因。
3. **无有效期**：名单永久生效，无法配置"限时黑名单"或"限时白名单"。
4. **职责混乱**：`sys_vehicle` 承载月租/储值/贵宾/黑名单/免费车五种截然不同的业务语义，字段大量冗余（如黑名单记录无意义的 `prepaidBalance`）。
5. **自动拉黑超纲**：`OverstayBlacklistScheduler` 默认开启，但 V1.1 需求未要求此功能。

### 3.2 现有链路分析

当前 `VehicleTypeDecisionServiceImpl.decide()` 优先级链（从高到低）：

```
0. monthly_pass（月卡，新体系，任务包 3-1 已上线）
1. sys_vehicle.vehicleType=BLACKLIST → allowEntry=false, allowExit=false
2. sys_vehicle.vehicleType=SUPER     → allowEntry=true, needCharge=false
3. sys_vehicle.vehicleType=VIP       → allowEntry=true, needCharge=false
4. sys_vehicle.vehicleType=PREPAID   → allowEntry=true, needCharge=true
5. FIXED_SPACE（固定车位，任务包 3-2 已上线）
6. sys_vehicle.vehicleType=FREE      → allowEntry=true, needCharge=false
7. TEMP（默认）
```

**入口点**：
- `EntryService.handleEntry()` 中 `createPreOrderIfChargeable()` 调用 `vehicleTypeDecisionService.decide()` 判定是否需要生成预订单。
- `EntryService` 主流程本身不直接判定车辆类型——开闸/拒入逻辑当前分散在上游 Consumer 和 DuplicateEntryHandler 中，**本期需在 EntryService 中新增黑白名单拦截逻辑**。

`ExitService.handleExit()` 中出场判定通过 `resolveReleaseDecision()` 基于订单状态和 `exit.unpaid_strategy` 参数决定放行/拦截/欠费放行，**不直接调用 VehicleTypeDecisionService**。本期需在出场链路增加黑白名单判定。

### 3.3 依赖的现有组件

| 组件 | 类 | 本期用途 |
| :--- | :--- | :--- |
| 缓存 | `RedisParamCacheStore`（`StringRedisTemplate` 包装） | 车辆名单缓存读写，遵循项目现有 Redis 优雅降级模式（无 Redis 时进程内 `ConcurrentHashMap` 兜底） |
| 告警 | `MonitorAlertService.createGateAlert()` | 黑名单车辆允许入场时创建告警（新告警类型 `BLACKLIST_ENTRY`） |
| WebSocket 推送 | `BoothWebSocketPublisher.sendAlert()` | 黑名单告警推送到岗亭端 |
| 全局参数 | `ParamResolver`（读 `sys_config`） | 读取 `blacklist.trigger_mode` 和 `blacklist.trigger_types` 字典 |
| 定时任务 | Spring `@Scheduled` | 每天凌晨 2:00 扫描到期名单置 `EXPIRED` |

---

## 4. Proposed Architecture

### 4.1 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                        运营端 (admin-web)                         │
│  VehicleListController  ← CRUD ← VehicleListService             │
│  对接新实体    互斥提示弹窗    类型字典下拉    有效期选择器        │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  VehicleListService                            │
│  create / update / delete / pageList / checkEntry /              │
│  isBlacklisted / isWhitelisted / resolveBlacklistRule            │
│                                                                  │
│  互斥校验（同车场同车牌黑+白 → 拒绝）  │  Redis 缓存管理          │
└─────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│              VehicleTypeDecisionService (改造)                    │
│                                                                  │
│  新优先级链：                                                    │
│  1. vehicle_list WHITE  →  allowEntry=true, needCharge=false     │
│  2. vehicle_list BLACK  →  按 trigger_mode 决定                   │
│  3. monthly_pass (月卡)                                          │
│  4. sys_vehicle (SUPER/VIP/PREPAID/FREE，旧口径保留但不再       │
│                  作为黑白名单主口径)                               │
│  5. FIXED_SPACE (固定车位)                                        │
│  6. TEMP (默认临时车)                                             │
└─────────────────────────────────────────────────────────────────┘
                                  │
                    ┌─────────────┼─────────────┐
                    ▼             ▼             ▼
            EntryService    ExitService    MonitorAlertService
            (入场拦截/放行)  (出场拦截/放行)  (黑名单告警+WS推送)
```

### 4.2 VehicleTypeDecisionService 判定链路改造

#### 4.2.1 新优先级链

```text
1. vehicle_list WHITE（白名单）
   → allowEntry=true, allowExit=true, needCharge=false
   → 仅创建通行记录（ParkingRecord），不生成预订单
   → 命中即返回，不继续往下走

2. vehicle_list BLACK（黑名单）
   → 读取全局参数 blacklist.trigger_mode（车场级）：
     ├─ DENY_ENTRY        → allowEntry=false, allowExit=false
     ├─ ALLOW_WITH_ALERT  → allowEntry=true, needCharge=true + MonitorAlert + WS推送
     └─ BY_TYPE           → 按 trigger_type 分：
          ├─ ARREARS（欠费类）  → allowEntry=false
          ├─ MANAGEMENT（管理类）→ allowEntry=true + MonitorAlert + WS推送
          └─ OTHER（其他类）    → allowEntry=false（保守默认=禁止入场）
   → 命中即返回

3. monthly_pass（月卡，任务包 3-1 体系）
   → allowEntry=true, needCharge=false

4. sys_vehicle（SUPER / VIP / PREPAID / FREE）
   → 保留现有逻辑不变（作为非黑白名单车辆类型的补充），
     但 BLACKLIST / VIP / SUPER / FREE 常量和对应 switch-case
     不再参与黑白名单主口径判定

5. FIXED_SPACE（固定车位，任务包 3-2 体系）
   → allowEntry=true, needCharge=false

6. TEMP（默认临时车）
   → allowEntry=true, needCharge=true
```

#### 4.2.2 实现策略

在 `VehicleTypeDecisionServiceImpl.decide()` 中**在步骤 0（查询 monthly_pass）之前**增加步骤 -1 和步骤 0'：

```text
步骤 -1：查询 vehicle_list 白名单
  → VehicleListService.isWhitelisted(lotId, plateNumber)
  → 命中 → 白名单分支返回

步骤 0'：查询 vehicle_list 黑名单
  → VehicleListService.resolveBlacklistRule(lotId, plateNumber)
  → 命中 → 黑名单分支返回
  → 未命中 → 继续原链路（步骤 0 月卡）
```

**`decide()` 方法签名需增加 `parkingLotId` 参数**。当前签名：

```java
VehicleTypeDecisionVO decide(String plateNumber);
VehicleTypeDecisionVO decide(String plateNumber, Long tenantId);
```

需新增重载或修改现有签名增加 `Long parkingLotId`：

```java
VehicleTypeDecisionVO decide(String plateNumber, Long parkingLotId, Long tenantId);
```

`parkingLotId` 在入场/出场链路中从 `RecognitionEventPayload` 或 `ParkingRecord` 获取，均为可信来源。

**兼容性**：保留旧签名 `decide(String plateNumber)` 为 deprecated，内部调用 `TenantContext.getTenantId()`，parkingLotId 传 null（名单查询时 parkLotId=null 查不到任何名单，走旧逻辑降级）。旧调用方（`allowEntry`/`allowExit` 便捷方法）维持行为不变。

### 4.3 进场链路改造（EntryService）

`EntryService.handleEntry()` 当前流程：

1. 停车场状态检查
2. 重复入场检测与处理
3. 创建 ParkingRecord
4. 快照计费规则
5. 固定车位检查 → 设置 vehicleType
6. 创建 ParkingSession
7. 更新停车场容量
8. `createPreOrderIfChargeable()` → 调用 `vehicleTypeDecisionService.decide()` 判定

**改造点**：

- 在步骤 2（重复入场检测）之后，新增**黑白名单拦截步骤**（步骤 2e）：

```java
// 2e. 黑白名单判定与拦截（本期新增）
VehicleListDecisionVO listDecision = vehicleListService.checkEntry(
        parkingLotId, standardizedPlate, payload.getTenantId());

if (listDecision.isDenyEntry()) {
    // 黑名单禁止入场：创建告警 + 记录异常 + 抛异常阻断
    log.warn("黑名单车辆禁止入场: plate={} lotId={} type={} trigger={}",
            standardizedPlate, parkingLotId,
            listDecision.getListType(), listDecision.getTriggerType());
    createEntryDeniedException(payload, standardizedPlate,
            listDecision.getReason());
    throw new BusinessException(CommonErrorCode.PARAM_ERROR,
            listDecision.getReason());
}

if (listDecision.isAlert()) {
    // 黑名单允许入场但需告警：创建 MonitorAlert + WS 推送
    monitorAlertService.createBlacklistEntryAlert(
            payload.getTenantId(), parkingLotId,
            standardizedPlate, listDecision.getTriggerType());
}
```

- `createPreOrderIfChargeable()` 中，`decide()` 已返回正确的 `needCharge`（白名单为 false），无需额外修改。

### 4.4 出场链路改造（ExitService）

`ExitService.handleExit()` 当前依赖 `resolveReleaseDecision()` 基于订单状态和 `exit.unpaid_strategy` 决定放行。

**改造点**：

- 在步骤 4（`resolveReleaseDecision` 决定放行策略）**之前**，新增黑白名单判定：

```java
// 3c. 黑白名单出场判定（本期新增）
VehicleTypeDecisionVO exitDecision = vehicleTypeDecisionService.decide(
        standardizedPlate, parkingLotId, record.getTenantId());

if ("WHITE".equals(exitDecision.getVehicleType())) {
    // 白名单：免费放行，feeCents 置零，跳过计费
    feeCents = 0;
    // 直接走零费放行逻辑
    // 注意：不创建临停订单
}

if ("BLACK".equals(exitDecision.getVehicleType())) {
    // 黑名单按 trigger_mode 处理（同入场逻辑）
    // DENY_ENTRY → 拒绝出场，记录异常
    // ALLOW_WITH_ALERT / BY_TYPE(管理类) → 允许出场 + 告警
}
```

**注意**：`ExitService` 当前未注入 `VehicleTypeDecisionService`，需通过构造函数注入。

### 4.5 告警模型新增

在 `MonitorAlert` 中新增告警类型常量：

```java
/** 告警类型：黑名单车辆入场 */
public static final String TYPE_BLACKLIST_ENTRY = "BLACKLIST_ENTRY";
```

`MonitorAlertService` 新增方法：

```java
/**
 * 创建黑名单车辆入场告警。
 *
 * @param tenantId      租户 ID
 * @param parkingLotId  停车场 ID
 * @param plateNumber   车牌号
 * @param triggerType   触发类型（ARREARS / MANAGEMENT / OTHER）
 */
@Transactional
public void createBlacklistEntryAlert(Long tenantId, Long parkingLotId,
                                       String plateNumber, String triggerType) {
    if (existsUnacknowledged(parkingLotId, MonitorAlert.TYPE_BLACKLIST_ENTRY, plateNumber)) {
        return;
    }
    String typeDesc = resolveTriggerTypeLabel(triggerType);
    MonitorAlert alert = new MonitorAlert();
    alert.setTenantId(tenantId);
    alert.setParkingLotId(parkingLotId);
    alert.setAlertType(MonitorAlert.TYPE_BLACKLIST_ENTRY);
    alert.setSeverity(MonitorAlert.SEVERITY_WARNING);
    alert.setSourceId(plateNumber);
    alert.setMessage(String.format("黑名单车辆入场: 车牌%s, 触发类型: %s",
            plateNumber, typeDesc));
    alert.setAcknowledged(0);
    alert.setCreatedAt(LocalDateTime.now());
    saveAndPush(alert);
}
```

WebSocket 推送复用现有 `BoothWebSocketPublisher.sendAlert()` 通道，topic 为 `/topic/booth/{parkingLotId}/alerts`，岗亭端已订阅该 topic。

---

## 5. 数据模型

### 5.1 新建表：`vehicle_list`

```sql
CREATE TABLE vehicle_list (
    id              BIGINT          NOT NULL AUTO_INCREMENT PRIMARY KEY COMMENT '主键',
    tenant_id       BIGINT          NOT NULL COMMENT '租户ID（多租户隔离）',
    plate_number    VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    list_type       VARCHAR(16)     NOT NULL COMMENT '名单类型：BLACK-黑名单 / WHITE-白名单',
    parking_lot_id  BIGINT          NOT NULL COMMENT '生效车场ID',
    start_date      DATE            NULL COMMENT '有效期开始（NULL=立即生效）',
    end_date        DATE            NULL COMMENT '有效期结束（NULL=永久）',
    trigger_type    VARCHAR(32)     NULL COMMENT '黑名单触发类型：ARREARS-欠费类 / MANAGEMENT-管理类 / OTHER-其他类（白名单时为空）',
    status          VARCHAR(16)     NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE-生效中 / EXPIRED-已过期 / DISABLED-已禁用',
    remark          VARCHAR(255)    NULL COMMENT '备注',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    deleted_at      DATETIME        NULL COMMENT '软删除时间'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='车辆黑白名单表';
```

### 5.2 索引

| 索引名 | 字段 | 类型 | 用途 |
| :--- | :--- | :--- | :--- |
| `uk_lot_plate_type` | (`parking_lot_id`, `plate_number`, `list_type`) | UNIQUE | 同车场同车牌同名单类型唯一，防止重复添加；同时作为互斥规则的应用层第一道防线（同车牌不同 list_type 可共存，由应用层互斥校验拦截） |
| `idx_plate_number` | (`plate_number`) | 普通索引 | 加速以车牌为维度的缓存回填查询 |
| `idx_parking_lot_id` | (`parking_lot_id`) | 普通索引 | 加速车场维度查询 |
| `idx_list_type_status` | (`list_type`, `status`) | 复合索引 | 定时到期扫描（`WHERE list_type IN ('BLACK','WHITE') AND status='ACTIVE' AND end_date < NOW()`） |

### 5.3 字段约束

| 规则 | 实现 |
| :--- | :--- |
| `list_type` 仅允许 `BLACK` 或 `WHITE` | 应用层枚举校验 |
| `trigger_type` 仅在 `list_type=BLACK` 时必填（白名单自动为 NULL） | 应用层校验，保存时拦截 |
| `start_date` ≤ `end_date`（当两者均非 NULL 时） | 应用层校验 |
| `status` 仅允许 `ACTIVE` / `EXPIRED` / `DISABLED` | 应用层枚举校验 |
| `plate_number` 入库前 `toUpperCase()` 标准化 | Service 层统一处理 |

---

## 6. 业务规则

### 6.1 互斥规则

**同车场同车牌不可同时存在于黑名单和白名单**。

```
例：parking_lot_id=1, plate_number="京A12345"

允许：
  存在 list_type=BLACK, trigger_type=ARREARS 的一条记录  ✓

不允许（互斥）：
  已存在 list_type=BLACK 的记录，再创建 list_type=WHITE  ✗
  已存在 list_type=WHITE 的记录，再创建 list_type=BLACK  ✗
```

**校验实现**：
- 位置：`VehicleListService.create()` / `VehicleListService.update()` 中
- 方式：直接查 DB（不依赖缓存，保证准确性）
- SQL：`SELECT COUNT(1) FROM vehicle_list WHERE parking_lot_id=? AND plate_number=? AND list_type=? AND status='ACTIVE' AND deleted_at IS NULL`
- 即查询**反类型**的活跃记录（如当前创建 BLACK，查 WHITE 是否存在）
- 存在时抛出 `BusinessException`，消息："该车牌已存在于{黑/白}名单中，无法同时添加为{白/黑}名单"
- 前端捕获后 toast 提示具体冲突

### 6.2 黑名单触发模式（全局参数）

参数 key：`blacklist.trigger_mode`

| 值 | 含义 | 入场行为 | 出场行为 |
| :--- | :--- | :--- | :--- |
| `DENY_ENTRY`（默认） | 禁止入场 | 车辆到达入口 → 拒绝入场，记录 ExceptionRecord | 拒绝出场，记录异常 |
| `ALLOW_WITH_ALERT` | 允许入场但告警 | 允许入场，生成预订单 + MonitorAlert + WS 推送 | 允许出场（正常计费），生成告警 |
| `BY_TYPE` | 按触发类型区分 | 见 §6.3 | 同入场逻辑 |

**参数作用域**：车场级（通过 `ParamResolver` 的 `parkingLotId` 维度查询）。

### 6.3 按类型区分（BY_TYPE 模式）

当 `blacklist.trigger_mode = BY_TYPE` 时：

| trigger_type | 入场 | 出场 | 告警 |
| :--- | :--- | :--- | :--- |
| `ARREARS`（欠费类） | 禁止入场 | 拒绝出场 | 记录异常 |
| `MANAGEMENT`（管理类） | 允许入场 | 允许出场 | 生成 MonitorAlert + WS 推送 |
| `OTHER`（其他类） | 禁止入场（保守默认） | 拒绝出场 | 记录异常 |

**类型字典**：全局参数 `blacklist.trigger_types` 维护 JSON 数组，供运营端下拉选项：

```json
[
  {"code": "ARREARS",    "label": "欠费类"},
  {"code": "MANAGEMENT", "label": "管理类"},
  {"code": "OTHER",      "label": "其他类"}
]
```

超管可扩展 `OTHER` 的说明或添加新类型，前端名单页下拉框动态读取此字典。

### 6.4 有效期规则

- `start_date = NULL` → 立即生效
- `end_date = NULL` → 永久有效（不自动过期）
- `start_date` 和 `end_date` 均非 NULL → 在 `[start_date, end_date]` 范围内生效（含边界）
- 定时任务每天凌晨 2:00 执行：`UPDATE vehicle_list SET status='EXPIRED' WHERE end_date < CURRENT_DATE AND status='ACTIVE'`
- 过期后该条名单不再参与进出场判定，按车辆默认类型处理（临时车或其他 sys_vehicle 类型）

### 6.5 进场预订单生成规则

| 判定结果 | 是否生成预订单 | 说明 |
| :--- | :--- | :--- |
| 白名单 | **否** | 仅生成 ParkingRecord（通行记录），不生成 ParkingOrder |
| 黑名单 + DENY_ENTRY | **否** | 拒绝入场，不生成任何记录 |
| 黑名单 + ALLOW_WITH_ALERT | **是** | 允许入场，正常生成预订单（needCharge=true） |
| 黑名单 + BY_TYPE + 管理类 | **是** | 同 ALLOW_WITH_ALERT |
| 黑名单 + BY_TYPE + 欠费类 | **否** | 拒绝入场 |

此规则已在 `createPreOrderIfChargeable()` 中通过 `decision.getNeedCharge()` 自然生效，无需额外代码。

### 6.6 出场计费规则

- 白名单出场：`feeCents=0`，不创建临停订单，直接完成停车记录并开闸，仅生成 ExitRecord。
- 黑名单出场：同入场逻辑（按 trigger_mode 决定拦截或允许+告警）。允许出场时正常计费（通过 `BillingEngine.calculateFee()`）。
- 月卡/固定车位等已在各自体系中处理，不受黑白名单影响。

---

## 7. 缓存策略

### 7.1 缓存设计

遵循项目现有 Redis 缓存模式（`RedisParamCacheStore`），使用 `StringRedisTemplate` + 优雅降级（Redis 不可用时降级为进程内 `ConcurrentHashMap`）。

| 项目 | 值 |
| :--- | :--- |
| 缓存 Key | `vehicle_list:{parkingLotId}:{plateNumber}` |
| 缓存 Value | JSON：`{"listType":"BLACK","triggerType":"ARREARS","endDate":"2026-12-31","status":"ACTIVE"}` |
| TTL | 30 分钟（`RedisConstants.VEHICLE_LIST_TTL_MINUTES`） |
| 写入时机 | `VehicleListService` 首次查询 DB 后回填（cache-aside）；创建/更新后 `SET`（write-through） |
| 失效时机 | 创建/更新/删除时精准 `DEL` 对应 Key；定时到期任务批量 `DEL` |
| 未命中行为 | 查 DB → 回填缓存（包括 null 值，短期缓存防止缓存穿透） |

### 7.2 性能目标

进出场主链路中名单查询时间 < 50ms。这是识别事件处理的同步路径，须确保缓存命中率 ≥ 95%。

### 7.3 缓存一致性

- 互斥校验**不依赖缓存**，直接查 DB，保证写入一致性。
- 判定链路查询走缓存，允许短暂不一致（最多 1 个 TTL 周期），但创建/更新/删除操作同步失效缓存。

---

## 8. API 接口定义

### 8.1 Controller: `VehicleListController`

```java
@RestController
@RequestMapping("/api/v1/admin/vehicle-list")
public class VehicleListController {

    /**
     * 创建名单（黑白名单）。
     */
    @PostMapping
    @RequirePermission("vehicle-list:create")
    @BusinessLog("创建黑白名单")
    public R<VehicleListVO> create(@RequestBody @Valid VehicleListCreateCmd cmd);

    /**
     * 编辑名单。
     */
    @PutMapping("/{id}")
    @RequirePermission("vehicle-list:update")
    @BusinessLog("编辑黑白名单")
    public R<VehicleListVO> update(@PathVariable Long id,
                                    @RequestBody @Valid VehicleListUpdateCmd cmd);

    /**
     * 删除名单（软删除）。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("vehicle-list:delete")
    @BusinessLog("删除黑白名单")
    public R<Void> delete(@PathVariable Long id);

    /**
     * 分页查询名单。
     */
    @GetMapping("/page")
    @RequirePermission("vehicle-list:view")
    public R<IPage<VehicleListVO>> page(@Valid VehicleListPageQuery query);

    /**
     * 名单详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("vehicle-list:view")
    public R<VehicleListVO> detail(@PathVariable Long id);

    /**
     * 查询黑名单触发类型字典（供前端下拉框）。
     */
    @GetMapping("/trigger-types")
    @RequirePermission("vehicle-list:view")
    public R<List<DictVO>> triggerTypes();
}
```

### 8.2 请求/响应 DTO

**VehicleListCreateCmd**：

| 字段 | 类型 | 必填 | 说明 |
| :--- | :--- | :--- | :--- |
| `plateNumber` | String | 是 | 车牌号（后端自动 `toUpperCase()`） |
| `listType` | String | 是 | `BLACK` 或 `WHITE` |
| `parkingLotId` | Long | 是 | 生效车场 ID |
| `startDate` | LocalDate | 否 | 有效期开始，空=立即生效 |
| `endDate` | LocalDate | 否 | 有效期结束，空=永久 |
| `triggerType` | String | 条件必填 | `listType=BLACK` 时必填，否则传 null |
| `remark` | String | 否 | 备注，最长 255 字符 |

**VehicleListVO**：

| 字段 | 类型 | 说明 |
| :--- | :--- | :--- |
| `id` | Long | 记录 ID |
| `plateNumber` | String | 车牌号 |
| `listType` | String | BLACK / WHITE |
| `listTypeLabel` | String | "黑名单" / "白名单" |
| `parkingLotId` | Long | 车场 ID |
| `parkingLotName` | String | 车场名称（关联查询） |
| `startDate` | LocalDate | 有效期开始 |
| `endDate` | LocalDate | 有效期结束 |
| `triggerType` | String | 触发类型 code |
| `triggerTypeLabel` | String | 触发类型中文 |
| `status` | String | ACTIVE / EXPIRED / DISABLED |
| `statusLabel` | String | 状态中文 |
| `remark` | String | 备注 |
| `createdAt` | LocalDateTime | 创建时间 |

### 8.3 前端改造要点（admin-web）

- 替换旧的 `AccessPolicy` 策略页中黑白名单 Tab（或新增独立的"黑白名单"菜单项）
- 表单字段：车牌号（文本，自动大写提交）、名单类型（下拉）、生效车场（下拉，租户授权车场范围）、有效期（DatePicker，开始/结束均可不选）、黑名单触发类型（仅 `listType=BLACK` 时显示）、备注（文本域）
- 互斥错误：后端返回 `message` 含"该车牌已存在于X名单中"，前端 toast 红色提示
- 列表页：支持按车场、名单类型、车牌号筛选，分页 20 条/页

---

## 9. 存量数据迁移

### 9.1 迁移脚本

Flyway 版本命名 `V{YYYYMMDD}__migrate_vehicle_list.sql`（日期为上线日）。

```sql
-- ============================================================
-- 1. 创建 vehicle_list 表
-- ============================================================
-- （DDL 参见 §5.1，此处省略）

-- ============================================================
-- 2. 迁移 AccessPolicy BLACKLIST 策略 → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    ap.tenant_id,
    UPPER(ap.policy_key) AS plate_number,
    'BLACK'               AS list_type,
    ap.parking_lot_id,
    NULL                  AS start_date,
    NULL                  AS end_date,
    'OTHER'               AS trigger_type,
    'ACTIVE'              AS status,
    CONCAT('迁移自access_policy: ', COALESCE(ap.description, '')) AS remark,
    ap.created_at,
    NOW()
FROM access_policy ap
WHERE ap.policy_type = 'BLACKLIST'
  AND ap.status = 'ACTIVE'
  AND ap.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = ap.parking_lot_id
        AND vl.plate_number  = UPPER(ap.policy_key)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 3. 迁移 sys_vehicle BLACKLIST → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'BLACK'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    'OTHER'                AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle BLACKLIST: ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type = 'BLACKLIST'
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'BLACK'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 4. 迁移 sys_vehicle VIP/SUPER/FREE（白名单） → vehicle_list
-- ============================================================
INSERT INTO vehicle_list (tenant_id, plate_number, list_type, parking_lot_id,
    start_date, end_date, trigger_type, status, remark, created_at, updated_at)
SELECT
    sv.tenant_id,
    UPPER(sv.plate_number) AS plate_number,
    'WHITE'                AS list_type,
    sv.parking_lot_id,
    sv.valid_start_date    AS start_date,
    sv.valid_end_date      AS end_date,
    NULL                   AS trigger_type,
    CASE
        WHEN sv.valid_end_date IS NOT NULL AND sv.valid_end_date < CURRENT_DATE THEN 'EXPIRED'
        ELSE 'ACTIVE'
    END                    AS status,
    CONCAT('迁移自sys_vehicle ', sv.vehicle_type, ': ', COALESCE(sv.remark, '')) AS remark,
    sv.created_at,
    NOW()
FROM sys_vehicle sv
WHERE sv.vehicle_type IN ('VIP', 'SUPER', 'FREE')
  AND sv.status = 'ACTIVE'
  AND sv.deleted_at IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM vehicle_list vl
      WHERE vl.parking_lot_id = sv.parking_lot_id
        AND vl.plate_number  = UPPER(sv.plate_number)
        AND vl.list_type     = 'WHITE'
        AND vl.deleted_at IS NULL
  );

-- ============================================================
-- 5. 插入全局参数默认值
-- ============================================================
INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_mode', 'DENY_ENTRY',
        '黑名单触发模式：DENY_ENTRY-禁止入场 / ALLOW_WITH_ALERT-允许但告警 / BY_TYPE-按类型区分',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO sys_config (config_key, config_value, description, created_at, updated_at)
VALUES ('blacklist.trigger_types',
        '[{"code":"ARREARS","label":"欠费类"},{"code":"MANAGEMENT","label":"管理类"},{"code":"OTHER","label":"其他类"}]',
        '黑名单触发类型字典（JSON数组）',
        NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();
```

### 9.2 迁移校验

上线后执行以下验证：

```sql
-- 验证迁移数量
SELECT 'AccessPolicy->vehicle_list' AS source, COUNT(1) AS cnt FROM access_policy
WHERE policy_type = 'BLACKLIST' AND status = 'ACTIVE' AND deleted_at IS NULL
UNION ALL
SELECT 'sys_vehicle BLACKLIST->vehicle_list', COUNT(1) FROM sys_vehicle
WHERE vehicle_type = 'BLACKLIST' AND status = 'ACTIVE' AND deleted_at IS NULL
UNION ALL
SELECT 'sys_vehicle WHITE->vehicle_list', COUNT(1) FROM sys_vehicle
WHERE vehicle_type IN ('VIP','SUPER','FREE') AND status = 'ACTIVE' AND deleted_at IS NULL
UNION ALL
SELECT 'vehicle_list total', COUNT(1) FROM vehicle_list WHERE deleted_at IS NULL;
-- 前三行之和应与第四行大致匹配
```

### 9.3 旧数据保留策略

- `sys_vehicle` 中 `vehicle_type=BLACKLIST/VIP/SUPER/FREE` 的记录**不删除**（保留作为历史审计和数据回退的兜底）。
- `AccessPolicy` 中 `policy_type=BLACKLIST` 的策略**不删除**。
- 仅判定链路中停止读取这些字段作为黑白名单依据。
- `SysVehicle.TYPE_BLACKLIST` / `TYPE_VIP` / `TYPE_SUPER` / `TYPE_FREE` 常量保留不删除。

---

## 10. OverstayBlacklistScheduler 处理

| 变更项 | 内容 |
| :--- | :--- |
| `application.yml`（或对应配置文件） | `jushan.overstay-blacklist.enabled` 默认值从 `true` 改为 `false` |
| `OverstayBlacklistScheduler.java` | 添加 `@ConditionalOnProperty(name = "jushan.overstay-blacklist.enabled", havingValue = "true")` |
| 代码主体 | 保留不删，类 Javadoc 追加注释：`二期评估：需确认自动拉黑策略与现有 vehicle_list 表的联动方式` |

---

## 11. Files To Change

### 11.1 新建文件

| 文件 | 说明 |
| :--- | :--- |
| `parking-system/src/main/java/com/jushan/system/entity/VehicleList.java` | 名单实体类（MyBatis-Plus） |
| `parking-system/src/main/java/com/jushan/system/mapper/VehicleListMapper.java` | 名单 Mapper 接口 |
| `parking-system/src/main/java/com/jushan/system/service/VehicleListService.java` | 名单业务服务（CRUD + 判定 + 缓存） |
| `parking-system/src/main/java/com/jushan/system/controller/VehicleListController.java` | 运营端名单 CRUD 控制器 |
| `parking-system/src/main/java/com/jushan/system/dto/VehicleListCreateCmd.java` | 创建 DTO |
| `parking-system/src/main/java/com/jushan/system/dto/VehicleListUpdateCmd.java` | 更新 DTO |
| `parking-system/src/main/java/com/jushan/system/dto/VehicleListPageQuery.java` | 分页查询 DTO |
| `parking-system/src/main/java/com/jushan/system/vo/VehicleListVO.java` | 视图 VO |
| `parking-system/src/main/java/com/jushan/system/vo/VehicleListDecisionVO.java` | 名单判定结果 VO（供 EntryService/ExitService 使用） |
| `parking-system/src/main/java/com/jushan/system/cache/VehicleListCacheStore.java` | 名单缓存存储（Redis 主 + 进程内降级） |
| `parking-system/src/main/java/com/jushan/system/task/VehicleListExpiryTask.java` | 名单到期定时任务 |
| `parking-boot/src/main/resources/db/migration/V{YYYYMMDD}__migrate_vehicle_list.sql` | Flyway 迁移脚本 |

### 11.2 修改文件

| 文件 | 变更说明 |
| :--- | :--- |
| `VehicleTypeDecisionServiceImpl.java` | 新增白名单/黑名单判定步骤（最高优先级），新增 `decide(plateNumber, parkingLotId, tenantId)` 方法 |
| `VehicleTypeDecisionService.java` | 接口新增 `decide(plateNumber, parkingLotId, tenantId)` 方法签名 |
| `VehicleTypeDecisionVO.java` | 新增 `triggerType` 和 `triggerTypeLabel` 字段（黑名单触发类型透传） |
| `EntryService.java` | 构造函数注入 `VehicleListService`；`handleEntry()` 中新增黑白名单拦截步骤；`createPreOrderIfChargeable()` 中 `decide()` 传入 `parkingLotId` |
| `ExitService.java` | 构造函数注入 `VehicleTypeDecisionService`；`handleExit()` 中新增黑白名单出场判定步骤 |
| `MonitorAlert.java` | 新增常量 `TYPE_BLACKLIST_ENTRY = "BLACKLIST_ENTRY"` |
| `MonitorAlertService.java` | 新增 `createBlacklistEntryAlert()` 方法 |
| `OverstayBlacklistScheduler.java` | 添加 `@ConditionalOnProperty` 注解，更新 Javadoc |
| `application.yml` | `jushan.overstay-blacklist.enabled` 改为 `false` |
| `admin-web/` | 新增/替换名单管理页（对接 `VehicleListController`） |

---

## 12. Testing Strategy

### 12.1 单元测试

| 测试类 | 覆盖内容 |
| :--- | :--- |
| `VehicleListServiceTest` | 互斥校验、车牌标准化、有效期校验、triggerType 条件必填、status 枚举校验、缓存回填与失效 |
| `VehicleTypeDecisionServiceImplTest` | 新优先级链路：白名单 → 黑名单三种触发模式 → 未命中走原链路；新增 `parkingLotId` 参数传递 |
| `VehicleListCacheStoreTest` | 缓存写入/读取/失效、Redis 不可用时降级、null 值防穿透 |

### 12.2 集成测试

| 测试类 | 覆盖内容 |
| :--- | :--- |
| `EntryServiceTest` | 白名单车辆入场不生成预订单仅生成 ParkingRecord；黑名单 DENY_ENTRY 模式拒绝入场并记录 ExceptionRecord；黑名单 ALLOW_WITH_ALERT 模式允许入场并生成 MonitorAlert + WS 推送 |
| `ExitServiceTest` | 白名单车辆出场零费放行不生成订单；黑名单车辆出场行为同入场模式 |
| `VehicleListControllerTest` | CRUD 接口、权限校验、互斥错误响应、分页筛选 |
| `VehicleListExpiryTaskTest` | 定时到期扫描：end_date 过期 → status=EXPIRED；缓存失效 |

### 12.3 手动验收

| 编号 | 验收项 | 期望结果 |
| :--- | :--- | :--- |
| 1 | 互斥校验 | 同车场同车牌在黑名单→再添加白名单被拦截，前端提示"该车牌已存在于黑名单中，无法同时添加为白名单" |
| 2 | DENY_ENTRY 模式 | 黑名单车牌到达入口 → 入场被拒，ExceptionRecord 中有对应记录 |
| 3 | ALLOW_WITH_ALERT 模式 | 黑名单车牌到达入口 → 允许入场生成预订单，岗亭端收到 MonitorAlert WS 推送 |
| 4 | BY_TYPE 模式 | 欠费类黑名单禁止入场；管理类黑名单允许入场+告警；其他类禁止入场 |
| 5 | 白名单自动放行 | 白名单车牌到达入口 → 仅创建 ParkingRecord，不生成 ParkingOrder；出场时零费放行 |
| 6 | 缓存命中率 | 识别主链路 `isBlacklisted` / `isWhitelisted` 调用耗时 < 50ms |
| 7 | 存量迁移 | Flyway 脚本执行后 vehicle_list 行数为 access_policy BLACKLIST + sys_vehicle BLACKLIST + sys_vehicle VIP/SUPER/FREE 之和 |
| 8 | 自动拉黑开关 | `jushan.overstay-blacklist.enabled=false`（默认）时定时任务不执行拉黑逻辑 |
| 9 | 有效期到期 | 名单 end_date 过期后定时任务置 EXPIRED，该车牌恢复按临时车处理 |

---

## 13. Risks And Mitigations

| # | 风险 | 影响 | 概率 | 缓解措施 |
| :--- | :--- | :--- | :--- | :--- |
| 1 | **缓存与 DB 数据不一致导致判定错误**：Redis 故障或缓存回填失败时，名单车辆可能被误判为临时车（白名单未免费）或未被拦截（黑名单未拒入） | 中 | 低 | ① 缓存 TTL 30 分钟，短暂不一致可接受；② 定时任务每天凌晨全量回填缓存（补偿机制）；③ 监控缓存命中率，低于 80% 告警；④ Redis 不可用时降级为进程内缓存继续服务，核心链路不中断 |
| 2 | **存量迁移后旧链路仍读取 sys_vehicle 导致双口径**：若 VehicleTypeDecisionService 改造不彻底，部分调用方仍走旧 switch-case 匹配 BLACKLIST/VIP | 高 | 中 | ① 在 `applyPriorityChain()` 的 `SysVehicle.TYPE_BLACKLIST` case 和 `TYPE_VIP`/`TYPE_SUPER`/`TYPE_FREE` case 中添加 warn 日志（标记为 deprecated 路径），上线后监控日志量；② 接口 `decide()` 旧签名保留但标记 `@Deprecated`，IDE 编译警告引导调用方迁移 |
| 3 | **parkingLotId 参数在全链路中不可得**：某些识别事件处理路径缺少 parkingLotId（如 Webhook 网关直传路径） | 中 | 低 | ① `decide(plateNumber, tenantId)` 旧签名中 parkingLotId 传 null → 名单查询跳过（不破坏兼容性）；② 识别事件消费链路中 `RecognitionEventPayload.getParkingLotId()` 已由 Consumer 从设备记录推导，可靠可用 |
| 4 | **定时任务批量失效性能影响**：大量名单同时到期时批量 UPDATE + DEL 可能瞬时锁表 | 低 | 低 | ① 分页处理（每批 500 条）避免长事务；② 凌晨 2:00 执行（业务低峰）；③ 使用 `idx_list_type_status` 索引加速扫描 |
| 5 | **前端切换过渡期用户混淆**：旧 AccessPolicy 页面和新 vehicle_list 页面并存时，运营人员不清楚用哪边 | 低 | 中 | ① 迁移完成后旧 AccessPolicy 页面的黑白名单 Tab 添加横幅提示"已迁移至新名单管理"并置灰新增按钮；② 下一个迭代（任务包 4-x）中完全下线旧页面 |

---

## 14. Decision Summary

- **D1**：新建独立表 `vehicle_list` 统一管理黑白名单，替代 `AccessPolicy(policy_type=BLACKLIST)` 和 `sys_vehicle(vehicleType=BLACKLIST/VIP/SUPER/FREE)` 中的名单数据。
- **D2**：`VehicleTypeDecisionService.decide()` 新增 `parkingLotId` 参数重载，名单查询绑定车场维度；旧签名保留 `@Deprecated` 兼容过渡。
- **D3**：判定优先级调整为：白名单 → 黑名单 → 月卡 → sys_vehicle (旧类型) → 固定车位 → 临时车。白名单和黑名单命中即短路返回。
- **D4**：黑名单触发模式通过全局参数 `blacklist.trigger_mode` 控制，支持 DENY_ENTRY / ALLOW_WITH_ALERT / BY_TYPE 三种模式，作用域为车场级。
- **D5**：互斥校验在 `VehicleListService` 应用层直接查 DB 实现，不依赖缓存，保证准确性。
- **D6**：缓存采用 Redis 主 + 进程内 ConcurrentHashMap 降级的双层架构（遵循项目现有 `RedisParamCacheStore` 模式），TTL 30 分钟。
- **D7**：进出场链路改造范围最小化：`EntryService` 增加黑白名单拦截步骤，`ExitService` 增加黑白名单出场判定，告警复用现有 `MonitorAlertService` + `BoothWebSocketPublisher`。
- **D8**：存量数据通过 Flyway 迁移脚本一次性迁入 `vehicle_list`，旧表数据保留不删除，旧常量保留不删除，仅判定链路停止读取。
- **D9**：`OverstayBlacklistScheduler` 默认关闭（`jushan.overstay-blacklist.enabled=false`），代码主体保留，添加 `@ConditionalOnProperty` 注解，标注二期评估。
- **D10**：前端新建独立名单管理页（替代旧 AccessPolicy 策略页），对接 `VehicleListController`，互斥错误前端 toast 提示。

---

## 附录 A：数据字典

| 字段 | 值 | 说明 |
| :--- | :--- | :--- |
| `list_type` | `BLACK` | 黑名单 |
| `list_type` | `WHITE` | 白名单 |
| `trigger_type` | `ARREARS` | 欠费类（黑名单） |
| `trigger_type` | `MANAGEMENT` | 管理类（黑名单） |
| `trigger_type` | `OTHER` | 其他类（黑名单） |
| `status` | `ACTIVE` | 生效中 |
| `status` | `EXPIRED` | 已过期 |
| `status` | `DISABLED` | 已禁用 |

## 附录 B：全局参数

| key | 默认值 | 说明 |
| :--- | :--- | :--- |
| `blacklist.trigger_mode` | `DENY_ENTRY` | 黑名单触发模式 |
| `blacklist.trigger_types` | `[{"code":"ARREARS","label":"欠费类"},...]` | 触发类型字典 |
