# 设计文档：开发阶段遗留项补充（BOOTH-005 / 订单重算关联 / Webhook 签名文档）

> 需求依据：BOOTH-005 任务包 4-1、任务包 2-2、任务包 0-2

---

## 1. Goal

本设计文档覆盖开发包阶段 1–6 完成后遗留的三项待补充内容：

- **Fix 1**（BOOTH-005 任务包 4-1）：岗亭端车牌校正功能。岗亭管理员可对识别错误的识别事件手动输入校正车牌号，校正记录为审计用途永久保留，不影响已生成的 `parking_record`。
- **Fix 2**（任务包 2-2）：订单重算关联在运营端订单详情中的前端展示。后端数据已就绪（`ParkingOrder.recalcSourceOrderId`），本项补充 `OrderAdminVO` 中的 `recalcSourceOrderNo` 字段及前端详情弹窗中的“重算关联”区域。
- **Fix 3**（任务包 0-2）：Webhook 签名规范文档。HMAC-SHA256 签名校验已在 `WebhookVerificationFilter` 中完整实现，但 `docs/设备接入/webhook签名规范.md` 文档未创建。本项补建该文档。

## 2. Non-Goals

- **Fix 1** 不修改已生成的 `parking_record` 中的车牌号；校正仅更新识别事件日志。
- **Fix 1** 不支持校正记录的二次修改或删除（不可变，见约束）。
- **Fix 2** 不修改重算业务逻辑本身（已实现），仅补充展示层。
- **Fix 3** 不修改任何签名校验代码，仅创建文档描述已有实现。
- 不新增任何支付、计费、设备通信能力。
- 不涉及前端跨页面路由变更。

## 3. Context

### 3.1 项目整体状态

- Java 21 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Flyway。
- 开发包阶段 1–6 已完成，进出场、计费引擎、订单、支付、设备管理、岗亭监控等主体功能已上线。
- 以下三处为明确记录的遗留项。

### 3.2 Fix 1 现存上下文

- **`RecognitionEventLog`**（`parking-system/.../entity/RecognitionEventLog.java`）：T28/T29 表，字段包括 `id, eventId, tenantId, parkingLotId, laneId, deviceId, plateNumber, direction, eventTime, confidence, imagePath, plateImagePath, source, rawData, status, failureReason, standardizedPlate, vendorEventId, tempPlateFlag, cameraSource, createdAt`。
- **`BoothWebSocketPublisher`**（`parking-system/.../ws/BoothWebSocketPublisher.java`）：负责向岗亭前端推送识别事件、设备状态等。识别事件通过 `sendRecognitionEvent(parkingLotId, event)` 发布到 `/topic/booth/{parkingLotId}/events`。
- **岗亭监控页**（`booth-web/src/views/monitor/index.vue`）：右侧“最近识别事件”列表由 `store.recentEvents` 驱动，每行点击调用 `handleEventClick(item)`，当前仅对 EXIT 方向事件打开收费面板。校正功能需在事件行交互上新增入口。
- **Flyway 迁移**：`recognition_event_log` 表原始定义在 `V20260712015`，后续迁移新增 `status`（`V20260712016`）、`tempPlateFlag`（`V20260717005`）、`cameraSource`（`V20260901001`）。
- 当前系统内不存在任何车牌校正相关字段、接口或前端组件。
- **`@BusinessLog`** 审计日志机制：项目已有成熟的 `@BusinessLog` 注解 + `BusinessLogAspect` + `SysBusinessLogStorage` 异步持久化链路，校正操作应使用此机制。

### 3.3 Fix 2 现存上下文

