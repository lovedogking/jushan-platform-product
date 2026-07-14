---
name: self-review-before-finish
description: Use at the end of any Claude Code implementation task in the jushan-platform parking SaaS project before declaring completion. Performs a final self-check of the diff, scope, tests, security, and delivery report.
user-invocable: true
disable-model-invocation: false
---

# Self-Review Before Finish

## Overview

A mandatory final self-check before declaring a task complete. It reviews the working tree diff, confirms scope, checks for regressions, verifies test coverage, runs a lightweight security sweep, and produces the standard completion report.

## When to Use

- At the end of every implementation task, just before the final user handoff.
- After `build-verify-handoff` or other verification commands have been run.
- Before generating the task completion report.

Do **not** skip this skill for "trivial" changes that touch production code.

## Trigger Conditions

- You are about to say the task is done.
- You have already run the relevant build/test/type-check/lint commands.
- You need to produce the completion report defined in project `CLAUDE.md`.

## Self-Review Steps

### 1. Capture Current State

Run and record:

```bash
git status --short --branch
git diff --stat
git diff --check
```

### 2. Analyze Diff Scope

For every changed file, decide:

- [ ] Is it within the agreed task scope?
- [ ] Does it match the task brief or user request?
- [ ] Are there unrelated changes that should be reverted?
- [ ] Are user-owned uncommitted changes preserved or safely merged?

### 3. Check Impact on Existing Functions

- [ ] Search for callers of modified public methods/endpoints.
- [ ] Verify no public API contract was broken without explicit approval.
- [ ] Verify no existing tests were weakened, skipped, or deleted.
- [ ] Check that no existing business flow was unintentionally altered.

### 4. Check Test Coverage

- [ ] Unit/module tests exist for new Service/Mapper logic.
- [ ] Integration tests exist for new endpoints or critical flows.
- [ ] Cross-tenant, cross-parking-lot, no-permission cases exist for scoped changes.
- [ ] Idempotent/repeated-message cases exist for event/payment/device changes.
- [ ] Failure/timeout/exception paths exist.
- [ ] Test commands were run and results are recorded.

### 5. Check Security Risks

- [ ] Permission checks are present on new endpoints.
- [ ] Tenant/parking-lot scope is enforced.
- [ ] Sensitive data is not logged or returned.
- [ ] Parameter validation is present.
- [ ] Payment/device/file-upload paths follow project P0 rules.

### 6. Check Delivery Hygiene

- [ ] No passwords, keys, or secrets committed.
- [ ] No debug code, TODOs, or commented-out core logic left behind.
- [ ] Migration files use a new Flyway version and are not edited after creation.
- [ ] SQL files can run independently if required by the project.

## Output: Task Completion Report

Use this exact structure:

```markdown
## 完成内容
- 实现了 ...
- 修复了 ...

## 修改文件
- 新增：...
- 修改：...
- 删除：...

## 数据库变更
- 有 / 无
- 迁移文件：...

## 接口变更
- 有 / 无
- 新增/修改端点：...

## 测试和验证结果
- 编译：通过 / 失败
- 单元测试：通过 / 失败 / 未执行（原因）
- 集成测试：通过 / 失败 / 未执行（原因）
- 类型检查：通过 / 失败 / 不适用
- Lint：通过 / 失败 / 不适用

## 未完成内容
1. ...（原因）

## 已知风险
1. ...

## 待确认事项
1. ...

## 建议下一步
1. ...

## Git 状态
- 分支：...
- 未提交文件：...
- git diff --check：通过 / 发现空白问题
```

## Rules

- Be honest. If a check failed, say so and explain why.
- Do not declare "all tests pass" unless you actually ran them.
- Do not hide user-owned uncommitted changes.
- If a P0 security or isolation issue is found, do not finish the task; stop and report.
- This skill does not modify code; it only produces the completion report.
