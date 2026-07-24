# 03 — Miniapp 域 Service 迁移

**Type:** task
**Status:** resolved
**Blocked by:** 01

## 范围

将以下 6 个 Service 从 `com.jushan.system.service` 迁至 `com.jushan.platform.modules.miniapp.service`：

- `MiniAuthService`
- `MiniFixedSpaceService`
- `MiniMonthlyPassService`
- `MonthlyPassService`
- `FixedSpaceService`
- `WxUserService`

## 关键风险

- 高度依赖 Parking 域（`ParkingLotService`、`ParkingFeeService`）
- `WxUserService` 已消除通配符 import，搬迁相对安全
- 测试文件 `MonthlyPassServiceTest` 已有覆盖

## Comments
