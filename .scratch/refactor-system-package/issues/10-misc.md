# 10 — Cache / Config / Event / Task / WS / 常量枚举（20 个）

**Type:** task
**Status:** resolved
**Blocked by:** none

## 范围

将 `com.jushan.system.*` 下剩余 20 个杂项文件按功能域迁入对应模块。

### Cache（3） → `modules/common/cache/`
- `ParamCacheStore.java`（接口）
- `RedisParamCacheStore.java`（实现）
- `VehicleListCacheStore.java`

### Config（2） → `modules/miniapp/config/`
- `WxMiniappConfig.java`
- `WxMiniappProperties.java`

### Event（4） → `modules/parking/event/`
- `EventSource.java`（枚举）
- `PaymentSuccessEvent.java`
- `PlateStandardizer.java`
- `RecognitionEventPayload.java`（237 行，booth 域？）

### Task/Scheduler/Job（7） → 各自模块
- `DeviceStatusPollingTask` → `modules/device/task/`
- `GateModeSyncRunner` → `modules/device/task/`
- `SessionTimeoutTask` → `modules/parking/task/`
- `FixedSpaceExpiryScheduler` → `modules/miniapp/task/`
- `VehicleListExpiryTask` → `modules/vehicle/task/`
- `MonthlyPassExpiryJob` → `modules/miniapp/job/`

### WebSocket（2） → `modules/booth/ws/`
- `BoothWebSocketPublisher.java`（317 行）
- `BoothTopicAccessChecker.java`

### 常量/枚举（2） → `modules/common/constant/`
- `ParamKeys.java`（183 行）
- `OrderStatus.java`（148 行，或归 parking？）

## 关键风险

- `RecognitionEventPayload` 归属：内容含 Booth 事件和 Parking 支付事件，建议归 `modules/booth/event/`
- `PlateStandardizer` 被 Booth 服务调用，归 `modules/booth/event/`
- WebSocket 文件较大（317 行），注意引用链
- 文件数多但每个引用范围小，逐个搬移编译验证

## 步骤

按文件逐个或小 batch 搬移，每个 batch 编译验证。

## Comments
