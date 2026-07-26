# 模块：device（设备管理）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/device/`
> **所属**：`parking-system` · `com.jushan.platform.modules.device`
> **职责**：设备台账 CRUD、厂商/型号管理、车道绑定、控闸设备解析、命令下发（开/关/常开/关闸/重启/触发识别/显示/语音/校时/白名单同步）、状态查询、Webhook 事件接入（识别联动分方向模板）、定时校时、显示屏/语音参数配置。
> **最近更新**：2026-07-25（v2.5：租户岗亭离线修复——device_status_snapshot 加入租户忽略表；手动放行出口写出场记录+查车类型；BoothMonitorService isActive 在线即活跃兜底；识别事件时间 Asia/Shanghai 时区；v2.4：设备语音/显示屏模板字段+识别联动）

**说明**：本模块于 2026-07-24 完成从 `com.jushan.system.*` 到 `com.jushan.platform.modules.device.*` 的完整迁移。旧 `system/entity/Device*.java`、`system/service/DeviceService.java`、`system/controller/DeviceController.java`、`system/dto/CreateDeviceRequest.java` / `UpdateDeviceRequest.java`、`system/vo/DeviceVO.java` / `DeviceStatusVO.java` 均已标 `@Deprecated`。

---

## 一、接口入口（Controller）

### DeviceController  `controller/DeviceController.java`
- **基础路径**：`/api/v1/admin/devices` ｜ **权限**：`device:*`
- **功能**：设备台账 CRUD、厂商/型号查询、车道绑定、控闸设备配置、命令下发、状态查询。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `device:write` | 创建设备 | `CreateDeviceRequest` | `R<DeviceVO>` |
| update | PUT | `/{id}` | `device:write` | 更新设备 | `id, UpdateDeviceRequest` | `R<DeviceVO>` |
| list | GET | `/` | `device:read` | 分页查询 | `page,size,parkingLotId,status,deviceType` | `R<IPage<DeviceVO>>` |
| detail | GET | `/{id}` | `device:read` | 设备详情 | `id` | `R<DeviceVO>` |
| listVendors | GET | `/vendors` | `device:read` | 厂商列表 | — | `R<List<DeviceVendor>>` |
| listModels | GET | `/vendors/{vendorId}/models` | `device:read` | 型号列表 | `vendorId` | `R<List<DeviceModel>>` |
| updateStatus | POST | `/{id}/status` | `device:write` | 启用/停用 | `id, action` | `R<Void>` |
| delete | DELETE | `/{id}` | `device:write` | 删除设备 | `id` | `R<Void>` |
| bindLane | POST | `/{id}/bind-lane` | `device:write` | 绑定车道 | `id, laneId` | `R<DeviceVO>` |
| unbindLane | POST | `/{id}/unbind-lane` | `device:write` | 解绑车道 | `id` | `R<DeviceVO>` |
| setExecutor | POST | `/{id}/set-executor` | `device:write` | 设置执行器 | `id, executorDeviceId` | `R<DeviceVO>` |
| listAvailableForLane | GET | `/available-for-lane` | `device:read` | 车道可选设备 | `parkingLotId` | `R<List<Map>>` |
| syncTime | POST | `/{id}/sync-time` | `device:write` | 校时 | `id, reason` | `R<TimeSyncResultDTO>` |
| openGateByLane | POST | `/gate/lane/{laneId}/open` | `device:operate` | 按车道开闸 | `laneId, reason` | `R<CommandResultDTO>` |
| lockGateByLane | POST | `/gate/lane/{laneId}/lock` | `device:operate` | 按车道常关 | `laneId, reason` | `R<CommandResultDTO>` |
| unlockGateByLane | POST | `/gate/lane/{laneId}/unlock` | `device:operate` | 按车道取消常关 | `laneId, reason` | `R<CommandResultDTO>` |
| openGate | POST | `/{id}/gate/open` | `device:operate` | 开闸 | `id, reason` | `R<CommandResultDTO>` |
| closeGate | POST | `/{id}/gate/close` | `device:operate` | 关闸 | `id, reason` | `R<CommandResultDTO>` |
| displayText | POST | `/{id}/display/text` | `device:operate` | 显示文字 | `id, content, direction, fontSize, color` | `R<DisplayResultDTO>` |
| displayConfig | POST | `/{id}/display/config` | `device:operate` | 显示配置 | `id, configType, intValue, stringValue` | `R<DisplayResultDTO>` |
| voiceControl | POST | `/{id}/voice` | `device:operate` | 语音控制 | `id, action, voiceId, variable` | `R<VoiceResultDTO>` |
| queryStatus | GET | `/{id}/status` | `device:read` | 实时状态 | `id` | `R<DeviceStatusVO>` |
| queryStatusBatch | POST | `/status/batch` | `device:read` | 批量状态（≤50） | `List<Long> deviceIds` | `R<List<DeviceStatusVO>>` |
| getLatestSnapshot | GET | `/{id}/status/snapshot` | `device:read` | 最新快照 | `id` | `R<DeviceStatusVO>` |

