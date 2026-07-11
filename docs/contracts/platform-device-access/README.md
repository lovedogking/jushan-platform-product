# Platform ↔ Device Access 共享契约草案

> 文档状态：**DRAFT FOR JOINT REVIEW**
> 契约版本：v1.0-draft（待双方人工评审）
> 当前兼容版本：Device Access v0.2
> 生成日期：2026-07-11
> 适用系统：停车业务平台（jushan-platform，develop 分支）、设备接入服务（jushan-device-access，device 分支）

---

## 1. 文档用途

本目录是停车业务平台（Platform）与设备接入服务（Device Access）之间**跨系统共享契约**的单一权威草案。

它在双方尚未全部开发完成之前，统一以下内容：

- 职责边界与数据所有权；
- 设备、车道、停车场、事件、命令等核心标识；
- 当前真实实现（CURRENT-IMPLEMENTED）与目标设计（TARGET-DRAFT）的分栏描述；
- Platform ↔ Device Access 的目标接口规范、事件信封、命令模型；
- 双方分阶段开发路线和验收闸门；
- 已知冲突清单与人工决策清单。

本目录**只包含跨系统共享契约**。两个系统各自内部专用文档（Platform 内部业务接口、Device Access 厂商内部实现）仍由各自分支独立维护，不复制到本目录。

## 2. 当前状态

- 本草案由跨系统架构梳理生成，**未经双方负责人签署**。
- **所有推荐方案均为 PROPOSED，不是正式决策。** 推荐决策不等于双方已同意的正式决策。
- 所有目标设计（TARGET-DRAFT）标记为 `PROPOSED`，不得当作已实现。
- 所有需要真机确认的能力标记为 `NEEDS_DEVICE_VERIFICATION`。
- 所有需要两位负责人共同决定的事项标记为 `NEEDS_HUMAN_DECISION`，可给出推荐方案，但状态不得写为 `ACCEPTED`。
- 任何文档不得被标记为"已正式冻结"或"双方已签署"。
- **在 8 项阻塞决策（见 `08-联合评审决策表.md`）完成前，禁止按目标契约正式编码。**
- `08-联合评审决策表.md` 是本目录的**双方评审入口**——所有 NEEDS_HUMAN_DECISION 的最终结论在此记录。

### 评审状态摘要（截至 2026-07-11）

| 事项 | 状态 |
|------|------|
| Platform / 项目负责人 B01～B08 评审 | ✅ 已同意全部推荐方案 |
| Device Access 负责人 B01～B08 评审 | ⬜ 待确认 |
| V01～V04 真机验证 | ⬜ 待完成（阻塞第一阶段编码） |
| 契约整体状态 | **DRAFT FOR JOINT REVIEW** |
| 评审入口 | `08-联合评审决策表.md` |

## 3. 阅读顺序

| 顺序 | 文件 | 内容 |
|------|------|------|
| 1 | `README.md`（本文件） | 用途、状态、阅读顺序、权威级别、评审与变更流程 |
| 2 | `01-文档治理.md` | 旧文档清单、分类、权威状态、冲突处理规则 |
| 3 | `02-职责边界与数据所有权.md` | 职责边界、数据所有权、状态所有权、架构图 |
| 4 | `03-能力矩阵.md` | Platform 与 Device Access 功能矩阵、当前/下一阶段/远期 |
| 5 | `04-当前兼容契约-v0.2.md` | 当前真实实现事实（AS-IS），仅描述当前可调用能力 |
| 6 | `05-目标契约-v1.0-草案.md` | 目标契约（TO-BE），包含命令、事件、状态、配置、幂等等 |
| 7 | `06-集成开发路线.md` | 分阶段开发路线、联调闸门、真机测试 |
| 8 | `07-冲突与决策记录.md` | 已知冲突、AS-IS/TO-BE、决策理由、人工决策清单 |
| 9 | `08-联合评审决策表.md` | **双方评审入口**：阻塞/非阻塞决策分类、推荐方案总览、签署表、冻结条件 |
| 10 | `openapi/device-access-v1-draft.yaml` | 目标 HTTP 接口（机器可读） |
| 11 | `asyncapi/device-events-v1-draft.yaml` | 目标事件通道（机器可读） |
| 12 | `schemas/*.schema.json` | 事件与命令 JSON Schema（机器可读） |

## 4. 文档权威级别

发生冲突时按以下顺序处理（详见 `01-文档治理.md`）：

1. 用户本次明确要求和已确认的项目业务目标；
2. 平台需求规格说明书中的业务边界；
3. 本次生成并经双方评审后的共享契约（本目录）；
4. 当前代码和真机行为，用于定义 AS-IS；
5. Device Access 当前 API 文档；
6. 官方厂商协议；
7. 旧路线图、历史计划和人工提取文档。

