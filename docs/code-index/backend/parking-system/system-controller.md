# 索引：com.jushan.system Controller（已清空）

> **包路径**：`parking-system/src/main/java/com/jushan/system/controller/`
> **最近更新**：2026-07-24（全量迁移至 modules）

**状态**：所有 43 个 Controller 已迁移至对应模块，旧包仅剩 `package-info.java`。

已迁移目标：
- `modules/booth/controller/`：BoothAuth, BoothMonitor, BoothParkingFee, BoothParkingLot, BoothRecognition, BoothTempPlate
- `modules/miniapp/controller/`：MiniAuth, MiniFixedSpace, MiniMonthlyPass, MonthlyPass, FixedSpace, MockPayment, MockRecognition, WxUser, WxParkingFee, MonthlyPassAudit, FixedSpaceAudit
- `modules/parking/controller/`：BillingRule, Dashboard, ExceptionRecordAdmin, ManualGateRecordAdmin, OrderAdmin, ParkingLane, ParkingLot, ParkingLotParam, ParkingOrder, ParkingRecordAdmin, Report, InternalGate, RemoteGate, PyunNotify, PyunPpFront, PyunVip
- `modules/device/controller/`：Device
- `modules/vehicle/controller/`：VehicleList, InternalWhitelist
- `modules/account/controller/`：Employee
- `modules/tenant/controller/`：Tenant
- `modules/company/controller/`：Company
- `modules/log/controller/`：Audit
- `modules/common/controller/`：Archive, FileUpload, SystemParam
