# parking-system 两套包结构说明

`parking-system` 内含两套共存的后端实现包，共享同一物理库：

| 包 | 定位 | 分层风格 | 状态 |
|---|---|---|---|
| `com.jushan.platform.modules.*` | **新业务模块**（一期到三期主力） | 标准分层：`controller/` `service/` `service/impl/` `entity/` `dto/` `vo/` `mapper/` | ✅ 活跃 |
| `com.jushan.system.*` | **较早的扁平业务实现** | 扁平拆分：所有 Controller 统一放 `system/controller/`、Service 放 `system/service/` … 按类型而非业务领域汇集 | ⚠️ 存量，部分功能逐步迁移中 |

**索引方式差异**：
- `modules.*` 每个业务模块一个独立索引文件（如 `modules-parking.md`）。
- `com.jushan.system.*` 因按层扁平汇集，按**分层维度**拆分索引：controller / service / entity+mapper / dto+vo / misc。

**常见"入口在哪"**：
- 通道/车场管理 → `com.jushan.system.ParkingLaneController` / `ParkingLotController`（但 Entity/DTO/Mapper 在 `parking` 模块）。
- 停车订单/欠费/月卡 → `com.jushan.system`。
- 微信用户/计费规则/设备台账/系统参数 → `com.jushan.system`。
- 新业务（停车会话、车辆档案、账户角色、访客预约等） → `com.jushan.platform.modules.*`。
