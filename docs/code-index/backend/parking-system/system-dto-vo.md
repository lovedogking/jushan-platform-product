# 索引：com.jushan.system DTO + VO（88个）

> **包路径**：`parking-system/src/main/java/com/jushan/system/dto/` `vo/`
> **所属**：`parking-system` · `com.jushan.system`
> **最近更新**：2026-07-21

---

## DTO（48个），请求命令

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| `CreateBillingRuleRequest` / `UpdateBillingRuleRequest` / `SwitchBillingRuleRequest` | 计费规则 CRUD/切换 | `CreateCompanyRequest` / `UpdateCompanyRequest` | 公司 |
| `CreateDeviceRequest` / `UpdateDeviceRequest` | 设备台账 | `CreateEmployeeRequest` / `UpdateEmployeeRequest` / `ResetPasswordRequest` | 员工 |
| `CreateLaneRequest` / `UpdateLaneRequest` | 车道 | `CreateParkingLotRequest` / `UpdateParkingLotRequest` / `ParkingLotCapacityRequest` / `ParkingLotStatusRequest` | 停车场 |
| `CreateTenantDetail` / `TenantAuditRequest` | 租户 | `RegisterRequest` | 注册 |
| `ChangePasswordRequest` | 改密 | `LoginRequest`（auth 模块 / booth-auth 共用） | |
| `FeePreviewRequest` | 费用预览 | `SystemParamUpdateRequest` | 系统参数 |
| `FixedSpaceCreateRequest` / `FixedSpaceRenewRequest` | 固定车位 | `MonthlyPassCreateRequest` / `MonthlyPassRenewRequest` | 月卡 |
| `MockRecognitionRequest` | 模拟识别 | `RemoteGateAlertDTO` | 远程警报 |
| `TempPlateEntryRequest` / `TempPlateExitRequest` | 临时车牌 | `ProxyRequest` | 代付请求 |
| `AuditLogQueryRequest` | 审计查询 | `VehicleListCreateCmd` / `VehicleListUpdateCmd` / `VehicleListPageQuery` | 黑白名单 |
| `BindPlateRequest` / `BindPhoneRequest` / `UnbindPlateRequest` | 车牌/手机绑定 | `WhitelistEntry` / `WhitelistSyncResponse` | 白名单 |
| `MiniLoginRequest` / `MiniPhoneRequest` | 小程序登录/手机 | `MiniFixedSpaceApplyRequest` / `MiniFixedSpaceRenewRequest` | 小程序固定车位 |
| `MiniMonthlyPassApplyRequest` / `MiniMonthlyPassRenewRequest` | 小程序月卡 | `WxLoginRequest` | 微信登录 |

---

## VO（40个），响应对象

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| `BillingRuleVO` / `BillingRuleVersionVO` | 收费规则 | `CompanyVO` / `CompanyTreeVO` | 公司 |
| `DeviceVO` / `DeviceModelVO` / `DeviceVendorVO` / `DeviceStatusVO` | 设备 | `EmployeeVO` | 员工 |
| `ParkingLaneVO` / `ParkingLotVO` / `ParkingLotParamVO` / `ParkingLotReadinessVO` / `ReadinessItem` | 车道/停车场 | `TenantVO` | 租户 |
| `DashboardVO` | 仪表盘 | `RevenueReportVO` / `TrafficReportVO` | 报表 |
| `SystemParamVO` | 系统参数 | `OrderAdminVO` / `OrderStatusLogVO` / `ParkingFeeVO` | 订单/费用 |
| `ParkingRecordAdminVO` | 停车记录 | `ManualGateRecordAdminVO` | 人工开闸记录 |
| `ExceptionAdminVO` | 异常记录 | `MonitorAlertVO` | 异常提醒 |
| `FixedSpaceVO` / `MonthlyPassVO` | 固定车位/月卡 | `AuditLogVO` | 审计日志 |
| `BoothMonitorSnapshotVO` / `BoothLaneVO` / `BoothLaneCameraVO` / `BoothRecognitionEventVO` | 岗亭监控 | |
| `WxLoginResult` / `WxUserVo` / `PlateBindingVo` / `BindPhoneResult` | 微信 | `VehicleListVO` / `VehicleListDecisionVO` | 黑白名单 |
