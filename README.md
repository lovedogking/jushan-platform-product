# 飓山停车 SaaS 平台

> 版本：v1.0 开发版 · 最后更新：2026-07-17

## 项目概述

飓山停车 SaaS 是一个多租户智慧停车管理平台，涵盖运营端（Web）、岗亭端（Web）、小程序端（微信）三端，通过 Device Access 适配器层对接停车场硬件设备（相机、道闸等）。

## 仓库结构

```
jushan-platform/
│
├── parking-*/              ← 平台侧后端（Java 21, Spring Boot 3.5.16）
├── admin-web/              ← 运营端前端（Vue 3 + Ant Design Vue）
├── booth-web/              ← 岗亭端前端（Vue 3）
├── miniapp/                ← 车主小程序（微信原生）
│
├── device-access/          ← 设备接入层（Java 17, Spring Boot 3.3.7）
│   ├── device-access-starter/
│   ├── device-access-api/       ← REST 接口（21 个端点）
│   ├── device-access-adapter/   ← 品牌协议适配（臻识 C5H / 信路通 XLT-01）
│   ├── device-access-mqtt/      ← MQTT 通信层
│   ├── device-access-registry/  ← 设备元数据管理
│   ├── device-access-event/     ← 事件推送（HTTP Webhook）
│   └── device-access-common/    ← 公共模块
│
├── docs/                   ← 统一文档目录
├── Makefile                ← 构建编排
└── docker-compose.yml      ← 本地开发环境
```

## 快速开始

### 平台侧（需要 Java 21）

```bash
make build-platform      # 编译
make test-platform       # 测试
make package-platform    # 打包
```

### 设备侧（需要 Java 17）

```bash
make build-device-access  # 编译
make test-device-access   # 测试
make package-device-access # 打包
```

### 前端

```bash
make build-admin      # 运营端
make build-booth      # 岗亭端
make build-frontend   # 全部前端
```

### 全部

```bash
make build-all
make test-all
make clean
```

## 文档入口

### 核心文档

| 文档 | 用途 |
|------|------|
| [需求规格说明书](docs/需求规格说明书_v1.2.md) | 权威需求基线 |
| [接口契约](docs/接口契约/) | Platform ↔ Device Access 跨项目约定 |

### 平台侧

| 文档 | 用途 |
|------|------|
| [开发计划](docs/开发计划/) | 任务拆分、DDL、API 设计 |
| [部署运维](docs/部署运维/) | Docker Compose 部署与配置 |
| [项目概述](docs/项目概述/) | 技术架构与项目介绍 |

### Device Access

| 文档 | 用途 |
|------|------|
| [架构设计](docs/Device-Access/架构设计.md) | 模块职责、数据流、依赖关系 |
| [演进路线](docs/Device-Access/演进路线.md) | 版本规划（v0.1 → v2.0） |
| [API 接口文档](docs/接口文档/API接口文档.md) | 完整 REST API 参考 |
| [业务侧 API 手册](docs/接口文档/业务侧API手册.md) | 面向平台侧的调用指南 |
| [对接资料](docs/Device-Access/对接资料/) | 臻识/科发/信路通硬件协议 |

## Device Access 当前能力（v0.4）

- 设备管理：注册 / 查询 / 更新 / 注销 / 产品目录 / 关系管理
- 设备控制：开闸 / 关闸 / 校时 / 状态查询
- 显示屏控制：实时显示 / 保存显示 / 配置（音量/亮度/方向）/ 增强显示 / 语音播报
- 事件推送：`PLATE_RECOGNIZED` 车牌识别事件 → HTTP Webhook → 平台侧
- 品牌支持：臻识 C5H + 科发 OLM-M1D 显示屏 / 信路通 XLT-01
- API Key 认证

## 开发协作

### 分支策略

```
master     ← 生产版本
develop    ← 唯一开发主线（所有人从这里拉分支）
feat/xxx   ← 功能分支 → 完成后合入 develop
```

### 注意事项

- 平台侧和 Device Access 是两套独立的 Maven 项目，各自编译、各自部署
- 平台侧改 `parking-*/`，设备侧改 `device-access/`，互不干扰
- 修改接口契约时，先更新 `docs/接口契约/` 再由双方评审
- 个人 AI 开发配置（CLAUDE.md / AGENTS.md 等）已加入 `.gitignore`，不上传仓库

## 技术栈

| 领域 | 平台侧 | Device Access |
|------|--------|---------------|
| 语言 | Java 21 | Java 17 |
| 框架 | Spring Boot 3.5.16 | Spring Boot 3.3.7 |
| 构建 | Maven (mvnw) | Maven |
| ORM | MyBatis-Plus 3.5.9 + Flyway | MyBatis-Plus 3.5.9 |
| 数据库 | MySQL 8.4 | MySQL 8.4 |
| 通信 | RabbitMQ + WebSocket | MQTT (EMQX) + HTTP Webhook |
| 前端 | Vue 3 + Ant Design Vue | 无（仅提供 API） |