- **`ParkingOrder.recalcSourceOrderId`**（`parking-system/.../entity/ParkingOrder.java`，第 114 行）：已存储超时关单后重算时新订单关联的原订单 ID。赋值链路在 `ParkingOrderService.createOrderInternal()` 中完成（第 161 行）。
- **`OrderAdminVO`**（`parking-system/.../vo/OrderAdminVO.java`）：当前包含 `id, orderNo, orderType, plateNumber, parkingLotId / parkingLotName, amountCents, payableAmount, paidAmount, status, payChannel, payTime, entryTime, exitTime, parkingDurationMinutes, operatorName, refundReason, refundTime, refundOperatorName, createdAt, updatedAt` 及其对应 label 字段。**不含 `recalcSourceOrderNo`**。
- **`OrderAdminController.convertToVO()`**（第 437 行起）：负责 `ParkingOrder` → `OrderAdminVO` 映射，需要在此处补充 `recalcSourceOrderNo` 的填充逻辑。
- **运营端订单详情弹窗**（`admin-web/src/views/order/index.vue`）：`<a-descriptions>` 展示订单各字段（第 93–115 行），已存在状态流转日志区域（第 126–144 行）。需要在此描述中新增“重算关联”项。
- **`admin-web/src/api/order.ts`**：`OrderAdminVO` TypeScript 接口（第 4–31 行），需要新增 `recalcSourceOrderNo?: string` 可选字段。

### 3.4 Fix 3 现存上下文

- **`WebhookVerificationFilter`**（`parking-system/.../webhook/WebhookVerificationFilter.java`）：已完整实现 HMAC-SHA256 签名校验，包括：
  - `X-Sign` / `X-Timestamp` / `X-Nonce` 三头提取。
  - 时间戳偏移容差 ±5 分钟（`TIMESTAMP_TOLERANCE = Duration.ofMinutes(5)`）。
  - Nonce 防重放：Redis `SETNX`，TTL 10 分钟（`NONCE_TTL = Duration.ofMinutes(10)`）。
  - 签名算法：`computeHmacSha256(secret, timestamp + nonce + rawBody)`，Base64 编码。
  - Secret 来源：通过 `deviceSn` 查 `webhook_secret` 表获取停车场级 secret。
  - 校验失败统一返回 401 `{"code":401,"message":"Unauthorized"}` 不暴露具体原因。
- **`WebhookSecurityConfig`**：注册过滤器到 `/api/v1/device-webhook/*` 路径，可通过 `device-access.webhook.signature-enabled` 开关控制。
- **`docs/设备接入/`** 目录不存在（已确认）。`webhook签名规范.md` 作为标准化对接文档需要创建。

## 4. Proposed Architecture

### 4.1 Fix 1：车牌校正（BOOTH-005, Task 4-1）

#### 4.1.1 数据库变更

新增 Flyway 迁移，向 `recognition_event_log` 表添加四个字段：

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `corrected_plate` | `VARCHAR(20)` | 校正后车牌号 |
| `correction_type` | `VARCHAR(30)` | 校正类型：固定 `MANUAL_CORRECTION` |
| `corrected_at` | `DATETIME` | 校正操作时间 |
| `corrector_id` | `BIGINT` | 校正人 ID（`sys_user.id`，岗亭管理员） |

四字段均为 `NULL` 可空（未校正的事件保持 `NULL`）。`correction_type` 留固定值空间便于未来扩展（如系统自动纠错 `AUTO_CORRECTION`）。不做历史表单独存储，校正记录直接写回原事件日志行，约定为永久不可变（应用层禁止二次校正）。

**Flyway 版本号**：新迁移文件名 `V20260902003__correct_plate_fields.sql`，接续当前最新迁移 `V20260902002` 的序号。

#### 4.1.2 实体层变更

`RecognitionEventLog.java` 新增四个字段的 getter/setter：

```java
private String correctedPlate;
private String correctionType;
private LocalDateTime correctedAt;
private Long correctorId;
```

#### 4.1.3 后端接口

**新增 Controller**：`BoothRecognitionController`（遵循现有 `BoothMonitorController` 等命名和路径约定）。

```java
@RestController
@RequestMapping("/api/booth/recognition")
public class BoothRecognitionController { ... }
```

**接口设计**：

