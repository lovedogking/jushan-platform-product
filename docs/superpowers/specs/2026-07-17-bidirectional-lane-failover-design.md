# 双向通道/主备相机业务逻辑 — 设计规格说明书（任务包 3-5）

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| **文档名称** | 双向通道/主备相机业务逻辑设计规格说明书 |
| **版本号** | V1.0 |
| **编写日期** | 2026-07-17 |
| **对应任务包** | 3-5：双向通道/主备相机业务逻辑 |
| **需求依据** | 需求规格说明书 V1.2 §3.1.5（ADMIN-004 双向通道）、§3.1.6（ADMIN-005 主备相机）、§7.2（设备管理） |
| **前置依赖** | 任务包 0-3：模型层扩展（recognitionDirection、cameraRole 字段 + 索引移除 + DeviceWebhookService 方向判定）已完成 |

---

## 1. 目标

在任务包 0-3 已完成模型层扩展的基础上，完成业务层和 UI 层的全链路收尾：双向车道识别事件方向由相机 `recognitionDirection` 推导、主备相机切换后的记录来源标记、运营端车道管理页面的双向相机配置引导，以及岗亭端监控页面的主备相机状态可视化。

整个改造在设计上确保零行为变更（zero behavior change）对现有单向车道产生——VARCHAR 列 DEFAULT NULL + 非 null 值仅在有主备配置时写入，所有单向车道场景等价于未实施本任务包之前的行为。

---

## 2. 非目标（本期不做）

| 项目 | 说明 |
| :--- | :--- |
| 相机在线状态实时探测 | 设备在线性由现有的设备状态轮询/Webhook 心跳机制覆盖，CameraFailoverService 已实现了离线→切换、上线→恢复的完整逻辑（任务包 0-3），本次不做改动 |
| 备相机独立计费或独立业务逻辑 | 备相机仅作为主相机的冗余识别源，识别事件与主相机进入完全相同的入场/出场处理管线，不作区分 |
| 潮汐模式（tideMode）的方向切换联动 | 潮汐模式下车道方向动态变化暂不实现，本期双向车道方向始终由绑定的相机 recognitionDirection 决定 |
| 岗亭端相机级别的开闸控制 | 开闸按 laneId 执行（现有 DeviceService.openGateByLane），不受主备切换影响，不新增相机级别的开闸 API |
| 备相机离线告警差异化 | 备相机离线仅记录日志，不推送到岗亭端告警面板（告警仅针对主相机离线触发 CAMERA_FAILOVER） |

---

## 3. 背景与现状

### 3.1 已完成内容（任务包 0-3）

| 组件 | 已交付功能 |
| :--- | :--- |
| `Device` 实体 | `recognitionDirection`（1=ENTRY, 2=EXIT）、`cameraRole`（1=PRIMARY, 2=BACKUP）字段 |
| `DeviceService.bindLane/validateCameraLaneBinding` | 双向车道绑定相机强制指定 recognitionDirection，同一方向最多 1 主 + 1 备 |
| `CameraFailoverService` | 主相机离线→切换至备相机、主相机恢复→切回主相机、60s 冷却期、创建 CAMERA_FAILOVER / CAMERA_RECOVERY 告警、`getActiveSource(laneId, direction)` 查询当前活跃来源 |
| `DeviceWebhookService.determineDirection()` | Webhook 入口优先用相机 recognitionDirection、兜底车道类型推断，MIXED 车道 + 无相机方向保留事件原始 direction |
| Admin 设备管理页 | 已展示 recognitionDirection 和 cameraRole 字段 |
| 数据库迁移 | `V20260717004__device_camera_direction_role.sql` 已完成：移除 `uq_lane_device_type` 唯一索引、新增 recognition_direction/camera_role 字段、存量数据回填 |

### 3.2 当前差距

1. **Consumer 未利用 camera recognitionDirection**：`RecognitionEventConsumer.validateAndStandardize()` 在双向车道（type=3）场景下不覆盖 payload.direction，入场/出场路由完全依赖事件发布端填写的 direction，在 Webhook 链路中这已被 DeviceWebhookService 覆盖，但 Mock/人工触发路径未覆盖。
2. **记录未标记相机来源**（PRIMARY/BACKUP）：`recognition_event_log`、`parking_record`、`exit_record` 三张表均无 `camera_source` 字段。当主备切换发生后，无法区分某次识别是主相机还是备相机完成的。
3. **运营端车道管理页缺少双向配置引导**：双向车道（type=3）的 entryCameraId/exitCameraId 为原始数字输入框，无相机下拉建议，无方向缺失提示。
4. **岗亭端监控页无法区分主备**：LaneCard 使用单一 `deviceOnline` 布尔值，双相机车道无法展示主备相机各自状态，主相机离线（已切到备）缺少视觉提示。

