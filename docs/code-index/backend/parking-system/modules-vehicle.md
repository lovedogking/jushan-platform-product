# 模块：vehicle（车辆档案 / 钱包 / 审核）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/vehicle/`
> **所属**：`parking-system` · `com.jushan.platform.modules.vehicle`
> **职责**：车辆主档 CRUD、一位多车、月卡/固定车续费、储值车钱包（充值/退款/调账/流水）、车辆审核、车辆类型判定引擎。
> **最近更新**：2026-07-21

**说明**：表格路径均相对上述**包路径**。5 个 Service 均为「接口 + `impl/` 实现」；另有 `listener/RenewalPaymentListener.java` 监听续费支付回调触发生效。

---

## 一、接口入口（Controller）

### SysVehicleController  `controller/SysVehicleController.java`
- **基础路径**：`/api/v1/vehicles` ｜ **权限**：`vehicle:*`
- **功能**：车辆增删改查、批量删除、一位多车绑定、续费。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `vehicle:create` | 新增车辆 | `VehicleCreateCmd` | `R<VehicleVO>` |
| update | PUT | `/{id}` | `vehicle:update` | 编辑车辆 | `id, VehicleUpdateCmd` | `R<VehicleVO>` |
| delete | DELETE | `/{id}` | `vehicle:delete` | 软删除 | `id` | `R<Void>` |
| batchDelete | POST | `/batch-delete` | `vehicle:delete` | 批量删除 | `List<Long> ids` | `R<Void>` |
| detail | GET | `/{id}` | `vehicle:view` | 详情 | `id` | `R<VehicleVO>` |
| page | GET | `/` | `vehicle:view` | 分页 | `plateNumber,vehicleType,departmentId,parkingLotId,status,current,size` | `R<IPage<VehicleVO>>` |
| addMultiPlate | POST | `/{id}/multi-plates` | `vehicle:update` | 添加一位多车 | `id, VehicleMultiPlateBindCmd` | `R<Void>` |
| removeMultiPlate | DELETE | `/{id}/multi-plates/{bindId}` | `vehicle:update` | 解绑一位多车 | `id, bindId` | `R<Void>` |
| renewPreview | GET | `/{id}/renew-preview` | `vehicle:view` | 续费有效期预览 | `id, months` | `R<RenewalPreviewVO>` |
| renew | POST | `/{id}/renew` | `vehicle:renew` | 发起续费（生成订单） | `id, VehicleRenewalCmd` | `R<RenewalOrderVO>` |
| confirmRenew | POST | `/renew/{orderId}/confirm` | `vehicle:renew` | 确认续费生效 | `orderId` | `R<RenewalOrderVO>` |

### SysVehicleWalletController  `controller/SysVehicleWalletController.java`
- **基础路径**：`/api/v1/vehicle-wallets` ｜ **权限**：`vehicle:*`
- **功能**：储值车钱包充值、退款、调账与流水查询。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| getByVehicleId | GET | `/vehicle/{vehicleId}` | `vehicle:view` | 查钱包 | `vehicleId` | `R<WalletVO>` |
| recharge | POST | `/recharge` | `vehicle:update` | 充值 | `WalletRechargeCmd` | `R<WalletVO>` |
| refund | POST | `/refund` | `vehicle:update` | 退款 | `WalletRefundCmd` | `R<WalletVO>` |
| adjust | POST | `/adjust` | `vehicle:update` | 调账 | `WalletAdjustCmd` | `R<WalletVO>` |
| listLogs | GET | `/logs` | `vehicle:view` | 流水列表 | `vehicleId,walletId,logType` | `R<List<WalletLogVO>>` |
| pageLogs | GET | `/logs/page` | `vehicle:view` | 流水分页 | `vehicleId,walletId,logType,current,size` | `R<IPage<WalletLogVO>>` |

