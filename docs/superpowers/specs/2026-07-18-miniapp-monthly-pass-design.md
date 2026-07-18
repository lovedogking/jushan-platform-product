# 设计文档：小程序月卡链路（任务包 5-2 月卡部分）

> 需求依据：V1.1 3.3.5（MINI-005）、确认项 26/42/78
> 依赖：任务包 5-1（登录与手机号绑定）、任务包 3-1/3-2（月卡/固定车位实体与审核流）
> 注意：本 spec 仅覆盖月卡，固定车位单独后续 spec

---

## 1. 概述

### 1.1 背景

小程序端当前：
- "我的月卡"入口为 toast 占位（"我的月卡 — 后续版本开放"）
- 无任何月卡申请、列表、支付、续费页面
- 后端运营端月卡管理已完备（`MonthlyPassController`、`MonthlyPassAuditController`、`MonthlyPassService`），但无小程序端 API

### 1.2 目标

1. 后端新增小程序月卡 API（`/api/v1/mini/monthly-passes`）：申请/列表/详情/支付/续费
2. 前端新增 3 个页面：我的月卡（列表）、申请月卡、续费
3. 审核模式读车场参数（AUTO 自动通过 / MANUAL 人工审核）
4. 审核通过后用户手动确认支付（模拟支付）才生效
5. 续费按车场定价 + 可选时长（1/3/6/12月），从原到期日顺延

### 1.3 非目标

- 不涉及固定车位小程序链路（单独 spec）
- 不涉及优惠券/发票
- 不涉及消息推送（任务包 5-3）
- 不改变运营端现有月卡管理接口

---

## 2. 现有基础设施

以下后端已建成，本 spec 复用不改：

| 组件 | 路径/类 | 说明 |
|------|--------|------|
| **月卡实体** | `MonthlyPass` (`monthly_pass` 表) | parkingLotId, plateNumber, passStatus, reviewStatus, applicantId, source, amountCents, validStartDate, validEndDate |
| **月卡 Service** | `MonthlyPassService` | create(), renew(), cancel(), pageList(), detail(), toVO() |
| **月卡审核 Controller** | `MonthlyPassAuditController` | `/api/v1/admin/monthly-pass-audit` — pendingList, approve, reject |
| **审核模式参数** | `ParamKeys` | `MONTHLY_PASS_REVIEW_MODE` — 读自 `sys_config`，值为 AUTO/MANUAL |
| **模拟支付** | `MockPaymentService` | preparePay(), confirmPay() |
| **续费日志** | `VehicleRenewalLog` | 已有表，记录续费操作 |
| **参数解析** | `ParamResolver` | 读 sys_config + parking_lot 级 fallback |

---

## 3. 状态机

### 小程序月卡全生命周期

```
用户提交申请
  │
  ▼
review_status=PENDING, pass_status=PENDING
  │
  ├──[AUTO模式]──→ review_status=APPROVED
  │
  └──[MANUAL模式]──→ 等待运营端审核
                        │
                        ├── 审核通过 → review_status=APPROVED
                        └── 审核拒绝 → review_status=REJECTED, pass_status=CANCELLED（终止，不产生订单）

review_status=APPROVED, pass_status=PENDING（已通过，待支付）
  │
  └── 用户确认支付（模拟支付）
        │
        ▼
      pass_status=ACTIVE（生效中）
        │
        ├── 到期 → pass_status=EXPIRED
        ├── 续费 → pass_status 保持 ACTIVE, validEndDate 顺延
        └── 注销 → pass_status=CANCELLED
```

### 状态标签映射

| review_status | pass_status | 展示文本 | 可操作 |
|:---|:---|:---|:---|
| PENDING | PENDING | 审核中 | 无 |
| APPROVED | PENDING | 审核通过，待支付 | "去支付" |
| APPROVED | ACTIVE | 生效中 | "续费" |
| - | EXPIRED | 已过期 | 无 |
| - | CANCELLED | 已注销 | 无 |
| REJECTED | CANCELLED | 已驳回 | 无 |

---

## 4. 后端 API 设计

### 4.1 MiniMonthlyPassController