```
POST /api/booth/recognition/{logId}/correct
@RequirePermission("booth:monitor")
@BusinessLog(value = "车牌校正", module = "recognition", operationType = "UPDATE",
        operationObject = "识别事件", objectIdExpression = "#logId")
```

- **路径参数**：`logId` — `recognition_event_log.id`（自增主键）。
- **请求体**：`{ "correctedPlate": "京A12346" }`
- **校验逻辑**：
  1. 查询事件日志，不存在则返回 404。
  2. 事件 `status` 必须为 `PROCESSED`，否则拒绝（返回业务错误码，提示“仅已处理状态的识别事件可校正”）。
  3. `correctedPlate` 非空且去空格后长度 >= 2，上限 20 字符。
  4. `correctedPlate` 与原始 `plateNumber` 相同则视为无效校正（提示“校正车牌与原识别车牌相同”）。
  5. 事件已存在校正记录（`corrected_plate IS NOT NULL`）则拒绝（不可变约束）。
  6. 校验 `tenantId` / `parkingLotId` 与当前岗亭管理员被分配的车场匹配（数据范围校验，复用 `DataScope` 或 `scopeResolver`）。
- **写入操作**：
  1. 更新事件日志：`SET corrected_plate = ?, correction_type = 'MANUAL_CORRECTION', corrected_at = NOW(), corrector_id = ? WHERE id = ? AND corrected_plate IS NULL`（带乐观条件）。
  2. `@BusinessLog` 自动记录审计日志（操作人、IP、模块、新值）。
- **返回值**：`R.ok()`（更新成功后无额外数据体）。
- **推送**：校正成功后调用 `BoothWebSocketPublisher.sendRecognitionEvent(parkingLotId, updatedEvent)`，岗亭前端事件列表收到更新后自动刷新该事件行（显示校正后车牌）。

#### 4.1.4 WebSocket 负载扩展

`BoothWebSocketPublisher.sendRecognitionEvent()` 当前负载已包含 `plateNumber`。校正后推送时需要同时包含 `correctedPlate` 字段，使前端可区分原始识别车牌与校正车牌。扩展负载：

```java
payload.put("correctedPlate", event.getCorrectedPlate()); // null 表示未校正
payload.put("correctionType", event.getCorrectionType()); // null 表示未校正
```

#### 4.1.5 前端变更

**新组件**：`booth-web/src/components/PlateCorrectionModal.vue`

- 接收 prop：`event: RecognitionEvent`（包含 `logId`, `plateNumber`, `correctedPlate` 等）。
- 弹窗展示原始识别车牌（只读）、输入框（`correctedPlate`，最大 20 字符）、提交按钮。
- 调用 API `POST /api/booth/recognition/{logId}/correct`，成功后 `emit('corrected')` 并关闭弹窗。
- 错误处理：展示 `message.error`。
- 若事件已校正（`correctedPlate` 不为空），弹窗为只读模式，仅展示原始/校正车牌，不显示输入框和提交按钮。

**修改现有页面**：`booth-web/src/views/monitor/index.vue`

- `handleEventClick(item)` 方法当前仅对 EXIT 事件打开收费面板。修改为以下行为：
  - **ENTRY 事件**：点击直接打开 `PlateCorrectionModal`（入场事件无收费流程，校正为其唯一操作）。
  - **EXIT 事件**：点击弹出 `a-dropdown` 操作菜单，包含两个选项：“收费处理”（现有逻辑，打开收费面板）和“校正车牌”（打开 `PlateCorrectionModal`）。已校正的 EXIT 事件仍可执行收费处理，但不允许再次校正。
- 事件列表项增加校正标记：已校正的事件行显示一个小标记（如 `a-tag` 内容为“已校正”），并使用 `correctedPlate`（若存在）替代原始 `plateNumber` 展示。
- 在 `monitor-types.ts`（`booth-web/src/api/monitor-types.ts`）中扩展 `RecognitionEvent` 接口和 `RecognitionEventPayload` 接口，均新增 `correctedPlate?: string` 和 `correctionType?: string` 可选字段。`RecognitionEvent` 是快照/列表展示类型，`RecognitionEventPayload` 是 WebSocket 推送类型，两者均需支持校正数据。