### VehicleAuditController  `controller/VehicleAuditController.java`
- **基础路径**：`/api/v1/vehicle-audits` ｜ **权限**：`vehicle:*`
- **功能**：车辆资料审核提交与处理。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| submit | POST | `/` | `vehicle:update` | 提交审核 | `VehicleAuditSubmitCmd` | `R<VehicleAuditVO>` |
| process | PUT | `/{id}/process` | `vehicle:update` | 处理审核（通过/驳回/补充） | `id, VehicleAuditProcessCmd` | `R<VehicleAuditVO>` |
| detail | GET | `/{id}` | `vehicle:view` | 审核详情 | `id` | `R<VehicleAuditVO>` |
| page | GET | `/` | `vehicle:view` | 分页 | `auditStatus,applyType,plateNumber,current,size` | `R<IPage<VehicleAuditVO>>` |
| listByVehicleId | GET | `/vehicle/{vehicleId}` | `vehicle:view` | 某车审核记录 | `vehicleId` | `R<List<VehicleAuditVO>>` |

---

## 二、业务服务（Service，均为 接口 + impl）

### SysVehicleService  `service/SysVehicleService.java`（+ `impl/SysVehicleServiceImpl.java`）
继承 `IService<SysVehicle>`；车辆主档 + 一位多车。

| 方法 | 签名 | 功能 |
|---|---|---|
| create | `VehicleVO create(VehicleCreateCmd cmd)` | 新增 |
| updateVehicle | `VehicleVO updateVehicle(Long id, VehicleUpdateCmd cmd)` | 编辑 |
| deleteVehicle | `void deleteVehicle(Long id)` | 软删除 |
| batchDelete | `void batchDelete(List<Long> ids)` | 批量删除 |
| detail | `VehicleVO detail(Long id)` | 详情 |
| pageList | `IPage<VehicleVO> pageList(IPage, String plateNumber, String vehicleType, Long departmentId, Long parkingLotId, String status)` | 分页 |
| addMultiPlate / removeMultiPlate | `void addMultiPlate(Long, VehicleMultiPlateBindCmd)` / `void removeMultiPlate(Long, Long)` | 一位多车增删 |
| findByPlateNumber | `VehicleVO findByPlateNumber(String plateNumber)` | 按车牌查 |

### SysVehicleWalletService  `service/SysVehicleWalletService.java`（+ impl）
继承 `IService<SysVehicleWallet>`；储值车钱包。

| 方法 | 签名 | 功能 |
|---|---|---|
| getWalletByVehicleId | `WalletVO getWalletByVehicleId(Long vehicleId)` | 查钱包 |
| recharge / refund / adjust | `WalletVO recharge(WalletRechargeCmd)` / `refund(WalletRefundCmd)` / `adjust(WalletAdjustCmd)` | 充值/退款/调账 |
| listLogs | `List<WalletLogVO> listLogs(Long vehicleId, Long walletId, String logType)` | 流水列表 |
| pageLogs | `IPage<WalletLogVO> pageLogs(IPage, Long vehicleId, Long walletId, String logType)` | 流水分页 |

### VehicleAuditService  `service/VehicleAuditService.java`（+ impl）
继承 `IService<VehicleAudit>`；车辆审核。

| 方法 | 签名 | 功能 |
|---|---|---|
| submit / process | `VehicleAuditVO submit(VehicleAuditSubmitCmd)` / `process(Long, VehicleAuditProcessCmd)` | 提交/处理 |
| detail / pageList / listByVehicleId | `VehicleAuditVO detail(Long)` / `IPage<VehicleAuditVO> pageList(...)` / `List<VehicleAuditVO> listByVehicleId(Long)` | 查询 |

### VehicleRenewalService  `service/VehicleRenewalService.java`（+ impl）
月卡/固定车续费闭环（发起→订单→支付回调→回写有效期），时间用 `LocalDate` 处理跨月跨年。

