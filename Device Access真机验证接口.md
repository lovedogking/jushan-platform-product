# Device Access API v0.1

提供给停车业务平台调用的设备控制接口。

- Base URL：`http://{host}:8081`
- Content-Type：`application/json`
- 所有接口统一返回格式：

```json
{
  "code": 200,
  "message": "success",
  "data": { ... },
  "timestamp": "2026-07-10 14:30:00"
}
```

---

## 1. 开闸

向指定摄像头下发开闸命令，同步等待设备回复后返回结果。

```
POST /api/v1/devices/{deviceId}/gate/open
```

### 请求

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| deviceId | Path | String | 是 | 设备序列号（臻识 C5H 的 sn，如 `a422cb58-6c62f055`） |

请求体为空。

### 响应

**成功（设备返回 200）：**

```json
{
  "code": 200,
  "message": "success",
  "data": {
    "success": true,
    "deviceCode": 200,
    "message": "Gate opened"
  }
}
```

**设备拒绝（设备返回非 200）：**

```json
{
  "code": 500,
  "message": "Device returned error code: 500",
  "data": null
}
```

**设备不存在：**

```json
{
  "code": 404,
  "message": "Device not found: a422cb58-6c62f055"
}
```

**MQTT 未连接：**

```json
{
  "code": 503,
  "message": "MQTT is not connected, cannot send command"
}
```

**命令超时（10 秒无回复）：**

```json
{
  "code": 500,
  "message": "Command failed: ..."
}
```

---

## 2. 校时

将平台当前时间同步到设备。

```
POST /api/v1/devices/{deviceId}/time/sync
```

### 请求

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| deviceId | Path | String | 是 | 设备序列号 |

请求体为空。

### 响应

格式与开闸一致。

**成功：**

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

**失败：** 同开闸的错误码（404 / 503 / 500）。

---

## 3. 查询设备状态

查询设备在线状态、道闸状态。

```
GET /api/v1/devices/{deviceId}/status
```

### 请求

| 参数 | 位置 | 类型 | 必填 | 说明 |
|---|---|---|---|---|
| deviceId | Path | String | 是 | 设备序列号 |

### 响应

**成功：**

```json
{
  "code": 200,
  "data": {
    "deviceId": "a422cb58-6c62f055",
    "deviceName": "东门入口摄像头",
    "brand": "ZHENSHI",
    "model": "C5H",
    "online": true,
    "lastOnlineTime": "2026-07-10 14:29:55",
    "gateStatus": 1,
    "gateConnectStatus": 1
  }
}
```

| 字段 | 类型 | 说明 |
|---|---|---|
| deviceId | String | 设备序列号 |
| deviceName | String | 设备名称 |
| brand | String | 品牌，当前固定 ZHENSHI |
| model | String | 型号，当前固定 C5H |
| online | Boolean | 是否在线（30 秒内有心跳） |
| lastOnlineTime | String | 最后在线时间，从未上线时为 null |
| gateStatus | Integer | 道闸状态：0-关到位，1-开到位，2-中间状态。未收到过道闸状态时为 null |
| gateConnectStatus | Integer | 道闸连接状态：0-未连接，1-已连接。未收到过时为 null |

**设备不存在：**

```json
{
  "code": 404,
  "message": "Device not found: xxx"
}
```

---

## 错误码速查

| HTTP 状态码 | 含义 | 平台侧处理建议 |
|---|---|---|
| 200 | 成功 | — |
| 404 | 设备不存在 | 检查 deviceId 是否录入系统 |
| 503 | MQTT Broker 不可达 | 等待自动重连后重试（10 秒间隔） |
| 500 | 内部错误 / 命令超时 | 查看 message 字段判断具体原因 |

---

## 注意事项

1. **设备序列号**：`deviceId` 必须与臻识 C5H 的 `sn` 完全一致（UUID 格式，如 `a422cb58-6c62f055`），否则设备收不到命令。
2. **超时时间**：开闸和校时内部超时 10 秒，调用方无需额外处理超时。
3. **在线判定**：`getStatus` 的 `online` 基于设备心跳（5 秒一次），30 秒内无心跳视为离线。
4. **幂等性**：开闸和校时可重复调用，设备侧无幂等保护——每次调用都会执行。
