# 模块：booth（岗亭操作）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/booth/`
> **所属**：`parking-system` · `com.jushan.platform.modules.booth`
> **职责**：识别事件处理（入场/出场判定+开闸）、人工入场补录、费用减免、交接班管理、岗亭车辆查询。
> **最近更新**：2026-07-26（v1.7：手动开闸后推送剩余车位更新 pushSpaceUpdate；exitImage/exitOperator 写入出场记录；selectInByPlateNumber 平台管理员跨租户查询）

---

## 一、接口入口

### BoothSpaceController  `controller/BoothSpaceController.java`
- **基础路径**：`/api/booth/spaces` ｜ **权限**：`booth:operate` ｜ **状态**：✅（v1.5.3 新增）
- **功能**：岗亭端手动修正剩余车位数。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| adjust | POST | `/adjust` | `booth:operate` | 调整余位（SET 直接设定 / ADJUST 加减调整），操作后 WS 推送 | `SpaceAdjustCmd` | `R<Void>` |

### BoothFeeReductionController  `controller/BoothFeeReductionController.java`
- **基础路径**：`/api/v1/booth/charge`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| applyFeeReduction | POST | `/fee-reduction` | `fee:reduce` | 费用减免 | `FeeReductionCmd` | `R<FeeReductionVO>` |

### BoothManualEntryController  `controller/BoothManualEntryController.java`
- **基础路径**：`/api/v1/booth/manual-entry` ｜ **状态**：`@Tag("人工补录")`（GAP-08）
- **权限**：无 `@RequirePermission`（需登录即可）

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| manualEntry | POST | `/` | 人工补录入场 | `ManualEntryRequest` | `R<Long>` |

### BoothVehicleController  `controller/BoothVehicleController.java`
- **基础路径**：`/api/v1/booth/vehicles` ｜ **权限**：`booth:view`

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| presentVehicles | GET | `/present` | 在场车辆（含计费） | `parkingLotId,...` | `R<Map>` |
| historyRecords | GET | `/history` | 历史通行记录（含入场抓拍图 entryImage） | `parkingLotId,plateNumber,...` | `R<Map>` |

### RecognitionEventController  `controller/RecognitionEventController.java`
- **基础路径**：`/api/v1/booth/recognition` ｜ **权限**：`booth:operate`
- **功能**：识别事件处理、道闸控制（开闸/关闸/常开/取消常开/常关/取消常关）。

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| handleEvent | POST | `/handle` | 处理识别事件 | `RecognitionEventCmd` | `R<RecognitionResultVO>` |
| manualOpenGate | POST | `/manual-open-gate` | 人工开闸（可选传抓拍图 entryImage） | `laneId,reason,isCharge,feeCents,plateNumber,entryImage` | `R<RecognitionResultVO>` |
| manualCapture | POST | `/manual-capture` | 手动抓拍指定车道相机 | `laneId` | `R<CaptureResultDTO>` |
| manualOpenGateBatch | POST | `/manual-open-gate-batch` | 批量开闸 | `Map body` | `R<Map>` |
| manualCloseGate | POST | `/manual-close-gate` | 人工关闸 | `laneId,reason` | `R<RecognitionResultVO>` |
| manualLockGate | POST | `/manual-lock-gate` | 常开（锁定） | `laneId,reason` | `R<RecognitionResultVO>` |
| manualUnlockGate | POST | `/manual-unlock-gate` | 取消常开 | `laneId,reason` | `R<RecognitionResultVO>` |
| manualLockCloseGate | POST | `/manual-lock-close-gate` | 常关（v1.5） | `laneId,reason` | `R<RecognitionResultVO>` |
| manualUnlockCloseGate | POST | `/manual-unlock-close-gate` | 取消常关（v1.5） | `laneId,reason` | `R<RecognitionResultVO>` |
| getGateCapabilities | GET | `/gate-capabilities` | 查询车道控闸设备能力（空则 fallback 到 DeviceModel.capabilities） | `laneId` | `R<List<String>>` |

### ShiftRecordController  `controller/ShiftRecordController.java`
- **基础路径**：`/api/v1/shift-records` ｜ **权限**：`booth:*`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| startShift | POST | `/start` | `booth:operate` | 开班 | `ShiftStartCmd` | `R<ShiftRecordVO>` |
| closeShift | POST | `/close` | `booth:operate` | 交班 | `ShiftCloseCmd` | `R<ShiftRecordVO>` |
| getCurrentShift | GET | `/current` | `booth:view` | 当前开班 | — | `R<ShiftRecordVO>` |
| detail | GET | `/{id}` | `booth:view` | 详情 | `id` | `R<ShiftRecordVO>` |
| page | GET | `/` | `booth:view` | 分页 | `parkingLotId,status,current,size` | `R<IPage<ShiftRecordVO>>` |
| listByParkingLotId | GET | `/lot/{parkingLotId}` | `booth:view` | 按车场列班次 | `parkingLotId` | `R<List<ShiftRecordVO>>` |