---

## 4. 设计决策汇总

| # | 决策点 | 结论 | 理由 |
|---|--------|------|------|
| 1 | camera_source 存储方式 | VARCHAR 列（'PRIMARY'/'BACKUP'/NULL），存储在 `recognition_event_log`、`parking_record`、`exit_record` 三表 | 存储优于 JOIN 推导：① 记录不可变——主备可能在写入后再次切换，JOIN 推导结果会是"当前"状态而非"事件发生当时"的状态；② 查询性能——列表/报表查询不需要多表关联 CameraFailoverService 的内存状态 |
| 2 | camera_source 写入时机 | Consumer 处理事件时调用 `CameraFailoverService.getActiveSource(laneId, direction)` 获取，随事件日志更新和 EntryService/ExitService 调用传入 | 事件处理时刻的活跃相机来源是唯一的真实来源；后续切换不影响已写入记录 |
| 3 | 双向车道方向推导 | Consumer 中，当 lane.type == 3 且 device.recognitionDirection != null 时，覆盖 payload.direction | DeviceWebhookService 已在 Webhook 入口完成推导，Consumer 的覆盖是对非 Webhook 路径（Mock/人工触发）的兜底 + 防御性二次确认 |
| 4 | 运营端相机配置引导方式 | 软引导（非阻塞 hints），保存时不做硬校验 | 允许分步配置：管理员可先创建双向车道再逐步绑定相机，避免强制一次性完成所有绑定 |
| 5 | 岗亭端监控 LaneCard 扩展 | Lane 接口新增 `cameras` 数组，每项含 deviceId/name/role/online/isActive；LaneCard 模板按 cameras.length 分支渲染——单相机保持现有行为，多相机展示主备标签 | 最小侵入：单相机车道渲染路径不变，仅双相机车道走新分支 |
| 6 | RecognitionEventPayload 扩展 | 新增 `cameraSource` 字段（String, nullable） | 作为 Consumer → EntryService/ExitService 的参数传递载体，无需新增独立 DTO |
| 7 | 不新增独立告警类型 | CAMERA_FAILOVER / CAMERA_RECOVERY 告警已在任务包 0-3 由 CameraFailoverService 创建 | 无需重复创建，现有告警已满足操作审计需求 |

---

## 5. 数据库设计

### 5.1 三表新增 camera_source 字段

```sql
-- =============================================================================
-- 任务包 3-5：识别事件/停车记录/出场记录新增相机来源标记
-- =============================================================================

ALTER TABLE recognition_event_log
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE parking_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '入场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE exit_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '出场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';
```

### 5.2 字段语义

| 表 | camera_source 值 | 含义 |
| :--- | :--- | :--- |
| `recognition_event_log` | PRIMARY | 本次识别由主相机完成 |
| | BACKUP | 本次识别由备相机完成（主相机已离线切换） |
| | NULL | 单相机车道或无主备配置，不适用此标记 |
| `parking_record` | PRIMARY/BACKUP/NULL | 标记创建该停车记录的入场识别事件来自哪个相机 |
| `exit_record` | PRIMARY/BACKUP/NULL | 标记创建该出场记录的出场识别事件来自哪个相机 |

### 5.3 Flyway 迁移脚本

**文件名**：`V20260901001__add_camera_source.sql`

以下 Flyway 日期序号 `20260901` 基于当前项目最新迁移 `V20260828001` 之后选取，确保与既有迁移顺序不冲突。实际提交时可根据仓库最新迁移序号调整。

### 5.4 向后兼容性

- 三列均 `DEFAULT NULL`、`nullable`，对存量数据不产生任何影响
- 现有 SQL 查询无需改动（SELECT * 不受新增列影响，MyBatis-Plus 实体新增字段后 INSERT/UPDATE 动态 SQL 自动忽略 null 列）
- 单向车道场景下 Consumer 不设置 camera_source，与迁移前行为完全等价

