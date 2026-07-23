# 模块：booth（岗亭操作）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/booth/`
> **所属**：`parking-system` · `com.jushan.platform.modules.booth`
> **职责**：识别事件处理（入场/出场判定+开闸）、人工入场补录、费用减免、交接班管理、岗亭车辆查询。
> **最近更新**：2026-07-24

---

## 一、接口入口

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
- **功能**：识别事件处理、道闸控制（开闸/关闸/常开/取消常开）。

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| handleEvent | POST | `/handle` | 处理识别事件 | `RecognitionEventCmd` | `R<RecognitionResultVO>` |
| manualOpenGate | POST | `/manual-open-gate` | 人工开闸（可选传抓拍图 entryImage） | `laneId,reason,isCharge,feeCents,plateNumber,entryImage` | `R<RecognitionResultVO>` |
| manualCapture | POST | `/manual-capture` | 手动抓拍指定车道相机 | `laneId` | `R<CaptureResultDTO>` |
| manualOpenGateBatch | POST | `/manual-open-gate-batch` | 批量开闸 | `Map body` | `R<Map>` |
| manualCloseGate | POST | `/manual-close-gate` | 人工关闸 | `laneId,reason` | `R<RecognitionResultVO>` |
| manualLockGate | POST | `/manual-lock-gate` | 常开（锁定） | `laneId,reason` | `R<RecognitionResultVO>` |
| manualUnlockGate | POST | `/manual-unlock-gate` | 取消常开 | `laneId,reason` | `R<RecognitionResultVO>` |

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

### RecognitionEventService  `service/RecognitionEventService.java`
岗亭核心服务：识别事件处理、道闸控制。通过 `DeviceAccessClient` 调用 Device Access 开闸接口。

| 方法 | 签名 | 功能 |
|---|---|---|
| handleEvent | `RecognitionResultVO handleEvent(RecognitionEventCmd)` | 识别→判定→余位→计费→开闸→日志 |
| manualOpenGate | `RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason, boolean isCharge, Integer feeCents, String plateNumber, String entryImage)` | 人工开闸（含审计；优先使用前端传入 entryImage，否则回溯车道最近识别事件抓拍图；补写 parking_session 附 entryImage，并落库 MANUAL 识别事件 + WS 推送，车道卡片实时显示放行车辆与抓拍图）；接口另保留 6 参 default 兼容重载（entryImage=null） |
| captureImage | `CaptureResultDTO captureImage(Long laneId)` | 选择车道主相机触发主动抓拍，委托 DeviceAccessClient → device-access `/api/v1/devices/{sn}/capture` |
| manualCloseGate | `RecognitionResultVO manualCloseGate(Long laneId, Long operatorId, String reason)` | 人工关闸 |
| manualLockGate | `RecognitionResultVO manualLockGate(Long laneId, Long operatorId, String reason)` | 常开锁定 |
| manualUnlockGate | `RecognitionResultVO manualUnlockGate(Long laneId, Long operatorId, String reason)` | 取消常开 |

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

- `RecognitionEventService.handleEvent` → `VehicleTypeDecisionService.decide(...)`（车辆类型判定）→ `FeeCalculationService.calculateFeeCents(...)`（计费）→ `DeviceAccessClient.openGate(...)`（开闸）。
- Device Webhook 链路：`DeviceWebhookService` 接收事件 → `DeviceWebhookEventHandler` 调用 `RecognitionEventService.handleEvent`。
