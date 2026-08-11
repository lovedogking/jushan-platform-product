# AGENTS.md — Jushan Platform 协作规范

本文件是 AI 编码助手的**项目级规则**，开始任何代码任务前必读。

---

## 一、项目概览

Jushan Platform 停车管理平台，采用「多模块单体后端 + 独立设备接入服务 + 前端」结构：

| 模块 | 说明 |
|---|---|
| `parking-system` | 核心业务后端（主力）。`com.jushan.platform.modules.*`（标准分层）为全部活跃模块，`com.jushan.system.*` 已清空（仅剩 `entity/Company.java`）。新代码**禁止**在 `system.*` 下新增类 |
| `parking-common` | 通用基类与响应封装：`R`、`BaseEntity`、`BusinessException`、`TenantContext`、`TenantIgnore` 等 |
| `parking-framework` | 框架层（Web/异常/拦截等通用能力） |
| `parking-infrastructure` | 基础设施：安全、日志、MyBatis（租户拦截/字段填充）、Web 配置 |
| `parking-boot` | 应用启动与全局配置 |
| `device-access` | 设备接入服务（独立多模块 Maven：adapter/api/common/event/mqtt/registry/starter） |
| `frontend` | 岗亭端前端（Vue3 + TS + Vite） |
| `h5-user` | H5 车主端（Vue3 + TS + Vite + Vant）：查费/缴费 |
| `miniapp` | 小程序端（预留） |

**技术栈**：Java 21 · Spring Boot 3.5.16 · MyBatis-Plus 3.5.9 · Flyway · MySQL · Hutool；前端 Vue 3.4 · TypeScript 5.4 · Vite 5.2 · Pinia · ant-design-vue 4.1 · axios。

---

## 二、代码约定

- **统一响应**：Controller 一律返回 `com.jushan.common.R<T>`；成功用 `R.ok(data)` / `R.ok()`，失败抛 `BusinessException`（由全局处理器转 `R.fail`）。
- **REST 风格**：路径统一前缀 `/api/v1/...`；接口权限用 `@RequirePermission("模块:动作")`（如 `fee:write`、`parking:read`）。
- **多租户**：业务查询经 `TenantContext` 解析租户；平台用户（super_admin / platform_operator）无租户绑定（`tenantId == null`），可跨租户访问。
- **金额**：一律以**整数分**存储与传递，禁止浮点数。
- **多租户 Service 安全写法**：任何从 `TenantContext.getTenantId()` 获取 `tenantId` 并用于**与实体归属比较**的方法，必须兼容 `tenantId == null`（平台用户/超管无租户）。标准写法：
  ```java
  Long tenantId = TenantContext.getTenantId();
  // ❌ 错误：tenantId 为 null 时直接 NPE
  if (!tenantId.equals(entity.getTenantId())) { ... }
  // ✅ 正确：先判空，平台用户跳过租户校验
  if (tenantId != null && !tenantId.equals(entity.getTenantId())) { ... }
  ```
  对于**创建操作**，需为超管提供合理的 tenantId（如从请求体获取，或默认值兜底）。
- **Service 分层**：标准写法为**接口 + `impl/` 实现**（参照 `modules/device/service/` 下 DeviceManagementService 等 5 个接口）。旧写法（直接继承 MyBatis-Plus `ServiceImpl<Mapper, Entity>`）仅存在于待迁移的遗留模块。新 Service **必须**先定义接口再写实现。
- **前端 API**：封装在 `frontend/src/api/*.ts`，每个函数用 JSDoc 标注 HTTP 方法与后端路径。
- **Entity 约定**：① 同一张 DB 表只允许一个 Entity 类映射（禁止 system 和 modules 各写一个）；② 继承 `BaseEntity` 时必须覆盖 `@TableId(type = IdType.AUTO)`（DB 实际使用自增主键，BaseEntity 默认的 ASSIGN_ID 是错误的）；③ 字段类型必须与 DB 列类型一致（如 `status` 是 `VARCHAR` 就不能用 `Integer`）。

---

## 三、⭐ 每次改代码必须同步更新代码索引

代码索引位于 [`docs/code-index/`](docs/code-index/README.md)，记录每个模块下类/方法/接口的路径与概要。改动代码结构后，**必须在同一次改动中**更新对应模块索引文件，并刷新文件头「最近更新」日期：