---

## 6. 实体层变更

### 6.1 RecognitionEventLog

新增 `cameraSource` 字段（String），getter/setter：

```java
/** 相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置 */
private String cameraSource;

public String getCameraSource() { return cameraSource; }
public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
```

### 6.2 ParkingRecord

同上，新增 `cameraSource` 字段（String），getter/setter。

### 6.3 ExitRecord

同上，新增 `cameraSource` 字段（String），getter/setter。

### 6.4 RecognitionEventPayload

新增 `cameraSource` 字段及 Fluent 方法：

```java
/** 相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=不适用 */
private String cameraSource;

public RecognitionEventPayload cameraSource(String cameraSource) {
    this.cameraSource = cameraSource;
    return this;
}

public String getCameraSource() { return cameraSource; }
public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
```

---

## 7. Consumer 改造

### 7.1 新依赖注入

`RecognitionEventConsumer` 构造函数新增：

```java
private final CameraFailoverService cameraFailoverService;
```

### 7.2 双向车道方向推导（`validateAndStandardize` 扩展）

在现有 `validateAndStandardize()` 方法的**车道方向校验块结尾**（当前第 271 行的 `}` 之后、第 274 行格式校验之前）插入：

```java
// 3.5b 双向车道：相机有识别方向时，覆盖事件方向
//      确保入场/出场路由基于相机实际安装位置，而非事件发布端填写值
if (lane != null && lane.getType() != null && lane.getType() == 3
        && device.getRecognitionDirection() != null) {
    String cameraDirection = device.getRecognitionDirection() == 1 ? "ENTRY" : "EXIT";
    if (!cameraDirection.equals(payload.getDirection())) {
        log.info("双向车道方向由相机推导覆盖: lane={} device={} cameraDirection={} originalDirection={}",
                lane.getName(), device.getId(), cameraDirection, payload.getDirection());
        payload.setDirection(cameraDirection);
    }
}
```

**关键设计点**：
- 仅在双向车道（type=3）且相机有 recognitionDirection 时生效
- 单向车道（type=1/2）现有逻辑不受影响（方向不匹配直接 FAILED）
- 此覆盖与 DeviceWebhookService.determineDirection() 形成双层防御：Webhook 入口层 + Consumer 校验层的方向推导一致，非 Webhook 来源（Mock/人工）在 Consumer 层补齐

### 7.3 相机来源标记

在 `validateAndStandardize()` 返回 `result.success = true` 之后、`onRecognitionEvent()` 调用 `handleEntry/handleExit` 之前，设定 payload 的 cameraSource：

```java
// ---- 3.6b 标记相机来源（主备切换场景） ----
if (result.success) {
    // 仅当设备绑定了车道且有识别方向时尝试获取活跃来源
    if (device.getLaneId() != null && device.getRecognitionDirection() != null) {
        String source = cameraFailoverService.getActiveSource(
                device.getLaneId(), device.getRecognitionDirection());
        payload.setCameraSource(source);
    }
}
```

**设计决定**：`cameraFailoverService.getActiveSource()` 返回值永远非 null（内存状态未初始化时默认 PRIMARY）。对于单相机车道，记录 `camera_source = 'PRIMARY'`（语义：该车道所有识别均由唯一相机完成）。NULL 值仅出现在设备未绑定车道或未设置 `recognitionDirection` 的退化场景（如尚未人工回填的存量双向车道相机），此时 Consumer 跳过 camera_source 标记。

### 7.4 事件日志传递 cameraSource

`updateEventLog()` 方法在更新 `RecognitionEventLog` 时增加 `cameraSource` 的设置：

```java
private void updateEventLog(RecognitionEventPayload payload, ProcessingResult result) {
    RecognitionEventLog update = new RecognitionEventLog();
    update.setStandardizedPlate(result.standardizedPlate);
    // 新增：记录相机来源
    if (payload.getCameraSource() != null) {
        update.setCameraSource(payload.getCameraSource());
    }
    if (result.success) {
        update.setStatus("PROCESSED");
    } else {
        update.setStatus("FAILED");
        update.setFailureReason(result.failureReason);
    }
    // ... 其余不变
}
```

### 7.5 EntryService / ExitService 接收 cameraSource

`EntryService.handleEntry()` 和 `ExitService.handleExit()` 的方法签名不改变——它们已接收 `RecognitionEventPayload`，payload 中已有 `cameraSource` 字段。