#### 4.1.6 数据范围与租户校验

岗亭管理员跨租户特性（见 AGENTS.md），因此在 `BoothRecognitionController` 中校验当前用户有权限访问事件所属车场即可（`scopeResolver.validateAccess(event.getParkingLotId())`），不强制校验 `tenant_id` 匹配当前用户租户。

### 4.2 Fix 2：订单重算关联展示（Task 2-2）

#### 4.2.1 后端变更

**`OrderAdminVO` 扩展**：

新增字段：
```java
/** 重算来源订单 ID。超时关单后重算时，新订单记录关联的原订单主键（用于前端跳转） */
private Long recalcSourceOrderId;
/** 重算来源订单号。超时关单后重算时，新订单记录关联的原订单号（用于前端展示） */
private String recalcSourceOrderNo;
// getters / setters
```

**`OrderAdminController.convertToVO()` 扩展**：

在 `convertToVO` 方法中，当 `order.getRecalcSourceOrderId() != null` 时，直接设置 `vo.setRecalcSourceOrderId(order.getRecalcSourceOrderId())`，并通过 `OrderMapper` 查询原订单（`selectById`），若存在则设置 `vo.setRecalcSourceOrderNo(sourceOrder.getOrderNo())`，不存在则 `recalcSourceOrderNo` 保持 `null`（容错：原订单被物理删除）。批量场景（`pageList` 中的 `convertToVO` 调用）同上，但批量查询时一次性收集所有非空 `recalcSourceOrderId`，批量 `selectBatchIds` 后映射 `Map<Long, String>` 填充各 VO，避免 N+1。

#### 4.2.2 前端变更

**`admin-web/src/api/order.ts`**：

`OrderAdminVO` 接口新增字段：
```typescript
recalcSourceOrderId?: number   // 新增：用于通过 ID 跳转到原订单详情
recalcSourceOrderNo?: string   // 新增：用于展示原订单号
```

**`admin-web/src/views/order/index.vue`**：

在订单详情弹窗 `<a-descriptions>` 区块中（现有字段之后，退款信息区块之前）新增“重算关联”行：

```html
<a-descriptions-item label="重算关联" :span="2">
  <template v-if="detailRecord?.recalcSourceOrderNo && detailRecord?.recalcSourceOrderId">
    <a @click="openDetailById(detailRecord.recalcSourceOrderId)">
      查看原订单：{{ detailRecord.recalcSourceOrderNo }}
    </a>
  </template>
  <template v-else>—</template>
</a-descriptions-item>
```

- `detailRecord.recalcSourceOrderNo` 或 `recalcSourceOrderId` 为空时显示“—”。
- 新增 `openDetailById(id: number)` 方法：内部调用 `getOrderDetail(id)`，将返回结果赋给 `detailRecord` 并打开详情弹窗。这与现有 `openDetail(record)` 方法并存（现有方法接收完整 VO，新方法按 ID 重新查询）。

**设计理由**：`OrderAdminVO` 中同时返回 `recalcSourceOrderId`（Long）和 `recalcSourceOrderNo`（String）两个字段，避免仅凭 `orderNo` 字符串反查 ID 的开销。前者用于前端 `openDetailById` 跳转，后者用于展示。

#### 4.2.3 TypeScript 接口变更清单

`admin-web/src/api/order.ts`：
```typescript
export interface OrderAdminVO {
  // ... 现有字段 ...
  recalcSourceOrderId?: number   // 新增
  recalcSourceOrderNo?: string   // 新增
}
```

### 4.3 Fix 3：Webhook 签名规范文档（Task 0-2）

#### 4.3.1 文档定位

`docs/设备接入/webhook签名规范.md` — 面向设备适配器开发者的对接描述文档，说明如何构造签名请求。

#### 4.3.2 文档内容梗概

