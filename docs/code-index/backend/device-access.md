# 模块：device-access（设备接入服务）

> **包路径**：`device-access/`（独立多模块 Maven 工程，根 pom 含 7 个子模块）
> **职责**：与硬件设备通信（MQTT）、协议适配（臻识/信路通/芊熠）、Webhook 推送事件到 Platform、设备注册与心跳管理。
> **最近更新**：2026-07-21

---

## 子模块一览

| 模块(Artifact) | 类数 | 职责 |
|---|---|---|
| `device-access-api` | 35 | REST API 层：设备注册/详情/状态/控制（开闸/关闸/语音/显示屏）；含品牌协调器（BrandCommandDispatcher / QianyiDeviceCoordinator / DeviceMonitorService） |
| `device-access-common` | 35 | 公共层：实体（Device/DeviceRelation/DeviceProduct/EventOutbox）+ DTO + Mapper |
| `device-access-adapter` | 15 | 设备协议适配：臻识（Zhenshi）+ 信路通（Xinlutong）+ 芊熠（Qianyi）+ 显示屏（OlmM1d）+ 心跳 |
| `device-access-event` | 6 | 事件模块：DeviceEvent、Webhook 转发、EventOutbox 重试、发布器 |
| `device-access-mqtt` | 6 | MQTT 连接：网关（MqttGatewayImpl，支持动态订阅设备 pubtopic）+ 消息监听 + 连接初始化 |
| `device-access-registry` | 3 | 注册中心：DeviceRegistry / DeviceProductRegistry / DeviceRelationRegistry |
| `device-access-starter` | 5 | 启动入口：Spring Boot 自动装配 |

---

## 与 parking-system 的交互

1. **开闸/关闸**：`parking-system` → HTTP → `device-access-api`（`/api/v1/devices/{sn}/gate/open`）
   - Platform 侧客户端：`com.jushan.system.client.DeviceAccessClient`
2. **识别事件**：设备上报 → MQTT → adapter 解析 → Webhook → `DeviceWebhookController`（`parking-system`）
3. **心跳/注册**：设备通过 MQTT 上报 → adapter 处理 → 记录状态

## 芊熠（Qianyi）接入关键类

| 类 | 位置 | 功能 |
|---|---|---|
| `QianyiMessageHandler` | adapter/qianyi | 处理芊熠 MQTT 注册/心跳/识别结果；发送 iooutput、barrierKeepOpen、syncSysTime、RS485 等命令并等待应答 |
| `QianyiCommandResult` | adapter/qianyi | 芊熠命令应答结果封装（status 字符串：ok/错误描述） |
| `QianyiDeviceCoordinator` | api | 芊熠设备命令编排：查设备 → 能力校验 → MQTT 校验 → Handler → 等待应答（开/关闸、常开、对时、显示屏、语音、外设） |

> 数据库变更：`device-access-starter/src/main/resources/migration-v0.5-qianyi.sql`
