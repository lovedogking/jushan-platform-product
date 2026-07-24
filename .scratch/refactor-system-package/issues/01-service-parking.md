# 01 — Parking 域 Service 迁移

**Type:** task
**Status:** resolved
**Blocked by:** none

## 范围

将以下 16 个 Service 从 `com.jushan.system.service` 迁至 `com.jushan.platform.modules.parking.service`：

- `BillingEngine`
- `BillingRuleConfig`
- `BillingRuleService`
- `ParkingFeeService`
- `ParkingLaneService`
- `ParkingLotService`
- `ParkingOrderService`
- `OrderStatusLogService`
- `ReadinessCheckService`
- `PrepaidDeductionService`
- `TimeSegmentConfig`
- `MockPaymentService`
- `MockRecognitionService`
- `ParkingLotScopeResolver`
- `PyunParkingSyncService`
- `PyunPaymentClient`

## 关键风险

- 这些 Service 之间相互引用（同包），需同步搬迁
- 被 Booth/Miniapp 等其他域引用，搬迁后其他域需补 `import` 语句
- `BillingRuleService` 和 `WxUserService` 已消除通配符 import

## 步骤

1. 在 `modules/parking/service/` 复制所有 16 个文件
2. 将 package 改为 `com.jushan.platform.modules.parking.service`
3. 全局替换 `import com.jushan.system.service.{cls}` → 新路径
4. 编译修复
5. 删除 `system/service/` 原文件
6. 运行测试

## Comments
