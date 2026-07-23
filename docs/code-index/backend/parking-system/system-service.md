# 索引：com.jushan.system Services（46个）

> **包路径**：`parking-system/src/main/java/com/jushan/system/service/`
> **所属**：`parking-system` · `com.jushan.system`
> **职责**：存量业务的所有 Service 层。注意区别于 `com.jushan.platform.modules.*` 的 Service。
> **最近更新**：2026-07-23（DeviceService 新增 resolveGateDevice / validateCapabilitiesMatch / gate-capabilities 车道级方法）

---

| 类名 | 职责 |
|---|---|
| `ArchiveService` | 数据归档服务 |
| `AuditService` | 审计日志服务 |
| `BillingEngine` | 计费引擎（存量，新项目用 `FeeCalculationService`） |
| `BillingRuleService` | 收费规则管理（存量版本） |
| `BillingRuleConfig` | 收费规则配置 |
| `BoothMonitorService` | 岗亭监控服务 |
| `CameraFailoverService` | 摄像头故障切换服务 |
| `CompanyService` | 公司/集团管理 |
| `DeviceService` | 设备台账管理 + **控闸设备解析（resolveGateDevice）** + **能力校验（validateCapabilitiesMatch）** + 开/关/常开/取消常开车道级方法 |
| `DuplicateEntryHandler` | 重复入场处理器 |
| `DuplicateEntryPolicy` | 重复入场策略 |
| `EmployeeService` | 员工管理 |
| `EntryService` | 车辆入场服务（T30） |
| `ExitService` / `ExitResult` | 车辆出场服务（P004） |
| `FixedSpaceService` | 固定车位管理 |
| `MiniAuthService` | 小程序认证 |
| `MiniFixedSpaceService` | 小程序固定车位 |
| `MiniMonthlyPassService` | 小程序月卡 |
| `MockPaymentService` | 模拟支付 |
| `MockRecognitionService` | 模拟识别事件 |
| `MonitorAlertService` | 异常提醒服务 |
| `MonthlyPassService` | 月卡管理 |
| `OrderStatusLogService` | 订单状态日志 |
| `ParamResolver` | 参数解析器 |
| `ParkingFeeService` | 停车费用查询 |
| `ParkingLaneService` | 车道管理 |
| `ParkingLotService` | 停车场管理 |
| `ParkingLotScopeResolver` | 停车场数据范围解析 |
| `ParkingOrderService` | 停车订单管理 |
| `PermissionService` | 权限管理 |
| `PrepaidDeductionService` | 储值车扣费 |
| `PyunParkingSyncService` | P云停车记录同步 |
| `PyunPaymentClient` | P云支付客户端 |
| `ReadinessCheckService` | 停车场就绪检查 |
| `RecognitionCorrectionService` | 识别纠正服务 |
| `ReleaseDecision` | 放行决策 |
| `SystemParamService` | 系统参数管理 |
| `TempPlateService` / `TempPlateNumberGenerator` | 临时车牌 |
| `TenantService` | 租户管理 |
| `TimeSegmentConfig` | 分时段配置 |
| `VehicleListService` | 黑白名单管理 |
| `WhitelistSyncService` | 白名单同步 |
| `WxUserService` | 微信用户管理 |
