# 设计文档：小程序固定车位链路（任务包 5-2 固定车位部分）

> 需求依据：V1.1 3.3.6（MINI-006）、确认项 42/78
> 依赖：任务包 5-1（登录）、任务包 3-1/3-2（固定车位实体与审核流）
> 设计模式：完全对标 5-2 月卡部分，差异点单独标注

---

## 1. 概述

### 1.1 背景

- 小程序端无任何固定车位页面
- 后端运营端固定车位管理已完备（`FixedSpaceController`、`FixedSpaceAuditController`、`FixedSpaceService`）
- 与月卡的唯一核心差异：申请时需选择车位号（spaceNo），而非仅选车场+车牌

### 1.2 目标

对标月卡完整流程（申请 → 审核 → 支付 → 生效 → 续费），新增车位号选择环节。

---

## 2. 现有基础设施

| 组件 | 说明 |
|------|------|
| `FixedSpaceBinding` 实体 | status(int:1/2/3), reviewStatus(PENDING/APPROVED/REJECTED), source, applicantId, spaceNo, zoneId, vehicleId, validStart, validEnd, payMethod, paidAmountCents |
| `FixedSpaceService` | create/renew/cancel/approve/reject/toVO |
| `FixedSpaceController` | `/api/v1/fixed-spaces` — 运营端 CRUD |
| `FixedSpaceAuditController` | `/api/v1/admin/fixed-space-audit` — 审核 |
| `FixedSpaceBindingMapper` | MyBatis-Plus BaseMapper |
| `ParamKeys.FIXED_SPACE_REVIEW_MODE` | 审核模式参数 |
| `FixedSpaceVO`, `FixedSpaceCreateRequest` | 已有 DTO/VO |

---

## 3. 状态机（对标月卡）

```
申请 → PENDING → AUTO直接APPROVED / MANUAL等审核 → APPROVED → 用户支付 → ACTIVE(1)
                                                                          │
                                                              ┌─ 到期 → EXPIRED(2)
                                                              ├─ 续费 → ACTIVE(有效期顺延)
                                                              └─ 注销 → DISABLED(3)
```

---

## 4. 后端 API（对标月卡）

**新建**：`MiniFixedSpaceController`（`/api/v1/mini/fixed-spaces`）

| 方法 | 路径 | 对标月卡 |
|------|------|---------|
| `POST` | `/` | 申请（差异：需传 spaceNo / zoneId，从可用车位中选择） |
| `GET` | `/` | 我的固定车位列表 |
| `GET` | `/{id}` | 详情 |
| `POST` | `/{id}/pay` | 确认支付 → 状态变 ACTIVE |
| `POST` | `/{id}/renew` | 续费（传 months） |

### 差异点详解

**1. 可用车位查询**（新增接口）：
`GET /api/v1/mini/fixed-spaces/available?parkingLotId={lotId}`
- 查询 `fixed_space_binding` WHERE `parking_lot_id=lotId AND status=ACTIVE AND deleted_at IS NULL`
- 返回已占用的 `spaceNo` 列表
- 前端与车场 `parking_zone` + `parking_space_policy` 表交叉得出可用车位列表
- （简化：可直接读 `parking_zone` 表获取该车场所有车位号，减去已绑定生效的）

**2. 申请校验**：
- 除车牌校验（同月卡）外，额外校验 `spaceNo` 未被 ACTIVE 绑定抢占
- 同一车辆+同一车场已有 ACTIVE 固定车位 → 拒绝

**3. 支付**：`validStart=today`, `validEnd=today + 1month`（同月卡）

**4. 续费**：有效期从 `validEnd` 顺延

### 新增 Service

`MiniFixedSpaceService`（对称 `MiniMonthlyPassService`）：
- 注入 `FixedSpaceBindingMapper` + `FixedSpaceService` + `VehicleMapper` + `PlateBindingMapper` + `ParamResolver`
- 复用 `FixedSpaceService.toVO()` 做 VO 转换

---

## 5. 前端（对标月卡）

### 页面

| 页面 | 路径 | 与月卡差异 |
|------|------|-----------|
| 固定车位列表 | `pages/fixed-space/fixed-space` | 展示车位号 + 车牌，其余同月卡 |
| 申请 | `pages/fixed-space/apply` | 多一个"选择车位号"下拉，其余同月卡 |
| 续费 | `pages/fixed-space/renew` | 同月卡 |

### app.json

新增 3 页注册，个人中心增加"固定车位"入口（profile.js）。

---

## 6. 配置

新增 Flyway 种子：`fixed_space.price_per_month_cents`（默认 30000分/月）+ ParamKeys 常量。

---

## 7. 文件影响（对标月卡，仅替换类名）

| 后端 | 说明 |
|------|------|
| `MiniFixedSpaceController.java` | **新增** |
| `MiniFixedSpaceService.java` | **新增** |
| `MiniFixedSpaceApplyRequest.java` | **新增**（含 spaceNo/zoneId 字段） |
| `MiniFixedSpaceRenewRequest.java` | **新增**（同月卡，传 months） |
| `ParamKeys.java` | **改造**（新增常数+Definition） |
| Flyway migration | **新增**（定价种子） |

| 前端 | 说明 |
|------|------|
| `pages/fixed-space/fixed-space.*` | **新增 4 文件** |
| `pages/fixed-space/apply.*` | **新增 4 文件** |
| `pages/fixed-space/renew.*` | **新增 4 文件** |
| `app.json` | **改造** |
| `profile.js` | **改造**（新增入口） |
