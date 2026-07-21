# Jushan Platform 代码索引（总览）

> 本目录是全仓库的**分层代码索引**，记录每个模块下类、方法的**路径、功能、REST 接口**，供 AI 与开发者快速定位代码。
>
> 使用与维护规则见根目录 [`AGENTS.md`](../../AGENTS.md) 第三节。**核心两条**：
> 1. **定位优先查索引** —— 改 / 查代码前先看本页模块地图，再进对应模块索引文件。
> 2. **改动同步更新** —— 改了类 / 方法 / 接口，必须在同一次改动中更新对应索引文件。

---

## 一、怎么用（AI 定位路径）

```
任务 → 读本页「模块地图」判断归属模块 → 打开该模块索引 .md → 用路径定位源码
```

例：「调整停车计费逻辑」→ 计费属 parking 模块 → 打开 [`backend/parking-system/modules-parking.md`](backend/parking-system/modules-parking.md) → 定位到 `FeeCalculationService` / `FeeRuleService` 的路径与方法。

---

## 二、模块地图

状态图例：✅ 已建索引 ｜ ✅ 待补充

### 后端 · parking-system

`com.jushan.platform.modules.*`（标准分层业务）

| 模块 | 职责 | 类数 | 索引 | 状态 |
|---|---|---|---|---|
| parking | 停车会话、计费规则、费用计算、通行/车位/车道策略、分析 | 68 | [modules-parking.md](backend/parking-system/modules-parking.md) | ✅ |
| vehicle | 车辆档案、钱包、审核 | 40 | `backend/parking-system/modules-vehicle.md` | ✅ |
| account | 管理账号、角色、权限 | 27 | `backend/parking-system/modules-account.md` | ✅ |
| miniapp | 小程序用户、支付、访客、消息 | 25 | `backend/parking-system/modules-miniapp.md` | ✅ |
| booth | 岗亭：识别事件、人工入场、减免、交班 | 20 | `backend/parking-system/modules-booth.md` | ✅ |
| company | 企业/租户主体管理 | 9 | `backend/parking-system/modules-company.md` | ✅ |
| department | 部门管理 | 9 | `backend/parking-system/modules-department.md` | ✅ |
| device | 设备 Webhook 接入 | 7 | `backend/parking-system/modules-device.md` | ✅ |
| auth | 登录鉴权、改密 | 4 | `backend/parking-system/modules-auth.md` | ✅ |
| log | 业务日志 | 3 | `backend/parking-system/modules-log.md` | ✅ |
| tenant | 租户 | 1 | `backend/parking-system/modules-tenant.md` | ✅ |

`com.jushan.system.*`（较早的扁平业务实现，按层拆分索引）

| 分层 | 内容 | 类数 | 索引 | 状态 |
|---|---|---|---|---|
| controller | 44 个后台/岗亭/小程序 Controller | 44 | `backend/parking-system/system-controller.md` | ✅ |
| service | 业务服务 | 46 | `backend/parking-system/system-service.md` | ✅ |
| entity + mapper | 实体与数据访问 | 101 | `backend/parking-system/system-entity-mapper.md` | ✅ |
| dto + vo | 请求/响应对象 | 88 | `backend/parking-system/system-dto-vo.md` | ✅ |
| 其它 | cache/client/config/event/task/ws/job | 33 | `backend/parking-system/system-misc.md` | ✅ |

> 两套包结构的关系与选型说明见 `backend/parking-system/_overview.md`（✅）。

### 后端 · 基础模块

| 模块 | 职责 | 类数 | 索引 | 状态 |
|---|---|---|---|---|
| parking-common | R / BaseEntity / 异常 / 租户上下文 | 8 | `backend/parking-common.md` | ✅ |
| parking-framework | 框架层通用能力 | 27 | `backend/parking-framework.md` | ✅ |
| parking-infrastructure | 安全 / 日志 / MyBatis / Web | 19 | `backend/parking-infrastructure.md` | ✅ |
| parking-boot | 启动与全局配置 | 8 | `backend/parking-boot.md` | ✅ |
| device-access | 设备接入（adapter/api/mqtt/event/registry/starter） | 105 | `backend/device-access.md` | ✅ |

### 前端

| 模块 | 职责 | 文件 | 索引 | 状态 |
|---|---|---|---|---|
| frontend | 岗亭端（api/components/stores/views） | 23 vue + 18 ts | `frontend/frontend.md` | ✅ |
| miniapp | 小程序（预留） | — | `frontend/miniapp.md` | ✅ |

---

## 三、目录结构

```
docs/code-index/
├── README.md                 # 本文件：总览 + 模块地图
├── _TEMPLATE.md              # 模块索引文件的统一模板
├── backend/
│   ├── parking-common.md     ├── parking-framework.md
│   ├── parking-infrastructure.md   ├── parking-boot.md
│   ├── device-access.md
│   └── parking-system/
│       ├── _overview.md            # 两套包结构说明
│       ├── modules-*.md            # platform.modules.* 各业务模块
│       └── system-*.md             # com.jushan.system.* 按层拆分
└── frontend/
    ├── frontend.md
    └── miniapp.md
```

---

## 四、维护约定（摘要）

- 每个模块索引文件头部含「最近更新」日期，改动后同步刷新。
- 颗粒度：Controller 逐方法；Service 逐 public 方法；Entity/DTO/VO/Mapper 类级一行 + 关键字段。
- 新增模块：新建索引文件 + 在本页模块地图登记并置为 ✅。
- 完整规则见 [`AGENTS.md`](../../AGENTS.md)。