#### EntryService 改造

`createParkingRecord()` 中，创建 `ParkingRecord` 后增加：

```java
// 记录相机来源（主备切换场景标记）
if (payload.getCameraSource() != null) {
    record.setCameraSource(payload.getCameraSource());
}
```

此设置在 `recordMapper.insert(record)` 之前完成，确保 INSERT 语句包含 camera_source 列。

#### ExitService 改造

`createExitRecord()` 中，创建 `ExitRecord` 后增加：

```java
// 记录相机来源
if (payload.getCameraSource() != null) {
    exitRecord.setCameraSource(payload.getCameraSource());
}
```

同样在 `exitRecordMapper.insert(exitRecord)` 之前完成。

`createNoRecordExit()` 和 `createExitRecordForArrears()` 也按相同方式处理。

---

## 8. 运营端车道管理页改造

### 8.1 文件位置

`admin-web/src/views/parking/ParkingLaneManage.vue`

### 8.2 现有状态

- 车道类型下拉已包含 type=3（双向）选项
- 入口相机和出口相机使用 `a-input-number`（仅输入数字 ID），双向时两者均显示
- **无**相机下拉选择、无方向过滤、无配置完整性提示

### 8.3 改造点

#### 8.3.1 引入可绑定的相机列表 API

在后端新增或复用接口，返回指定停车场下可绑定到车道的相机列表。返回数据需包含：

```json
{
  "deviceId": 101,
  "deviceName": "东入口相机A",
  "recognitionDirection": 1  // 1=ENTRY, 2=EXIT, null=未指定
}
```

**实现方式**：在 `DeviceAdminController` 中新增 `GET /api/v1/admin/devices/available-for-lane` 端点，接受 `parkingLotId` 参数，返回该车场下所有已启用且设备类型为 CAMERA 的设备列表，含 `recognitionDirection` 字段。

#### 8.3.2 相机选择改为下拉

双向车道（type=3）时，入口相机和出口相机从 `a-input-number` 改为 `a-select`：

- **入口相机下拉**：过滤 `recognitionDirection == 1 || recognitionDirection == null` 的设备
- **出口相机下拉**：过滤 `recognitionDirection == 2 || recognitionDirection == null` 的设备
- 下拉项显示格式：`设备名 (SN)` 或仅 `设备名`
- 允许清空（allow-clear），保障分步配置

单向车道保持现有 `a-input-number` 行为不变。

#### 8.3.3 配置引导提示

保存双向车道后（或编辑时），在车道表单下方展示配置完整性提示：

```html
<!-- 双向车道配置引导区域，仅 type=3 时展示 -->
<div v-if="formData.type === 3" class="camera-guide" style="margin-top: 12px;">
  <a-alert v-if="hasEntryCamera && hasExitCamera" type="success" message="已配置完整" show-icon />
  <a-alert v-if="!hasEntryCamera" type="warning" message="建议添加入场方向相机" show-icon />
  <a-alert v-if="!hasExitCamera" type="warning" message="建议添加出场方向相机" show-icon />
</div>
```

`hasEntryCamera` 和 `hasExitCamera` 的计算逻辑：
- `hasEntryCamera`：`formData.entryCameraId != null && formData.entryCameraId > 0`
- `hasExitCamera`：`formData.exitCameraId != null && formData.exitCameraId > 0`

提示仅在满足条件时展示，不阻塞保存操作。

#### 8.3.4 表格列增强

车道列表表格中，双向车道行（type=3）在"相机模式"列旁展示一个简短的相机绑定摘要，例如：
- 已绑定入场相机但未绑定出场：`入场: 101 ✅ | 出场: ❌`
- 两者均已绑定：`✅ 双方向`

此摘要通过后端接口返回的 `entryCameraId` / `exitCameraId` 字段在前端计算，无需额外接口。

---

## 9. 岗亭端监控页改造

### 9.1 文件位置

`booth-web/src/views/monitor/index.vue` + `booth-web/src/api/monitor-types.ts` + `booth-web/src/stores/monitor.ts`

### 9.2 现有状态

- `Lane` 接口含单一 `deviceId?: number` 和 `deviceName?: string`
- `LaneCard` 使用 `store.deviceStatuses.find(d => d.deviceId === lane.deviceId)` 获取设备在线状态，输出单一 `deviceOnline` 布尔值
- 单相机和双相机车道渲染完全一致

