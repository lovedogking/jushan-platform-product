# Device Access 项目交接 — 当前状态与计划

> 日期：2026-07-12
> 分支：`device`
> 最新提交：`131babd` docs(ROADMAP): 同步前端定位更新

---

## 一、已完成

### v0.3 全部交付 ✅
- 产品目录（t_device_product）
- 设备方向（ENTRANCE/EXIT/BIDIRECTIONAL）
- 设备关系（AUX_CAMERA / RS485_DISPLAY）
- Vue 3 设备管理后台（注册/查看/关系管理）
- 显示屏控制 API（PERIPHERAL_CONTROL / DISPLAY_TEXT / DISPLAY_SAVE）
- DeviceCapability 能力模型
- ZhenshiDeviceCoordinator 命令编排器

### 架构边界审查修复 ✅（commit `92662f0`）
- OPEN_GATE / CLOSE_GATE 能力与种子数据
- 命令 API 能力校验（requireCapability → 422）
- 信路通协议解析下沉（XinlutongMessageHandler.parseCommandResult + XinlutongCommandResult）
- 心跳/上下线逻辑公共化（DeviceHeartbeatRecorder，两品牌共用）
- DeviceService 消除品牌 switch（BrandCommandDispatcher）
- BrandCommandDispatcherTest + DeviceControllerTest

### 前端定位明确 ✅（commit `e421622`）
- 前端 = 设备管理后台（运维：注册/查看/关系/配置展示）
- 设备控制（开闸/显示/校时）由业务侧 Parking Platform 直接调用 REST API
- 前端已移除显示屏实时控制面板

### 信路通 XLT-01 真机联调 ✅
- MQTT 连接/心跳（Conn/Rtd）通过
- 校时（set_time）通过
- 开闸（Open）通过
- 关闸（Close）通过

---

## 二、当前代码结构

```
device-access
├── starter              # Spring Boot 启动入口（端口 8081）
├── common               # DTO、Entity、Enum、Exception
│   └── 注意：DeviceDTO / DeviceProductDTO / DeviceDetailDTO 已暴露 capabilities 字段（DeviceService.toDTO() 从 product.getCapabilityList() 填充）
├── mqtt                 # MQTT 通信（订阅了 device/+/message/up/# 和 upload/+）
├── adapter
│   ├── support          # DeviceHeartbeatRecorder（两品牌共用）
│   ├── xinlutong        # XinlutongMessageHandler + XinlutongCommandResult
│   └── zhenshi          # ZhenshiMessageHandler
├── registry             # DeviceRegistry + DeviceProductRegistry + DeviceRelationRegistry
├── api                  # REST 接口 + BrandCommandDispatcher + Coordinator
│   ├── DeviceController（18 端点）
│   ├── DeviceService（通过 BrandCommandDispatcher 调用各品牌）
│   ├── ZhenshiDeviceCoordinator
│   └── XinlutongDeviceCoordinator
└── frontend             # Vue 3 设备管理后台（端口 5173）
```

**依赖方向**：starter → api → adapter → mqtt → common，starter → api → registry → common

---

## 三、待决策事项

### 事项 1：是否提取 DeviceAdapter 接口？

| 选择 | 说明 | 工作量 |
|------|------|:------:|
| A. 不提取 | BrandCommandDispatcher 就是最终方案。两段 switch 复杂度可控。 | 0 |
| B. 提取 | 新增 DeviceAdapter 接口 + ZhenshiDeviceAdapter + XinlutongDeviceAdapter + AdapterFactory，删除 BrandCommandDispatcher。DeviceService 只依赖接口。 | 半天 |

**BrandCommandDispatcher 公共方法 7 个**：syncTime、openGate、closeGate、isDeviceOnline、controlPeripheral、displayText、saveDisplay

### 事项 2：后端 DTO 是否暴露 capabilities？

当前 DeviceDTO / DeviceProductDTO / DeviceDetailDTO **没有 capabilities 字段**。前端无法按能力模型判断，仍用 deviceType。

如果要支持前端按 capabilities 动态展示（如隐藏不支持的按钮），需要：
- DeviceDTO 增加 `capabilities` 字段
- DeviceProductDTO 增加 `capabilities` 字段  
- DeviceService.toDTO() 从 product 填充 capabilities

### 事项 3：下一步路线

| 路线 | 顺序 |
|------|------|
| 路线 A（推荐，上线快） | 信路通联调通过 → 直接做 v1.0 生产就绪（测试/鉴权/监控/CI） |
| 路线 B | 信路通联调通过 → 提取 DeviceAdapter → 做 v1.0 生产就绪 |

---

## 四、已知问题

1. **臻识 Handler 日志噪音**：ZhenshiMessageHandler 收到信路通 `upload/...` 消息时报警告 "without 'name' field"。无害，但日志杂乱。
2. **校时命令**：信路通 XLT-01 对 set_time 返回 errCode=1 "Unknow CMD"（代码注释已记录）。

---

## 五、启动命令

```bash
# 后端
mvn clean install -DskipTests
cd device-access-starter && mvn spring-boot:run
# http://localhost:8081

# 前端
cd device-access-frontend && npm run dev
# http://localhost:5173
```

---

## 六、关键文件位置

| 文件 | 路径 |
|------|------|
| 启动类 | `device-access-starter/src/main/java/.../DeviceAccessApplication.java` |
| 配置文件 | `device-access-starter/src/main/resources/application.yml` |
| 数据库 DDL | `device-access-starter/src/main/resources/schema.sql` |
| 信路通 Handler | `device-access-adapter/.../xinlutong/XinlutongMessageHandler.java` |
| BrandCommandDispatcher | `device-access-api/.../api/BrandCommandDispatcher.java` |
| DeviceService | `device-access-api/.../api/DeviceService.java` |
| DeviceController | `device-access-api/.../api/DeviceController.java` |
| 前端 API | `device-access-frontend/src/api/devices.js` |
| 前端主组件 | `device-access-frontend/src/App.vue` |

---

## 七、API 清单

### 管理类（前端 + 业务侧均可调用）
- POST /api/v1/devices — 注册
- GET /api/v1/devices — 列表
- GET /api/v1/devices/{id} — 详情
- PUT /api/v1/devices/{id} — 更新
- DELETE /api/v1/devices/{id} — 注销
- GET /api/v1/products — 产品目录
- GET/POST/DELETE/PUT /api/v1/devices/{id}/relations/* — 关系管理

### 控制类（业务侧 Parking Platform 直接调用）
- POST /api/v1/devices/{id}/time/sync — 校时
- GET /api/v1/devices/{id}/status — 状态查询
- POST /api/v1/devices/{id}/gate/open — 开闸（仅信路通）
- POST /api/v1/devices/{id}/gate/close — 关闸（仅信路通）
- POST /api/v1/devices/{id}/peripheral/display — 显示屏控制
- POST /api/v1/devices/{id}/display/text — 实时显示
- POST /api/v1/devices/{id}/display/save — 保存显示

---

*在新 Claude Code 窗口中，先阅读本文件，然后阅读 CLAUDE.md 和 docs/ARCHITECTURE.md，再开始工作。*