> 注意："真机行为优先"只适用于描述 CURRENT-IMPLEMENTED。它不能自动覆盖 TARGET-DRAFT，也不能把当前技术债固化为目标设计。

## 5. 文档治理分类

本目录使用以下五类标签（定义见 `01-文档治理.md`）：

| 分类 | 含义 |
|------|------|
| `BUSINESS-REQUIREMENT` | 平台业务目标和产品范围 |
| `CURRENT-IMPLEMENTED` | 已由代码、测试或真机验证的当前事实 |
| `CURRENT-COMPATIBILITY` | 为兼容当前 Device Access v0.2 保留的临时规则 |
| `TARGET-DRAFT` | 下一阶段双方准备共同实现的目标契约 |
| `VENDOR-REFERENCE` | 厂商 MQTT、HTTP、SDK 协议资料 |

历史文档可标记为 `DEPRECATED / HISTORICAL`，但本轮不删除历史文档。

## 6. 当前兼容版本与目标版本

| 维度 | 当前（CURRENT-COMPATIBILITY） | 目标（TARGET-DRAFT） |
|------|-------------------------------|----------------------|
| Device Access 版本 | v0.2 | v1.0 |
| 接口风格 | 每能力独立 REST 路径（校时/状态/设备 CRUD） | 统一命令入口 + 查询 + 配置同步 |
| 响应成功码 | Device Access `code=200`；平台前端 `code=0` | 独立服务间契约（HTTP 状态 + 业务 errorCode） |
| 命令幂等 | 无 commandId | commandId 幂等 + 命令查询 |
| 事件通道 | 无（车牌事件仅日志，未上报） | RabbitMQ 主通道（推荐），HTTP 回调降级 |
| 设备标识 | deviceId == 厂商 SN | platformDeviceId / deviceCode / deviceSn 分离 |
| 服务认证 | 无 | HMAC-SHA256（推荐），待双方确认 |
| 开闸 | NOT_IMPLEMENTED_IN_V0.2（v0.2 有意排除，非从已实现代码删除） | 统一命令 OPEN_GATE（待真机确认 C5H 开闸机制） |
| 多租户/多停车场 | 无 | Platform 数据源，Device Access 接收 tenantId/parkingLotId |
| 图片 | 无 | 对象存储 objectKey，临时凭证上传 |

## 7. 双方负责人（待填写）

| 角色 | 姓名 | 邮箱 | 确认状态 |
|------|------|------|----------|
| 停车业务平台负责人 | _待填写_ | _待填写_ | 未确认 |
| Device Access 负责人 | _待填写_ | _待填写_ | 未确认 |
| 项目负责人 | _待填写_ | _待填写_ | 未确认 |

## 8. 评审流程

1. 两位负责人按"阅读顺序"逐份评审本目录；
2. 对 `07-冲突与决策记录.md` 中每一项 `NEEDS_HUMAN_DECISION` 给出明确结论；
3. 对 `NEEDS_DEVICE_VERIFICATION` 项安排真机联调并补齐真机报告；
4. 评审意见写入 `07-冲突与决策记录.md` 对应条目的"评审结论"字段；
5. 全部冲突闭环后，双方在"签署表"（见 `05-目标契约-v1.0-草案.md` 附录）签字，本目录状态由 `DRAFT FOR JOINT REVIEW` 升级为 `FROZEN v1.0`；
6. 冻结后，机器可读文件（openapi / asyncapi / schemas）与本 Markdown 必须同时更新并经双方评审。

## 9. 变更流程

冻结前的变更：

- 任何一方可直接修改本目录草案，但必须在 `07-冲突与决策记录.md` 记录变更理由；
- 修改后须通知对方评审。

冻结后的变更（`FROZEN v1.0` 之后）：

- 可新增可选字段；
- 不得直接重命名已有字段；
- 不得改变已有字段语义；
- 不得删除已有 Routing Key / 路径；
- 破坏性变更必须新建 v2，并提供新旧过渡期；
- 任何变更必须同步更新 Markdown 与机器可读文件，并经双方评审。

## 10. 同步规则

本目录为跨系统共享契约。两个 Worktree 中的内容必须**完全一致**：

```
jushan-platform/docs/contracts/platform-device-access/
jushan-device-access/docs/contracts/platform-device-access/
```

修改任一侧后，必须将同一份内容复制到另一侧，并执行 `diff -ru` 与 `shasum` 验证一致。

- 不复制两个项目各自内部专用文档；
- 只同步跨系统共享契约；
- Device Access 厂商内部实现文档仍由 device 分支独立维护；
- Platform 内部业务接口文档仍由 develop 分支独立维护。