| 方法 | 签名 | 功能 |
|---|---|---|
| createRenewalOrder | `RenewalOrderVO createRenewalOrder(Long vehicleId, VehicleRenewalCmd cmd)` | 创建 MONTH_RENEW 订单 |
| previewRenewal | `RenewalPreviewVO previewRenewal(Long vehicleId, int months)` | 预览有效期（不落库） |
| applyRenewalEffect | `RenewalOrderVO applyRenewalEffect(Long orderId, String paySerial)` | 支付成功生效（幂等） |
| applyRenewalByPlate | `RenewalOrderVO applyRenewalByPlate(Long tenantId, Long parkingLotId, String plateNumber, String paySerial)` | 按车牌回调生效（P云 notify） |

### VehicleTypeDecisionService  `service/VehicleTypeDecisionService.java`（+ impl）
**车辆类型判定引擎**——优先级链：黑名单 → 超级车牌 → VIP → 月租(查有效期) → 储值 → 免费 → 临时。

| 方法 | 签名 | 功能 |
|---|---|---|
| decide | `VehicleTypeDecisionVO decide(String plateNumber)` | 按车牌判定（用 TenantContext） |
| decide | `VehicleTypeDecisionVO decide(String plateNumber, Long tenantId)` | 无上下文链路（MQ 入场）判定 |
| decide | `VehicleTypeDecisionVO decide(String plateNumber, Long parkingLotId, Long tenantId)` | 含车场维度（vehicle_list 名单） |
| allowEntry / allowExit | `boolean allowEntry(String)` / `boolean allowExit(String)` | 准入/准出校验 |

---

## 三、领域对象（Entity / DTO / VO）

### Entity（`entity/`）

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| SysVehicle | 车辆主表 | SysVehicleMultiPlate | 一位多车绑定 |
| SysVehicleWallet | 储值车钱包 | SysVehicleWalletLog | 钱包流水 |
| VehicleAudit | 车辆审核记录 | | |

### DTO（`dto/`）

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| VehicleCreateCmd | 车辆创建 | VehicleUpdateCmd | 车辆更新 |
| VehicleMultiPlateBindCmd | 一位多车绑定 | VehicleRenewalCmd | 续费 |
| VehicleAuditSubmitCmd | 审核提交 | VehicleAuditProcessCmd | 审核处理 |
| WalletRechargeCmd | 钱包充值 | WalletRefundCmd | 钱包退款 |
| WalletAdjustCmd | 钱包调账 | | |

### VO（`vo/`）

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| VehicleVO | 车辆视图 | VehicleAuditVO | 审核记录视图 |
| WalletVO | 钱包视图 | WalletLogVO | 钱包流水视图 |
| RenewalOrderVO | 续费订单视图 | RenewalPreviewVO | 续费有效期预览 |
| VehicleTypeDecisionVO | 车辆类型判定结果 | | |

---

## 四、数据层（Mapper，`mapper/`）

| 类名 | 关键自定义查询 |
|---|---|
| SysVehicleMapper | `selectByPlateNumber`、`countByDepartmentId` |
| SysVehicleMultiPlateMapper | `selectByVehicleId`、`selectByPlateNumber`、`countByVehicleId` |
| SysVehicleWalletMapper | `selectByVehicleId` |
| SysVehicleWalletLogMapper | （MyBatis-Plus 基础方法） |
| VehicleAuditMapper | `selectByVehicleId`、`countPendingByVehicleId` |

---

## 五、跨模块依赖与备注

- **续费支付**：`listener/RenewalPaymentListener` 监听支付事件 → `VehicleRenewalService.applyRenewalEffect`。
- **类型判定被调用方**：识别入场链路（booth `RecognitionEventService`、device webhook）调用 `VehicleTypeDecisionService.decide(...)` 决定放行与计费策略。
- **访客审核**：`VisitorApplyController.audit` 复用 `vehicle:update` 权限（见 miniapp 模块）。
