# ADR 0001: 二期计费引擎从 BillingRule 迁移至 FeeRule

> **Status**: accepted  
> **Date**: 2026-07-27  
> **Deciders**: 产品 / 技术负责人  
> **Related**: `docs/requirements/二期实施计划.md`, `docs/requirements/待实现需求.md` P0-1

---

## 1. Context

当前系统存在两套计费体系：

- **旧 `BillingRule`**（`com.jushan.system.*` 及 `com.jushan.platform.modules.parking.service.BillingEngine`）
  - 已在线上识别链路、H5 查费/支付、岗亭收费等 10+ 处使用。
  - 支持免费、固定金额、按时累计、分时段、自然日封顶、版本切换、规则快照。
  - 缺点：不支持连续 24h 滚动封顶、区域定价、车型/车牌颜色过滤。

- **新 `FeeRule`**（`com.jushan.platform.modules.parking.*`）
  - 数据模型更灵活：支持 4 种计费模式、区域/车型/颜色维度、连续 24h 封顶、优先级匹配。
  - 配置入口 `/api/v1/fee-rules` 与运营端页面 `FeeRules.vue` 已启用。
  - 缺点：数据库缺 4 个关键列（`first_period_minutes`、`max_amount`、`cross_day_mode`、`effect_mode`），计算逻辑未接入车型/颜色/区域过滤，识别链路完全未调用。

二期需要扩展计费能力（连续 24h 封顶、区域/车型/颜色定价、规则快照），同时简化代码资产。

---

## 2. Decision

**二期全面迁移至 `FeeRule`，并下线旧 `BillingRule`。**

具体决策：

1. 以 `FeeCalculationService` 替代 `BillingEngine` 作为唯一计费入口。
2. 补齐 `fee_rule` 表缺失列，实现首时段、封顶、跨天模式、生效方式。
3. 实现 `FeeRule` 版本快照机制（附表 `fee_rule_history`），支持查看历史版本与回退。
4. 实现入场规则快照：车辆入场时把生效中的 `fee_rule` 快照写入 `parking_session`。
5. 支持 `effect_mode`：立即生效 / 定时生效 / 仅新入场生效。
6. 识别链路（`EntryService` / `ExitService` / `RecognitionEventServiceImpl` / `TempPlateService`）统一切到 `FeeCalculationService`。
7. H5 查费/支付后端（`H5FeeController` / `H5PayController`）同步切到 `FeeCalculationService`。
8. 生产环境支付走 **P云 OpenAPI 纯支付链路**（后端下单 → 返回 `pay_url` → 前端跳转 → P云 HTTP 回调），模拟支付保留为开发/演示 fallback。
9. 删除旧 `BillingRule` 相关表、代码、前端页面，不保留双轨。
10. 断网本地开闸 + 恢复补传纳入二期 P0 异常场景。

---

## 3. Consequences

### Positive

- 计费能力扩展：支持连续 24h 封顶、区域/车型/颜色定价、优先级匹配。
- 规则变更有迹可循：版本历史 + 快照避免"一改规则就乱账"。
- 代码资产简化：只保留一套计费引擎，降低维护成本。
- 支付体验自主：H5/小程序前端控制支付流程，不依赖 P云 PP 体系。

### Negative / Risks

- 迁移期间计费结果必须与旧引擎逐项对齐，否则影响收费。
- 删除旧 `BillingRule` 后无法快速回滚，需通过 feature flag 或数据库备份兜底。
- 断网本地开闸涉及岗亭本地服务/硬件方案，需单独评估落地成本。

---

## 4. Alternatives Considered

| 方案 | 结论 | 原因 |
|---|---|---|
| A. H5 支付走 P云 OpenAPI（后端下单 → 返回 pay_url） | **采纳** | 与现有 Mock 链路一致，前端控制权在己方，工作量最小。 |
| B. H5 支付走 P云 PP 体系（`/api/v1/pyun/billing`） | 排除 | 需重建无牌车/Passport/固定车查询等接口，工作量大且与已固化决策冲突。 |
| 双轨运行（FeeRule + BillingRule 并行） | 排除 | 增加维护负担，旧引擎无需保留。 |

---

## 5. Migration Strategy

详见 `docs/requirements/二期实施计划.md`。

阶段概览：

1. **阶段一**：DB 补齐 + Entity/DTO/VO/Mapper 同步。
2. **阶段二**：FeeRule 计算能力完善 + 版本快照机制。
3. **阶段三**：识别链路 + H5 后端切换；P云 OpenAPI 真实支付接入。
4. **阶段四**：旧 BillingRule 删除 + 回归测试。
5. **阶段五**：断网本地开闸 + 岗亭异常场景补齐。

---

## 6. Rollback

- 上线前保留数据库全量备份。
- 识别链路可通过临时开关回切（如 `billing.engine=fee-rule`，默认开启）。
- 若 FeeRule 计算出现严重偏差，优先回滚数据库并启用备份规则，而非恢复 BillingRule 代码。
