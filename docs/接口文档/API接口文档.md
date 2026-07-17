# Device Access v0.4 API 文档

> 版本：v0.4
> 更新：2026-07-14
> 关联文档：[ARCHITECTURE.md](ARCHITECTURE.md) · [ROADMAP.md](ROADMAP.md)

## 变更摘要（v0.3 → v0.4）

| 变更项 | 说明 |
|--------|------|
| 新增品牌 | 信路通 XLT-01（心跳、校时、开闸/关闸、显示屏控制） |
| 新增 API | `POST /gate/open`、`POST /gate/close`（开闸/关闸） |
| 新增 API | `POST /display/config`（显示屏配置：音量/亮度/方向/时间同步） |
| 新增 API | `POST /voice/control`（语音控制：播放/停止） |
| 新增 API | `POST /display/text/enhanced`（增强显示：字体/颜色/语音联动） |
| 设备注册扩展 | 新增 `tenantId`/`parkingLotId`/`laneId`/`platformDeviceId` 业务字段 |
| 显示屏控制扩展 | `/display/text`、`/display/save`、`/peripheral/display` 现在支持臻识 C5H 和信路通 XLT-01 |
| 品牌命令分派 | `BrandCommandDispatcher` 按品牌路由命令，业务侧无感知 |
| 前端定位调整 | 设备管理后台保留（运维：注册/查看/关系/配置），设备控制面板移除（由业务侧 Parking Platform 直接调用 API） |
| Event 模块 | ⏸️ 规划中，待业务侧 Webhook 接口定义后实现 |

---

## 通用约定

- 所有接口返回 `Result<T>`：`{"code":200,"message":"success","data":{...}}`
- 请求体 `Content-Type: application/json`
- deviceId 归一化为小写存储
- `displayEnabled` 和 `displayMode` 通过 `PUT /api/v1/devices/{deviceId}` 持久化到数据库

---

## 设备 CRUD

### 注册设备

```
POST /api/v1/devices
```

