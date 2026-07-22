# 模块：device（设备 Webhook 接入）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/device/`
> **所属**：`parking-system` · `com.jushan.platform.modules.device`
> **职责**：接收 Device Access 推送的车牌识别事件 Webhook，校验签名/IP 白名单后将事件写入内部事件总线。
> **最近更新**：2026-08-12

**说明**：本模块无传统 Service 分层——核心逻辑在 `DeviceWebhookService`（事件处理 + 幂等）和 `DeviceWebhookEventHandler`（Spring 事件监听）。安全由过滤器链 `WebhookVerificationFilter` + `WebhookSecurityConfig` 实现。

---

## 一、接口入口

### DeviceWebhookController  `webhook/DeviceWebhookController.java`
- **基础路径**：`/api/v1/device-webhook` ｜ **权限**：Webhook 签名/IP 校验（非 `@RequirePermission`）

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| receiveEvent | POST | `/events` | 接收设备识别事件 | `Map<String,Object> body, HttpServletRequest` | `R<Void>` |

---

## 二、服务 / 事件处理

| 类 | 作用 |
|---|---|
| `DeviceWebhookService` | 接收事件 DTO → 校验幂等 → 发布 Spring `RecognitionEvent`；事件落库含全景图/车牌特写图 URL（imagePath/plateImagePath） |
| `DeviceWebhookEventHandler` | 监听 `RecognitionEvent` → 调用 `RecognitionEventService.handleEvent` 执行车辆入场/出场逻辑 |
| `WebhookVerificationFilter` | 过滤器：校验 Webhook 请求签名 + 来源 IP 白名单 |
| `WebhookSecurityConfig` | Webhook 路径的 Spring Security 配置（放行 + 过滤器注册） |
| `WebhookSecurityStartupValidator` | 启动时校验 Webhook 签名密钥是否配置 |

---

## 三、DTO

| 类名 | 作用 |
|---|---|
| `DeviceWebhookEvent` | 设备推送事件 DTO（车牌、通道、车场、时间戳等） |
