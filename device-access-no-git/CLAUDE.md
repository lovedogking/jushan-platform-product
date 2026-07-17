# CLAUDE.md

---

## 会话启动指令（最高优先级）

**每个开发会话开始时，必须先阅读以下文档：**

1. `CLAUDE.md`（本文档）— 项目宪法，定义边界、原则、当前版本目标
2. `docs/ARCHITECTURE.md` — 架构设计文档，定义模块职责、数据流、依赖关系
3. `docs/ROADMAP.md` — 演进路线图，定义当前阶段目标与后续演进方向
4. `docs/contracts/platform-device-access/` — Platform ↔ Device Access 共享契约（联合决策、目标接口、当前兼容事实）

**阅读完成后，请先总结你对当前项目的理解，再等待开发任务。**

---

## 开发过程中必须遵循

1. **不破坏已有架构。** 新增代码必须符合当前模块的职责边界和依赖方向。
2. **优先复用已有模块。** 新功能首先考虑在现有模块中实现，只有在现有模块无法承载时才新增模块。
3. **保持分层职责清晰。** 各层职责不得混淆：
   - `mqtt` — 只负责通信，不解析业务数据
   - `adapter` — 只负责协议适配，不处理 HTTP
   - `api` — 只负责 REST 接口，不碰厂商协议字段
   - `registry`（v0.2+）— 设备元数据管理（Device + Product + Relation）
   - `event`（v0.4）— 只负责统一事件输出
4. **新增模块需符合 ARCHITECTURE.md。** 任何新模块的引入必须有明确的架构理由，且符合演进式架构原则。
5. **若发现设计冲突，先提出方案，不直接修改代码。** 当需求的实现方式与现有架构设计冲突时，先向项目负责人说明冲突点和备选方案，获得确认后再动手。

---

## 项目身份

**Device Access** — 智能停车场系统的设备接入层。

### 核心边界（最高优先级）

Device Access **只做一件事**：让业务系统不用关心设备品牌和通信协议。

| ✓ 属于 Device Access | ✗ 不属于 Device Access |
|---|---|
| 接入各种品牌设备 | 停车记录 |
| 屏蔽设备协议差异 | 收费 |
| 接收设备事件（识别结果、心跳、状态变更） | 月卡 / 白名单 |
| 下发设备控制命令（开闸、关闸、校时、显示屏控制等） | 订单 |
| 输出统一设备能力 | 数据统计 / 报表 |
| 协议适配与转换 | 用户管理 |
| 设备事件推送（HTTP Webhook） | 业务规则判断（是否开闸/收费/白名单） |

> **Device Access 提供设备控制能力，但不做业务规则判断。**
> 
> 例如：Device Access 提供 `POST /gate/open` 开闸 API，但**是否开闸**由 Parking Platform 根据白名单/收费规则决定。Device Access 负责实现开闸命令代码（MQTT 下发给摄像头），并通过 REST API 暴露给业务侧调用。

---

## 架构哲学

**演进式架构** —— 不是预先设计出来的，是一步一步长出来的。

1. **先接通真实设备，再进行抽象**
2. **不做过度设计（YAGNI）**
3. **所有抽象必须来源于真实需求** — 出现重复代码时才提取
4. **基于一个品牌猜测另一个品牌，几乎一定是错的**

### 关于抽象

不要为了接口而定义接口。

所有抽象必须来源于真实需求，出现重复代码时才提取。

---

## 新增设备开发原则

当需要接入新品牌设备或新型号外设时，必须遵守以下原则：

### 1. 新品牌使用新 Adapter

新品牌设备接入时，在 `adapter/` 下创建新包（如 `adapter/fengjing/`），不修改已有 Adapter。

```
adapter
├── zhenshi/        # 臻识 C5H（已有，不修改）
│   └── display/    # 科发 OLM-M1D 协议（已有，不修改）
└── xinlutong/      # 信路通（新品牌示例）
```

### 2. 不修改已有 Adapter

已有品牌的 Adapter 代码是经过真机联调验证的。新需求应在新 Adapter 中实现，不在旧 Adapter 上叠加条件分支。

### 3. 不破坏统一接口

- API 端点路径、请求/响应格式保持向后兼容
- `Result<T>` 统一响应格式不变
- DeviceCapability 枚举可扩展，但已有能力不删除、不重命名