### 9.3 后端 API 改造

#### 9.3.1 快照接口扩展

岗亭监控快照接口（`BoothMonitorController.getSnapshot`）的 `Lane` 返回对象需扩展，新增 `cameras` 数组：

```json
{
  "id": 1,
  "name": "东大门",
  "direction": "MIXED",
  "cameras": [
    {
      "deviceId": 101,
      "name": "东入口主相机",
      "role": "PRIMARY",
      "direction": "ENTRY",
      "online": true,
      "isActive": true
    },
    {
      "deviceId": 102,
      "name": "东入口备相机",
      "role": "BACKUP",
      "direction": "ENTRY",
      "online": true,
      "isActive": false
    },
    {
      "deviceId": 103,
      "name": "东出口主相机",
      "role": "PRIMARY",
      "direction": "EXIT",
      "online": true,
      "isActive": true
    }
  ]
}
```

**服务层实现**：在构建快照数据时，查询 `device` 表获取该车道下所有 CAMERA 类型的设备，读取其 `cameraRole`、`recognitionDirection`，搭配设备在线状态（从设备状态快照表或实时查询获取），计算 `isActive`（通过 `CameraFailoverService.getActiveSource()` 判断当前活跃来源是否匹配该设备）。

`isActive` 的计算规则：
- 若设备 role == PRIMARY 且 failover 状态中 activeSource == PRIMARY → `isActive = true`
- 若设备 role == BACKUP 且 failover 状态中 activeSource == BACKUP → `isActive = true`
- 其他情况 → `isActive = false`

### 9.4 前端类型扩展

#### 9.4.1 `monitor-types.ts`

新增 `LaneCamera` 接口，扩展 `Lane` 接口：

```typescript
/** 车道绑定的单个相机信息 */
export interface LaneCamera {
  deviceId: number
  name: string
  role: 'PRIMARY' | 'BACKUP'
  direction: 'ENTRY' | 'EXIT'
  online: boolean
  isActive: boolean
}

/** 车道（扩展） */
export interface Lane {
  id: number
  parkingLotId: number
  name: string
  code: string
  direction: 'ENTRY' | 'EXIT' | 'MIXED'
  status: string
  deviceId?: number        // 保留兼容：单相机场景继续使用
  deviceName?: string      // 保留兼容
  cameras?: LaneCamera[]   // 新增：多相机详情（含主备角色）
}
```

### 9.5 LaneCard 渲染分支

在 `laneCards` computed 中扩展逻辑，`index.vue` 模板新增双相机分支：

#### 9.5.1 `LaneCard` 接口扩展

```typescript
interface LaneCard {
  laneId: number
  laneName: string
  direction: string
  deviceId?: number
  deviceOnline: boolean
  isOffline: boolean
  charging: boolean
  latestEvent?: RecognitionEventPayload
  /** 新增：多相机模式下的相机详情 */
  cameras?: LaneCamera[]
  /** 新增：主相机是否离线（用于高亮告警） */
  primaryOffline: boolean
  /** 新增：当前活跃相机描述文本 */
  activeSourceLabel?: string
}
```

#### 9.5.2 计算逻辑

```typescript
const laneCards = computed((): LaneCard[] => {
  return store.lanes.map((lane) => {
    const cameras = lane.cameras || []
    const hasMultiCameras = cameras.length > 0

    // 单相机模式（兼容现有逻辑）
    if (!hasMultiCameras) {
      const device = lane.deviceId
        ? store.deviceStatuses.find((d) => d.deviceId === lane.deviceId)
        : undefined
      const latestEvent = store.recentEvents.find((e) => e.laneId === lane.id)
      const charging = store.chargePanelVisible && store.currentChargeInfo?.laneId === lane.id
      return {
        laneId: lane.id,
        laneName: lane.name || `车道 ${lane.id}`,
        direction: lane.direction,
        deviceId: lane.deviceId,
        deviceOnline: !!device?.online && !device?.stale,
        isOffline: !device || !device.online || device.stale,
        charging,
        latestEvent,
        cameras: [],
        primaryOffline: false,
        activeSourceLabel: undefined,
      }
    }

    // 多相机模式（新增分支）
    const primaryCameras = cameras.filter(c => c.role === 'PRIMARY')
    const primaryOffline = primaryCameras.some(c => !c.online)
    const activeCamera = cameras.find(c => c.isActive)
    const activeSourceLabel = activeCamera
      ? `当前: ${activeCamera.role === 'PRIMARY' ? '主相机' : '备相机'}`
      : undefined

    return {
      laneId: lane.id,
      laneName: lane.name || `车道 ${lane.id}`,
      direction: lane.direction,
      deviceOnline: cameras.some(c => c.isActive && c.online),
      isOffline: cameras.every(c => !c.online),
      charging: store.chargePanelVisible && store.currentChargeInfo?.laneId === lane.id,
      latestEvent: store.recentEvents.find((e) => e.laneId === lane.id),
      cameras,
      primaryOffline,
      activeSourceLabel,
    }
  })
})
```