### DeviceWebhookController  `webhook/DeviceWebhookController.java`
- **基础路径**：`/api/v1/device-webhook` ｜ **权限**：Webhook 签名/IP 校验（非 `@RequirePermission`）

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| receiveEvent | POST | `/events` | 接收设备识别事件 | `Map<String,Object> body, HttpServletRequest` | `R<Void>` |

---

## 二、业务服务（Service）

> `DeviceService` 实现全部 4 个接口。拆分目的是让每个关注点可独立 mock 和测试。

### DeviceManagementService  `service/DeviceManagementService.java`（接口）
| 方法 | 功能 |
|---|---|
| create / update / list / get | 设备 CRUD |
| updateStatus / delete | 启用/停用/删除 |

### DeviceCommandService  `service/DeviceCommandService.java`（接口）
| 方法 | 功能 |
|---|---|
| syncTime | 设备校时 |
| openGateByLane / lockGateByLane / unlockGateByLane | 车道级控闸 |
| openGate / closeGate | 设备级开/关闸 |
| openGatePlaceholder | 人工开闸占位 |
| displayText / displayConfig | 屏幕显示 |
| voiceControl | 语音播报 |

### LaneDeviceBindingService  `service/LaneDeviceBindingService.java`（接口）
| 方法 | 功能 |
|---|---|
| bindLane / unbindLane | 车道绑定/解绑 |
| setExecutor | 设置执行器 |
| listAvailableForLane | 车道可选设备列表 |
| resolveGateDevice | **控闸设备解析**（优先级：gateDeviceId > GATE 设备 > CAMERA+OPEN_GATE） |

### DeviceStatusService  `service/DeviceStatusService.java`（接口）
| 方法 | 功能 |
|---|---|
| queryStatus / queryStatusBatch | 实时状态查询（受控并发 ≤5） |
| getLatestSnapshot / getLatestSnapshots | 快照读取 |

### DeviceVendorService  `service/DeviceVendorService.java`（具体类）
| 方法 | 功能 |
|---|---|
| listVendors / listModels | 厂商列表 / 型号列表 |

### 实现类
| 类 | 路径 | 说明 |
|---|---|---|
| `DeviceService` | `service/DeviceService.java` | 主实现，同时实现上述 4 个接口 |

### Webhook 相关
| 类 | 作用 |
|---|---|
| `DeviceWebhookService` | 接收事件 DTO → 校验幂等 → 发布 Spring 事件 |
| `DeviceWebhookEventHandler` | 监听事件 → 调用 RecognitionEventService 入场/出场 |
| `WebhookVerificationFilter` | 签名 + IP 白名单校验 |
| `WebhookSecurityConfig` | Webhook 路径 Spring Security 配置 |
| `WebhookSecurityStartupValidator` | 启动时校验密钥配置 |

---

## 三、领域对象

### Entity（`entity/`）

| 类名 | 作用 | 来源 |
|---|---|---|
| `Device` | 设备台账（T20），含 recognitionDirection/cameraRole/capabilities | 从 system/entity 迁入 |
| `DeviceCommandAudit` | 命令审计（P001） | 从 system/entity 迁入 |
| `DeviceModel` | 设备型号，含 capabilities | 从 system/entity 迁入 |
| `DeviceStatusSnapshot` | 状态快照（T24） | 从 system/entity 迁入 |
| `DeviceVendor` | 设备厂商 | 从 system/entity 迁入 |

### DTO（`dto/`）

| 类名 | 作用 | 来源 |
|---|---|---|
| `CreateDeviceRequest` | 创建设备请求 | 从 system/dto 迁入 |
| `UpdateDeviceRequest` | 更新设备请求 | 从 system/dto 迁入 |
| `DeviceWebhookEvent` | 设备推送事件 DTO | 原有 |

### VO（`vo/`）

| 类名 | 作用 | 来源 |
|---|---|---|
| `DeviceVO` | 设备视图 | 从 system/vo 迁入 |
| `DeviceStatusVO` | 设备状态视图 | 从 system/vo 迁入 |

---

## 四、数据层（Mapper）

> Mapper 仍位于 `com.jushan.system.mapper`（与 system.* 的 Mapper 共享路径，待后续统一迁移）。

| 类名 | 关键自定义查询 |
|---|---|
| `DeviceMapper` | `selectByIdIgnoreTenant`、`selectByLaneIdAndTypeIgnoreTenant` |
| `DeviceCommandAuditMapper` | — |
| `DeviceModelMapper` | — |
| `DeviceStatusSnapshotMapper` | — |
| `DeviceVendorMapper` | — |