### 4. 保持 Device Access 职责边界

新增功能必须确认属于设备接入层职责：
- ✅ 协议适配、命令下发、事件解析
- ❌ 停车业务逻辑（收费规则、白名单判断、月卡管理）

### 5. 不引入业务层概念

Device Access 的领域模型中**不处理业务逻辑**，但允许存储业务侧传入的上下文字段（如 tenantId/parkingLotId/laneId），仅用于事件推送和日志追溯：
- ✅ 存储业务字段（设备注册时传入，事件推送时透传）
- ❌ 不基于业务字段做逻辑判断（如白名单/收费/开闸决策）

禁止出现的业务逻辑：
- `fee` / `order` / `payment`（费用/订单/支付）
- `monthly_card` / `white_list`（月卡/白名单）
- 基于 tenantId/parkingLotId 的权限判断

---

## 架构变更治理规则

任何新增需求或技术变化，在编码前必须先判断属于哪一类。

### 1. Core Capability（核心能力）

**定义**：影响系统长期结构的变化。

**判断标准**（满足任一即为 Core Capability）：

- 新增核心模块
- 修改模块职责边界
- 修改模块依赖方向
- 修改系统数据流
- 修改公共领域模型
- 修改跨模块接口契约
- 引入新的通信方式或基础设施能力

**动作**：先更新 `docs/ARCHITECTURE.md`，确认架构设计后再进行代码实现。

**示例**：

- 新增 Device Registry 模块
- 新增 Device Shadow
- 引入新的消息通信机制
- 修改 Event 模型

### 2. Future Capability（未来能力）

**定义**：已确认属于未来规划，但当前版本暂不实现的能力。

**动作**：更新 `docs/ROADMAP.md` 对应阶段。

> 注意：探索性想法不要直接加入 ROADMAP，避免 ROADMAP 变成需求列表。

**示例**：

- 新品牌设备接入规划
- OTA 固件升级
- 规则引擎
- 数字孪生
- 边缘 AI

### 3. Implementation Detail（实现细节）

**定义**：不改变系统架构边界的修改。

**动作**：只修改代码，不修改架构文档。

**示例**：

- Bug 修复
- 性能优化（不改变数据流）
- 内部代码重构（保持模块职责不变）
- 新增辅助工具类
- 单个接口实现优化

### 判断流程

新增需求必须按以下顺序判断：

1. **是否改变架构边界？** → 是：Core Capability
2. **是否属于未来规划但当前不实现？** → 是：Future Capability
3. **否则** → Implementation Detail

### 架构禁止事项

#### 1. 不为单个设备型号修改核心架构

设备型号差异必须通过 Adapter 层解决。

以下核心模块不能因某个品牌设备的特殊行为而改变整体设计：

- `registry`
- `mqtt`
- `api`
- `event`

#### 2. 品牌特殊逻辑禁止进入通用层

禁止以下行为：

- `mqtt` 模块出现品牌判断（如 `if (brand == "ZHENSHI")`）
- `api` 模块出现品牌判断
- `registry` 模块保存品牌专用业务逻辑

品牌差异必须封装在 Adapter 层：

```
Adapter
├── ZhenShiAdapter
├── FengJingAdapter
└── OtherAdapter
```

#### 3. 不因短期需求破坏长期演进

如果存在多个实现方案，优先选择：

- 当前简单
- 不阻塞未来扩展
- 保持架构边界清晰

禁止为了快速实现短期需求，采用会阻塞 ROADMAP 演进方向的方案。

> 注意：这不等同于"为未来功能提前设计"。当前不需要的抽象仍然不引入。核心是**选择不阻塞的方案**，而非**提前实现未来功能**。

---

## 当前版本：v0.4

**目标：接入第二品牌 + 统一设备控制 — 信路通 XLT-01 真机验证 + 臻识/信路通 开闸/关闸/校时/显示屏控制。**

- 设备：臻识 C5H（主要）+ 信路通 XLT-01（真机验证通过）
- 显示屏：科发 OLM-M1D LED 控制卡，通过臻识 C5H RS485 串口透传接入
- 通信：MQTT（EMQX Broker）+ HTTP REST API（Parking Platform → Device Access）
- 事件：`PlateRecognizedEvent` 统一格式，通过 HTTP Webhook 异步推送到 Parking Platform（v0.4 已实现）
- 无前端 UI：Device Access 只提供 API，业务侧负责 UI。frontend 模块已删除。

