---
name: parking-payment-security-review
description: 审查 jushan-platform 停车支付的回调验签、商户身份、订单号、金额、重复回调、状态更新、退款、免费订单、负金额以及支付与开闸边界。支付链路审查、任务拆分或验收时触发；普通订单 CRUD 和直接实现时不触发。输入为支付规范、代码、迁移、日志与测试，输出为安全 Findings 和任务书，不修改代码。
---

# Parking Payment Security Review

1. 读取项目 `AGENTS.md` 和 `.codex/references/parking-p0-checklist.md`。
2. 追踪回调原文、验签、平台证书/密钥使用边界和服务端查询。
3. 校验 `appid`、`mchid`、`outTradeNo`、BigDecimal 金额和业务订单归属。
4. 检查重复/乱序/伪造回调、并发状态更新和精确字段条件更新。
5. 检查退款上限、重复退款、部分退款和异常补偿。
6. 区分合法免费订单与非法负金额，禁止浮点资金计算。
7. 检查支付成功与开闸解耦、开闸条件、失败补偿和审计。
8. 检查日志、异常和测试中是否泄露凭据或敏感信息。
9. Findings first；需要实施时输出 Claude Code 任务书。

严格只读，不持续实现，不 commit、push、部署或操作生产资金。
