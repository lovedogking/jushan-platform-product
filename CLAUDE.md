# CLAUDE.md — 停车SaaS系统

@AGENTS.md

## 包架构约束（任务包 0-1 更新）
- **com.jushan.system** 本期为主战场，允许受控新增（标注 V1.1 功能编号），二期统一迁移。
- **com.jushan.platform.modules.parking**（fee_rule 体系）本期冻结，禁止接入计费链路；只允许读代码参考，不允许修改。
- 本期唯一计费引擎：com.jushan.system.service.BillingEngine（旧 billing_rule）。

## Claude 特有指令

### 开发模式
- 使用 Plan 模式处理复杂任务（>3 个文件修改）
- 每次修改后运行 `mvn clean compile` 验证
- 优先复用现有代码，禁止大规模重写
- 禁止修改已稳定的入场/出场/计费核心流程，除非修复 bug

### 代码审查
- 生成代码后自检：是否含 `tenant_id`？是否用 `BigDecimal`？是否加 `@RequirePermission`？
- 新增 API 必须同步更新 Swagger/OpenAPI 注解
- 新增表必须同步创建 Flyway 迁移

### 提交规范
- Commit message 格式：`[功能编号] 类型: 描述`，例如 `[A1] feat: monthly pass management`
- 每个功能点独立 commit
- 提交前运行 `mvn test`，确保无新增失败

### 与人类协作
- 遇到需求歧义必须暂停并提问，禁止自行假设
- 架构变更（通信协议/核心表结构）必须提前报备
- 禁止开发"本期不做"清单功能：优惠券、访客预约、商家优惠、真实支付、电子发票、寻车导航、短信推送

### 上下文参考
- 需求文档：`docs/requirements/停车SaaS系统需求规格说明书_v1.0.md`
- 部署文档：`docs/deployment/部署检查清单-P0-3.md`
- 二期规划：`docs/roadmap/二期需求优先级-P0-P3.md`
