# 索引：com.jushan.system 其它（cache/client/config/event/task/ws/job）

> **所属**：`parking-system` · `com.jushan.system`
> **最近更新**：2026-07-21

---

| 子包 | 类名 | 职责 |
|---|---|---|
| `cache`（3） | `ConditionalCacheConfig` / `CaffeineCacheConfig` / `CacheConfig` | Caffeine 缓存配置 |
| `client`（16） | `DeviceAccessClient` | Device Access HTTP 客户端（核心：开闸/关闸） |
| | `DeviceAccessProperties` | Device Access 连接配置 |
| | 其余 | 对外 HTTP 客户端与属性 |
| `config`（2） | — | 系统级 @Configuration |
| `constant`（1） | — | 常量定义 |
| `enums`（1） | — | 枚举 |
| `event`（5） | `RecognitionEvent` | 识别事件（Spring Event） |
| | 其余 | 事件发布/消费基础设施 |
| `job`（1） | — | 定时任务 |
| `mybatis`（4） | — | MyBatis 配置/拦截器 |
| `task`（5） | — | 异步任务调度 |
| `ws`（3） | `WebSocketConfig` / `WebSocketInterceptor` / `WebSocketHandler` | WebSocket STOMP 岗亭实时推送 |
