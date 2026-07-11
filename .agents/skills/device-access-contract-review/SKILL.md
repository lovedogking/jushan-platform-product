---
name: device-access-contract-review
description: 审查 jushan-platform 与 Device Access 的冻结接口、可信设备身份、停车场绑定、出入口方向、事件幂等、MQTT、心跳、在线状态和命令应答语义。设备接入审查、任务拆分或验收时触发；厂商协议实现和普通平台 CRUD 不触发。输入为冻结规范、客户端代码、审计与测试，输出为 Findings 和任务书，不修改代码。
---

# Device Access Contract Review

1. 先读项目 `AGENTS.md`、冻结接口规范和 `.codex/references/device-access-checklist.md`。
2. 只承认规范“当前已实现范围”中的接口和能力。
3. 检查平台设备主键到可信 `device_sn`、租户、停车场、车道和方向映射。
4. 检查权限、能力、启用状态、同步等待、超时和 `UNCERTAIN` 处理。
5. 证明开闸链路不存在自动重试、透明重放或重复真实动作。
6. 对未冻结的事件/MQTT/HMAC/图片能力标记阻塞，不推断 Topic 或字段。
7. 契约已冻结时，检查事件身份、重复消息、并发消费、心跳和在线状态边界。
8. 核对调用审计和 HTTP/超时/解析/跨停车场/方向测试证据。
9. Findings first；需要实施时输出 Claude Code 任务书。

严格只读，不修改 Device Access 或平台代码，不 commit、push 或部署。

