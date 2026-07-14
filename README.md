# 智慧停车 SaaS 平台 (jushan-platform)

> 文档状态：**Sprint 1 执行中**
> 最后更新：2026-07-13

## 项目定位

智慧停车 SaaS 平台（jushan-platform）是停车业务的核心业务平台，负责多租户管理、停车场配置、设备台账、停车计费、支付、订单管理、月卡、优惠券等业务功能，并提供管理后台、岗亭端和微信小程序。

Device Access 是独立的外部服务，负责设备通信、协议适配和事件转换。双方通过共享契约协作。

## 当前阶段

**Sprint 1：租户体系与平台底座重构（执行中）**

- 技术栈从 Sa-Token 切换为 Spring Security + JWT
- 模块结构：`parking-common` + `parking-infrastructure` + `parking-system` + `parking-boot`
- 数据库：Flyway 迁移，Snowflake 主键，软删除 `deleted_at`
- 前端：Ant Design Vue 3

**当前不可形成真实停车主链路** —— Device Access v0.2 无开闸、无车牌识别事件、无 RabbitMQ。

## 文档入口

### 协作规范（AI 与人类共用）

| 文档 | 用途 |
|------|------|
| [AGENTS.md](AGENTS.md) | 仓库级协作规范，停车平台特有约束 |
| [CLAUDE.md](CLAUDE.md) | Claude Code 执行手册，开发流程与检查清单 |

### 项目概述

| 文档 | 用途 |
|------|------|
| [项目介绍](docs/项目概述/项目介绍.md) | 项目定位与文档地图 |
| [技术架构](docs/项目概述/技术架构.md) | 实际技术栈与架构约束 |

### 需求与规范

| 文档 | 用途 | 状态 |
|------|------|------|
| [PRD 需求文档](docs/需求文档/停车SaaS系统完整需求文档_PRD_V1.0_最终定稿.md) | 完整产品需求文档 | **权威基线** |
| [开发计划](docs/开发计划/section_01_dev_plan.md) | 三期任务拆分（Sprint 1-23） | 持续更新 |
| [核心算法](docs/开发计划/section_02_algorithms.md) | 费用计算、优惠券匹配等伪代码 | 持续更新 |
| [数据库 DDL](docs/开发计划/section_03_ddl.md) | 完整表结构定义 | 持续更新 |
| [API 接口](docs/开发计划/section_04_api.md) | 核心 API 接口定义 | 持续更新 |
| [预留扩展](docs/开发计划/section_05_extensions.md) | 二期/三期预留接口设计 | 持续更新 |
| [技术架构](docs/开发计划/section_06_architecture.md) | 缓存、消息队列、部署约束 | 持续更新 |

### 部署运维

| 文档 | 用途 |
|------|------|
| [部署说明](docs/部署运维/部署说明.md) | Docker Compose 部署 |
| [配置说明](docs/部署运维/配置说明.md) | 应用配置与环境变量 |

### 共享契约（Platform ↔ Device Access）

| 文档 | 用途 | 优先级 |
|------|------|--------|
| [08-联合评审决策表](docs/contracts/platform-device-access/08-联合评审决策表.md) | 双方 ACCEPTED 的联合决策 | **第一优先级** |
| [05-目标契约-v1.0-草案](docs/contracts/platform-device-access/05-目标契约-v1.0-草案.md) | 目标接口和事件契约 | 参考 |
| [04-当前兼容契约-v0.2](docs/contracts/platform-device-access/04-当前兼容契约-v0.2.md) | 当前代码事实 | 参考 |

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

## 技术约束速查

| 约束项 | 落地要求 |
|--------|----------|
| 多租户 `tenant_id` | 所有表必须包含 `tenant_id`；MyBatis-Plus 租户插件自动注入 |
| 软删除 `deleted_at` | 所有业务表使用 `deleted_at DATETIME(3)`；禁止物理删除 |
| 主键 | Snowflake 算法（`IdType.ASSIGN_ID`），禁止 `AUTO_INCREMENT` |
| 金额 `BigDecimal` | 所有金额字段使用 `BigDecimal`；DB 用 `DECIMAL(18,2)`；禁止浮点数 |
| 车牌大写存储 | 入库前 `toUpperCase()`；查询使用大写匹配 |
| 操作日志 | AOP + `@BusinessLog`；记录变更前后 JSON；敏感字段脱敏 |
| 并发控制 | Redisson 分布式锁 + `@Version` 乐观锁 |
| 预留接口 | 标注【预留】；返回 mock；字段定义完整 |
| 幂等 | 写接口携带 `X-Idempotency-Key`；服务端 24 小时去重 |

## 模块结构

```
jushan-platform/
├── parking-common/              # 公共模块（BaseEntity、ErrorCode、工具类）
├── parking-infrastructure/      # 基础设施层（安全、租户、日志、异常处理）
├── parking-system/              # 业务模块（实体、Mapper、Service、Controller）
├── parking-boot/                # 启动模块（Application、Flyway 迁移）
├── admin-web/                   # PC 运营平台前端（Vue 3 + Ant Design Vue）
├── booth-web/                   # 岗亭端前端（Vue 3 + PWA）
└── miniapp/                     # 车主小程序（微信小程序原生框架）
```
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
| [项目介绍](docs/项目概述/项目介绍.md) | 项目定位与文档地图 |
| [技术架构](docs/项目概述/技术架构.md) | 实际技术栈 |
| [PRD 需求文档](docs/需求文档/停车SaaS系统完整需求文档_PRD_V1.0_最终定稿.md) | 完整产品需求文档 |
| [开发计划](docs/开发计划/section_01_dev_plan.md) | 任务拆分（Sprint 1-23）、核心算法、DDL、API 接口、技术架构 |
| [部署说明](docs/部署运维/部署说明.md) | Docker Compose 部署 |
| [配置说明](docs/部署运维/配置说明.md) | 应用配置与环境变量 |

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
