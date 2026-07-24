# 02 — Booth 域 Service 迁移

**Type:** task
**Status:** resolved
**Blocked by:** 01 (Parking 域先搬，验证方法可行)

## 范围

将以下 11 个 Service 从 `com.jushan.system.service` 迁至 `com.jushan.platform.modules.booth.service`：

- `BoothMonitorService`
- `EntryService`
- `ExitService`
- `ExitResult`
- `TempPlateService`
- `TempPlateNumberGenerator`
- `CameraFailoverService`
- `DuplicateEntryHandler`
- `DuplicateEntryPolicy`
- `RecognitionCorrectionService`
- `ReleaseDecision`

## 关键风险

- `ExitResult` / `ReleaseDecision` 是工具类（非 Spring Bean），被多个 Service 引用
- Booth 域高度依赖 Parking 域 Service（如 `ParkingLotService`、`ParkingFeeService`），Parking 域必须先搬完
- `BoothMonitorService` 引用 `DeviceService`（在稍后批次迁移）

## 步骤

1. Parking 域迁移完成后
2. 方法同 `01-service-parking`

## Comments
