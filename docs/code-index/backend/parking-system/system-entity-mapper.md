# 索引：com.jushan.system Entity + Mapper（101个）

> **包路径**：`parking-system/src/main/java/com/jushan/system/entity/` `mapper/`
> **所属**：`parking-system` · `com.jushan.system`
> **最近更新**：2026-07-21

**说明**：50 Entity + 51 Mapper。由于 Mapper 多为 MyBatis-Plus BaseMapper 无自定义方法,此处只列出 Entity 及职责。

---

## Entity（50个）

| 类名 | 职责 |
|---|---|
| `BaseEntity` | 公共基类（id/tenantId/createdAt/updatedAt） |
| `ArchiveData` / `ArchiveJobLog` | 数据归档 |
| `BillingRule` / `BillingRuleVersion` / `BillingRuleSwitchLog` / `BillingRuleRecalcLog` | 收费规则（存量） |
| `BindingPolicy` | 车辆绑定策略 |
| `Company` | 公司/集团档案 |
| `Device` / `DeviceCommandAudit` / `DeviceModel` / `DeviceVendor` / `DeviceStatusSnapshot` | 设备台账（T20/T24）+ 命令审计（P001） |
| `DuplicateEntryLog` | 异常重复入场（P003） |
| `EmployeeParkingLot` | 员工-停车场关联 |
| `ExceptionRecord` | 异常记录（Phase 2 D2） |
| `ExitRecord` | 出场记录（P004） |
| `FixedSpaceBinding` | 固定车位绑定 |
| `LegacySysRolePermission` | 旧版角色权限（Sa-Token 兼容） |
| `MockPaymentConfig` / `MockPaymentRecord` | 模拟支付 |
| `MonitorAlert` | 异常提醒 |
| `MonthlyPass` / `OrderStatusLog` | 月卡 + 订单状态日志 |
| `ParkingLane` / `ParkingLot` | 车道 + 停车场（与 parking 模块共享 DB 表） |
| `ParkingLotCapacityLog` / `ParkingLotStatusLog` | 容量/状态变更审计 |
| `ParkingOrder` / `PayOrder` / `PayMerchantConfig` | 停车订单 + 支付流水 + P云配置 |
| `ParkingRecord` / `ParkingRecordSyncLog` | 停车记录 + P云同步日志 |
| `PlateBinding` | 车牌绑定 |
| `RecognitionEventLog` | 识别事件日志（T28） |
| `SysAuditLog` / `SysLoginLog` | 审计日志 + 登录日志 |
| `SysConfig` | 系统配置 |
| `SysPermission` / `SysRole` / `SysUser` | 权限/角色/用户 |
| `Tenant` / `TenantAuditLog` | 租户 + 审核日志 |
| `Vehicle` / `VehicleList` / `VehicleRenewalLog` | 车辆 + 黑白名单 + 续费 |
| `WebhookSecret` | Webhook 签名密钥 |
| `WxUser` | 微信用户 |

---

## Mapper（51个）

各实体对应同名 Mapper（如 `ArchiveMapper` → `ArchiveData`），大部分使用 MyBatis-Plus `BaseMapper` 基础方法。关键自定义查询需查对应 Mapper 源码。