文档应包含以下章节，全部基于 `WebhookVerificationFilter` 已实现逻辑：

1. **概述**：平台 Webhook 签名校验使用 HMAC-SHA256，校验失败返回 401。
2. **签名算法**：`Base64(HMAC-SHA256(secret, timestamp + nonce + rawBody))`
3. **请求头**：`X-Sign`（签名字符串）、`X-Timestamp`（Unix 毫秒时间戳）、`X-Nonce`（UUID v4 随机串）。
4. **校验规则**：时间戳偏差 ±5 分钟、nonce 10 分钟内不可重复、`deviceSn` 字段必须存在于 JSON body 中。
5. **Secret 管理**：每个停车场独立配置 `webhook_secret`，通过平台接口获取（或由管理员在平台配置后分发给适配器部署方）。
6. **错误响应**：统一 `HTTP 401 {"code":401,"message":"Unauthorized"}`，不返回具体校验失败原因。
7. **示例代码**：提供一次正确签名请求的 HTTP 示例（含请求头、请求体 JSON、签名字符串的计算过程伪代码）。

## 5. Files To Change

### 5.1 Fix 1：车牌校正

| 文件 | 操作 | 说明 |
|------|------|------|
| `parking-boot/src/main/resources/db/migration/V20260902003__correct_plate_fields.sql` | **新增** | 向 `recognition_event_log` 添加 4 字段 |
| `parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java` | **改造** | 新增 4 字段及 getter/setter |
| `parking-system/src/main/java/com/jushan/system/controller/BoothRecognitionController.java` | **新增** | 校正接口 |
| `parking-system/src/main/java/com/jushan/system/service/RecognitionCorrectionService.java` | **新增** | 校正业务逻辑（校验 + 更新） |
| `parking-system/src/main/java/com/jushan/system/ws/BoothWebSocketPublisher.java` | **改造** | `sendRecognitionEvent` 负载新增 `correctedPlate` / `correctionType` |
| `booth-web/src/components/PlateCorrectionModal.vue` | **新增** | 校正弹窗组件 |
| `booth-web/src/views/monitor/index.vue` | **改造** | 事件行点击增加校正入口 + 校正标记展示 |
| `booth-web/src/api/monitor-types.ts` | **改造** | `RecognitionEvent` 和 `RecognitionEventPayload` 均新增 `correctedPlate` / `correctionType` 可选字段 |
| `booth-web/src/api/monitor.ts` | **改造** | 新增 `correctPlate` API 函数 |

### 5.2 Fix 2：订单重算关联展示

| 文件 | 操作 | 说明 |
|------|------|------|
| `parking-system/src/main/java/com/jushan/system/vo/OrderAdminVO.java` | **改造** | 新增 `recalcSourceOrderId` / `recalcSourceOrderNo` |
| `parking-system/src/main/java/com/jushan/system/controller/OrderAdminController.java` | **改造** | `convertToVO` 填充重算关联字段 |
| `admin-web/src/api/order.ts` | **改造** | `OrderAdminVO` 接口新增字段 |
| `admin-web/src/views/order/index.vue` | **改造** | 详情弹窗新增“重算关联”区域 + `openDetailById` 方法 |

### 5.3 Fix 3：Webhook 签名文档

| 文件 | 操作 | 说明 |
|------|------|------|
| `docs/设备接入/webhook签名规范.md` | **新增** | 签名规范文档 |

## 6. Testing Strategy

### 6.1 Fix 1

- **单元测试**：`RecognitionCorrectionService` 单元测试覆盖：正常校正、非 PROCESSED 状态拒绝、重复校正拒绝、车牌格式非法拒绝、车牌与原值相同拒绝、事件不存在 404。
- **集成测试**：`BoothRecognitionController` 集成测试覆盖：校正成功返回 200 + 数据库字段已更新、WebSocket 负载包含 `correctedPlate`。
- **前端验证**：手动测试 EXPOSED 场景 — 岗亭监控页点击识别事件 → 弹出校正弹窗 → 输入正确车牌号 → 提交 → 事件列表刷新并显示校正后车牌 → 已校正事件点击弹窗为只读模式。
- **权限测试**：非岗亭管理员角色调用接口应返回 403。