> **v0.4 当前状态（2026-07-14）**：
> - ✅ 设备侧开发完成：臻识/信路通 开闸/关闸/校时/显示屏控制
> - ✅ 数据库扩展完成：t_device 新增 tenantId/parkingLotId/laneId/platformDeviceId
> - ✅ 信路通移除自动开闸：handleImage() 改为仅记录车牌，等待业务侧命令
> - ✅ 显示屏高级控制：音量/时间同步/显示方向/字体/语音（三接口分离架构）
> - ✅ Event 模块已实现：`PlateRecognizedEvent` + HTTP Webhook 异步推送
> - ✅ API Key 认证拦截器：ApiKeyAuthInterceptor + WebMvcConfig，支持大小写不敏感、空配置禁用
> - 下一步：臻识 C5H + 科发 OLM-M1D 真机联调

### v0.4 验收清单

- [x] 信路通 XLT-01 真机联调（心跳、校时、开闸/关闸）
- [x] 臻识 `gate_direct_open` 命令实现（待真机验证）
- [x] 信路通 `handleImage()` 移除自动 `sendOpen()`，改为仅上报事件（待 EventPublisher 实现）
- [x] 设备注册扩展：`tenantId`/`parkingLotId`/`laneId`/`platformDeviceId` 字段
- [x] 品牌命令分派：`BrandCommandDispatcher` 支持臻识/信路通 开闸/关闸/校时/显示屏控制
- [x] 显示屏高级控制：音量控制（0x0D）/ 时间同步（0x05）/ 显示方向（0x19）/ 字体编辑（0x6F FINDEX）/ 语音编辑（0x30/0x31）
- [x] 三接口分离架构：`/display/config`（配置）+ `/display/text/enhanced`（内容）+ `/voice/control`（语音）
- [x] **Event 模块**：`DeviceEvent` + `PlateRecognizedEvent` + `EventPublisher`
- [x] **HTTP Webhook 推送**：异步 POST 到 Parking Platform
- [x] 删除 frontend 模块
- [x] 全量测试通过（设备控制 + 品牌命令）
- [x] schema.sql 同步到 v0.4（含业务字段）

> v0.3 验收项全部保留并回归通过。

### v0.4 真机联调测试计划（臻识 + 科发 OLM-M1D）

> **目标**：验证业务侧可通过 HTTP API 控制设备侧，设备侧通过 MQTT 控制臻识摄像头，摄像头通过 RS485 控制科发显示屏的语音播报和屏幕显示，同时控制摄像头自身继电器。
> **范围**：仅臻识 + 科发组合，暂不考虑信路通和云平台对接。
> **环境**：同局域网部署，EMQX 192.168.20.143:1883，Device Access 8081，MySQL localhost:3306。
> **硬件**：臻识 C5H 摄像头通过 RS485 直连科发 OLM-M1D 显示屏。

| 步骤 | 前置条件 | 测试动作 | 预期结果 | 验证方式 |
|------|---------|---------|---------|---------|
| 1. 环境检查 | 服务已启动，EMQX在线，摄像头已注册 | GET /api/v1/devices/{deviceId}/status | 返回 online=true | 查看返回JSON |
| 2. 显示屏实时文字 | 步骤1通过 | POST /api/v1/devices/{deviceId}/display/text {content:"欢迎光临\n剩余车位100", direction:"HORIZONTAL"} | 显示屏立即显示两行文字 | 肉眼观察屏幕 |
| 3. 显示屏配置（音量） | 步骤1通过 | POST /api/v1/devices/{deviceId}/display/config {configType:"VOLUME", intValue:80} | 音量调整为80% | 后续语音测试时听音量变化 |
| 4. 语音播报 | 步骤1通过，音量已设置 | POST /api/v1/devices/{deviceId}/voice/control {action:"PLAY", voiceId:1, variable:"鲁A12345"} | 摄像头/显示屏播报语音 | 听觉确认 |
| 5. 开闸（继电器） | 步骤1通过 | POST /api/v1/devices/{deviceId}/gate/open | 返回 success=true，道闸开启 | 观察道闸动作 + 查看返回JSON |
| 6. 关闸（继电器） | 步骤5通过 | POST /api/v1/devices/{deviceId}/gate/close | 返回 success=true，道闸关闭 | 观察道闸动作 + 查看返回JSON |
| 7. 增强显示（文字+语音联动） | 步骤1通过 | POST /api/v1/devices/{deviceId}/display/text/enhanced {content:"月卡车辆\n鲁A12345", voiceId:5, voiceVariable:"鲁A12345"} | 屏幕显示文字同时播报语音 | 肉眼+听觉确认 |
| 8. 车牌识别事件 | 步骤1通过，Webhook已配置 | 在摄像头前展示真实车牌 | Webhook接收端收到 PLATE_RECOGNIZED 事件 | 检查业务侧Webhook接收日志 |
| 9. 命令日志 | 步骤2-8执行完成 | 查询 t_device_command_log 表 | 所有命令均有记录，success字段正确 | SQL查询 |
| 10. 事件发件箱 | 步骤8执行完成 | 查询 t_event_outbox 表 | 车牌识别事件已记录，状态为SENT | SQL查询 |