| 改动类型 | 需同步更新的索引内容 |
|---|---|
| 新增 / 删除 / 重命名 REST 接口 | 该 Controller 的「接口清单」表 |
| 修改接口 路径 / 权限 / 入参 / 返回 | 对应接口行 |
| 新增 / 删除 Service public 方法 | 该 Service 的「关键方法」表 |
| 新增 / 删除 / 重命名 类 | 对应分节表 +（如影响模块职责）根 README 模块地图 |
| 新增业务模块（新建包） | 新建模块索引文件 + 在根 README 模块地图登记 |

> **索引未同步更新的代码改动视为未完成。**

### 查代码时先查索引

1. 先读 [`docs/code-index/README.md`](docs/code-index/README.md) 的模块地图，判断目标业务属于哪个模块；
2. 打开对应模块索引文件，用其中记录的路径直接定位类/方法/接口；
3. 再打开源码。**禁止在未查索引的情况下对全库做盲目 grep/遍历。**

### 索引颗粒度

- **Controller**：逐方法列出（方法名 / HTTP / 路径 / 权限 / 说明 / 入参 / 返回）。
- **Service**：public 方法逐一列出；private 辅助方法不列。
- **Entity / DTO / VO / Mapper**：类级一行功能描述 + 关键字段/关键查询，不逐字段展开。
- **工具类 / Config**：类级一行描述即可。
- 统一参照模板：[`docs/code-index/_TEMPLATE.md`](docs/code-index/_TEMPLATE.md)。

---

## 四、⭐ 每次改代码必须同步更新受影响的文档

改动代码时，**必须在同一次改动中**同步更新受影响的文档，并刷新文件头「最近更新」日期。文档与代码同寿命，禁止留到"以后处理"。

| 改动对象 | 必须同步更新的文档 |
|---|---|
| 功能交付 / 需求范围变更 / 功能状态变化 | [`docs/requirements/`](docs/requirements/README.md)（已实现需求 + 待实现需求） |
| 设备协议 / 通信主题 / 品牌适配差异 | `docs/协议/` 对应品牌文档 |
| 技术栈 / 模块划分 / 代码约定变更 | 本文件第一、二节 |

### 需求进度文档

位于 [`docs/requirements/`](docs/requirements/README.md)：

| 文档 | 内容 |
|---|---|
| `README.md` | 进度总览：阶段划分、功能域完成度速览、下一步重点 |
| `已实现需求.md` | 已实现功能清单（按功能域，含入口/表），标注占位🧪/半成品🚧/冻结⚠️ |
| `待实现需求.md` | 未实现需求清单（P0–P3）、Gap、明确不做清单、风险依赖 |

同步规则：

| 改动类型 | 需同步动作 |
|---|---|
| 完成一个待实现需求 | 在 `待实现需求.md` 标记完成/移除；在 `已实现需求.md` 对应功能域登记 |
| 占位/半成品功能转为正式实现 | 更新 `已实现需求.md` 该条状态标记 |
| 需求范围变更 | 更新 `待实现需求.md`，移入「明确不做」或调整优先级 |
| 发现代码已有但需求文档未登记的功能 | 补登 `已实现需求.md` |

需求文档是**功能级**视角（一个条目 = 一个可验收的用户功能），实现细节引用 `docs/code-index/` 路径，不写类/方法级细节。

### 查阅时顺手修正

查阅任何文档时发现与实际代码不符（路径失效、接口已删、状态过时、描述不符），立即修正并刷新日期，不得在答复中继续引用已知过期的内容。

> **文档未同步更新的代码改动视为未完成。**

---

## 五、Agent Skills 配置

### Issue tracker

Issues 以本地 markdown 形式存储在 `.scratch/<feature-slug>/` 下。详见 `docs/agents/issue-tracker.md`。

### Triage labels

使用默认五标签体系：`needs-triage` / `needs-info` / `ready-for-agent` / `ready-for-human` / `wontfix`。详见 `docs/agents/triage-labels.md`。

### Domain docs

单上下文：`CONTEXT.md` 在仓库根目录，ADR 在 `docs/adr/`。详见 `docs/agents/domain.md`。