### 6.2 Fix 2

- **集成测试**：`OrderAdminController` 测试中验证有 `recalcSourceOrderId` 的订单在 VO 中正确填充 `recalcSourceOrderNo`；无关联的订单字段为 `null`；原订单已删除场景不报异常。
- **前端验证**：手动测试有重算关联的订单详情弹窗显示“重算关联”链接 → 点击可跳转到原订单详情 → 无重算关联的订单不显示该区域。

### 6.3 Fix 3

- **文档审查**：由开发团队审阅文档内容与 `WebhookVerificationFilter` 源码是否一致（签名算法、容差参数、错误响应格式）。
- **对接验证**：文档本身无需自动化测试；文档示例代码可由适配器开发者按示例构造请求并验证可被平台接受。

## 7. Risks And Mitigations

### 7.1 Fix 1

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 校正操作误覆盖正确识别结果 | 审计追溯混乱 | 仅 `PROCESSED` 状态可校正；校正不可变且不可删除；`correctedPlate` 单独存储不影响原始 `plateNumber`；`@BusinessLog` 记录操作人全链路可追溯 |
| 岗亭管理员越权校正非其管理车场的事件 | 数据越权 | `scopeResolver.validateAccess(event.getParkingLotId())` 在 Controller 层强制校验数据范围 |
| 校正推送到 WebSocket 但前端未收到（网络波动） | 前端事件列表未更新 | WebSocket 推送在 try-catch 中，失败记 warn 不丢数据（前端下次切换或刷新可拉取完整列表） |

### 7.2 Fix 2

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 批量分页查询中 N+1 查原订单 | 性能劣化 | 批量 `selectBatchIds` 收集 `recalcSourceOrderId` 集合后一次性查询，映射结果到各 VO |
| 原订单被物理删除（软删除已恢复硬删）后 `selectById` 返回 null | 字段展示不完整 | 容错处理：null 时不抛异常，`recalcSourceOrderNo` 设为 null，前端展示“—” |

### 7.3 Fix 3

| 风险 | 影响 | 缓解措施 |
|------|------|----------|
| 文档描述与实际实现不一致 | 对接方构造的签名请求被平台拒绝 | 文档中所有参数值直接引用 `WebhookVerificationFilter` 源码常量（如 `TIMESTAMP_TOLERANCE = 5 分钟`），并附源码文件路径引用 |

## 8. Decision Summary

- **校正类型固定值**：`MANUAL_CORRECTION`，预留 `AUTO_CORRECTION` 用于未来系统自动纠错。
- **校正不可变**：`corrected_plate IS NOT NULL` 时拒绝二次校正，应用层强约束 + SQL `WHERE corrected_plate IS NULL` 条件。
- **仅 PROCESSED 状态可校正**：`RECEIVED`、`PROCESSING`、`FAILED` 状态均不可校正。
- **不修改 parking_record**：校正仅更新 `recognition_event_log`，与现有进出场链路完全解耦。
- **WebSocket 推送更新**：校正成功后通过已有 `sendRecognitionEvent` 推送更新后的完整事件数据，前端利用 `eventId` 匹配更新列表项。
- **订单重算 VO 字段**：同时返回 `recalcSourceOrderId`（Long）和 `recalcSourceOrderNo`（String），前端用前者导航、后者展示。
- **批量填充优化**：`convertToVO` 批量调用时使用 `selectBatchIds` 后 Map 映射，避免 N+1。
- **签名文档内容**：完全基于 `WebhookVerificationFilter.java` 已实现逻辑描述，不引入未实现参数。
- **Flyway 迁移编号**：新迁移文件名为 `V20260902003__correct_plate_fields.sql`，接续当前最新迁移 `V20260902002`。
