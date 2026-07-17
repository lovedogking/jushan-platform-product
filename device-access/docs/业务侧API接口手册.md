# Device Access — 业务侧 API 接口手册

> 面向停车业务平台（Parking Platform）调用方。所有接口统一返回 `Result<T>` 格式，前缀 `/api/v1`。

---

## 认证

所有接口需携带 API Key（`X-API-Key` 请求头），在 `application.yml` 中配置合法密钥。配置为空或不设时鉴权自动放行（开发/测试环境）。

---

## 通用响应格式

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| code | 含义 |
|------|------|
| 200 | 成功 |
| 400 | 请求参数不合法 |
| 401 | X-API-Key 缺失或无效 |
| 404 | 设备/产品/关系不存在 |
| 409 | 设备已存在 / 关系重复 |
| 422 | 设备不支持该能力 |
| 500 | 服务内部错误 |
| 503 | MQTT 连接不可用 |

---

## 一、设备管理

### 1. 注册设备

将新设备登记到 Device Access，后续方可控制。

```
POST /api/v1/devices
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceId | string | 是 | 设备唯一标识（序列号/MAC） |
| deviceName | string | 是 | 设备名称，如"东门入口摄像头" |
| productId | long | 是 | 产品ID，来自 `GET /api/v1/products` |
| direction | string | 否 | 安装方向：`ENTRANCE` / `EXIT` / `BIDIRECTIONAL` |
| platformDeviceId | string | 否 | 业务侧设备ID（透传字段，不参与逻辑） |
| tenantId | string | 否 | 租户ID（透传字段） |
| parkingLotId | string | 否 | 停车场ID（透传字段） |
| laneId | string | 否 | 车道ID（透传字段） |
| remark | string | 否 | 备注 |

响应 `data` 返回已创建的设备对象。

---

### 2. 设备列表

```
GET /api/v1/devices?keyword=&deviceType=&direction=&status=&tenantId=&parkingLotId=&laneId=
```

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| keyword | string | 否 | 模糊搜索 deviceId / deviceName |
| deviceType | string | 否 | 筛选类型：`CAMERA` / `DISPLAY` |
| direction | string | 否 | 筛选方向：`ENTRANCE` / `EXIT` / `BIDIRECTIONAL` |
| status | string | 否 | 筛选状态：`ONLINE` / `OFFLINE` |
| tenantId | string | 否 | 按租户筛选 |
| parkingLotId | string | 否 | 按停车场筛选 |
| laneId | string | 否 | 按车道筛选 |

响应 `data` 返回设备列表（不含关联关系）。

---

### 3. 设备详情

```
GET /api/v1/devices/{deviceId}
```

响应 `data` 返回设备完整信息，**含关联关系列表**（如摄像头→显示屏的 RS485 透传关系）。

---

### 4. 更新设备

部分更新，只传需要修改的字段。

```
PUT /api/v1/devices/{deviceId}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| deviceName | string | 否 | 设备名称 |
| direction | string | 否 | 方向 |
| platformDeviceId | string | 否 | 平台设备ID |
| tenantId | string | 否 | 租户ID |
| parkingLotId | string | 否 | 停车场ID |
| laneId | string | 否 | 车道ID |
| displayEnabled | boolean | 否 | 显示屏启用/关闭 |
| displayMode | string | 否 | 显示模式：`TWO_LINE` / `FOUR_LINE` |
| remark | string | 否 | 备注 |

---

### 5. 注销设备

软删除，不会物理删除数据。

```
DELETE /api/v1/devices/{deviceId}
```

---

## 二、设备关系管理

设备关系用于描述设备间的物理连接（如摄像头通过 RS485 串口连接显示屏）。

### 6. 查询设备关系

```
GET /api/v1/devices/{deviceId}/relations
```

### 7. 创建设备关系

