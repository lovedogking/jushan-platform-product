# 模块：device-access（设备接入服务）

> **包路径**：`device-access/`（独立多模块 Maven 工程，根 pom 含 7 个子模块）
> **职责**：与硬件设备通信（MQTT）、协议适配（臻识/信路通/芊熠）、Webhook 推送事件到 Platform、设备注册与心跳管理。
> **最近更新**：2026-07-24

---

## 子模块一览

| 模块(Artifact) | 类数 | 职责 |
|---|---|---|
| `device-access-api` | 41 | REST API 层：设备注册/详情/状态/控制（开闸/关闸/语音/显示屏）；含品牌协调器（BrandCommandDispatcher / QianyiDeviceCoordinator / DeviceMonitorService）+ 抓拍图片模块（image 包） |
| `device-access-common` | 35 | 公共层：实体（Device/DeviceRelation/DeviceProduct/EventOutbox）+ DTO + Mapper |
| `device-access-adapter` | 15 | 设备协议适配：臻识（Zhenshi）+ 信路通（Xinlutong）+ 芊熠（Qianyi）+ 显示屏（OlmM1d）+ 心跳 |
| `device-access-event` | 6 | 事件模块：DeviceEvent、Webhook 转发、EventOutbox 重试、发布器 |
| `device-access-mqtt` | 6 | MQTT 连接：网关（MqttGatewayImpl，支持动态订阅设备 pubtopic；静态订阅含芊熠 `/serverAll` 注册、`aiot/plate/+` 默认上行、`/qymqtt/+/qymqttpost` 自定义上行）+ 消息监听 + 连接初始化 |
| `device-access-registry` | 3 | 注册中心：DeviceRegistry / DeviceProductRegistry / DeviceRelationRegistry |
| `device-access-starter` | 5 | 启动入口：Spring Boot 自动装配；v0.7 启用 Flyway 自动迁移（`db/migration/V20260724001__add_capture_capability.sql`） |

---

## 与 parking-system 的交互

1. **开闸/关闸**：`parking-system` → HTTP → `device-access-api`（`/api/v1/devices/{sn}/gate/open`）
   - Platform 侧客户端：`com.jushan.system.client.DeviceAccessClient`
2. **识别事件**：设备上报 → MQTT → adapter 解析 → Webhook → `DeviceWebhookController`（`parking-system`）
3. **心跳/注册**：设备通过 MQTT 上报 → adapter 处理 → 记录状态
4. **主动抓拍**：`parking-system` → HTTP → `device-access-api` `POST /api/v1/devices/{sn}/capture`（v0.7 芊熠优先 `snapshot`、回退 `tarkphoto`；v0.7.1 臻识 MQTT snapshot 两段式，图片落盘后返回 URL）

## 抓拍图片（v0.6，芊熠独立上传，协议 §7.1.2）

| 类 | 位置 | 功能 |
|---|---|---|
| `ImageUploadController` | api/image | `POST /api/v1/images/upload` 接收相机 multipart 上传（sn/plateSignTime/bigFile/smallFile），协议格式应答；ApiKey 豁免 |
| `ImageStorageService` | api/image | 图片落盘 `{storageDir}/{yyyyMMdd}/{sn}/{ts}.jpg`（`_plate` 为车牌图）；按 sn+utc_ts 构造可访问 URL；每日 03:20 清理过期图片（默认 30 天）；`saveBytes` 支持字节形式落盘（远程图片转存用） |
| `RemoteImageDownloader` | api/image | 远程图片转存：臻识等品牌事件携带的 OSS 签名 URL（约 1 小时过期）在事件到达时立即下载落盘，替换为本地可访问 URL；失败保留原地址 |
| `ImageProperties` | api/image | `device-access.image.*`：storage-dir / public-base-url / retention-days |
| `ImageWebConfig` | api/image | `GET /images/**` 静态映射（本地开发用；生产 nginx alias 直读磁盘） |

## 主动抓拍（v0.7）

