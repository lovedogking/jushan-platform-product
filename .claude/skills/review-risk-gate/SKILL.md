---
name: review-risk-gate
description: Use when a task involves nontrivial code changes across layers, APIs, databases, security boundaries, or shared modules in the jushan-platform parking SaaS project. Replaces daily Codex review by running a structured read-only risk gate before continuing.
user-invocable: true
disable-model-invocation: false
---

# Review Risk Gate

## Overview

A structured, read-only risk review that runs before a change is considered ready to merge or hand off. It checks architecture, API compatibility, database impact, and security for the jushan-platform parking SaaS codebase. No code is modified.

## When to Use

- Before finishing a medium-or-larger feature.
- Before touching shared modules, public APIs, payment, device access, tenant isolation, or file upload.
- After implementation but before `build-verify-handoff`.
- When you suspect a change might cross module boundaries or affect existing callers.
- When the task touches SQL, Flyway migrations, indexes, or constraints.

Do **not** use for trivial one-line fixes with no cross-module impact.

## Trigger Conditions

- Modified files include Controller, Service, Mapper/Entity, DTO/VO, migration, or frontend API call.
- New public endpoint or changed request/response shape.
- New database table, column, index, or constraint.
- Changes to `@SaCheckPermission`, tenant context, or data-scope helpers.
- Changes to payment, order, refund, device, gate-open, or file-upload flows.

## Review Steps

### 1. Read Context

- Current task scope and any task brief.
- `AGENTS.md`, project `CLAUDE.md`, and relevant `.codex/references/*.md`.
- The full `git diff` of the change.
- Related controllers, services, mappers, entities, DTOs, VOs, tests, and frontend callers.

### 2. Architecture Review

Check:

- [ ] Module boundaries are respected; no cross-module direct Mapper/Entity access.
- [ ] No new circular dependencies between modules.
- [ ] No duplicate implementation of an existing utility, service, or pattern.
- [ ] New abstractions are justified and not introduced "for future use".
- [ ] Complex business logic lives in Service, not Controller.

### 3. API Review

Check:

- [ ] Request parameters are validated (`@Valid`, `@NotBlank`, `@NotNull`, etc.).
- [ ] Response structure follows the project `Result<T>` / `R<T>` convention.
- [ ] Existing callers are not broken by field renames, type changes, or removed fields.
- [ ] New error codes are unique and documented.
- [ ] Path variables, query parameters, and request bodies match frontend expectations.

### 4. Database Review

Check:

- [ ] Migrations use a new Flyway version; no published migration is edited.
- [ ] SQL does not use `SELECT *` in performance-sensitive paths.
- [ ] New queries include tenant/parking-lot scope where required.
- [ ] Indexes and unique constraints support tenant isolation and query patterns.
- [ ] Updates to money/status use precise field updates, not full `updateById`.
- [ ] Rollback plan is documented for risky or irreversible migrations.

### 5. Security Review

Check:

- [ ] No fail-open auth (missing permission check, default allow, or bypass).
- [ ] Tenant and parking-lot scope is enforced on every read/write path.
- [ ] Frontend-supplied `tenantId`, `parkingLotId`, or device SN is not trusted directly.
- [ ] Sensitive data (passwords, tokens, keys, certificates, phone/id) is not logged or returned.
- [ ] File upload has type/size validation and does not expose arbitrary server paths.
- [ ] Payment paths validate amount, sign, appid/mchid/outTradeNo, and are idempotent.
- [ ] Device/gate-open paths validate device binding, direction, and record state before acting.

### 6. Test & Verification Review

Check:

- [ ] Normal path is covered.
- [ ] Cross-tenant and cross-parking-lot rejection cases exist.
- [ ] Idempotent/repeated-message cases exist.
- [ ] Concurrent/state-race cases exist.
- [ ] Failure/timeout/exception paths exist.
- [ ] Related tests actually run and pass (not skipped or commented).

## Output Format

Produce findings in this exact structure:

```markdown
## Review Risk Gate: <task summary>

### Overall Verdict
PASS / CONDITIONAL PASS / FAIL

### Architecture Findings
| Severity | File | Line | Finding | Evidence | Suggested Fix |
|----------|------|------|---------|----------|---------------|
| P0/P1/P2/P3 | `path/to/File.java` | 42 | ... | ... | ... |

### API Findings
...

### Database Findings
...

### Security Findings
...

### Test & Verification Findings
...

### Required Actions Before Handoff
1. ...
2. ...

### Optional Improvements
1. ...
```

## Severity Definitions

- **P0**: Must fix before handoff. Security bypass, tenant escape, data loss, payment risk, uncontrolled gate action, or broken public API.
- **P1**: Should fix before handoff. Missing validation, missing test, unclear error handling, performance risk.
- **P2**: Can be deferred with owner approval. Style, minor duplication, optional refactor.
- **P3**: Informational. Suggestions for future improvement.

## Rules

- This skill is **read-only**. Do not modify files, commit, push, or run destructive commands.
- Evidence must include file path and line number when possible.
- If a finding requires architecture or product decision, mark it as "needs user/Codex decision" and stop expanding.
- When the gate fails, list the minimal required actions; do not rewrite the entire change.