```
POST /api/v1/devices/{deviceId}/relations
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| relationType | string | 是 | 关系类型，如 `RS485_PASSTHROUGH` |
| targetDeviceId | string | 是 | 对端设备ID |
| remark | string | 否 | 备注 |

### 8. 删除设备关系

```
DELETE /api/v1/devices/{deviceId}/relations/{relationId}
```

### 9. 启用 / 停用设备关系

```
PUT  /api/v1/devices/{deviceId}/relations/{relationId}/enable
PUT  /api/v1/devices/{deviceId}/relations/{relationId}/disable
```

---

## 三、道闸控制

### 10. 开闸

向摄像头发送继电器触发信号，由摄像头通过 RS485 触发电信号给道闸。

```
POST /api/v1/devices/{deviceId}/gate/open
```

要求设备产品目录声明 `OPEN_GATE` 能力。异步执行，响应 `data.success` 表示命令是否下发成功。

### 11. 关闸

```
POST /api/v1/devices/{deviceId}/gate/close
```

要求设备产品目录声明 `CLOSE_GATE` 能力。

---

## 四、设备状态

### 12. 查询在线状态

```
GET /api/v1/devices/{deviceId}/status
```

响应 `data.online` 表示是否在线，`data.lastOnlineTime` 为最后心跳时间。心跳超时阈值：臻识 30s，信路通 90s。

### 13. 查询健康状态

```
GET /api/v1/devices/{deviceId}/health
```

响应包含心跳间隔、离线时长、响应延迟等诊断信息。

### 14. 校时

将 Device Access 服务器当前时间同步至设备。

```
POST /api/v1/devices/{deviceId}/time/sync
```

要求设备产品目录声明 `TIME_SYNC` 能力。

---

## 五、显示屏控制

所有显示屏接口通过摄像头的 RS485 串口透传至科发 OLM-M1D LED 控制卡。

### 15. 外围显示器控制

控制显示器开关和布局模式。

```
POST /api/v1/devices/{deviceId}/peripheral/display
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| action | string | 是 | `ENABLE` / `DISABLE` / `SET_MODE` |
| mode | string | 否 | `TWO_LINE` / `FOUR_LINE`（SET_MODE 时需填） |

要求设备产品目录声明 `PERIPHERAL_CONTROL` 能力。

---

### 16. 实时显示文字

向控制卡临时区写入文本，立即覆盖当前画面。**掉电丢失，不持久化**。适合欢迎语、临时提醒等高频变化内容。

```
POST /api/v1/devices/{deviceId}/display/text
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 是 | 显示文字，`\n` 分隔多行 |
| direction | string | 是 | 显示方向：`HORIZONTAL` / `VERTICAL` |

要求设备产品目录声明 `DISPLAY_TEXT` 能力。

---

### 17. 保存显示内容

写入控制卡外部存储器（Flash），**掉电不丢失**。控制卡空闲时自动循环显示已存储内容。低频操作，适合固定广告语、停车场名称等。

```
POST /api/v1/devices/{deviceId}/display/save
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 是 | 显示文字，`\n` 分隔多行 |
| direction | string | 是 | 显示方向：`HORIZONTAL` / `VERTICAL` |

要求设备产品目录声明 `DISPLAY_SAVE` 能力。

---

### 18. 显示屏配置

调节音量、亮度、显示方向、屏卡时间同步等硬件参数。配置与内容分离，互不影响。

```
POST /api/v1/devices/{deviceId}/display/config
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| configType | string | 是 | 配置类型：`VOLUME` / `BRIGHTNESS` / `DIRECTION` / `TIME_SYNC` |
| intValue | int | 否 | 整数取值（0-100 音量或亮度等） |
| directionValue | string | 否 | 方向值：`LEFT_TO_RIGHT` / `RIGHT_TO_LEFT` / `TOP_TO_BOTTOM` / `BOTTOM_TO_TOP` |

要求设备产品目录声明 `DISPLAY_CONFIG` 能力。

---

### 19. 增强显示文字

实时显示 + 字体/颜色/语音联动，是 `/display/text` 的超集。新增字段均为可选，向后兼容。

```
POST /api/v1/devices/{deviceId}/display/text/enhanced
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| content | string | 是 | 显示文字 |
| direction | string | 是 | 显示方向 |
| font | int | 否 | 字体编号（0-7），对应 8 种内置字体 |
| color | array | 否 | 颜色 `[R, G, B]`，如 `[255, 0, 0]` |
| voiceId | int | 否 | 语音编号（联动播报） |
| voiceVariable | string | 否 | 语音变量替换（如车牌号） |