**Request:**
```json
{
    "deviceId": "b30113ab-a034d147",
    "deviceName": "东门入口摄像头",
    "productId": 1,
    "direction": "ENTRANCE",
    "tenantId": "tenant_001",
    "parkingLotId": "park_001",
    "laneId": "lane_entry_001",
    "platformDeviceId": "dev_camera_001",
    "remark": "主摄像头"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | String | ✅ | 设备序列号，必须与摄像头 SN 一致（小写） |
| deviceName | String | ✅ | 设备名称 |
| productId | Long | ✅ | 产品ID → t_device_product.id |
| direction | String | | ENTRANCE / EXIT / BIDIRECTIONAL |
| tenantId | String | | 租户ID（业务侧传入，事件推送时透传） |
| parkingLotId | String | | 停车场ID（业务侧传入，事件推送时透传） |
| laneId | String | | 车道ID（业务侧传入，事件推送时透传） |
| platformDeviceId | String | | 业务侧设备标识（业务侧传入，事件推送时透传） |
| remark | String | | 备注 |

> v0.4 新增：`tenantId`/`parkingLotId`/`laneId`/`platformDeviceId` 为业务侧传入的上下文字段，Device Access 仅存储和透传，不基于这些字段做逻辑判断。

**Response (DeviceDTO):**
```json
{
    "code": 200,
    "data": {
        "deviceId": "b30113ab-a034d147",
        "deviceName": "东门入口摄像头",
        "productId": 1,
        "brand": "ZHENSHI",
        "model": "C5H",
        "productName": "ZHENSHI C5H",
        "deviceType": "CAMERA",
        "protocol": "MQTT",
        "direction": "ENTRANCE",
        "tenantId": "tenant_001",
        "parkingLotId": "park_001",
        "laneId": "lane_entry_001",
        "platformDeviceId": "dev_camera_001",
        "displayEnabled": true,
        "displayMode": "TWO_LINE",
        "status": "OFFLINE",
        "remark": "主摄像头",
        "createTime": "2026-07-13 10:00:00",
        "updateTime": null,
        "lastOnlineTime": null
    }
}
```

---

### 设备列表

```
GET /api/v1/devices?keyword=&deviceType=&direction=&status=
```

**Query params（全部可选）：**
- `keyword` — 模糊匹配 deviceId / deviceName
- `deviceType` — CAMERA / DISPLAY
- `direction` — ENTRANCE / EXIT / BIDIRECTIONAL
- `status` — ONLINE / OFFLINE

**Response: `List<DeviceDTO>`（不含 relations）**

---

### 设备详情

```
GET /api/v1/devices/{deviceId}
```

**Response: `DeviceDetailDTO`（含 `relations` 列表）**

与 DeviceDTO 字段相同，额外包含：
- `relations` — `List<DeviceRelationDTO>`，设备关联关系列表

---

### 更新设备

```
PUT /api/v1/devices/{deviceId}
```

**Request（全部可选，只更新提供的非 null 字段）：**
```json
{
    "deviceName": "新名称",
    "direction": "BIDIRECTIONAL",
    "displayEnabled": true,
    "displayMode": "FOUR_LINE",
    "remark": "备注"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceName | String | | 设备名称 |
| direction | String | | ENTRANCE / EXIT / BIDIRECTIONAL |
| displayEnabled | Boolean | | 显示屏启用状态 |
| displayMode | String | | TWO_LINE / FOUR_LINE |
| remark | String | | 备注 |

> ⚠️ `productId` 不可修改。`status` 由系统维护，不可通过此接口修改。

---

### 注销设备（软删除）

```
DELETE /api/v1/devices/{deviceId}
```

---

### 校时

```
POST /api/v1/devices/{deviceId}/time/sync
```

**Response (CommandResultDTO):**
```json
{
    "code": 200,
    "data": {
        "success": true,
        "deviceCode": 200,
        "message": "Time synced"
    }
}
```

---

### 查询状态

```
GET /api/v1/devices/{deviceId}/status
```

**Response (DeviceStatusDTO):**
```json
{
    "code": 200,
    "data": {
        "deviceId": "b30113ab-a034d147",
        "deviceName": "东门入口摄像头",
        "brand": "ZHENSHI",
        "model": "C5H",
        "online": true,
        "lastOnlineTime": "2026-07-13 10:30:00"
    }
}
```

在线判定：最近 30 秒内有心跳即视为在线。

---

### 设备健康检查

```
GET /api/v1/devices/{deviceId}/health
```

比 `/status` 更全面的运维监控接口，除在线状态外还包含健康判定和心跳超时阈值。

**Response (DeviceHealthDTO):**
```json
{
    "code": 200,
    "data": {
        "deviceId": "b30113ab-a034d147",
        "deviceName": "东门入口摄像头",
        "brand": "ZHENSHI",
        "model": "C5H",
        "status": "ONLINE",
        "healthy": true,
        "lastOnlineTime": "2026-07-13T10:30:00",
        "heartbeatTimeoutSeconds": 30
    }
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | String | 设备唯一标识 |
| deviceName | String | 设备名称 |
| brand | String | 品牌 |
| model | String | 型号 |
| status | String | ONLINE / OFFLINE |
| healthy | Boolean | 心跳是否未超时（true=健康） |
| lastOnlineTime | String | 最后在线时间 |
| heartbeatTimeoutSeconds | Long | 心跳超时阈值（秒） |

---

## 产品目录

### 产品列表

```
GET /api/v1/products
```

**Response: `List<DeviceProductDTO>`**
```json
{
    "code": 200,
    "data": [
        {
            "id": 1,
            "brand": "ZHENSHI",
            "model": "C5H",
            "productName": "ZHENSHI C5H",
            "deviceType": "CAMERA",
            "protocol": "MQTT",
            "capabilities": ["DISPLAY_TEXT", "DISPLAY_SAVE", "DISPLAY_CONFIG", "VOICE_CONTROL", "DISPLAY_ENHANCED", "PERIPHERAL_CONTROL", "TIME_SYNC", "OPEN_GATE", "CLOSE_GATE"]
        },
        {
            "id": 3,
            "brand": "信路通",
            "model": "XLT-01",
            "productName": "信路通 XLT-01",
            "deviceType": "CAMERA",
            "protocol": "MQTT",
            "capabilities": ["DISPLAY_TEXT", "DISPLAY_SAVE", "DISPLAY_CONFIG", "VOICE_CONTROL", "DISPLAY_ENHANCED", "PERIPHERAL_CONTROL", "TIME_SYNC", "OPEN_GATE", "CLOSE_GATE"]
        }
    ]
}
```

`capabilities` 为 JSON 数组，声明该产品型号支持的能力集合。能力枚举见 [§数据模型](#数据模型)。

---

## 设备关系

### 查询关系

```
GET /api/v1/devices/{deviceId}/relations
```

返回双向关系（OUTBOUND + INBOUND）。

### 创建关系

```
POST /api/v1/devices/{deviceId}/relations
```

**Request:**
```json
{
    "relationType": "AUX_CAMERA",
    "targetDeviceId": "cam-aux-001",
    "remark": "辅助摄像头"
}
```

校验规则：
- 不能关联自己
- `AUX_CAMERA`：双方 product.deviceType 必须都是 CAMERA
- `RS485_DISPLAY`：target 的 product.deviceType 必须是 DISPLAY

### 删除关系

```
DELETE /api/v1/devices/{deviceId}/relations/{relationId}
```

物理删除。

### 启用关系

```
PUT /api/v1/devices/{deviceId}/relations/{relationId}/enable
```

### 停用关系

```
PUT /api/v1/devices/{deviceId}/relations/{relationId}/disable
```

---

## 开闸/关闸（v0.4 新增）

### 开闸

```
POST /api/v1/devices/{deviceId}/gate/open
```

向设备发送开闸指令。

**Response (CommandResultDTO):**
```json
{
    "code": 200,
    "data": {
        "success": true,
        "deviceCode": 200,
        "message": "Gate opened"
    }
}
```

**能力要求**：设备产品目录必须声明 `OPEN_GATE` 能力，否则返回 422。

**品牌支持**：
- 臻识 C5H：通过 `gate_direct_open` MQTT 命令开闸（待真机验证）
- 信路通 XLT-01：通过 `Open` MQTT 命令开闸（真机验证通过）

---

### 关闸

```
POST /api/v1/devices/{deviceId}/gate/close
```

向设备发送关闸指令。

**Response (CommandResultDTO):**
```json
{
    "code": 200,
    "data": {
        "success": true,
        "deviceCode": 200,
        "message": "Gate closed"
    }
}
```

**能力要求**：设备产品目录必须声明 `CLOSE_GATE` 能力，否则返回 422。

**品牌支持**：
- 臻识 C5H：通过 `gpio_out` MQTT 命令关闸（待真机验证）
- 信路通 XLT-01：通过 `Close` MQTT 命令关闸（真机验证通过）

---

## 显示内容控制

### 实时显示文字

```
POST /api/v1/devices/{deviceId}/display/text
```

向设备下挂 LED 控制卡**临时区（RAM）**发送文本，立即覆盖当前显示。
内容**掉电丢失**，不影响控制卡存储区。适合高频变更的临时内容。

对应 OLM-M1D 协议 `0x6F` 命令（SF=0，临时区）。

**Request:**
```json
{
    "content": "欢迎光临\n请扫码缴费\n剩余车位 123\n一路平安",
    "direction": "HORIZONTAL"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | ✅ | 文本内容，HORIZONTAL 模式下用 `\n` 分隔多行 |
| direction | String | ✅ | 文本布局方向。当前仅支持 `HORIZONTAL` |

> ⚠️ `VERTICAL` 方向为协议限制（OLM-M1D v2.0 不支持运行时方向切换），非开发计划。如需竖屏效果，应由上位机提前旋转图片或文字点阵后下发。

**Response (DisplayResult):**
```json
{
    "code": 200,
    "data": {
        "success": true
    }
}
```

失败时：
```json
{
    "code": 500,
    "message": "error",
    "data": {
        "success": false,
        "errorMessage": "MQTT is not connected, cannot send command"
    }
}
```

**能力要求**：设备产品目录必须声明 `DISPLAY_TEXT` 能力，否则返回 422。

**品牌支持**：
- 臻识 C5H：通过 RS485 串口透传 OLM-M1D 协议帧（`serial_data` MQTT Topic）
- 信路通 XLT-01：通过 SerialData 串口数据透传 OLM-M1D 协议帧（`download/{sn}` MQTT Topic，内置科发屏卡）

---

### 保存显示内容

```
POST /api/v1/devices/{deviceId}/display/save
```

将文字**逐行**写入控制卡外部存储器（Flash），**掉电不丢失**。
控制卡空闲时自动循环显示已存储的内容。

> ⚠️ **低频操作。**每次调用会擦写控制卡 Flash，频繁调用会降低存储器寿命。
> 频繁变动的内容请使用 `/display/text`。

对应 OLM-M1D 协议 `0x67` 命令（广告语下载，逐行发送）。

**Request:**
```json
{
    "content": "欢迎光临\n请扫码缴费\n剩余车位 123\n一路平安",
    "direction": "HORIZONTAL"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | String | ✅ | 文本内容，HORIZONTAL 模式下用 `\n` 分隔多行 |
| direction | String | ✅ | 文本布局方向。当前仅支持 `HORIZONTAL` |

**Response (DisplayResult):**
```json
{
    "code": 200,
    "data": {
        "success": true
    }
}
```

**能力要求**：设备产品目录必须声明 `DISPLAY_SAVE` 能力，否则返回 422。

**品牌支持**：同 `/display/text`（臻识 C5H + 信路通 XLT-01）。

---

## 外围设备控制

### 显示屏控制

```
POST /api/v1/devices/{deviceId}/peripheral/display
```

控制 Camera 下挂的显示屏外围设备。Device Access 只负责设备能力控制（启用/关闭/模式），不管理显示内容。

**Request:**

_启用显示屏：_
```json
{
    "action": "ENABLE"
}
```

_关闭显示屏：_
```json
{
    "action": "DISABLE"
}
```

_设置显示模式：_
```json
{
    "action": "SET_MODE",
    "mode": "FOUR_LINE"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| action | String | ✅ | ENABLE / DISABLE / SET_MODE |
| mode | String | SET_MODE 时必填 | TWO_LINE（2行）/ FOUR_LINE（4行） |

**动作说明：**

| action | OLM-M1D 命令 | 硬件行为 |
|--------|-------------|---------|
| ENABLE | 0x0C brightness=80 | 启用显示屏 |
| DISABLE | 0x0C brightness=10 | 关闭显示屏 |
| SET_MODE | 0x6F 空文本帧 | 配置 zone 分区布局（仅设布局，不发文本） |

> ⚠️ SET_MODE 只配置控制卡的 zone 分区（2行或4行），不发送文本内容。实际显示文字请使用 `/display/text` 或 `/display/save`。

**Response (PeripheralControlResult):**
```json
{
    "code": 200,
    "data": {
        "success": true,
        "action": "SET_MODE",
        "message": "Display mode set to FOUR_LINE"
    }
}
```

**能力要求**：设备产品目录必须声明 `PERIPHERAL_CONTROL` 能力，否则返回 422。

**品牌支持**：同 `/display/text`（臻识 C5H + 信路通 XLT-01）。

---

## 数据模型

### Device（t_device）

| 字段 | 类型 | 说明 |
|------|------|------|
| deviceId | VARCHAR(64) UNIQUE | 设备唯一标识（序列号） |
| deviceName | VARCHAR(128) | 设备名称 |
| productId | BIGINT FK | → t_device_product.id |
| direction | VARCHAR(16) | ENTRANCE / EXIT / BIDIRECTIONAL |
| tenantId | VARCHAR(64) | 租户ID（v0.4 新增，业务侧传入） |
| parkingLotId | VARCHAR(64) | 停车场ID（v0.4 新增，业务侧传入） |
| laneId | VARCHAR(64) | 车道ID（v0.4 新增，业务侧传入） |
| platformDeviceId | VARCHAR(64) | 业务侧设备标识（v0.4 新增，业务侧传入） |
| displayEnabled | TINYINT(1) | 显示屏启用状态（0=关闭, 1=启用） |
| displayMode | VARCHAR(16) | TWO_LINE / FOUR_LINE |
| status | VARCHAR(16) | ONLINE / OFFLINE（系统维护，禁止 API 修改） |
| lastOnlineTime | DATETIME | 最后心跳时间 |
| remark | VARCHAR(255) | 备注 |
| deleted | TINYINT(1) | 软删除标记 |
| createTime | DATETIME | 创建时间（自动填充） |
| updateTime | DATETIME | 更新时间（自动填充） |

### DeviceProduct（t_device_product）

| 字段 | 类型 | 说明 |
|------|------|------|
| brand | VARCHAR(32) | 品牌（如 ZHENSHI、信路通） |
| model | VARCHAR(32) | 型号（如 C5H、XLT-01） |
| productName | VARCHAR(128) | 产品全称 |
| deviceType | VARCHAR(16) | CAMERA / DISPLAY |
| protocol | VARCHAR(16) | MQTT / RS485 / NONE |
| capabilities | JSON | 能力列表，如 `["DISPLAY_TEXT","TIME_SYNC","OPEN_GATE"]` |

### DeviceCapability 能力枚举

| 枚举值 | 对应 API | 说明 | 品牌支持 |
|--------|---------|------|----------|
| DISPLAY_TEXT | POST /display/text | 实时显示文字（RAM） | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| DISPLAY_SAVE | POST /display/save | 保存显示内容（Flash） | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| DISPLAY_CONFIG | POST /display/config | 显示屏配置（音量/亮度/方向/时间同步） | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| VOICE_CONTROL | POST /voice/control | 语音控制（播放/停止） | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| DISPLAY_ENHANCED | POST /display/text/enhanced | 增强显示（字体/颜色/语音联动） | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| PERIPHERAL_CONTROL | POST /peripheral/display | 外围设备控制 | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| TIME_SYNC | POST /time/sync | 校时 | 臻识 C5H ✅、信路通 XLT-01 ✅ |
| OPEN_GATE | POST /gate/open | 开闸 | 臻识 C5H ⏳（待真机验证）、信路通 XLT-01 ✅ |
| CLOSE_GATE | POST /gate/close | 关闸 | 臻识 C5H ⏳（待真机验证）、信路通 XLT-01 ✅ |
| VIDEO_STREAM | GET /stream | 视频流信息查询 | 臻识 C5H ✅、信路通 XLT-01 ⏳（待确认协议） |

能力绑定在 DeviceProduct 上，Service 层通过 `ProductRegistry.hasCapability()` 校验。

### DisplayDirection

| 枚举值 | 状态 | 说明 |
|--------|------|------|
| HORIZONTAL | ✅ 可用 | 横向排列，`\n` 分隔多行 |
| VERTICAL | ❌ 不可用 | OLM-M1D v2.0 协议不支持，非开发计划 |

### DeviceRelation（t_device_relation）

| 字段 | 类型 | 说明 |
|------|------|------|
| sourceDeviceId | VARCHAR(64) | 关系源 |
| targetDeviceId | VARCHAR(64) | 关系目标 |
| relationType | VARCHAR(32) | AUX_CAMERA / RS485_DISPLAY |
| enabled | TINYINT(1) | 启用/停用 |

### DeviceRelationDTO

| 字段 | 类型 | 说明 |
|------|------|------|
| relationId | Long | 关系ID |
| relationType | String | AUX_CAMERA / RS485_DISPLAY |
| direction | String | OUTBOUND（当前设备为source）/ INBOUND（当前设备为target） |
| relatedDeviceId | String | 关联设备ID |
| relatedDeviceName | String | 关联设备名称 |
| relatedProductName | String | 关联设备产品名 |
| relatedDeviceType | String | 关联设备类型 |
| relatedStatus | String | 关联设备在线状态 |
| enabled | Boolean | 是否启用 |
| remark | String | 备注 |
| createTime | String | 创建时间 |

---

## 调用约束

### 显示屏接口的可靠性边界

显示屏接口（`/display/text`、`/display/save`、`/peripheral/display`）通过以下链路执行：

**臻识 C5H（RS485 透传外接显示屏）：**
```
Platform → HTTP → Device Access → MQTT → 臻识 C5H → RS485 → 科发 OLM-M1D
```

**信路通 XLT-01（SerialData 透传内置科发屏卡）：**
```
Platform → HTTP → Device Access → MQTT → 信路通 XLT-01 → 内部转发 → 内置科发屏卡
```

**API 返回值只能确认 MQTT 层面的结果**：`success=true` 表示 Device Access 已收到设备的 MQTT 协议响应，并不能证明 OLM-M1D 控制卡已成功执行显示操作。RS485/内部转发下游链路缺少最终执行确认机制，调用方无法仅凭 API 返回值判断屏幕实际显示状态。

### 已知限制

- `direction` 参数当前仅支持 `HORIZONTAL`。`VERTICAL` 不可用，原因见 [ARCHITECTURE.md §12](ARCHITECTURE.md#12-外围设备控制扩展参考)。
- `/display/save` 为低频操作（每次擦写控制卡 Flash），频繁调用会降低存储器寿命。高频变更请使用 `/display/text`。
- 信路通 XLT-01 的显示屏为内置科发屏卡，通过 SerialData MQTT 命令透传 OLM-M1D 协议帧控制，与外接显示屏的 RS485 透传方式在协议层等价。
- 详细的协议层限制和联调结论见 [ARCHITECTURE.md §8.1](ARCHITECTURE.md#81-外围设备控制与显示内容)。

---

## 显示屏高级控制（v0.4 新增）

### 显示屏配置

```
POST /api/v1/devices/{deviceId}/display/config
```

**Request:**
```json
{
    "configType": "VOLUME",
    "intValue": 50
}
```

**configType 取值：**
| 类型 | intValue 含义 | 范围 |
|------|-------------|------|
| `VOLUME` | 音量百分比 | 0~100 |
| `BRIGHTNESS` | 亮度百分比 | 10~100 |
| `DIRECTION` | 显示方向 | 0=正常, 1=旋转180度 |
| `TIME_SYNC` | 忽略，自动同步服务器时间 | - |

**Response:**
```json
{
    "code": 200,
    "data": {
        "success": true,
        "message": "Volume set to 50%"
    }
}
```

**要求设备能力**: `DISPLAY_CONFIG`

---

### 语音控制

```
POST /api/v1/devices/{deviceId}/voice/control
```

**Request:**
```json
{
    "action": "PLAY",
    "voiceId": 42,
    "variable": "5.00",
    "displayText": "缴费 5.00 元"
}
```

**action 取值：**
| 动作 | 说明 |
|------|------|
| `PLAY` | 播放指定语音（voiceId 必填） |
| `STOP` | 立即停止当前语音 |

**voiceId**: 0~341，对应协议内置语音表。
**variable**: 变量替换文本（如金额、车牌号），GBK 编码。

**Response:**
```json
{
    "code": 200,
    "data": {
        "success": true,
        "action": "PLAY",
        "voiceId": 42
    }
}
```

**要求设备能力**: `VOICE_CONTROL`

---

### 增强版实时显示

```
POST /api/v1/devices/{deviceId}/display/text/enhanced
```

**Request:**
```json
{
    "content": "欢迎光临",
    "direction": "HORIZONTAL",
    "font": "SONG_32",
    "color": [255, 255, 255, 255],
    "playVoice": true,
    "voiceId": 100,
    "voiceVariable": ""
}
```

**字段说明：**
| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `content` | string | 是 | 显示内容，换行用 `\n` |
| `direction` | string | 是 | `HORIZONTAL` / `VERTICAL`（暂仅支持 HORIZONTAL） |
| `font` | string | 否 | 字体：`ASCII_8`/`ASCII_10`/`ASCII_13`/`SONG_16`/`SONG_24`/`SONG_32`/`SONG_48`/`SONG_64`，默认 `SONG_16` |
| `color` | int[4] | 否 | RGBA 颜色数组，默认 `[255,255,255,0]` |
| `playVoice` | boolean | 否 | 是否同步播放语音 |
| `voiceId` | int | 否 | 同步语音ID（playVoice=true 时必填） |
| `voiceVariable` | string | 否 | 语音变量替换文本 |

**要求设备能力**: `DISPLAY_ENHANCED`

---

## 视频流信息查询（v0.4+ 新增）

### 查询设备视频流

```
GET /api/v1/devices/{deviceId}/stream
```

Device Access **仅返回视频流地址配置，不代理视频流数据**。
业务侧（Parking Platform / 前端）直接通过返回的 URL 连接摄像头获取视频流。

**能力要求**：设备产品目录必须声明 `VIDEO_STREAM` 能力，否则返回 422。

**Response (DeviceStreamInfoDTO):**

```json
{
  "code": 200,
  "data": {
    "deviceId": "b30113ab-a034d147",
    "deviceName": "东门入口摄像头",
    "streamAvailable": true,
    "streams": [
      {
        "streamId": "main",
        "streamName": "主码流",
        "protocol": "RTSP",
        "url": "rtsp://192.168.20.101:554/live/stream",
        "authRequired": true,
        "resolution": "1920x1080",
        "encoding": "H.264"
      }
    ]
  }
}
```

**字段说明：**

| 字段 | 类型 | 说明 |
|------|------|------|
| `streamAvailable` | Boolean | 该设备是否配置了有效的视频流地址 |
| `streams` | List | 视频流列表（主码流 / 子码流） |
| `streamId` | String | 流标识，如 main / sub |
| `streamName` | String | 流名称，如"主码流" / "子码流" |
| `protocol` | String | 协议类型：RTSP / HTTP / WebRTC 等 |
| `url` | String | 可直接播放的完整 URL |
| `authRequired` | Boolean | 是否需要认证（RTSP 用户名/密码由业务侧自行维护） |
| `resolution` | String | 分辨率，如 1920x1080 |
| `encoding` | String | 编码格式，如 H.264 / H.265 |

> **安全说明**：API 响应不包含 RTSP 密码等敏感凭据。业务侧与摄像头之间的认证由业务侧自行维护。

> **URL 来源**：优先使用设备注册时传入的 `streamUrl`（设备级覆盖），为空时使用产品目录的 `streamConfig` 模板自动生成。

---

## 错误码

| code | 说明 |
|------|------|
| 200 | 成功 |
| 400 | 参数校验失败 / InvalidRelation |
| 404 | DeviceNotFound / ProductNotFound / RelationNotFound |
| 409 | DeviceAlreadyExists / RelationAlreadyExists |
| 422 | CapabilityUnsupported（设备不具备请求的能力） |
| 500 | 内部错误 / 命令执行失败 |
| 503 | MQTT 未连接 |
