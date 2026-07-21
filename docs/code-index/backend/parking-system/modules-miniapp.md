# 模块：miniapp（小程序端）

> **包路径**：`parking-system/src/main/java/com/jushan/platform/modules/miniapp/`
> **所属**：`parking-system` · `com.jushan.platform.modules.miniapp`
> **职责**：小程序车主端接口——停车记录、余位查询、车牌绑定、支付（预下单/回调）、代理支付、消息通知、访客预约。
> **最近更新**：2026-07-21

---

## 一、接口入口

### MiniUserController  `controller/MiniUserController.java`
- **基础路径**：`/api/v1/mini` ｜ **权限**：`miniapp:view`

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| listParkingRecords | GET | `/parking-records` | 停车记录（tab 筛选） | `current,size,tab` | `R<IPage<MiniParkingRecordVO>>` |
| getParkingRecordDetail | GET | `/parking-records/{id}` | 记录详情 | `id` | `R<MiniParkingRecordVO>` |
| listParkingRecordsByPlate | GET | `/parking-records/plate/{plateNumber}` | 按车牌查记录 | `plateNumber,current,size` | `R<IPage<MiniParkingRecordVO>>` |
| listCurrentSessions | GET | `/current-sessions` | 当前在场 | — | `R<List<MiniParkingRecordVO>>` |
| getParkingLotRemain | GET | `/parking-lot/{parkingLotId}/remain` | 余位 | `parkingLotId` | `R<ParkingSpaceRemainVO>` |
| listBoundPlates | GET | `/bound-plates` | 已绑车牌（旧） | — | `R<List<String>>` |
| listPlates | GET | `/plates` | 车牌列表（含绑定信息） | — | `R<List<PlateBindingVo>>` |
| bindPlate | POST | `/plates` | 绑定车牌 | `BindPlateRequest` | `R<PlateBindingVo>` |
| unbindPlate | DELETE | `/plates/{bindingId}` | 解绑 | `bindingId` | `R<Void>` |
| setDefaultPlate | PUT | `/plates/{bindingId}/default` | 设默认车牌 | `bindingId` | `R<Void>` |
| getCurrentUser | GET | `/user` | 当前用户信息 | — | `R<WxUserVo>` |

### MiniParkingLotController  `controller/MiniParkingLotController.java`
- **基础路径**：`/api/v1/mini/parking-lots` ｜ **权限**：`miniapp:view`

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| listParkingLots | GET | `/` | 停车场列表 | — | `R<List<Map>>` |
| listNearbyParkingLots | GET | `/nearby` | 附近车场 | `lat,lng,...` | `R<List<Map>>` |

### MiniPayController  `controller/MiniPayController.java`
- **基础路径**：`/api/v1/mini/pay` ｜ **权限**：`miniapp:view`

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| preparePay | POST | `/prepare` | 预下单 | `MiniPayPrepareRequest` | `R<MiniPayResultVO>` |
| payNotify | POST | `/notify` | 支付结果通知 | `MiniPayNotifyRequest` | `R<?>` |
| proxyPreview | POST | `/proxy-preview` | 代付预览 | `ProxyPayRequest` | `R<Map>` |
| proxyPay | POST | `/proxy-pay` | 代理支付 | `ProxyPayRequest` | `R<Map>` |

### MiniMessageController  `controller/MiniMessageController.java`
- **基础路径**：`/api/v1/mini/messages` ｜ **权限**：`miniapp:view`

| 方法 | HTTP | 路径 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|
| listMessages | GET | `/` | 消息列表 | `current,size,type` | `R<IPage<Map>>` |
| markAsRead | PUT | `/{id}/read` | 标为已读 | `id` | `R<?>` |
| getUnreadCount | GET | `/unread-count` | 未读数 | — | `R<Map>` |

### VisitorApplyController  `controller/VisitorApplyController.java`
- **基础路径**：`/api/v1/visitor-applies` ｜ **权限**：`miniapp:*` + `vehicle:update`

