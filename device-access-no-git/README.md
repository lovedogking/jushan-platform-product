# Device Access 设备接入服务 (jushan-device-access)

> 文档状态：**DRAFT FOR JOINT REVIEW**
> 最后更新：2026-07-11

## 项目定位

Device Access 是智慧停车系统的设备接入层，负责：

- 与现场设备通信（MQTT）
- 厂商协议适配（当前：臻识 C5H）
- 向 Platform 发布统一业务事件（阶段 1 目标）
- 接收和执行 Platform 下发的命令

Device Access **不处理**停车计费、订单、支付、月卡等业务逻辑。Platform 是设备业务配置的唯一主数据源。

## 当前版本 v0.3

### 主要 HTTP 端点

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/api/v1/devices` | 注册设备 |
| GET | `/api/v1/devices` | 设备列表 |
| GET | `/api/v1/devices/{deviceId}` | 设备详情 |
| PUT | `/api/v1/devices/{deviceId}` | 更新设备 |
| DELETE | `/api/v1/devices/{deviceId}` | 注销设备 |
| POST | `/api/v1/devices/{deviceId}/time/sync` | 校时 |
| GET | `/api/v1/devices/{deviceId}/status` | 状态查询 |
| POST | `/api/v1/devices/{deviceId}/gate/open` | 开闸 |
| POST | `/api/v1/devices/{deviceId}/gate/close` | 关闸 |
| GET | `/api/v1/products` | 产品目录 |
| GET | `/api/v1/devices/{deviceId}/relations` | 查询关系 |
| POST | `/api/v1/devices/{deviceId}/relations` | 创建关系 |
| DELETE | `/api/v1/devices/{deviceId}/relations/{relationId}` | 删除关系 |
| PUT | `/api/v1/devices/{deviceId}/relations/{relationId}/enable` | 启用关系 |
| PUT | `/api/v1/devices/{deviceId}/relations/{relationId}/disable` | 停用关系 |
| POST | `/api/v1/devices/{deviceId}/peripheral/display` | 显示屏控制 |
| POST | `/api/v1/devices/{deviceId}/display/text` | 实时显示文字 |
| POST | `/api/v1/devices/{deviceId}/display/save` | 保存显示内容 |

### 当前没有

- **ZHENSHI 品牌开闸/关闸**（待 V04 真机确认）
- 跨系统事件通道（RabbitMQ）
- HMAC 服务间认证
- commandId 幂等
- COMMAND_RESULT
- 多租户/多停车场绑定

完整 API 文档见 [api-v0.3.md](docs/api-v0.3.md)。

## 文档入口

| 文档 | 用途 |
|------|------|
| [CLAUDE.md](CLAUDE.md) | Device Access 执行规则 |
| [api-v0.3.md](docs/api-v0.3.md) | v0.3 当前 API 文档 |
| [ARCHITECTURE.md](docs/ARCHITECTURE.md) | 技术架构 |
| [ROADMAP.md](docs/ROADMAP.md) | 演进路线图 |

## 共享契约

Platform ↔ Device Access 跨系统契约：[docs/contracts/platform-device-access/](docs/contracts/platform-device-access/)

## 厂商协议

臻识 C5H：[docs/vendor/zhenshi/](docs/vendor/zhenshi/)

## 联合决策状态

| 事项 | 状态 |
|------|------|
| B01～B08 | ✅ **ACCEPTED**（双方，2026-07-11） |
| V01～V04 真机验证 | ⬜ 待完成 |
| 整体契约 | DRAFT FOR JOINT REVIEW |