**已知缺失项（代码扫描确认）**：
- **视频流获取与返回**：当前代码中无任何视频流相关实现。缺失项，需标注为 v0.5+ 或业务侧直接访问摄像头 RTSP 流。
- **车牌识别日志**：命令日志（t_device_command_log）已完整记录，但车牌识别事件本身不写入命令日志表（事件通过Webhook推送和t_event_outbox持久化）。
- **API Key认证**：application.yml 已配置 auth.keys 占位，但代码中未找到实际鉴权拦截器实现。当前为开发/测试便利放行所有请求。

### v0.4 模块结构

```
device-access
├── starter              # Spring Boot 启动入口
├── common               # DTO、Entity（Device/Product/Relation）、Enum、Exception
├── mqtt                 # MQTT 通信（不变）
├── adapter
│   ├── support          # 心跳公共组件（DeviceHeartbeatRecorder）
│   ├── xinlutong        # 信路通 XLT-01 协议适配（真机验证通过）
│   └── zhenshi          # 臻识协议适配（不变）
├── registry             # DeviceRegistry + DeviceProductRegistry + DeviceRelationRegistry
├── event                # 统一设备事件定义 + HTTP Webhook 推送（v0.4 已实现）
├── api                  # REST — DeviceController（21 端点）+ ProductController
│                        #   + BrandCommandDispatcher + ZhenshiDeviceCoordinator + XinlutongDeviceCoordinator
```

**依赖方向**：`starter → api → registry → common`，`starter → api → adapter → mqtt → common`，`starter → api → event → common`

### 当前版本不要的东西

Flyway、Sa-Token、Redis、RabbitMQ、WebSocket、微服务、边缘网关、OTA、配置中心、DeviceGateway、DeviceAdapter、Adapter SPI。

### 当前版本已知缺失（代码扫描确认）

| 功能 | 状态 | 说明 |
|------|------|------|
| 视频流获取与返回 | 缺失 | 代码中无任何 RTSP / HLS / 视频流相关实现。Device Access 当前不处理视频流，业务侧如需视频应直接访问摄像头 RTSP 地址。 |
| API Key 认证拦截器 | ✅ 已实现 | `ApiKeyAuthInterceptor` + `WebMvcConfig` 注册到 `/api/v1/**`，支持大小写不敏感校验、keys 为空或全空白时禁用鉴权（开发/测试便利）。测试覆盖：401（缺失/无效 key）、200（有效 key）、大小写不敏感。 |
| 车牌识别事件持久化查询API | 缺失 | 事件通过 Webhook 推送和 t_event_outbox 表持久化，但无查询 API 供业务侧反向拉取。 |

### 显示屏控制架构（v0.4 三接口分离）

显示屏控制采用"配置-内容-语音"三接口分离设计，确保三类操作独立演化：

- **配置接口** `/display/config`：硬件参数（音量、亮度、方向、时间同步）
- **内容接口** `/display/text/enhanced`：显示内容（文字、字体、颜色）
- **语音接口** `/voice/control`：语音播报（播放、停止）

设计原则：配置、内容、语音有各自独立的生命周期，互不影响。

---

## 当前设备能力

v0.3 以臻识 C5H 为核心 Camera，通过 RS485 串口透传接入科发 OLM-M1D LED 控制卡。产品目录预设 C6H、信路通 XLT-01。

### 臻识 C5H 能力