#### 9.5.3 模板改造

在 LaneCard 的 `.lane-tags` 区域，单相机/多相机分叉渲染：

```html
<!-- 单相机标签（现有逻辑不变） -->
<template v-if="!lane.cameras || lane.cameras.length === 0">
  <a-tag :color="lane.deviceOnline ? 'success' : 'error'">
    {{ lane.deviceOnline ? '在线' : '离线' }}
  </a-tag>
</template>

<!-- 多相机标签（新增） -->
<template v-else>
  <a-tag v-for="cam in lane.cameras" :key="cam.deviceId"
    :color="cam.online ? (cam.isActive ? 'blue' : 'green') : 'error'">
    {{ cam.role === 'PRIMARY' ? '主' : '备' }}:{{ cam.direction === 'ENTRY' ? '入' : '出' }}
    {{ cam.online ? (cam.isActive ? '●' : '○') : '✕' }}
  </a-tag>
</template>
```

**主相机离线高亮**：当 `lane.primaryOffline` 为 true 时，LaneCard 增加 `lane-primary-offline` CSS class，应用橙色/红色边框样式：

```css
.lane-card.lane-primary-offline {
  border: 2px solid #f59e0b;
  background-color: rgba(245, 158, 11, 0.04);
}
```

同时在 LaneCard 标题区域追加一个警告图标（`ExclamationCircleOutlined`）和"主相机离线"文字。

---

## 10. 业务规则

### 10.1 双向车道方向推导

- **优先级**：相机 `recognitionDirection`（明确绑定）> 事件发布端 direction（默认来源）
- **单向车道不变**：type=1（入口）或 type=2（出口）的车道，事件 direction 必须与车道类型一致，不一致直接 FAILED
- **无相机方向的 MIXED 车道**：不覆盖 direction，保留事件发布端的值（Webhook 路径中 DeviceWebhookService 兜底按车道类型推断）

### 10.2 相机来源标记

- 仅当设备绑定了车道且有 `recognitionDirection` 时尝试写入 `camera_source`
- 写入值来自 `CameraFailoverService.getActiveSource(laneId, direction)`，保证记录时点的活跃相机来源
- 单相机车道：值为 'PRIMARY'（语义：所有识别由此相机完成）
- 无 `recognitionDirection` 的设备（如未回填的存量双向车道相机）：Consumer 跳过 camera_source 标记，三表保持 NULL

### 10.3 主备切换期间的数据一致性

- CameraFailoverService 的状态切换（`failoverStates` ConcurrentHashMap）在设备离线/上线回调中触发
- Consumer 在事件处理时读取此状态——如果恰好在切换瞬间，可能会有一两帧事件标记为旧来源，这是可接受的（状态切换本身不是 ACID 事务，最终一致性即可）
- 60s 冷却期防止抖动，切换和恢复的日志由 CameraFailoverService 记录

---

## 11. 文件变更清单

### 11.1 新建文件

| 文件 | 模块 | 说明 |
| :--- | :--- | :--- |
| `db/migration/V20260901001__add_camera_source.sql` | parking-boot | Flyway 迁移：三表新增 camera_source 列 |

### 11.2 修改文件（后端）

