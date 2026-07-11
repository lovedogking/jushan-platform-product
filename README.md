# 智慧停车 SaaS 平台 (jushan-platform)

> 文档状态：**DRAFT FOR JOINT REVIEW**
> 最后更新：2026-07-11

## 项目定位

智慧停车 SaaS 平台（jushan-platform）是停车业务的核心业务平台，负责多租户管理、停车场配置、设备台账、停车计费、支付、订单管理、月卡、优惠券等业务功能，并提供管理后台、岗亭端和微信小程序。

Device Access 是独立的外部服务，负责设备通信、协议适配和事件转换。双方通过共享契约协作。

## 当前阶段

M0（基线与冻结）→ M1+（工程骨架）。基础框架已搭建（多模块 Maven、Spring Boot 3、MyBatis-Plus、Sa-Token），业务模块正在逐步实现。

**当前不可形成真实停车主链路** —— Device Access v0.2 无开闸、无车牌识别事件、无 RabbitMQ。

## 文档入口

| 文档 | 用途 |
|------|------|
| [AGENTS.md](AGENTS.md) | 仓库级协作规范，AI 与人类共用 |
| [CLAUDE.md](CLAUDE.md) | Claude Code 执行手册 |
| [平台侧需求规格说明书](docs/需求文档/平台侧需求规格说明书.md) | 产品需求唯一主文档 |
| [平台总体架构方案](docs/架构方案/平台总体架构方案.md) | 技术架构、模块边界、部署、安全 |
| [开发计划](docs/开发计划.md) | 逐任务开发计划 |
| [基础响应与认证契约](docs/接口规范/基础响应与认证契约.md) | Platform 内部接口响应格式和认证规范 |

## 共享契约入口

Platform ↔ Device Access 跨系统契约：[docs/contracts/platform-device-access/](docs/contracts/platform-device-access/)

| 文档 | 用途 |
|------|------|
| [08-联合评审决策表](docs/contracts/platform-device-access/08-联合评审决策表.md) | **第一优先级**：双方 ACCEPTED 的联合决策 |
| [05-目标契约-v1.0-草案](docs/contracts/platform-device-access/05-目标契约-v1.0-草案.md) | 目标接口和事件契约 |
| [04-当前兼容契约-v0.2](docs/contracts/platform-device-access/04-当前兼容契约-v0.2.md) | 当前代码事实 |

## Device Access v0.2 当前能力

7 个 HTTP 端点：

1. `POST /api/v1/devices` — 创建设备
2. `GET /api/v1/devices` — 查询列表
3. `GET /api/v1/devices/{deviceId}` — 查询单个
4. `PUT /api/v1/devices/{deviceId}` — 更新设备
5. `DELETE /api/v1/devices/{deviceId}` — 删除设备
6. `POST /api/v1/devices/{deviceId}/time/sync` — 校时
7. `GET /api/v1/devices/{deviceId}/status` — 状态查询

**当前没有：** 开闸、车牌识别事件、RabbitMQ、HMAC、commandId 幂等。

## 联合决策状态

| 事项 | 状态 |
|------|------|
| B01～B08 | ✅ **ACCEPTED**（双方已同意全部推荐方案，2026-07-11） |
| V01～V04 真机验证 | ⬜ 待完成（阻塞第一阶段编码） |
| 整体契约 | DRAFT FOR JOINT REVIEW |

## 文档优先级

1. 共享契约 08 中 ACCEPTED 的联合决策
2. 共享契约 05（目标契约）
3. 共享契约 OpenAPI / AsyncAPI / JSON Schema
4. 共享契约 04（当前代码事实）
5. 平台需求规格和开发计划
6. Device Access api-v0.2.md 和 ARCHITECTURE.md
7. 厂商协议
8. archive 中历史资料

> 当前实现看 04，目标契约看 05，联合决策看 08。
