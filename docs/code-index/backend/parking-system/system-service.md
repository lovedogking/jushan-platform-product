# 索引：com.jushan.system Service（已清空）

> **包路径**：`parking-system/src/main/java/com/jushan/system/service/`
> **最近更新**：2026-07-24（全量迁移至 modules）

**状态**：所有 46 个 Service 已迁移至对应模块，旧包仅剩 `package-info.java`。

已迁移目标：
- `modules/parking/service/` (16)：BillingEngine, BillingRuleConfig, BillingRuleService, ParkingFeeService, ParkingLaneService, ParkingLotService, ParkingOrderService, OrderStatusLogService, ReadinessCheckService, PrepaidDeductionService, TimeSegmentConfig, MockPaymentService, MockRecognitionService, ParkingLotScopeResolver, PyunParkingSyncService, PyunPaymentClient
- `modules/booth/service/` (11)：BoothMonitorService, EntryService, ExitService, ExitResult, TempPlateService, TempPlateNumberGenerator, CameraFailoverService, DuplicateEntryHandler, DuplicateEntryPolicy, RecognitionCorrectionService, ReleaseDecision
- `modules/miniapp/service/` (6)：MiniAuthService, MiniFixedSpaceService, MiniMonthlyPassService, MonthlyPassService, FixedSpaceService, WxUserService
- `modules/vehicle/service/` (2)：VehicleListService, WhitelistSyncService
- `modules/account/service/` (2)：EmployeeService, PermissionService
- `modules/tenant/service/` (1)：TenantService
- `modules/company/service/` (1)：CompanyService
- `modules/common/service/` (4)：ArchiveService, AuditService, SystemParamService, ParamResolver
- `modules/device/service/` (2)：DeviceService, MonitorAlertService