| 能力 | 状态 |
|------|------|
| MQTT 连接 | ✅ |
| 心跳 | ✅ |
| 车牌识别 | ✅ |
| 校时 | ✅ |
| 开闸 | ✅ v0.4（gate_direct_open，待真机验证） |
| 关闸 | ✅ v0.4（gpio_out，待真机验证） |
| 查询设备状态 | ✅ |
| 设备注册 / 管理 | ✅ |
| RS485 串口透传（serial_data） | ✅ |

### 科发 OLM-M1D 显示屏能力

| 能力 | 协议命令 | 状态 |
|------|---------|------|
| 启用/关闭显示（亮度控制） | 0x0C | ✅ v0.3 Peripheral Control |
| 设置显示布局（2行/4行分区） | 0x6F（空文本） | ✅ v0.3 Peripheral Control |
| 实时显示文字（临时区，掉电丢失） | 0x6F（SF=0） | ✅ v0.3 Display Content |
| 保存显示内容（存储区，掉电保存） | 0x67 逐行写入 Flash | ✅ v0.3 Display Content |
| 设备能力模型（DeviceCapability） | — | ✅ v0.3 替代 deviceType 硬编码 |
| 音量控制（0-100%） | 0x0D | ✅ v0.4 Display Config |
| 屏卡时间同步 | 0x05 | ✅ v0.4 Display Config |
| 显示方向控制 | 0x19 | ✅ v0.4 Display Config |
| 字体编辑（8种字体） | 0x6F FINDEX | ✅ v0.4 Display Enhanced |
| 语音播放（带变量替换） | 0x30 | ✅ v0.4 Voice Control |
| 语音停止 | 0x31 | ✅ v0.4 Voice Control |

### 信路通 XLT-01 能力

| 能力 | 状态 |
|------|------|
| MQTT 连接 | ✅ |
| 心跳 | ✅ |
| 校时 | ✅ |
| 查询设备状态 | ✅ |
| 开闸 | ✅ v0.4（OPEN_GATE） |
| 关闸 | ✅ v0.4（CLOSE_GATE） |
| 车牌识别 | ✅（移除自动开闸，改为上报事件等待业务侧命令） |

### 协议限制

- **OLM-M1D v2.0 不支持横竖屏软件切换**。屏幕方向由控卡硬件屏参（分辨率、级联面板数）决定。
- **不允许实现协议文档不存在的命令**。如需旋转显示效果，应由上位机提前旋转图片或文字点阵，而非在协议层虚构命令。

---

## 技术栈（v0.3 实际）

| 领域 | 选型 | 版本 |
|---|---|---|
| 语言 | Java | 17（当前开发环境） |
| 框架 | Spring Boot | 3.3.7（当前开发环境） |
| 构建 | Maven | 多模块 |
| ORM | MyBatis Plus | 3.5.9 |
| 数据库 | MySQL | 8.4 |
| MQTT Broker | EMQX | — |
| MQTT 客户端 | Eclipse Paho | 1.2.5 |
| 前端 | Vue 3 + Vite | — |
| JSON | Jackson | Spring Boot 自带 |
| 测试 | JUnit 5 + Mockito + Testcontainers | — |

---

## API 接口（v0.4）

### 管理类 API（业务侧直接调用）

```
POST   /api/v1/devices                      → 注册设备（productId 替代 brand/model，v0.4 新增业务字段）
GET    /api/v1/devices                      → 设备列表（keyword/deviceType/direction/status）
GET    /api/v1/devices/{deviceId}           → 设备详情（含 relations）
PUT    /api/v1/devices/{deviceId}           → 更新设备（方向/名称/备注/displayEnabled/displayMode）
DELETE /api/v1/devices/{deviceId}           → 注销设备（软删除）

GET    /api/v1/products                     → 产品目录（v0.3 新增）

GET    /api/v1/devices/{deviceId}/relations            → 查询关系（v0.3 新增）
POST   /api/v1/devices/{deviceId}/relations            → 创建关系（v0.3 新增）
DELETE /api/v1/devices/{deviceId}/relations/{relationId}       → 删除关系（v0.3 新增）
PUT    /api/v1/devices/{deviceId}/relations/{relationId}/enable  → 启用关系（v0.3 新增）
PUT    /api/v1/devices/{deviceId}/relations/{relationId}/disable → 停用关系（v0.3 新增）
```