| 文件 | 改动内容 |
| :--- | :--- |
| `entity/RecognitionEventLog.java` | 新增 `cameraSource` 字段 + getter/setter |
| `entity/ParkingRecord.java` | 新增 `cameraSource` 字段 + getter/setter |
| `entity/ExitRecord.java` | 新增 `cameraSource` 字段 + getter/setter |
| `event/RecognitionEventPayload.java` | 新增 `cameraSource` 字段 + getter/setter + Fluent 方法 |
| `event/RecognitionEventConsumer.java` | ① 注入 CameraFailoverService；② validateAndStandardize 末尾新增双向车道方向推导；③ onRecognitionEvent 中设置 payload.cameraSource；④ updateEventLog 中设置 cameraSource |
| `service/EntryService.java` | createParkingRecord 中从 payload 读取 cameraSource 写入 ParkingRecord |
| `service/ExitService.java` | createExitRecord / createNoRecordExit / createExitRecordForArrears 中从 payload 读取 cameraSource 写入 ExitRecord |
| `controller/DeviceAdminController.java` | 新增 `GET /api/v1/admin/devices/available-for-lane` 端点，返回可绑定相机列表含 recognitionDirection |
| `controller/BoothMonitorController.java` | 快照接口 Lane 返回对象扩展 cameras 数组（含 deviceId/name/role/direction/online/isActive） |

### 11.3 修改文件（前端）

| 文件 | 改动内容 |
| :--- | :--- |
| `admin-web/src/views/parking/ParkingLaneManage.vue` | ① 双向车道相机选择改为下拉（按 recognitionDirection 过滤）；② 新增配置完整性提示区域；③ 表格增加相机绑定摘要列 |
| `booth-web/src/api/monitor-types.ts` | 新增 `LaneCamera` 接口，`Lane` 接口新增 `cameras?: LaneCamera[]` |
| `booth-web/src/stores/monitor.ts` | lanes 字段类型适配新 Lane 接口 |
| `booth-web/src/views/monitor/index.vue` | ① LaneCard 接口扩展 cameras/primaryOffline/activeSourceLabel；② laneCards computed 新增多相机分支；③ 模板新增双相机标签 + 主相机离线高亮样式 |

---

## 12. 测试策略

### 12.1 单元测试

| 测试类 | 测试点 |
| :--- | :--- |
| `RecognitionEventConsumerTest` | ① 双向车道 + camera recognitionDirection=ENTRY + payload direction=EXIT → direction 被覆盖为 ENTRY，调用 entryService（不调用 exitService）；② 双向车道 + camera recognitionDirection 为 null → direction 不变，按原始 direction 路由；③ 单向车道 recognitionDirection 非 null → 不影响现有方向校验逻辑；④ camera_source='BACKUP' 经 payload 传递后正确写入事件日志更新 |
| `CameraFailoverServiceTest` | 已有（任务包 0-3）：getActiveSource 返回正确值、冷却期检查、主备切换告警创建 |

### 12.2 集成测试

| 场景 | 验证点 |
| :--- | :--- |
| 双向车道入场（entry 方向） | Mock 发送 event direction=EXIT，lane=type3，device.recognitionDirection=1（ENTRY）→ Consumer 覆盖 direction 为 ENTRY → 委派 EntryService → parking_record 写入 camera_source='PRIMARY' |
| 双向车道出场（exit 方向） | Mock 发送 event direction=ENTRY，lane=type3，device.recognitionDirection=2（EXIT）→ Consumer 覆盖 direction 为 EXIT → 委派 ExitService → exit_record 写入 camera_source='PRIMARY' |
| 主备切换后识别标记 | ① 标记主相机离线 → CameraFailoverService 切换 activeSource=BACKUP；② Mock 发送识别事件 → Consumer 获取 camera_source='BACKUP'；③ recognition_event_log 和 parking_record 的 camera_source 均为 'BACKUP' |
| 主相机恢复后识别标记 | ① CameraFailoverService 切回 activeSource=PRIMARY；② 新的识别事件 camera_source 恢复为 'PRIMARY' |
| 单向车道零行为变更 | Mock 发送 type=1/2 车道的识别事件，验证 camera_source 为 'PRIMARY'（或 NULL，取决于实现），入场/出场流程与改造前完全一致 |
| 运营端双向车道配置 | ① 创建双向车道后，相机下拉按 recognitionDirection 正确过滤；② 仅绑定入场相机时提示"建议添加出场方向相机"；③ 双向绑定后显示"已配置完整" |
| 岗亭端主备可视化 | ① 双相机车道 LaneCard 显示主/备:入/出 标签；② 主相机在线时显示绿色 ○；③ 触发主相机离线→备切换，岗亭端 LaneCard 边框变黄 + 警告图标 |

