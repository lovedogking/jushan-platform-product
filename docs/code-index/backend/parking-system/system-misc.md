# 索引：com.jushan.system 其它（cache/client/config/event/task/ws/job）

> **所属**：`parking-system` · `com.jushan.system`
> **最近更新**：2026-07-24

---

| 子包 | 类名 | 职责 |
|---|---|---|
| `cache`（3） | `ConditionalCacheConfig` / `CaffeineCacheConfig` / `CacheConfig` | Caffeine 缓存配置 |
| `client`（15） | `DeviceAccessClient` | Device Access HTTP 客户端（核心：开闸/关闸；v0.7 新增 `captureImage` 主动抓拍） |
| | `client/dto/CaptureResultDTO` | 抓拍结果 DTO（success / imageUrl / plateImageUrl / message） |
| | `DeviceAccessProperties` | Device Access 连接配置 |
| | 其余 | 对外 HTTP 客户端与属性 |
| `config`（2） | — | 系统级 @Configuration |
| `constant`（1） | — | 常量定义 |
| `enums`（1） | — | 枚举 |
| `event`（4） | `RecognitionEventPayload` | 识别事件载荷（Spring Event） |
| | 其余 | PaymentSuccessEvent / EventSource / PlateStandardizer |
| `job`（1） | — | 定时任务 |
| `mybatis`（3） | — | MyBatis 配置/拦截器 |
| `task`（5） | — | 异步任务调度 |
| `ws`（2） | `BoothWebSocketPublisher` / `BoothTopicAccessChecker` | 岗亭 WS 实时推送 / 主题订阅鉴权 |