| 方法 | HTTP | 路径 | 权限 | 说明 | 入参 | 返回 |
|---|---|---|---|---|---|---|
| submit | POST | `/` | `miniapp:operate` | 提交预约 | `VisitorApplyCreateCmd` | `R<VisitorApplyVO>` |
| audit | PUT | `/{id}/audit` | `vehicle:update` | 审核（通过/驳回） | `id, VisitorApplyAuditCmd` | `R<VisitorApplyVO>` |
| cancel | DELETE | `/{id}` | `miniapp:operate` | 取消 | `id` | `R<VisitorApplyVO>` |
| detail | GET | `/{id}` | `miniapp:view` | 详情 | `id` | `R<VisitorApplyVO>` |
| page | GET | `/` | `miniapp:view` | 分页 | `parkingLotId,applyStatus,plateNumber,current,size` | `R<IPage<VisitorApplyVO>>` |
| listByParkingLotId | GET | `/lot/{parkingLotId}` | `miniapp:view` | 按车场列 | `parkingLotId` | `R<List<VisitorApplyVO>>` |
| listMyApplies | GET | `/my` | `miniapp:view` | 我的预约 | — | `R<List<VisitorApplyVO>>` |

---

## 二、Service（均为接口 + impl）

### MiniUserService  `service/MiniUserService.java`

| 方法 | 签名 | 功能 |
|---|---|---|
| listParkingRecords | `IPage<MiniParkingRecordVO> listParkingRecords(long,long,String tab)` | 停车记录（tab=in_progress/pending_pay/completed） |
| listParkingRecordsByPlate | `IPage<MiniParkingRecordVO> listParkingRecordsByPlate(String,long,long)` | 按车牌查 |
| listCurrentSessions | `List<MiniParkingRecordVO> listCurrentSessions()` | 在场车辆 |
| getParkingLotRemain | `ParkingSpaceRemainVO getParkingLotRemain(Long)` | 余位 |
| getParkingRecordDetail | `MiniParkingRecordVO getParkingRecordDetail(Long)` | 记录详情 |
| listBoundPlates | `List<String> listBoundPlates()` | 绑定车牌 |

### MiniMessageService  `service/MiniMessageService.java`

| 方法 | 签名 | 功能 |
|---|---|---|
| createPaySuccessMessage | `MiniMessage createPaySuccessMessage(...)` | 支付成功通知 |
| createArrearsReleasedMessage | `MiniMessage createArrearsReleasedMessage(...)` | 欠费放行通知 |
| createArrearsRemindMessage | `MiniMessage createArrearsRemindMessage(...)` | 欠费提醒 |
| createArrearsPaidMessage | `MiniMessage createArrearsPaidMessage(...)` | 欠费补缴成功 |
| createProxyPaySuccessMessages | `void createProxyPaySuccessMessages(...)` | 代缴通知（双方） |
| findOwnerByPlate | `Long findOwnerByPlate(String plateNumber)` | 按车牌查车主 |

### VisitorApplyService  `service/VisitorApplyService.java`
继承 `IService<VisitorApply>`。

| 方法 | 功能 |
|---|---|
| submit / audit / cancel | 提交/审核/取消 |
| detail / pageList / listByParkingLotId / listByApplicantId | 查询 |

---

## 三、领域对象

| 类型 | 类名 | 作用 |
|---|---|---|
| Entity | MiniMessage | 消息通知 |
| Entity | ProxyPayRecord | 代付记录 |
| Entity | VisitorApply | 访客预约 |
| DTO | MiniPayPrepareRequest / MiniPayNotifyRequest | 支付预下单/回调 |
| DTO | ProxyPayRequest | 代付请求 |
| DTO | VisitorApplyCreateCmd / VisitorApplyAuditCmd | 预约/审核 |
| VO | MiniParkingRecordVO / MiniPayResultVO / VisitorApplyVO | 视图对象 |

---

## 四、Mapper

| 类名 | 关键方法 |
|---|---|
| MiniMessageMapper | （MyBatis-Plus 基础方法） |
| ProxyPayRecordMapper | （MyBatis-Plus 基础方法） |
| VisitorApplyMapper | `selectByParkingLotId`、`selectByApplicantId` |
