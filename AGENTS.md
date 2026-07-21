# AGENTS.md — Jushan Platform 协作规范

本文件是 AI 编码助手的**项目级规则**，开始任何代码任务前必读。

---

## 一、项目概览

Jushan Platform 停车管理平台，采用「多模块单体后端 + 独立设备接入服务 + 前端」结构：

| 模块 | 说明 |
|---|---|
| `parking-system` | 核心业务后端（主力，537 类）。内含两套包：`com.jushan.platform.modules.*`（标准分层新业务）与 `com.jushan.system.*`（较早的扁平业务实现） |
| `parking-common` | 通用基类与响应封装：`R`、`BaseEntity`、`BusinessException`、`TenantContext` 等 |
| `parking-framework` | 框架层（Web/异常/拦截等通用能力） |
| `parking-infrastructure` | 基础设施：安全、日志、MyBatis、Web 配置 |
| `parking-boot` | 应用启动与全局配置 |
| `device-access` | 设备接入服务（独立多模块 Maven：adapter/api/common/event/mqtt/registry/starter） |
| `frontend` | 岗亭端前端（Vue3 + TS + Vite） |
| `miniapp` | 小程序端（预留） |

**技术栈**：Java 21 · Spring Boot 3.5.16 · MyBatis-Plus 3.5.9 · Flyway · MySQL · Hutool；前端 Vue 3.4 · TypeScript 5.4 · Vite 5.2 · Pinia · ant-design-vue 4.1 · axios。

---

## 二、代码约定

- **统一响应**：Controller 一律返回 `com.jushan.common.R<T>`；成功用 `R.ok(data)` / `R.ok()`，失败抛 `BusinessException`（由全局处理器转 `R.fail`）。
- **REST 风格**：路径统一前缀 `/api/v1/...`；接口权限用 `@RequirePermission("模块:动作")`（如 `fee:write`、`parking:read`）。
- **多租户**：业务查询经 `TenantContext` 解析租户；平台用户（super_admin / platform_operator）无租户绑定（`tenantId == null`），可跨租户访问。
- **金额**：一律以**整数分**存储与传递，禁止浮点数。
- **Service 分层**：存在两种写法——① 接口 + `impl/` 实现；② 直接继承 MyBatis-Plus `ServiceImpl<Mapper, Entity>` 的具体类。改代码时先看清目标属于哪种。
- **前端 API**：封装在 `frontend/src/api/*.ts`，每个函数用 JSDoc 标注 HTTP 方法与后端路径。

---

## 三、⭐ 代码索引（强制规则）

本仓库维护一套**分层代码索引**，位于 [`docs/code-index/`](docs/code-index/README.md)。
它记录每个模块下类、方法的**路径、功能、REST 接口**，用于快速定位代码，避免全库盲搜。

### 规则 1 — 定位优先查索引

接到任何「改 / 查 / 排查」代码的任务，按顺序：

1. 先读 [`docs/code-index/README.md`](docs/code-index/README.md) 的**模块地图**，判断目标业务属于哪个模块；
2. 打开对应**模块索引文件**，用其中记录的路径直接定位类 / 方法 / 接口；
3. 再打开源码。**禁止在未查索引的情况下对全库做盲目 grep / 遍历。**

### 规则 2 — 改动必须同步更新索引（硬性）

任何改动源码结构后，**必须在同一次改动中**更新对应模块索引文件，并刷新文件头「最近更新」日期：

| 改动类型 | 需同步更新的索引内容 |
|---|---|
| 新增 / 删除 / 重命名 REST 接口 | 该 Controller 的「接口清单」表 |
| 修改接口 路径 / 权限 / 入参 / 返回 | 对应接口行 |
| 新增 / 删除 Service public 方法 | 该 Service 的「关键方法」表 |
| 新增 / 删除 / 重命名 类 | 对应分节表 +（如影响模块职责）根 README 模块地图 |
| 新增业务模块（新建包） | 新建模块索引文件 + 在根 README 模块地图登记 |

> **未同步更新索引的代码改动视为未完成。** 收尾 / 提交前对照本表逐条自查。

### 写索引时的颗粒度约定

- **Controller**：逐方法列出（方法名 / HTTP / 路径 / 权限 / 说明 / 入参 / 返回）。
- **Service**：public 方法逐一列出；private 辅助方法不列。
- **Entity / DTO / VO / Mapper**：类级一行功能描述 + 关键字段 / 关键查询，**不逐字段展开**。
- **工具类 / Config**：类级一行描述即可。
- 统一参照模板：[`docs/code-index/_TEMPLATE.md`](docs/code-index/_TEMPLATE.md)。
