# 模块：h5（H5 车主端接口）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/h5/`
> **所属**：`parking-system` · `com.jushan.platform.modules.h5`
> **职责**：H5 车主端免登录查费、支付预下单、状态查询、模拟支付确认。
> **最近更新**：2026-08-11（B1 已完成：查费/支付计费入口已从 `BillingEngine` 切换为 `FeeCalculationService`，优先读取 `ParkingSession.feeRuleSnapshot`）

**说明**：
- 本模块为 H5 车主端（`h5-user/`）提供后端接口，不依赖登录态。
- 查费/支付计费已切换至 `FeeCalculationService.calculateFeeCents`，优先使用 `ParkingSession.feeRuleSnapshot`。
- 模拟支付由 `h5.mock-pay-enabled` 开关控制；真实生产环境二期走 P云 OpenAPI。

---

## 一、接口入口（Controller）

### H5FeeController  `controller/H5FeeController.java`
- **基础路径**：`/api/v1/h5/fee` ｜ **权限**：无（免登录） ｜ **状态**：✅ 已启用
- **功能**：按车牌查询在场记录和费用。

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| queryByPlate | GET | `/query` | 按车牌查费 | `plate` | `R<List<H5FeeVO>>` |
| queryByPlatePost | POST | `/query` | 按车牌查费（POST，防中文乱码） | `H5FeeQueryRequest` | `R<List<H5FeeVO>>` |

### H5PayController  `controller/H5PayController.java`
- **基础路径**：`/api/v1/h5/pay` ｜ **权限**：无（免登录） ｜ **状态**：✅ 已启用
- **功能**：支付预下单、状态查询、模拟支付通知。

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| prepare | POST | `/prepare` | 预下单（锁定订单为 PAYING） | `H5PayPrepareRequest` | `R<H5PayResultVO>` |
| query | GET | `/query` | 查询支付状态 | `orderId` | `R<H5PayResultVO>` |
| notify | POST | `/notify` | 模拟支付通知 | `H5PayNotifyRequest` | `R<?>` |

---

## 二、领域对象

| 类型 | 类名 | 作用 |
|---|---|---|
| DTO | `H5FeeQueryRequest` | 查费请求（plate） |
| DTO | `H5PayPrepareRequest` | 预下单请求（orderId） |
| DTO | `H5PayNotifyRequest` | 模拟支付通知（orderId + action） |
| VO | `H5FeeVO` | 费用信息（parkName/plate/entryTime/duration/feeCents/feeYuan/payable/orderId） |
| VO | `H5PayResultVO` | 支付结果（orderNo/parkName/plate/payableAmount/status/mock） |

---

## 三、依赖与备注

- 查费：调用 `FeeCalculationService.calculateFeeCents`（已从 `BillingEngine.calculateFee` 切换，优先读取 `ParkingSession.feeRuleSnapshot`）。
- 订单：复用 `ParkingOrderService` 创建/更新 `ParkingOrder`。
- 模拟支付：调用 `MockPaymentService.preparePay` / `confirmPay`。
- 租户：接口内部以平台用户身份执行，绕过租户行级过滤。
