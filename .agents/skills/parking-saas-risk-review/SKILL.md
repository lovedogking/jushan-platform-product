---
name: parking-saas-risk-review
description: 审查 jushan-platform 停车 SaaS 的多租户、停车场范围、停车记录状态机、计费、月卡、订单、支付、开闸和审计风险。停车业务变更审查、任务拆分或验收时触发；普通持续实现和非停车项目不触发。输入为需求、diff、代码与测试，输出为 P0-P3 Findings 和 Claude Code 任务书，不修改代码。
---

# Parking SaaS Risk Review

1. 先读项目 `AGENTS.md` 和 `.codex/references/parking-p0-checklist.md`。
2. 追踪租户、停车场、车辆、停车记录、订单和设备的可信上下文来源。
3. 检查普通管理员与超级管理员边界、异步处理和导出统计的数据隔离。
4. 检查停车记录状态机、重复入出场、并发、计费规则和月卡范围。
5. 检查 BigDecimal/DECIMAL、订单、支付回调、免费/负金额和退款。
6. 检查开闸条件、设备方向、UNCERTAIN、补偿和审计。
7. 核对测试是否覆盖跨租户、跨停车场、重复事件、并发和异常路径。
8. Findings first；需要实施时输出 Claude Code 任务书。

严格只读，不持续实现，不 commit、push 或部署。

