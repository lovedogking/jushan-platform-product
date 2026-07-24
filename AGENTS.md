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

---

## 四、⭐ 需求进度文档（强制规则）

本仓库维护一套**需求进度文档**，位于 [`docs/requirements/`](docs/requirements/README.md)，回答三个问题：**已经做了什么 / 还没做什么 / 下一步做什么**。

| 文档 | 内容 |
|---|---|
| `docs/requirements/README.md` | 进度总览：阶段划分、功能域完成度速览、下一步重点 |
| `docs/requirements/已实现需求.md` | 已实现功能清单（按功能域，含页面/接口/表入口），标注占位🧪/半成品🚧/冻结⚠️ |
| `docs/requirements/待实现需求.md` | 未实现需求清单（二期 P0–P3）、与现状的 Gap、明确不做清单、风险依赖 |

### 规则 1 — 接需求 / 评估进度先查需求文档

接到「新需求开发 / 进度评估 / 迭代规划」类任务，按顺序：

1. 先读 [`docs/requirements/README.md`](docs/requirements/README.md) 与待实现清单，判断该需求属于「已实现 / 部分实现 / 未实现 / **明确不做**」哪一类；
2. 再按需求文档中标注的代码索引路径定位实现细节（功能级 → 代码级两层递进，不直接全库盲搜）；
3. **禁止**把已实现功能当新需求重复开发；**禁止**把「明确不做」清单中的功能纳入方案。

### 规则 2 — 改动必须同步更新需求文档（硬性）

| 改动类型 | 需同步动作 |
|---|---|
| 完成一个待实现需求（或可独立验收的子项） | 在 `待实现需求.md` 标记完成 / 移除；在 `已实现需求.md` 对应功能域登记（入口 / 数据表 / 状态）；刷新两文件头「最近更新」日期 |
| 占位 / 半成品功能转为正式实现 | 更新 `已实现需求.md` 该条的状态标记与说明 |
| 需求范围变更（新增 / 砍掉 / 调整优先级） | 更新 `待实现需求.md`：新增条目、删除条目或移入「明确不做」，并记录变更日期 |
| 发现代码已有但需求文档未登记的功能 | 补登 `已实现需求.md` |

> 本规则与第三节代码索引同步规则**并列生效**：功能代码改完后，**代码索引 + 需求进度文档都要在同一次改动中更新**。未同步的视为未完成。

### 写需求文档时的颗粒度约定

- 需求文档是**功能级**视角（一条 = 一个可验收的用户功能），**不写类/方法级细节** —— 细节一律引用 `docs/code-index/` 对应模块索引路径。
- 每条已实现功能标注：入口（页面 / 接口基础路径）、主要数据表、状态（✅ 正式 / 🧪 占位 / ⚠️ 冻结 / 🚧 半成品）。
- 每条待实现需求标注：优先级（P0–P3）、实现状态（◻️ 未启动 / ◧ 部分实现，附现状引用）。
- 术语与字段命名与代码保持一致，不发明新叫法。

---

## 五、⭐ 文档实时更新总则（随时更新规则）

**核心原则：文档与代码同寿命。** 本仓库所有文档（代码索引 `docs/code-index/`、需求进度文档 `docs/requirements/`、协议文档 `docs/协议/` 等）必须与代码**随时保持一致**，以下三条对 AI 助手是硬性要求：

### 规则 1 — 主动更新，不等提醒

任何任务中改动了以下任一项，**必须在同一轮工作中主动更新对应文档**，无需用户明确要求：

| 改动对象 | 必须同步更新的文档 |
|---|---|
| 类 / 方法 / REST 接口 / 包结构 | `docs/code-index/` 对应模块索引（见第三节） |
| 功能交付 / 需求范围 / 优先级 / 功能状态 | `docs/requirements/` 需求进度文档（见第四节） |
| 设备协议 / 通信主题 / 品牌适配差异 | `docs/协议/` 对应品牌文档 |
| 技术栈 / 模块划分 / 代码约定 | 本文件（AGENTS.md）第一、二节 |

### 规则 2 — 顺手修正过期内容

查阅任何文档时发现与实际代码不符（路径失效、接口已删、状态过时、描述不符），**立即修正并刷新该文件「最近更新」日期**，不得留到“以后处理”，也不得在答复中继续引用已知过期的内容。

### 规则 3 — 收尾自查（每次任务结束前必做）

1. 改了类 / 方法 / 接口 → 代码索引更新了吗？
2. 交付 / 变更了功能 → 需求进度文档更新了吗？
3. 涉及文档的「最近更新」日期都刷新了吗？

> **未同步更新文档的改动一律视为未完成。**

---

## 六、Agent Skills 配置

### Issue tracker

Issues 以 Local markdown 形式存储在 `.scratch/<feature-slug>/` 下。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认五标签体系：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文：`CONTEXT.md` 在仓库根目录，ADR 在 `docs/adr/`。详见 `docs/agents/domain.md`。