| 类 / 接口 | 位置 | 功能 |
|---|---|---|
| `CaptureResultDTO` | api/dto | 抓拍结果：success / imageUrl / plateImageUrl / message |
| `DeviceCapability.CAPTURE` | common/enums | 设备能力枚举新增「主动抓拍」 |
| `DeviceService.capture` | api | 能力校验后按品牌分派 |
| `DeviceCoordinator.capture` | api | 协调器接口新增抓拍方法（v0.7）；臻识 v0.7.1 已实现，信路通仍为桩（抛 `UnsupportedOperationException`） |
| `BrandCommandDispatcher.capture` | api | 路由到对应品牌协调器 |
| `DeviceController.capture` | api | `POST /api/v1/devices/{deviceId}/capture` 触发抓拍 |
| `QianyiDeviceCoordinator.capture` | api | 芊熠：先 `snapshot` 取全景图，失败回退 `tarkphoto` 取车牌图；Base64 解码后由 `ImageStorageService.saveBytes` 落盘并返回 URL |
| `QianyiMessageHandler.sendSnapshot` | adapter | 下发 `snapshot` 命令，应答 `snapshot_rsp` 含 `picture` |
| `QianyiMessageHandler.sendTarkphoto` | adapter | 下发 `tarkphoto` 命令，应答 `tarkphoto_rsp` 含 `plate_pic` |
| `ZhenshiDeviceCoordinator.capture` | api | 臻识（v0.7.1）：两段式 — 下行 `snapshot` 命令回执确认后等待 `up/snapshot` 异步结果；优先 `image_content`（Base64 直传），为空则解码 `imgAbsolutePath`/`imgPath`（Base64 签名 OSS URL）下载落盘，下载失败退化返回签名 URL |
| `ZhenshiMessageHandler.sendSnapshot` | adapter | 下发 `snapshot` 命令；`pendingSnapshots` 按 sn 关联 `up/snapshot` 异步结果（先注册后下发防竞态） |
| 迁移 `V20260724002__zhenshi_capture_capability.sql` | starter/db/migration | 臻识品牌产品幂等追加 `CAPTURE` 能力（对齐平台侧 V20260906002 型号种子） |


> 事件链路：`QianyiMessageHandler` 透传 `full_pic_path`/`plate_pic_path` → `PlateRecognizedEventDispatcher` 对芊熠品牌按（sn+utc_ts）重写为可访问 URL → Webhook payload 增加 `plateImagePath`。相机端配置 `alonepush=1/aloneaddr/alone_port/alone_url=/images/upload`（nginx 反代到本接口）。
>
> 臻识链路：`ZhenshiMessageHandler` 对 `imagePath`/`image_path` 做 Base64 解码（同 license）；`quick_ivs_result` 无图仅记录不下发（避免平台 BR-08 去重把带图的 `ivs_result` 判为重复）；`PlateRecognizedEventDispatcher` 经 `RemoteImageDownloader` 将 OSS 限时 URL 转存为本地 URL 后再下发 Webhook。

## 芊熠（Qianyi）接入关键类

| 类 | 位置 | 功能 |
|---|---|---|
| `QianyiMessageHandler` | adapter/qianyi | 处理芊熠 MQTT 注册/心跳/识别结果；发送 iooutput（开闸 `sendOpenGate` ionum=0 / 关闸 `sendCloseGate` ionum=2，均 action=on 脉冲触发）、barrierKeepOpen、syncSysTime、RS485、`snapshot`/`tarkphoto` 等命令并等待应答。SN 统一小写规范化；下行主题三级解析 |
| `QianyiCommandResult` | adapter/qianyi | 芊熠命令应答结果封装（status 字符串：ok/错误描述） |
| `QianyiDeviceCoordinator` | api | 芊熠设备命令编排：查设备 → 能力校验 → MQTT 校验 → Handler → 等待应答（开/关闸、常开、对时、显示屏、语音、外设、主动抓拍） |

> 数据库变更：
> - `device-access-starter/src/main/resources/migration-v0.5-qianyi.sql`（手动历史迁移，用于新库初始化 schema + 种子）
> - `device-access-starter/src/main/resources/db/migration/V20260724001__add_capture_capability.sql`（Flyway 自动迁移，给芊熠 QY-01 追加 `CAPTURE` 能力）
> - `application.yml` 启用 `spring.flyway.baseline-on-migrate=true`；存量库首次启动自动 baseline 后执行 v0.7 迁移