### 控制类 API（业务侧 Parking Platform 直接调用）

> Device Access 不提供前端 UI。业务侧根据业务场景（如车辆识别后自动开闸）直接调用。

```
POST   /api/v1/devices/{deviceId}/time/sync  → 校时
GET    /api/v1/devices/{deviceId}/status     → 查询设备在线状态

POST   /api/v1/devices/{deviceId}/gate/open   → 开闸（v0.4 新增，信路通 XLT-01 + 臻识 C5H）
POST   /api/v1/devices/{deviceId}/gate/close  → 关闸（v0.4 新增，信路通 XLT-01 + 臻识 C5H）

POST   /api/v1/devices/{deviceId}/peripheral/display  → 显示屏控制（ENABLE/DISABLE/SET_MODE）
POST   /api/v1/devices/{deviceId}/display/text       → 实时显示文字（临时区，掉电丢失）
POST   /api/v1/devices/{deviceId}/display/save       → 保存显示内容（存储区，掉电保存）
POST   /api/v1/devices/{deviceId}/display/config     → 显示屏硬件配置（音量/亮度/方向/时间同步）
POST   /api/v1/devices/{deviceId}/voice/control      → 语音控制（播放/停止）
POST   /api/v1/devices/{deviceId}/display/text/enhanced → 增强显示文字（支持字体/颜色/语音联动）
```

### 事件推送（Device Access → Parking Platform）

```
POST   {webhook.url}                        → 统一事件推送（v0.4 已实现）
```

- 推送方式：HTTP POST Webhook（异步）
- 事件类型：`PLATE_RECOGNIZED`（v0.4）
- 事件信封：通过 `DeviceRegistry` 查表填充 `tenantId` / `parkingLotId` / `laneId` / `platformDeviceId`
- 推送失败：内存重试 3 次
- 配置位置：`application.yml` → `device-access.webhook.url`

所有 API 返回统一 `Result<T>`。

> v0.1 仅接入臻识 C5H 摄像头，不包含道闸控制。

API 不暴露任何厂商协议字段。

Device Access 对外提供统一能力，不暴露 MQTT Topic、协议字段、消息格式等厂商细节。

接口文档：`docs/api-v0.4.md`

---

## 编码约定

- package：`com.smartparking.deviceaccess.<module>`
- 模块名：小写单数（`adapter` 非 `adapters`）
- 品牌包名：品牌拼音小写（`zhenshi`）
- 不要在任何模块出现品牌 if-else 判断
- 不要在 mqtt 模块里解析消息的业务数据
- API 层不直接处理设备协议

---

## 道闸控制

Device Access **不直接控制道闸**。`openGate()` 实际是向摄像头发送开闸指令，由摄像头通过 RS485 触发电信号给道闸。

---

## 联调原则

真实设备行为优先于协议文档。

若真实设备行为与协议文档存在差异：

1. 保留协议文档记录；
2. 以真实联调结果修正实现；
3. 更新协议映射文档；
4. 不猜测协议行为。

---

## AI 开发约束

所有新增代码必须符合当前版本目标（v0.4）。

不要为了未来版本提前引入：

- DeviceAdapter
- AdapterFactory
- RabbitMQ / Kafka
- EventBus（内部事件总线，v0.5 引入）
- SPI
- 多品牌路由抽象
- 事件持久化（t_event 表）

只有在 ROADMAP 明确要求进入下一版本时，才允许新增对应模块。

**v0.4 允许引入**：
- `event` 模块（统一事件定义 + HTTP Webhook 推送）
- 设备注册业务字段（`tenantId`/`parkingLotId`/`laneId`/`platformDeviceId`）

**v0.4 禁止引入**：
- 前端（Vue 3 / 任何 UI）
- 业务规则引擎（白名单/黑名单/收费/月卡）
- 多租户 Topic 设计
- JWT 认证
- EMQX 集群

---

## 相关文档

- [架构设计文档](docs/ARCHITECTURE.md)
- [演进路线图](docs/ROADMAP.md)
- [API 接口文档](docs/api-v0.3.md)（v0.3 历史版本）
- [API 接口文档](docs/api-v0.4.md)（v0.4 当前版本）
- [协议文档](docs/vendor/zhenshi/自定义MQTT协议文档v1.1.14.pdf)
- [数据库 DDL](device-access-starter/src/main/resources/schema.sql)
