# 索引：com.jushan.system Controllers（44个）

> **包路径**：`parking-system/src/main/java/com/jushan/system/controller/`
> **所属**：`parking-system` · `com.jushan.system`
> **职责**：运营后台岗亭端、微信端、P云对接的全部 REST 接口入口。
> **最近更新**：2026-07-23（新增 gate-capabilities 接口；新增 resolveGateDevice 方法）

**说明**：此为扁平 Controller 集合索引，按功能域分组。系统内存在部分与 `com.jushan.platform.modules.*` 同名功能（如计费规则、车辆类型），以 `com.jushan.platform.modules.*` 的新实现为准。

---

## 一、岗亭端（booth）

| Controller | 基础路径 | 职责 | 接口数 |
|---|---|---|---|
| `BoothAuthController` | `/auth` | 岗亭登录/退出/会话 | 3 |
| `BoothMonitorController` | `/api/booth/monitor` | 监控快照、设备刷新、异常确认 | 3 |
| `BoothParkingFeeController` | `/api/booth/parking/fee` | 按车牌/记录查费、费用预览 | 3 |
| `BoothRecognitionController` | `/api/booth/recognition` | 识别日志车牌纠正 | 1 |
| `BoothTempPlateController` | `/api/booth/temp-plate` | 临时车牌建议/人工入场/出场匹配 | 3 |

---

## 二、运营后台（admin）

| Controller | 基础路径 | 职责 | 接口数 |
|---|---|---|---|
| `ArchiveController` | `/api/v1/archive` | 数据归档记录/日志查询 | 2 |
| `AuditController` | `/api/admin/audit-logs` | 高风险操作审计日志 | 2 |
| `BillingRuleController` | `/api/admin/billing-rules` | 收费规则 CRUD/版本切换/试算 | 7 |
| `CompanyController` | `/api/admin/companies` | 公司/集团 CRUD+树形 | 6 |
| `DashboardController` | `/api/v1/admin/dashboard` | 运营仪表盘 | 1 |
| `DeviceController` | `/api/admin/devices` | 设备台账 CRUD+状态快照+可用设备 | 8 |
| `EmployeeController` | `/api/admin/employees` | 员工 CRUD+密码重置+启停 | 6 |
| `ExceptionRecordAdminController` | `/api/v1/admin/exception-records` | 异常记录列表/处理 | 2 |
| `FileUploadController` | `/api/v1/admin/files` | 文件上传 | 1 |
| `FixedSpaceController` | `/api/v1/fixed-spaces` | 固定车位 CRUD/到期列表/续费/审核 | 7 |
| `FixedSpaceAuditController` | `/api/v1/admin/fixed-space-audit` | 固定车位待审/审批/驳回 | 3 |
| `ManualGateRecordAdminController` | `/api/v1/admin/manual-gate-records` | 人工开闸记录 | 1 |
| `MonthlyPassController` | `/api/v1/monthly-passes` | 月卡 CRUD/到期列表/续费/取消 | 6 |
| `MonthlyPassAuditController` | `/api/v1/admin/monthly-pass-audit` | 月卡审批 | 3 |
| `OrderAdminController` | `/api/v1/admin/orders` | 订单管理(分页/详情/关闭/退款/状态日志/导出) | 6 |
| `ParkingLaneController` | `/api/admin/lanes` | 车道 CRUD+状态管理 | 5 |
| `ParkingLotController` | `/api/admin/parking-lots` | 停车场 CRUD+状态/容量/就绪检查 | 8 |
| `ParkingLotParamController` | `/api/admin/parking-lots/{lotId}/params` | 车场级参数(列表/更新/重置) | 3 |
| `ParkingRecordAdminController` | `/api/v1/admin/parking-records` | 停车记录分页/导出 | 2 |
| `ReportController` | `/api/v1/admin/reports` | 营收/车流报表+导出 | 4 |
| `SystemParamController` | `/api/v1/system-params` | 系统参数(列表/分组/单条/更新) | 4 |
| `TenantController` | `/api/admin/tenants` | 租户 CRUD/审核 | 5 |
| `VehicleListController` | `/api/v1/admin/vehicle-list` | 黑白名单 CRUD/分页/导入/触发类型 | 7 |

---

## 三、停车/支付

| Controller | 基础路径 | 职责 |
|---|---|---|
| `ParkingOrderController` | `/api/v1/orders` | 停车订单支付/取消/查询/P云回调 |

---

## 四、微信/小程序端

| Controller | 基础路径 | 职责 |
|---|---|---|
| `WxUserController` | `/wx` | 微信登录/退出/用户信息/车牌绑定/手机绑定 |
| `WxParkingFeeController` | `/wx/parking/fee` | 车主查费/费用预览 |
| `MiniAuthController` | `/api/v1/mini` | 小程序登录/手机绑定 |
| `MiniFixedSpaceController` | `/api/v1/mini/fixed-spaces` | 小程序固定车位申请/支付/续费/可用查询 |
| `MiniMonthlyPassController` | `/api/v1/mini/monthly-passes` | 小程序月卡申请/支付/续费 |

---

## 五、P云对接

| Controller | 基础路径 | 职责 |
|---|---|---|
| `PyunNotifyController` | `/api/v1/pay` | 支付回调通知（幂等） |
| `PyunPpFrontController` | `/api/v1/pyun` | P云前端接口（计费/支付结果/车辆信息/续费通知） |
| `PyunVipController` | `/api/v1/pyun` | P云 VIP 同步 |

---

## 六、模拟 / 内部接口

| Controller | 基础路径 | 职责 |
|---|---|---|
| `MockPaymentController` | `/api/v1/mock-payment` | 模拟支付(配置/记录/手动标记支付) |
| `MockRecognitionController` | `/api/v1/internal/mock` | 模拟触发识别事件 |
| `RemoteGateController` | `/api/v1/admin/remote-gate` | 远程开闸 |
| `InternalGateController` | `/api/v1/internal/gate` | 内部开闸/关闸 |
| `InternalWhitelistController` | `/api/v1/internal/whitelist` | 内部白名单同步 |