---

## 二、Service（均为接口 + impl）

### BoothSpaceService  `service/BoothSpaceService.java`（v1.5.3 新增）
岗亭余位调整。委托 `ParkingLotService.updateCapacity` 执行 DB 更新+审计日志，完成后通过 `BoothWebSocketPublisher` 推送。

| 方法 | 签名 | 功能 |
|---|---|---|
| adjust | `void adjust(SpaceAdjustCmd)` | 调整余位：SET（直接设定）/ ADJUST（加减），负数保护，WS 推送 |

### RecognitionEventService  `service/RecognitionEventService.java`
岗亭核心服务：识别事件处理、道闸控制。通过 `DeviceAccessClient` 调用 Device Access 开闸接口。

| 方法 | 签名 | 功能 |
|---|---|---|
| handleEvent | `RecognitionResultVO handleEvent(RecognitionEventCmd)` | 识别→判定→余位→计费→开闸→日志 |
| manualOpenGate | `RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason, boolean isCharge, Integer feeCents, String plateNumber, String entryImage)` | 人工开闸（设备解析委托 `DeviceService.resolveGateDevice(laneId)`；含审计/抓拍图/session补写/WS推送） |
| captureImage | `CaptureResultDTO captureImage(Long laneId)` | 严格按入口相机（识别方向=入口的主相机）触发主动抓拍；车道未绑入口相机直接报错 |
| manualCloseGate | `RecognitionResultVO manualCloseGate(Long laneId, Long operatorId, String reason)` | 人工关闸（设备解析委托 `DeviceService.resolveGateDevice(laneId)`） |
| manualLockGate | `RecognitionResultVO manualLockGate(Long laneId, Long operatorId, String reason)` | 常开锁定（委托 `DeviceService.lockGateByLane`） |
| manualUnlockGate | `RecognitionResultVO manualUnlockGate(Long laneId, Long operatorId, String reason)` | 取消常开（委托 `DeviceService.unlockGateByLane`） |
| manualLockCloseGate | `RecognitionResultVO manualLockCloseGate(Long laneId, Long operatorId, String reason)` | 常关锁定（v1.5；委托 `DeviceService.lockCloseGateByLane`，内部先关闸再锁定） |
| manualUnlockCloseGate | `RecognitionResultVO manualUnlockCloseGate(Long laneId, Long operatorId, String reason)` | 取消常关（v1.5；委托 `unlockGateInternal`，与取消常开共用底层 unlock 逻辑） |

### ShiftRecordService  `service/ShiftRecordService.java`
继承 `IService<ShiftRecord>`。

| 方法 | 签名 | 功能 |
|---|---|---|
| startShift / closeShift | `ShiftRecordVO startShift(ShiftStartCmd)` / `closeShift(ShiftCloseCmd)` | 开班/交班 |
| getCurrentShift / detail / pageList / listByParkingLotId | 标准查询 | |

### FeeReductionService  `service/FeeReductionService.java`

| 方法 | 签名 | 功能 |
|---|---|---|
| apply | `FeeReductionVO apply(FeeReductionCmd cmd)` | 费用减免申请 |

---

## 三、领域对象

| 类型 | 类名 | 作用 |
|---|---|---|
| DTO | SpaceAdjustCmd | 余位调整命令（mode: SET/ADJUST, value, parkingLotId, reason）v1.5.3 新增 |
| Entity | ShiftRecord | 交接班记录 |
| DTO | FeeReductionCmd | 费用减免命令 |
| DTO | RecognitionEventCmd | 识别事件命令 |
| DTO | ShiftStartCmd / ShiftCloseCmd | 开班/交班 |
| VO | FeeReductionVO | 减免结果 |
| VO | RecognitionResultVO | 识别处理结果（含三层开闸状态） |
| VO | ShiftRecordVO | 交接班视图 |

---

## 四、Mapper

| 类名 | 关键方法 |
|---|---|
| ShiftRecordMapper | `selectOpenByOperator`、`selectByParkingLotId`、`sumCashOrderFeeCents`、`countEntries/countExits/countArrearsOrders/countHandoverOrders`、`listArrearsOrders` |

---

## 五、跨模块依赖

- `RecognitionEventService.handleEvent` → `VehicleTypeDecisionService.decide(...)`（车辆类型判定）→ `FeeCalculationService.calculateFeeCents(...)`（计费）→ `DeviceService.resolveGateDevice(laneId)`（控闸设备解析，4级优先级：gate_device_id→GATE→CAMERA+OPEN_GATE→报错）→ `DeviceAccessClient.openGate(...)`（开闸）。
- 手动开闸/关闸/自动开闸均统一走 `DeviceService.resolveGateDevice(laneId)`，停车场级回退已删除。
- 前端按钮按 `GET /api/v1/booth/recognition/gate-capabilities?laneId=` 返回的能力列表动态渲染（Q3=开闸/常开，C5=开/关/常开/常关），MIXED 车道按方向拆分为两张独立卡片。