### 12.3 已有测试适配

- `RecognitionEventConsumer` 现有关键测试用例（如方向不匹配→FAILED）的行为不变
- Consumer 构造函数新增 CameraFailoverService 参数，需更新所有 Consumer 实例化处的注入

---

## 13. 风险与缓解

### 风险 1：CameraFailoverService 内存状态丢失导致 camera_source 标记错误

- **严重程度**：低
- **影响**：服务重启后 `failoverStates` ConcurrentHashMap 清空，新事件的 camera_source 恢复到默认 PRIMARY，但实际可能主相机已离线
- **根因**：CameraFailoverService 的 failoverStates 是纯内存状态，无持久化
- **缓解措施**：
  - 服务重启后，CameraFailoverService 的默认返回值是 `CAMERA_SOURCE_PRIMARY`，这与"刚启动时主相机默认在线"的假设一致
  - 设备在线状态通过心跳/状态查询重新探测，一旦检测到主相机离线，CameraFailoverService.onDeviceOffline() 被调用并重建 failoverStates
  - 在活跃重建窗口期（数秒到数十秒）内，少数事件可能标记为 PRIMARY 而非 BACKUP，但这是可接受的——主备切换本质上是可观测性标记，不影响业务正确性（识别数据本身不因相机来源而改变处理逻辑）
  - 未来可考虑将 failoverStates 持久化到 Redis，但本期不做——任务包 0-3 的设计已明确这是内存状态

### 风险 2：双向车道方向覆盖覆盖了正确的 payload direction

- **严重程度**：低
- **影响**：如果某双向车道相机 recognitionDirection 配置错误（如绑定到入口方向但标记为 EXIT），事件将被错误路由到 ExitService
- **根因**：设备台账中 recognitionDirection 的人工配置错误
- **缓解措施**：
  - DeviceService.validateCameraLaneBinding() 已在绑定阶段校验相机方向与车道类型的一致性（任务包 0-3），最大限度地防止错误配置入库
  - Consumer 日志中明确记录方向覆盖事件（`"双向车道方向由相机推导覆盖"`），便于排查
  - 如果出现此类问题，运营端重新配置相机 recognitionDirection 后即刻生效，无需重启

### 风险 3：camera_source 列与既有查询的兼容性

- **严重程度**：低
- **影响**：某些硬编码列名的 SQL（非 MyBatis-Plus 动态 SQL）可能未包含新列
- **缓解措施**：
  - 所有 MyBatis-Plus 查询自动适配新增列（INSERT/UPDATE 动态 SQL 只写入非 null 列，SELECT 自动包含所有列）
  - 手动编写的 SQL（如 Flyway 中的存量数据修复脚本、报表查询）需单独检查，但本期不在 recognition_event_log / parking_record / exit_record 的查询路径上新增任何手动 SQL

---

## 14. 决策总结

- 三张表（recognition_event_log、parking_record、exit_record）新增 `camera_source VARCHAR(20) DEFAULT NULL` 列，存储 PRIMARY/BACKUP/null。
- RecognitionEventPayload 新增 `cameraSource` 字段，作为 Consumer → EntryService/ExitService 的参数载体。
- Consumer 在双向车道（type=3）场景下，若相机 recognitionDirection != null，覆盖 payload.direction，确保入场/出场路由正确。
- Consumer 在事件处理时调用 CameraFailoverService.getActiveSource(laneId, direction) 获取当前活跃相机来源，写入 payload.cameraSource。
- EntryService/ExitService 从 payload 读取 cameraSource，写入 ParkingRecord/ExitRecord 的 camera_source 列。
- 运营端车道管理页：双向车道相机字段改为按 recognitionDirection 过滤的下拉选择器，保存后展示配置完整性提示（入场缺/出场缺/已完整）。
- 岗亭端监控页：LaneCard 扩展 cameras 数组，双相机车道展示主备标签 + 在线状态 + 活跃指示，主相机离线时 LaneCard 边框高亮黄色并显示警告图标。
- 单向车道所有行为保持不变，camera_source 写入 PRIMARY 或无写入，向后完全兼容。
- 主备切换告警沿用 CameraFailoverService 已有的 CAMERA_FAILOVER / CAMERA_RECOVERY，不新增告警类型。
