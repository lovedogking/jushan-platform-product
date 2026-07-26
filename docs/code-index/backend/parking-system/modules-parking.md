# 模块：parking（停车核心 / 计费）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/parking/`
> **所属**：`parking-system` · `com.jushan.platform.modules.parking`
> **职责**：停车会话（入/出场）、收费规则、费用计算、通行策略、车位管控策略、通道权限、数据分析。
> **最近更新**：2026-07-26（FeeRuleController 解冻启用；fee_rule 新增 vehicleType/plateColor/description；FeeRuleService 拆分为接口+实现；parking_lot 新增 feeRuleId 绑定计费规则；前端新增计费规则管理页 + 车场配置规则选择器）

**说明**：
- 本模块下表格路径均相对上述**包路径**（如 `controller/FeeRuleController.java`）。
- **状态标记**：`FeeRuleController` 已于 2026-07-26 解冻启用；`FeeCalculationController`、`ParkingZoneController` 标注 `@Tag("二期计费体系候选(冻结)")`，属**冻结**能力，改动前先确认是否在启用范围。
- **通道 / 车场档案**：`ParkingLane` / `ParkingLot` 的 Entity/DTO/Mapper/**Controller 全在本模块**——已于 2026-07-24 完成两套 Entity 合并（消重复映射 + 修 ID 策略 bug），旧 `system/entity/ParkingLot.java` / `ParkingLane.java` 标 `@Deprecated`。

---

## 一、接口入口（Controller）

### ParkingSessionController  `controller/ParkingSessionController.java`
- **基础路径**：`/api/v1/parking-sessions` ｜ **权限**：`booth:*` ｜ **状态**：正常
- **功能**：在场车辆的入场、出场、查询与在场统计。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| entry | POST | `/entry` | `booth:operate` | 车辆入场 | `ParkingSessionEntryCmd` | `R<ParkingSessionVO>` |
| exit | POST | `/exit` | `booth:operate` | 车辆出场 | `ParkingSessionExitCmd` | `R<ParkingSessionVO>` |
| detail | GET | `/{id}` | `booth:view` | 在场记录详情 | `id` | `R<ParkingSessionVO>` |
| page | GET | `/` | `booth:view` | 分页查询 | `current,size,parkingLotId,plateNumber,status` | `R<IPage<ParkingSessionVO>>` |
| listInByParkingLotId | GET | `/in/{parkingLotId}` | `booth:view` | 某车场在场列表 | `parkingLotId` | `R<List<ParkingSessionVO>>` |
| getInByPlateNumber | GET | `/in/plate/{plateNumber}` | `booth:view` | 按车牌查在场（岗亭收费入口） | `plateNumber` | `R<ParkingSessionVO>` |
| countInByParkingLotId | GET | `/in/{parkingLotId}/count` | `booth:view` | 在场车辆数 | `parkingLotId` | `R<Long>` |

### FeeRuleController  `controller/FeeRuleController.java`
- **基础路径**：`/api/v1/fee-rules` ｜ **权限**：`fee:*` ｜ **状态**：✅ 正常
- **功能**：收费规则增删改查、复制、状态管理。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `fee:write` | 创建规则 | `FeeRuleCreateCmd` | `R<FeeRuleVO>` |
| update | PUT | `/{id}` | `fee:write` | 更新规则 | `id, FeeRuleUpdateCmd` | `R<FeeRuleVO>` |
| delete | DELETE | `/{id}` | `fee:write` | 软删除 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `fee:read` | 规则详情 | `id` | `R<FeeRuleVO>` |
| page | GET | `/` | `fee:read` | 分页查询 | `current,size,lotId,zoneId,billingMode,status` | `R<IPage<FeeRuleVO>>` |
| copy | POST | `/{id}/copy` | `fee:write` | 复制规则 | `id` | `R<FeeRuleVO>` |
| updateStatus | POST | `/{id}/status` | `fee:write` | 更新状态 | `id, status` | `R<Void>` |

### FeeCalculationController  `controller/FeeCalculationController.java`
- **基础路径**：`/api/v1/fee` ｜ **权限**：`fee:read` ｜ **状态**：⚠️ 冻结（二期计费候选）
- **功能**：停车费用试算。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| calculate | POST | `/calculate` | `fee:read` | 费用试算 | `FeeCalculateCmd` | `R<FeeCalculateResultVO>` |

### ParkingZoneController  `controller/ParkingZoneController.java`
- **基础路径**：`/api/v1/parking-zones` ｜ **权限**：`parking:*` ｜ **状态**：⚠️ 冻结（二期计费候选）
- **功能**：区域增删改查、标签枚举、状态管理。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `parking:write` | 创建区域 | `ParkingZoneCreateCmd` | `R<ParkingZoneVO>` |
| update | PUT | `/{id}` | `parking:write` | 更新区域 | `id, ParkingZoneUpdateCmd` | `R<ParkingZoneVO>` |
| delete | DELETE | `/{id}` | `parking:write` | 软删除 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `parking:read` | 区域详情 | `id` | `R<ParkingZoneVO>` |
| listByLotId | GET | `/by-lot/{lotId}` | `parking:read` | 按车场列区域 | `lotId` | `R<List<ParkingZoneVO>>` |
| page | GET | `/` | `parking:read` | 分页查询 | `current,size,lotId,status` | `R<IPage<ParkingZoneVO>>` |
| getTags | GET | `/tags` | `parking:read` | 区域标签枚举（普通/VIP/员工/装卸/充电） | — | `R<List<Map>>` |
| updateStatus | POST | `/{id}/status` | `parking:write` | 更新状态 | `id, status` | `R<Void>` |

### AccessPolicyController  `controller/AccessPolicyController.java`
- **基础路径**：`/api/v1/access-policies` ｜ **权限**：`parking:*` ｜ **状态**：正常
- **功能**：车辆进出策略（如超时时长等）配置。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `parking:update` | 创建策略 | `AccessPolicyCreateCmd` | `R<AccessPolicyVO>` |
| update | PUT | `/{id}` | `parking:update` | 更新策略 | `id, AccessPolicyCreateCmd` | `R<AccessPolicyVO>` |
| delete | DELETE | `/{id}` | `parking:delete` | 删除策略 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `parking:view` | 策略详情 | `id` | `R<AccessPolicyVO>` |
| page | GET | `/` | `parking:view` | 分页查询 | `current,size,parkingLotId,policyType` | `R<IPage<AccessPolicyVO>>` |
| listByParkingLotId | GET | `/lot/{parkingLotId}` | `parking:view` | 按车场列策略 | `parkingLotId` | `R<List<AccessPolicyVO>>` |
| listByType | GET | `/lot/{parkingLotId}/type/{policyType}` | `parking:view` | 按类型列策略 | `parkingLotId, policyType` | `R<List<AccessPolicyVO>>` |
| getPolicyMap | GET | `/lot/{parkingLotId}/type/{policyType}/map` | `parking:view` | 策略键值映射 | `parkingLotId, policyType` | `R<Map<String,String>>` |

### LanePermissionController  `controller/LanePermissionController.java`
- **基础路径**：`/api/v1/lane-permissions` ｜ **权限**：`lane:*` ｜ **状态**：正常
- **功能**：通道通行权限（按车辆/部门授权）配置。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `lane:update` | 创建权限 | `LanePermissionCreateCmd` | `R<LanePermissionVO>` |
| update | PUT | `/{id}` | `lane:update` | 更新权限 | `id, LanePermissionUpdateCmd` | `R<LanePermissionVO>` |
| delete | DELETE | `/{id}` | `lane:delete` | 删除权限 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `lane:view` | 权限详情 | `id` | `R<LanePermissionVO>` |
| page | GET | `/` | `lane:view` | 分页查询 | `current,size,laneId,targetType,targetId,status` | `R<IPage<LanePermissionVO>>` |
| listByLaneId | GET | `/lane/{laneId}` | `lane:view` | 按通道列权限 | `laneId` | `R<List<LanePermissionVO>>` |
| listByVehicleId | GET | `/vehicle/{vehicleId}` | `lane:view` | 按车辆列权限 | `vehicleId` | `R<List<LanePermissionVO>>` |
| listByDepartmentId | GET | `/department/{departmentId}` | `lane:view` | 按部门列权限 | `departmentId` | `R<List<LanePermissionVO>>` |

### ParkingSpacePolicyController  `controller/ParkingSpacePolicyController.java`
- **基础路径**：`/api/v1/parking-space-policies` ｜ **权限**：`parking:*` ｜ **状态**：正常
- **功能**：车位管控策略配置与余位计算。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| create | POST | `/` | `parking:update` | 创建策略 | `ParkingSpacePolicyCreateCmd` | `R<ParkingSpacePolicyVO>` |
| update | PUT | `/{id}` | `parking:update` | 更新策略 | `id, ParkingSpacePolicyCreateCmd` | `R<ParkingSpacePolicyVO>` |
| delete | DELETE | `/{id}` | `parking:delete` | 删除策略 | `id` | `R<Void>` |
| detail | GET | `/{id}` | `parking:view` | 策略详情 | `id` | `R<ParkingSpacePolicyVO>` |
| page | GET | `/` | `parking:view` | 分页查询 | `current,size,parkingLotId` | `R<IPage<ParkingSpacePolicyVO>>` |
| listByParkingLotId | GET | `/lot/{parkingLotId}` | `parking:view` | 按车场列策略 | `parkingLotId` | `R<List<ParkingSpacePolicyVO>>` |
| getByZoneId | GET | `/zone/{zoneId}` | `parking:view` | 按区域取策略 | `zoneId` | `R<ParkingSpacePolicyVO>` |
| calculateRemain | GET | `/lot/{parkingLotId}/remain` | `parking:view` | 余位计算 | `parkingLotId` | `R<ParkingSpaceRemainVO>` |

### AnalyticsController  `controller/AnalyticsController.java`
- **基础路径**：`/api/v1/admin/analytics` ｜ **权限**：`parking:read` ｜ **状态**：正常
- **功能**：运营数据概览统计。

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| getOverview | GET | `/overview` | `parking:read` | 概览统计（默认 today） | `lotId,period,startDate,endDate` | `R<AnalyticsOverviewVO>` |

---

## 二、业务服务（Service）

### FeeCalculationService  `service/FeeCalculationService.java`
- **类型**：具体类（`@Service`） · **计费核心**
- **功能**：按当前生效规则计算费用；支持 4 种计费模式（按时 / 按次 / 阶梯 / 分时段），支持免费时长、首时段、跨天封顶（自然日 / 连续 24h 窗口）。金额一律整数分。

| 方法 | 签名 | 功能 |
|---|---|---|
| calculate | `FeeCalculateResultVO calculate(Long lotId, Long zoneId, String plateNumber, String vehicleType, LocalDateTime entryTime, LocalDateTime exitTime)` | 试算并返回费用明细（breakdown） |
| calculateFeeCents | `int calculateFeeCents(Long lotId, LocalDateTime entryTime, LocalDateTime exitTime)` | 整数分计费（收敛入口，替代旧 BillingEngine） |
| calculateFeeCents | `int calculateFeeCents(Long lotId, Long zoneId, LocalDateTime entryTime, LocalDateTime exitTime)` | 同上，可指定区域 |

### FeeRuleService（接口）  `service/FeeRuleService.java`
- **实现**：`service/FeeRuleServiceImpl.java`（extends `ServiceImpl<FeeRuleMapper, FeeRule>` implements `FeeRuleService`）
- **功能**：收费规则 CRUD、时段管理、规则复制、生效规则查询。优先级：区域 > 车场 > 平台默认。

| 方法 | 签名 | 功能 |
|---|---|---|
| create | `FeeRuleVO create(FeeRuleCreateCmd cmd)` | 创建（分时段模式校验时段，平台用户从车场反查tenantId） |
| update | `FeeRuleVO update(Long ruleId, FeeRuleUpdateCmd cmd)` | 更新（乐观锁，重建时段） |
| removeById | `boolean removeById(Long ruleId)` | 软删除 + 级联删时段 |
| copy | `FeeRuleVO copy(Long ruleId)` | 复制规则含时段 |
| detail | `FeeRuleVO detail(Long ruleId)` | 详情（含时段列表） |
| pageList | `IPage<FeeRuleVO> pageList(...)` | 分页 |
| listActiveByLotId | `List<FeeRuleVO> listActiveByLotId(Long lotId)` | 按车场列生效规则 |
| getActiveRule | `FeeRuleVO getActiveRule(Long lotId, Long zoneId)` | 取当前生效规则（优先级匹配） |
| updateStatus | `void updateStatus(Long ruleId, Integer status)` | 启用/禁用 |

### ParkingZoneService  `service/ParkingZoneService.java`
- **类型**：具体类，继承 `ServiceImpl<ParkingZoneMapper, ParkingZone>`
- **功能**：区域 CRUD、状态管理、临停车位自动计算（tempSpaces = total − fixed）。禁用区域时联动关联通道置为「维护中」。常量 `STATUS_ENABLED=1` / `STATUS_DISABLED=2`。

| 方法 | 签名 | 功能 |
|---|---|---|
| create | `ParkingZoneVO create(ParkingZoneCreateCmd cmd)` | DTO 入口创建 |
| update | `void update(Long id, ParkingZoneUpdateCmd cmd)` | DTO 入口更新 |
| save | `boolean save(ParkingZone zone)` | 底层保存，自动算 tempSpaces |
| updateById | `boolean updateById(ParkingZone zone)` | 底层更新，重算 tempSpaces |
| removeById | `boolean removeById(Long id)` | 删除（校验无关联通道） |
| detail | `ParkingZoneVO detail(Long id)` | 详情 |
| listByLotId | `List<ParkingZoneVO> listByLotId(Long lotId)` | 按车场列区域 |
| pageList | `IPage<ParkingZoneVO> pageList(long current, long size, Long lotId, Integer status)` | 分页 |
| updateStatus | `void updateStatus(Long id, Integer status)` | 状态变更 + 联动通道 |

### ParkingSessionService  `service/ParkingSessionService.java`（接口） + `service/impl/ParkingSessionServiceImpl.java`
- **类型**：接口 + 实现，接口继承 `IService<ParkingSession>`
- **功能**：在场记录入/出场、查询、在场统计；含 Webhook 无租户场景与出场识别幂等支持。

| 方法 | 签名 | 功能 |
|---|---|---|
| entry | `ParkingSessionVO entry(ParkingSessionEntryCmd cmd)` | 车辆入场 |
| exit | `ParkingSessionVO exit(ParkingSessionExitCmd cmd)` | 车辆出场 |
| detail | `ParkingSessionVO detail(Long id)` | 详情 |
| pageList | `IPage<ParkingSessionVO> pageList(IPage<ParkingSession> page, Long parkingLotId, String plateNumber, String status)` | 分页 |
| listInByParkingLotId | `List<ParkingSessionVO> listInByParkingLotId(Long parkingLotId)` | 在场列表 |
| getInByPlateNumber | `ParkingSessionVO getInByPlateNumber(String plateNumber)` | 按车牌查在场 |
| getInByPlateAndLot | `ParkingSessionVO getInByPlateAndLot(String plateNumber, Long parkingLotId)` | 按车牌+车场查在场 |
| countInByParkingLotId | `long countInByParkingLotId(Long parkingLotId)` | 在场数 |
| countInByParkingLotIdIgnoreTenant | `long countInByParkingLotIdIgnoreTenant(Long parkingLotId)` | 在场数（忽略租户，供 Webhook） |
| getRecentOutByPlateAndLot | `ParkingSessionVO getRecentOutByPlateAndLot(String plateNumber, Long parkingLotId, int withinSeconds)` | 最近出场记录（出场幂等判重） |

### AccessPolicyService  `service/AccessPolicyService.java`（接口） + `service/impl/AccessPolicyServiceImpl.java`
- **类型**：接口 + 实现 ｜ **功能**：进出策略 CRUD 与查询。

| 方法 | 签名 | 功能 |
|---|---|---|
| create / update / delete | `AccessPolicyVO create(AccessPolicyCreateCmd)` / `update(Long, AccessPolicyCreateCmd)` / `void delete(Long)` | 增改删 |
| detail / pageList | `AccessPolicyVO detail(Long)` / `IPage<AccessPolicyVO> pageList(IPage, Long parkingLotId, String policyType)` | 详情 / 分页 |
| listByParkingLotId / listByType | `List<AccessPolicyVO> listByParkingLotId(Long)` / `listByType(Long, String)` | 按车场 / 类型列出 |
| getPolicyMap | `Map<String,String> getPolicyMap(Long parkingLotId, String policyType)` | 策略键值映射 |

### LanePermissionService  `service/LanePermissionService.java`（接口） + `service/impl/LanePermissionServiceImpl.java`
- **类型**：接口 + 实现 ｜ **功能**：通道权限 CRUD 与多维查询。

| 方法 | 签名 | 功能 |
|---|---|---|
| create / update / delete | `LanePermissionVO create(LanePermissionCreateCmd)` / `update(Long, LanePermissionUpdateCmd)` / `void delete(Long)` | 增改删 |
| detail / pageList | `LanePermissionVO detail(Long)` / `IPage<LanePermissionVO> pageList(IPage, Long laneId, String targetType, Long targetId, String status)` | 详情 / 分页 |
| listByLaneId / listByVehicleId / listByDepartmentId | `List<LanePermissionVO> listByLaneId(Long)` / `listByVehicleId(Long)` / `listByDepartmentId(Long)` | 按通道 / 车辆 / 部门列出 |

### ParkingSpacePolicyService  `service/ParkingSpacePolicyService.java`（接口） + `service/impl/ParkingSpacePolicyServiceImpl.java`
- **类型**：接口 + 实现 ｜ **功能**：车位管控策略 CRUD 与余位计算。

| 方法 | 签名 | 功能 |
|---|---|---|
| create / update / delete | `ParkingSpacePolicyVO create(ParkingSpacePolicyCreateCmd)` / `update(Long, ParkingSpacePolicyCreateCmd)` / `void delete(Long)` | 增改删 |
| detail / pageList | `ParkingSpacePolicyVO detail(Long)` / `IPage<ParkingSpacePolicyVO> pageList(IPage, Long parkingLotId)` | 详情 / 分页 |
| listByParkingLotId / getByZoneId | `List<ParkingSpacePolicyVO> listByParkingLotId(Long)` / `ParkingSpacePolicyVO getByZoneId(Long)` | 按车场 / 区域取 |
| calculateRemain | `ParkingSpaceRemainVO calculateRemain(Long parkingLotId)` | 余位计算 |

### AnalyticsService  `service/AnalyticsService.java`（接口） + `service/impl/AnalyticsServiceImpl.java`
- **类型**：接口 + 实现 ｜ **功能**：运营概览统计。

| 方法 | 签名 | 功能 |
|---|---|---|
| getOverview | `AnalyticsOverviewVO getOverview(AnalyticsQueryCmd cmd)` | 概览统计（按 period / 日期范围） |

### ParkingLotService  `service/ParkingLotService.java`（从 system.* 迁入）
- **类型**：具体类（`@Service`） ｜ **功能**：车场 CRUD + 容量/状态审计 + 停用策略。

### ParkingLaneService  `service/ParkingLaneService.java`（从 system.* 迁入）
- **类型**：具体类（`@Service`） ｜ **功能**：通道 CRUD + 潮汐模式 + 相机配置。

---

## 三、领域对象（Entity / DTO / VO）

### Entity（`entity/`）

| 类名 | 路径 | 作用 | 关键字段 / 常量 |
|---|---|---|---|
| FeeRule | `entity/FeeRule.java` | 收费规则 | `billingMode`(1按时/2按次/3阶梯/4分时段)、`vehicleType`(适用车辆类型,逗号分隔)、`plateColor`(适用车牌颜色,逗号分隔)、`description`(规则描述)、`freeMinutes`、`unitMinutes`、`firstPeriodPrice`、`subsequentPrice`、`dailyCap`、`nightCap`、`priority`、`status`(1启/2禁)、`version`<br>注：`firstPeriodMinutes`/`crossDayMode`/`effectMode`/`maxAmount` 为 `@TableField(exist=false)` 预留字段，DB 暂无对应列 |
| FeeRuleSegment | `entity/FeeRuleSegment.java` | 收费规则时段（辅助表） | `feeRuleId`、`startTime`、`endTime`、`unitMinutes`、`unitPrice`、`capAmount`、`sortOrder` |
| ParkingSession | `entity/ParkingSession.java` | 在场车辆记录 | `plateNumber`、`parkingLotId`、`entryTime`、`exitTime`、`status`、`feeAmount` |
| ParkingZone | `entity/ParkingZone.java` | 区域 | `lotId`、`tag`、`level`、`totalSpaces`、`fixedSpaces`、`tempSpaces`、`status`、`version` |
| ParkingLot | `entity/ParkingLot.java` | 停车场档案 | 新增 `feeRuleId`(绑定计费规则) |
| ParkingLane | `entity/ParkingLane.java` | 通道 | `zoneId`、`laneNo`、`status`；管理入口在 `com.jushan.system` |
| AccessPolicy | `entity/AccessPolicy.java` | 进出策略配置 | `parkingLotId`、`policyType`、`policyKey` |
| ParkingSpacePolicy | `entity/ParkingSpacePolicy.java` | 车位管控策略 | `parkingLotId`、`zoneId` |
| LanePermission | `entity/LanePermission.java` | 通道权限配置 | `laneId`、`targetType`、`targetId`、`status` |

### DTO（`dto/`，请求命令）

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| ParkingSessionEntryCmd | 入场命令 | ParkingSessionExitCmd | 出场命令 |
| FeeRuleCreateCmd | 收费规则创建 | FeeRuleUpdateCmd | 收费规则更新 |
| FeeCalculateCmd | 费用试算请求 | AnalyticsQueryCmd | 分析查询 |
| ParkingZoneCreateCmd | 区域创建 | ParkingZoneUpdateCmd | 区域更新 |
| AccessPolicyCreateCmd | 进出策略创建 | ParkingSpacePolicyCreateCmd | 车位策略创建 |
| LanePermissionCreateCmd | 通道权限创建 | LanePermissionUpdateCmd | 通道权限更新 |
| ParkingLaneCreateCmd | 通道创建 | ParkingLaneUpdateCmd | 通道更新 |
| ParkingLotCreateCmd | 车场创建 | ParkingLotUpdateCmd | 车场更新 |

### VO（`vo/`，响应对象）

| 类名 | 作用 | 类名 | 作用 |
|---|---|---|---|
| ParkingSessionVO | 在场记录视图 | FeeCalculateResultVO | 费用计算结果（含 breakdown） |
| FeeRuleVO | 收费规则视图 | FeeRuleSegmentVO | 收费时段视图 |
| ParkingZoneVO | 区域视图 | AnalyticsOverviewVO | 概览统计视图 |
| AccessPolicyVO | 进出策略视图 | ParkingSpacePolicyVO | 车位策略视图 |
| ParkingSpaceRemainVO | 车位余位视图 | LanePermissionVO | 通道权限视图 |
| ParkingLaneVO | 通道视图 | ParkingLotVO | 车场档案视图 |

---

## 四、数据层（Mapper，`mapper/`）

| 类名 | 关键自定义查询 |
|---|---|
| FeeRuleMapper | `selectActiveByLotId(lotId)`、`selectByLotIdAndZoneId(lotId, zoneId)` |
| FeeRuleSegmentMapper | `selectListByFeeRuleId(feeRuleId)` |
| ParkingSessionMapper | `selectInByParkingLotId`、`selectInByPlateNumber`、`countInByParkingLotId`、`selectInByPlateAndLot`、`selectAllInSessions`、`updateExitWithRecord(...)` |
| ParkingZoneMapper | `selectListByLotId(lotId)` |
| ParkingLotMapper | `selectByIdIgnoreTenant`、`selectByNameIgnoreTenant`（跨租户查询，供 Webhook） |
| ParkingLaneMapper | `selectByIdIgnoreTenant`、`selectListByLotIdAndZoneId`、`selectByLaneNo` |
| AccessPolicyMapper | `selectByParkingLotId`、`selectByType`、`selectTimeoutHoursValue` |
| ParkingSpacePolicyMapper | `selectByParkingLotId`、`selectByZoneId` |
| LanePermissionMapper | `selectByLaneId`、`selectByTarget`、`selectByVehicleId`、`selectByDepartmentId` |

---

## 五、从 system.* 迁入的 Controller（2026-07-24）

> 以下 Controller 原属 `com.jushan.system.controller`，已迁入本模块。旧文件标 `@Deprecated`。

| Controller | 基础路径 | 职责 | 接口数 |
|---|---|---|---|
| `ParkingLotController` | `/api/v1/admin/parking-lots` | 车场 CRUD + 容量/状态审计 | 8 |
| `ParkingLaneController` | `/api/v1/admin/parking-lanes` | 通道 CRUD + 潮汐模式 | 6 |
| `ParkingLotParamController` | `/api/v1/admin/parking-lot-params` | 车场级参数配置 | 3 |
| `ParkingOrderController` | `/api/v1/admin/parking-orders` | 停车订单/欠费/退款管理 | 6 |
| `ParkingRecordAdminController` | `/api/v1/admin/parking-records` | 停车记录管理 | 5 |
| `ExceptionRecordAdminController` | `/api/v1/admin/exception-records` | 异常记录管理 | 3 |
| `ManualGateRecordAdminController` | `/api/v1/admin/manual-gate-records` | 人工开闸记录 | 3 |

> 详细接口清单见 `system-controller.md`（待迁移至本文件）

---

## 六、跨模块依赖与备注

- **计费链路**：出场（`ParkingSessionServiceImpl.exit`）→ `FeeCalculationService.calculateFeeCents` → `FeeRuleMapper.selectByLotIdAndZoneId` 取生效规则。改计费算法优先看 `FeeCalculationService`。
- **通道/车场管理入口**：`ParkingLane` / `ParkingLot` 的 Entity/DTO/Mapper/Controller 现已全部在本模块。旧 `system/controller/ParkingLaneController.java` / `ParkingLotController.java` 已标 `@Deprecated`。
- **Webhook 入口**：设备识别经 `com.jushan.platform.modules.device.webhook.DeviceWebhookController` 触发入/出场，走 `*IgnoreTenant` 系列查询（无租户上下文）。
- **冻结能力**：`fee-rules` / `fee` / `parking-zones` 为二期候选（冻结），当前是否启用以运营配置为准。
