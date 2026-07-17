# 停车SaaS 差距分析与任务拆解 — 执行计划

## 目标
以 `docs/Kimi_Agent_停车SaaS架构评审/停车SaaS系统需求规格说明书_v1.1.md` 为权威文档，
对当前 develop 分支代码做差距分析，并拆成可直接投喂给 Qoder/Claude Code 的任务提示词包。

## 阶段一：并行侦察（子代理 + 主代理并行）
- E1（explore 子代理·后台）：后端代码盘点 —— parking-system / parking-boot / parking-framework /
  parking-infrastructure / parking-common，按业务域列出 Controller/Service/已实现功能/Stub。
- E2（explore 子代理·后台）：前端盘点 —— admin-web（运营端）、booth-web（岗亭端）、miniapp（小程序端）
  的页面/路由/功能实现情况。
- E3（explore 子代理·前台）：读取仓库内上一版《差距分析报告_V1.1对齐》与《开发任务包提示词》，
  总结其结论与任务拆分（用于比对是否过时）。
- 主代理：通读 v1.1 需求文档（1658 行），抽取权威需求清单（按模块编号）。

## 阶段二：差距分析（主代理综合）
- 需求清单 × 代码盘点 → 逐项标记：✅已实现 / 🟡部分实现 / ❌未实现 / ⚠️与需求冲突。
- 与上一版差距报告比对，标注本轮新增/已闭环项。
- reviewer 子代理交叉验证关键差距项，防止漏判误判。

## 阶段三：任务拆解与提示词包产出（主代理撰写）
- 按依赖关系分 Phase（阶段）拆任务包，每个任务包含：目标、涉及文件、验收标准、约束。
- 提示词风格对齐用户历史偏好（vibecoding、直接可复制进 Qoder/Claude Code、含验证步骤）。
- 产出文件：
  1. `/mnt/agents/output/差距分析报告_v1.1对齐.md`
  2. `/mnt/agents/output/开发任务提示词包_v1.1/Phase*.md`（按阶段拆分）

## 验证门槛
- 阶段二输出必须覆盖 v1.1 全部章节，无遗漏方可进入阶段三。