要求设备产品目录声明 `DISPLAY_ENHANCED` 能力。

---

## 六、语音控制

### 20. 语音播报控制

通过摄像头连接的显示屏/喇叭进行语音播报。

```
POST /api/v1/devices/{deviceId}/voice/control
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| action | string | 是 | `PLAY`（播放）/ `STOP`（停止） |
| voiceId | int | 否 | 语音编号（PLAY 时需填） |
| voiceText | string | 否 | 完整语音文字（直接播报模式） |
| variable | string | 否 | 变量替换值（如车牌号 `鲁A12345`） |

要求设备产品目录声明 `VOICE_CONTROL` 能力。

---

## 七、产品目录（辅助）

### 21. 产品目录列表

注册设备前，先查产品目录获取 `productId`。

```
GET /api/v1/products
```

响应返回所有可接入的产品型号（品牌、型号、设备类型、通信协议、能力列表）。

---

## 事件推送（Device Access → 业务侧）

Device Access 通过 HTTP Webhook 异步向业务侧推送设备事件。

| 事件类型 | 触发条件 | 说明 |
|----------|----------|------|
| `PLATE_RECOGNIZED` | 摄像头识别到车牌 | 携带车牌号、识别时间、设备ID、业务维度的 tenantId/parkingLotId/laneId |

Webhook URL 在 `application.yml` 中配置（`device-access.webhook.url`）。

---

## 接口全景

```
产品目录
  GET    /api/v1/products                              产品列表

设备管理
  POST   /api/v1/devices                               注册设备
  GET    /api/v1/devices                               设备列表
  GET    /api/v1/devices/{deviceId}                    设备详情（含关系）
  PUT    /api/v1/devices/{deviceId}                    更新设备
  DELETE /api/v1/devices/{deviceId}                    注销设备

设备关系
  GET    /api/v1/devices/{deviceId}/relations           查询关系
  POST   /api/v1/devices/{deviceId}/relations           创建关系
  DELETE /api/v1/devices/{deviceId}/relations/{id}      删除关系
  PUT    /api/v1/devices/{deviceId}/relations/{id}/enable   启用
  PUT    /api/v1/devices/{deviceId}/relations/{id}/disable  停用

道闸控制
  POST   /api/v1/devices/{deviceId}/gate/open           开闸
  POST   /api/v1/devices/{deviceId}/gate/close          关闸

状态
  POST   /api/v1/devices/{deviceId}/time/sync           校时
  GET    /api/v1/devices/{deviceId}/status              在线状态
  GET    /api/v1/devices/{deviceId}/health              健康状态

显示
  POST   /api/v1/devices/{deviceId}/peripheral/display  外围显示器控制
  POST   /api/v1/devices/{deviceId}/display/text        实时显示（掉电丢失）
  POST   /api/v1/devices/{deviceId}/display/save        保存显示（掉电保留）
  POST   /api/v1/devices/{deviceId}/display/config      配置（音量/亮度/方向/时间）
  POST   /api/v1/devices/{deviceId}/display/text/enhanced  增强显示（字体/颜色/语音联动）

语音
  POST   /api/v1/devices/{deviceId}/voice/control       语音播报/停止
```

---

## 典型业务流程

```
1. 接入新设备
   GET /api/v1/products           → 选产品型号拿到 productId
   POST /api/v1/devices            → 注册设备（传 productId + deviceId + 业务字段）
   POST /api/v1/devices/{id}/relations  → 关联外设（如摄像头→显示屏）

2. 车辆入场
   （摄像头识别车牌 → Device Access 推送 PLATE_RECOGNIZED 事件到业务侧 Webhook）
   （业务侧判断开闸条件）
   POST /api/v1/devices/{id}/gate/open     → 开闸
   POST /api/v1/devices/{id}/display/text  → 显示"欢迎光临\n{车牌}"
   POST /api/v1/devices/{id}/voice/control → 播报"欢迎光临"

3. 设备巡检
   GET /api/v1/devices/{id}/status         → 检查是否在线
   GET /api/v1/devices/{id}/health         → 检查心跳是否正常
   POST /api/v1/devices/{id}/time/sync     → 对时
```