**新建**：`com.jushan.system.controller.MiniMonthlyPassController`
**路径前缀**：`/api/v1/mini/monthly-passes`
**权限**：所有方法 `@RequirePermission("miniapp:view")`（5-1 已设置 JWT audience=miniapp）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/` | 提交月卡申请 |
| `GET` | `/` | 我的月卡列表（全部状态，按 created_at 倒序） |
| `GET` | `/{id}` | 月卡详情 |
| `POST` | `/{id}/pay` | 确认支付（审核通过后） |
| `POST` | `/{id}/renew` | 续费（需传 months 参数） |

### 4.2 申请 `POST /`

**请求体**：
```json
{
  "parkingLotId": 1,
  "plateNumber": "京A12345",
  "ownerName": "张三",
  "ownerPhone": "13800138000"
}
```

**后端逻辑**：
1. 获取当前用户 ID（`TenantContext.requireUserId()`）
2. 校验 `plateNumber` 属于当前用户的已绑定车牌（查 `plate_binding` 表，`wxUserId`+`verifyStatus=APPROVED`）
3. 同一 `plateNumber` + 同一 `parkingLotId` 已有 ACTIVE 月卡 → 拒绝（409）
4. 创建 `MonthlyPass`：
   - `source = MINIAPP`
   - `applicantId = 当前用户ID`
   - `review_status = PENDING`
   - `pass_status = PENDING`
   - `plateNumber, parkingLotId, ownerName, ownerPhone` 从请求体
   - `amountCents = 0`（支付时再定）
5. 读取审核模式参数：
   - AUTO → `review_status = APPROVED`（直接可支付）
   - MANUAL → 保持 `review_status = PENDING`（等待运营端审核）
6. 返回 `MonthlyPassVO`

### 4.3 列表 `GET /`

**请求参数**：无（仅返回当前用户的月卡）

**后端逻辑**：
1. `applicantId = 当前用户ID`
2. 查询 `monthly_pass` WHERE `applicant_id = {userId}` AND `source = 'MINIAPP'` AND `deleted_at IS NULL`
3. 按 `created_at DESC` 排序
4. 返回 `List<MonthlyPassVO>`

### 4.4 支付 `POST /{id}/pay`

**后端逻辑**：
1. 查 `monthly_pass`，校验 `applicantId == 当前用户`，否则 403
2. 校验 `review_status == APPROVED`，否则 400
3. 校验 `pass_status != ACTIVE`（防止重复支付）
4. 如 `amountCents <= 0`，从车场参数 `monthly_pass.price_per_month_cents` 读取金额
5. 调用 `MockPaymentService.confirmPay()` 完成模拟支付
6. 更新 `pass_status = ACTIVE`，`paid_amount_cents = 实际支付金额`，`pay_method = SIMULATED_PAY`
7. 返回 `MonthlyPassVO`

### 4.5 续费 `POST /{id}/renew`

**请求体**：
```json
{ "months": 3 }
```

**后端逻辑**：
1. 查 `monthly_pass`，校验 `pass_status == ACTIVE` 且 `source == MINIAPP`
2. 读车场参数 `monthly_pass.price_per_month_cents`，计算 `amountCents = price * months`
3. 有效期顺延：`validEndDate = currentValidEndDate.plusMonths(months)`
4. 生成续费订单 + 模拟支付
5. 记录 `VehicleRenewalLog`（复用 MonthlyPassService 现有逻辑）
6. 返回 `MonthlyPassVO`

---

## 5. 前端设计

### 5.1 页面路由

需在 `app.json` 注册 3 个新页面：
```json
"pages/monthly-pass/monthly-pass",
"pages/monthly-pass/apply",
"pages/monthly-pass/renew"
```

### 5.2 我的月卡（列表页）`pages/monthly-pass/monthly-pass`

**文件**：monthly-pass.js, .json, .wxml, .wxss（4 文件）

**功能**：
- `onLoad` → `GET /api/v1/mini/monthly-passes` 获取列表
- 分组展示：生效中 / 待支付 / 其他（审核中、已过期等）
- 生效中的月卡显示有效期和"续费"按钮
- APPROVED + PENDING 状态的显示"去支付"按钮
- 审核中/已驳回/已过期 仅展示，无操作按钮
- 空状态："暂无月卡，去申请"
- 顶部"申请月卡"按钮 → `/pages/monthly-pass/apply`
- 下拉刷新

**入口**：`profile.js` 的 `goToMonthCards()` 替换 toast 为 `wx.navigateTo`

### 5.3 申请月卡 `pages/monthly-pass/apply`

**文件**：apply.js, .json, .wxml, .wxss（4 文件）

**功能**：
- 选择车场：调用 `GET /api/v1/mini/parking-lots` 获取可选车场列表
- 选择车辆：展示当前用户已绑定车牌列表（从 app.globalData 或调接口），默认选中默认车牌
- 填写车主信息：ownerName + ownerPhone
- 展示月卡价格（从车场参数读取）
- 提交按钮 → `POST /api/v1/mini/monthly-passes` → 成功跳转列表页
- 前置 phoneBound 检查（复用 5-1 拦截逻辑）

### 5.4 续费 `pages/monthly-pass/renew?id={passId}`

**文件**：renew.js, .json, .wxml, .wxss（4 文件）

**功能**：
- `onLoad` → `GET /api/v1/mini/monthly-passes/{id}` 获取月卡详情
- 展示：车牌、车场、当前有效期
- 时长选择：1/3/6/12 个月，展示对应金额
- 续费后有效期预览（从原到期日顺延）
- 确认续费 → `POST /api/v1/mini/monthly-passes/{id}/renew` → 模拟支付确认 → 成功跳转列表页

---

## 6. 配置依赖

### 6.1 需要的 SystemParam

| 参数 Key | 说明 | 默认值 | 用途 |
|---------|------|--------|------|
| `monthly_pass.review_mode` | 月卡审核模式 | `AUTO` | 决定新申请自动通过还是人工审核 |
| `monthly_pass.price_per_month_cents` | 月卡单价（分/月） | `30000` | 计算续费金额和支付金额 |

> **注意**：`monthly_pass.review_mode` 已在 ParamKeys 定义（`MONTHLY_PASS_REVIEW_MODE`），`price_per_month_cents` 需新增参数种子。

### 6.2 Flyway 迁移

在 `sys_config` 表中注入月卡定价参数种子（如不存在）：
```sql
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, created_at, updated_at)
VALUES ('monthly_pass.price_per_month_cents', '30000', '月卡单价（分/月）', '计费设置', 'INT', NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);
```

---

## 7. 安全与约束

| 约束 | 措施 |
|------|------|
| 仅限本人操作 | 所有接口校验 `applicantId == 当前用户ID`（通过 TenantContext） |
| 车牌归属 | 申请时校验车牌属于当前用户的 plate_binding |
| 重复申请 | 同一车牌+同一车场已有 ACTIVE 月卡时拒绝 |
| 重复支付 | 支付接口校验 pass_status != ACTIVE |
| 续费限制 | 仅 ACTIVE 状态可续费 |
| 手机号绑定 | 5-1 拦截逻辑覆盖本包所有页面入口 |

---

## 8. 文件影响清单

### 后端

| 文件 | 操作 | 说明 |
|------|------|------|
| `MiniMonthlyPassController.java` | **新增** | `/api/v1/mini/monthly-passes` 控制器 |
| `MiniMonthlyPassService.java` | **新增** | 小程序月卡业务服务（申请/支付/列表/续费） |
| `MiniMonthlyPassApplyRequest.java` | **新增** | 申请请求 DTO |
| `MiniMonthlyPassRenewRequest.java` | **新增** | 续费请求 DTO |
| `MonthlyPassVO.java` | **可能改造** | 如需要额外字段（如审核模式信息） |
| Flyway migration | **新增** | `monthly_pass.price_per_month_cents` 种子 |

### 前端

| 文件 | 操作 | 说明 |
|------|------|------|
| `pages/monthly-pass/monthly-pass.*` | **新增 4 文件** | 月卡列表页 |
| `pages/monthly-pass/apply.*` | **新增 4 文件** | 申请页 |
| `pages/monthly-pass/renew.*` | **新增 4 文件** | 续费页 |
| `app.json` | **改造** | 注册 3 新页面 |
| `pages/profile/profile.js` | **改造** | "我的月卡"入口指向新页面 |

---

## 9. 测试要点

| 场景 | 验证点 |
|------|--------|
| AUTO 模式申请 | 提交 → 直接 APPROVED → 用户支付 → ACTIVE → 出场自动放行 |
| MANUAL 模式申请 | 提交 → PENDING → 运营端审核通过 → 用户支付 → ACTIVE |
| 审核拒绝 | PENDING → 运营端驳回 → REJECTED+CANCELLED → 不产生订单 |
| 续费 | ACTIVE 月卡 → 选 3 个月 → 有效期顺延 3 个月 → 金额正确 |
| 重复申请拦截 | 已有 ACTIVE 月卡的车牌+车场 → 再次申请被拒 |
| 手机号拦截 | 未绑定手机号 → 点击"我的月卡" → 弹窗引导绑定 |
| 车牌归属 | 选非本人车牌 → 拒绝 |
| 支付幂等 | 已支付月卡再次调用 pay → 拒绝 |

---

## 10. 验收标准

1. AUTO 模式全流程：申请 → 自动通过 → 支付 → 生效，车辆识别自动放行
2. MANUAL 模式全流程：申请 → 运营端审核 → 支付 → 生效
3. 审核拒绝不产生订单
4. 续费有效期正确顺延（从原截止日 + N 个月）
5. "我的月卡"入口不再显示 toast
