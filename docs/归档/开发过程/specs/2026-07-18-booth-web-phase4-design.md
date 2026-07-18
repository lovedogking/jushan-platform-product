# 岗亭端（booth-web）Phase 4 设计规格说明书

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| **文档名称** | 岗亭端 Phase 4 对齐 V1.1 §3.2 设计规格说明书 |
| **版本号** | V1.0 |
| **编写日期** | 2026-07-18 |
| **对应阶段** | Phase 4：岗亭端对齐 V1.1 |
| **需求依据** | V1.1 §3.2 岗亭端需求；BOOTH-005 通行作业、BOOTH-006 收费处理、BOOTH-007 车辆查询、BOOTH-009 交接班；ADMIN-015 手动开闸记录 |
| **前置依赖** | Phase 1-3 后端 API（进出场、计费引擎、订单、设备管理、车场/车道）已完成 |
| **任务包拆分** | 4-1 通行作业与收费处理对齐 / 4-2 交接班钱箱核对 / 4-3 岗亭布局与查询页 |

---

## 1. 目标

在 Phase 1-3 后端 API 就绪的基础上，对齐 V1.1 对岗亭端（booth-web）的完整功能要求。覆盖三个方向：通行作业与收费处理（手动开闸记录、车牌修正、费用减免）、交接班钱箱核对（应收/实收计算、手工校正、欠费订单交接）、岗亭终端布局与查询页（三段式布局、sessionStorage 登录态、车辆查询、识别失败弹窗优化）。

---

## 2. 非目标（本期不做）

| 项目 | 说明 |
| :--- | :--- |
| 微信/支付宝扫码支付 | 本期仅保留现金支付和免费放行，扫码支付入口隐藏（代码保留注释以备后续启用） |
| 真实支付对接 | 仅使用 MockPaymentService，不连接任何真实支付平台 |
| 优惠券/积分/访客预约 | 不在 V1.2 范围内 |
| 岗亭离线模式 | 岗亭端依赖 WebSocket 实时通信和 HTTP API，不做离线缓存和本地数据库 |
| 视频预览真实流 | 视频区域仅显示占位卡片 "视频接入中"，真实 GB28181→WebRTC 流在 Phase 7-2 实现 |
| 设备直连控制 | 前端不可直接传入 deviceSn 操作设备，必须通过 laneId 查数据库后下发 |
| 月卡/固定车位办理 | 由运营端和小程序端完成，岗亭端仅查看车辆是否为月卡/固定车位 |
| admin-web 交接班管理 | admin-web 端仅做只读查询，不做统计核对和手工校正 |
| 支付宝小程序 | 不做 |

---

## 3. 背景与现状

### 3.1 已有基础

| 组件 | 已有功能 |
| :--- | :--- |
| `device_command_audit` 表 | 记录所有设备命令调用。`command_type=OPEN_GATE` + `source=MANUAL` 已作为手动开闸审计。含 `plate_number`、`fee_cents`、`operator_id`、`operator_name`、`reason`、`status` 字段 |
| `ManualGateRecordAdminController` | 运营端开闸记录分页查询，VO 含 operatorName/operationTime/parkingLotName/laneName/reason/plateNumber/feeCents/commandStatus |
| `ChargePanel.vue` | 收费面板：现金/微信/支付宝支付按钮、免费放行、人工放行。**无费用减免能力**，始终按全额收费 |
| `ManualReleaseModal.vue` | 人工放行弹窗（单通道/批量两模式）。单通道模式填备注开闸；批量模式含 `isCharge`/`amount` 参数。单通道模式**无**是否计费/金额字段 |
| `shift_record` 表 | 含 `fee_amount`/`cash_amount`/`online_amount`/`entry_count`/`exit_count`/`exception_count` 但全部初始化为 0 且从未填充。有 `handover_status`(OPEN/CLOSED)、`handover_to`、`handover_remark` |
| `ShiftRecordServiceImpl.closeShift()` | 仅设置 endTime + handoverStatus=CLOSED，**不做任何金额统计计算** |
| 岗亭监控页 `index.vue` | 顶部车场下拉选择、车道网格、识别事件列表、异常提醒列表、远程开闸弹窗。单页面结构，数据通过 `localStorage` 持久化选择 |
| WebSocket (`booth-web/src/utils/websocket.ts`) | STOMP over WSS，按 `/topic/booth/{parkingLotId}/*` 订阅 5 个主题，指数退避重连 |
| 权限体系 | `gate:manual` 权限码覆盖人工放行+车牌修正+人工减免（仅描述中有，代码中无实际减免逻辑）。`booth:operate` 覆盖开班/交班/开闸操作 |
| `sys_config` | `booth.fee_reduction_threshold_cents` = 50000（500元，单位：分）；`remote_gate.alert_auto_dismiss_seconds` = 10 |
| booth-web 路由 | 仅 `/login`、`/monitor`、`/404` 三条路由，`localStorage` 读 token，白名单仅 login 免鉴权 |

### 3.2 当前差距

1. **手动开闸记录不完整**：单通道模式下 ManualReleaseModal 缺少"是否计费"开关和金额输入，导致无法区分计费开闸与免费开闸（`fee_cents` 在单通道模式下始终为 NULL，未录入计费金额）
2. **无费用减免能力**：ChargePanel.vue 没有费用减免按钮和后端接口，无法对收费金额进行减免调整
3. **交接班无金额统计**：开班/交班只记录时间，`fee_amount`/`cash_amount`/`entry_count`/`exit_count` 始终为 0，无法做钱箱核对
4. **无岗亭交接班前端页面**：仅后端接口存在，booth-web 无交接班 UI
5. **布局不符合 V1.1 要求**：当前为单页面平铺布局，V1.1 要求左侧车场列表 + 右侧上视频/下操作区的三段式布局
6. **Token 存储在 localStorage**：页面关闭后不丢失，不符合岗亭终端安全要求（每次打开应重新登录）
7. **无车辆查询页面**：无在场车辆列表、无历史通行记录查询
8. **识别失败弹窗为模态框**：阻塞操作，应改为右下角非阻塞小弹窗

---

## 4. 设计决策汇总

| # | 决策点 | 结论 | 理由 |
|---|--------|------|------|
| 1 | 手动开闸审计存储 | **扩展现有 `device_command_audit` 表**，不新建表；七元素全部就绪，无需新增列 | 七元素映射：operator→operator_name、time→issued_at、lane→lane_id、reason→reason、plate→plate_number、isCharge→隐式编码（fee_cents IS NOT NULL 表示已计费，NULL 表示未计费）、amount→fee_cents。单表查询性能优于多表 JOIN |
| 2 | 费用减免权限 | 新增独立权限码 `fee:reduce`，分配至 `booth_operator` 角色；前端按权限显/隐减免按钮，后端接口校验 | `gate:manual` 已覆盖手动开闸+车牌修正+减免，语义过重；独立权限码实现最小权限原则 |
| 3 | `shift_record` 列语义重定义 | `fee_amount`→系统应收金额；`cash_amount`→操作员确认实收金额；`online_amount`→差额（应收-实收）；新增 `adjust_reason VARCHAR(200)` | 复用建表已有列，避免 ALTER TABLE DROP+ADD 的破坏性变更；语义映射清晰且向后兼容（现值均为 0） |
| 4 | 欠费订单交接 | 交班时统计本班内产生的欠费订单数量，写入 `shift_record` 新列 `arrears_count`；不进行物理"转移"——下一班通过本班 shiftId 查询关联的欠费订单 | 订单本身有 `created_at` 时间戳和 `updated_at`，交班时刻自然形成时间边界；无需额外关联表 |
| 5 | 交接班计算时机 | **交班时（closeShift）一次性计算并写入**，不在开班过程中实时累加 | 实时累加需要监听每笔订单/通行事件变化，引入复杂性和不一致风险；一次性计算查询本班时间段内的汇总数据，简单可靠且可复验 |
| 6 | Token 存储 | 从 `localStorage` 改为 `sessionStorage` | 关闭浏览器/Tab 即失效，满足岗亭终端安全需求；登录页在每次打开终端时出现 |
| 7 | 布局改造 | 三栏/三段式：左侧固定车场列表 → 右侧上下分区（视频占位 + 操作面板 Tab 切换） | V1.1 明确要求；现有监控面板内容整体迁移到右下操作面板，不丢失功能 |
| 8 | 识别失败弹窗 | 从模态框 `TempPlateAlertModal` 改为右下角 `a-notification` 小弹窗，非阻塞 | 不影响岗亭操作员当前操作；未处理计数以 badge 展示 |
| 9 | 视频预览区 | **占位卡片**，显示"视频接入中，敬请期待"，不做任何 WebRTC/GB28181 对接 | Phase 7-2 实现，本期仅为布局占位 |
| 10 | WebSocket 重订阅 | 切换车场时主动断开当前 WebSocket 连接，重新以新 `parkingLotId` 建立连接并订阅 | 每个车场独立 WebSocket 主题 `/topic/booth/{parkingLotId}/*`，不同车场不能共享连接 |

---

## 5. 数据库设计

### 5.1 shift_record 表扩展

现有列语义不变，新增列 + 语义重定义：

| 列名 | 原语义 | 新语义 | 类型 |
| :--- | :--- | :--- | :--- |
| `fee_amount` | 本班收费金额 | **系统应收金额（元）**：本班期间产生的所有临停订单 `fee_amount` 汇总 | DECIMAL(18,2) |
| `cash_amount` | 现金收费金额 | **操作员确认实收金额（元）**：操作员交班时确认的现金实收（可手工校正） | DECIMAL(18,2) |
| `online_amount` | 线上收费金额 | **差额 = 应收 - 实收**（用于核对差异，不做进一步业务约束） | DECIMAL(18,2) |
| `adjust_reason` | （新增） | 手工校正实收金额的原因说明，最长 200 字符 | VARCHAR(200) |
| `arrears_count` | （新增） | 本班产生的欠费订单数（status=ARREARS 的订单） | INT DEFAULT 0 |
| `handover_order_count` | （新增） | 交接给下一班的未支付/欠费订单数量 | INT DEFAULT 0 |

### 5.2 Flyway 迁移脚本

**文件名**：`V20260719001__phase4_shift_record_extend.sql`

```sql
-- Phase 4: shift_record 交接班扩展
-- 1. 新增 adjust_reason 列
ALTER TABLE shift_record
    ADD COLUMN adjust_reason VARCHAR(200) DEFAULT NULL COMMENT '手工校正实收金额原因' AFTER online_amount;

-- 2. 新增 arrears_count 列
ALTER TABLE shift_record
    ADD COLUMN arrears_count INT NOT NULL DEFAULT 0 COMMENT '本班产生的欠费订单数' AFTER exception_count;

-- 3. 新增 handover_order_count 列
ALTER TABLE shift_record
    ADD COLUMN handover_order_count INT NOT NULL DEFAULT 0 COMMENT '交接给下一班的未支付/欠费订单数' AFTER arrears_count;
```

### 5.3 sys_permission 新增权限

**文件名**：`V20260719002__phase4_fee_reduce_permission.sql`

```sql
-- Phase 4: 新增 fee:reduce 权限
-- 费用减免独立权限码，最小权限原则
INSERT INTO sys_permission (code, name, description)
SELECT 'fee:reduce', '费用减免', '对收费金额进行减免，需要二次确认超阈值操作'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE code = 'fee:reduce');

-- 为 booth_operator 角色授权
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT 'booth_operator', 'fee:reduce'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_code = 'booth_operator' AND permission_code = 'fee:reduce'
);

-- 为 parking_manager 角色授权（停车场管理员也应能减免）
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT 'parking_manager', 'fee:reduce'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_code = 'parking_manager' AND permission_code = 'fee:reduce'
);
```

---

## 6. API 端点设计

### 6.1 费用减免（新）

**端点**：`POST /api/v1/booth/charge/fee-reduction`

**权限**：`@RequirePermission("fee:reduce")`

**请求体**：
```json
{
  "sessionId": 123456,
  "originalFeeCents": 2500,
  "reducedFeeCents": 2000,
  "reductionCents": 500,
  "reason": "VIP客户减免"
}
```

**校验规则**：
- `sessionId` 必须对应状态为 `PENDING_PAYMENT` 的在场记录
- `reducedFeeCents` 必须 >= 0 且 <= `originalFeeCents`
- `reductionCents = originalFeeCents - reducedFeeCents`
- 当 `originalFeeCents > sys_config.booth.fee_reduction_threshold_cents` 时，前端须展示二次确认弹窗（后端不做二次拦截，仅记录审计日志）

**响应**：
```json
{
  "code": 200,
  "data": {
    "sessionId": 123456,
    "originalFeeCents": 2500,
    "reducedFeeCents": 2000,
    "reductionCents": 500,
    "appliedAt": "2026-07-18T14:30:00"
  }
}
```

**审计**：每次减免调用写入 `device_command_audit`（command_type=FEE_REDUCTION, source=MANUAL），记录 operator/plate/reason/originalFeeCents/reducedFeeCents。

### 6.2 手动开闸接口扩展（修改）

**端点**：`POST /api/v1/booth/recognition/manual-open-gate`

**新增参数**：
```json
{
  "laneId": 1,
  "reason": "设备故障",
  "isCharge": true,
  "feeCents": 1500,
  "plateNumber": "京A12345"
}
```

- `isCharge`（boolean）：是否计费。默认 `false`。
- `feeCents`（int）：计费金额（分）。仅 `isCharge=true` 时有意义，默认 0。
- 后端将 `isCharge` + `feeCents` 写入 `device_command_audit` 的 `fee_cents` 列和 `request_payload` JSON。

**批量开闸接口**：已有 `isCharge`/`amount` 参数支持，保持不变。

### 6.3 交接班接口扩展（修改）

#### 6.3.1 交班接口（扩展）

**端点**：`POST /api/v1/shift-records/close`

**新增请求体字段**：
```json
{
  "shiftId": 1,
  "handoverTo": 2,
  "handoverRemark": "夜班交接",
  "confirmedCashAmount": 350.00,
  "adjustReason": "一笔现金未找零"
}
```

- `confirmedCashAmount`（BigDecimal, 可选）：操作员确认的实收金额。不传则使用系统计算的应收金额。
- `adjustReason`（String, 可选，最长200）：实收金额与应收金额不一致时的校正原因。

**后端处理逻辑**（在 `ShiftRecordServiceImpl.closeShift()` 中扩展）：

1. 查询本班时间段（startTime 到 now）内的统计数据：
   - `fee_amount` = SUM(本时间段内创建的 `parking_order.fee_amount`，WHERE `status IN ('PAID', 'COMPLETED')` 且 `pay_method='CASH'`) 
   - `entry_count` = COUNT(本时间段内 `parking_record` WHERE `entry_time BETWEEN startTime AND now`)
   - `exit_count` = COUNT(本时间段内 `exit_record` WHERE `exit_time BETWEEN startTime AND now`)
   - `arrears_count` = COUNT(本时间段内 `parking_order` WHERE `status='ARREARS'`)
   - `exception_count` = COUNT(本时间段内 `monitor_alert` WHERE `severity='CRITICAL'`)
2. `cash_amount` = 若前端传了 `confirmedCashAmount` 则使用之，否则等于 `fee_amount`
3. 若 `confirmedCashAmount != fee_amount`，记录 `adjust_reason` 到数据库
4. `online_amount` = `fee_amount - cash_amount`（差额）
5. `handover_order_count` = COUNT(当前车场下 `parking_order.status IN ('PENDING_PAYMENT', 'ARREARS')`)

#### 6.3.2 当前班次信息（扩展）

**端点**：`GET /api/v1/shift-records/current`

**新增响应字段**（在 `ShiftRecordVO` 中，动态计算不写入数据库）：
```json
{
  "id": 1,
  "startTime": "2026-07-18T08:00:00",
  "feeAmount": 350.00,
  "cashAmount": 350.00,
  "onlineAmount": 0,
  "entryCount": 42,
  "exitCount": 38,
  "arrearsCount": 2,
  "handoverOrderCount": 5,
  "arrearsOrders": [
    { "orderId": 101, "plateNumber": "京B67890", "feeCents": 2500, "createdAt": "..." },
    { "orderId": 102, "plateNumber": "京C11111", "feeCents": 1500, "createdAt": "..." }
  ]
}
```

`arrearsOrders` 列表仅在交班预览时返回（closeShift 接口），当前班次接口不返回，避免过大响应。

### 6.4 在场车辆查询（新）

**端点**：`GET /api/v1/booth/vehicles/present`

**权限**：`@RequirePermission("booth:view")`

**参数**：`parkingLotId`（必填）, `sortBy`（entryTime / duration, 默认 entryTime）, `sortDir`（asc / desc, 默认 desc）, `page`, `size`

**响应**：
```json
{
  "code": 200,
  "data": {
    "records": [
      {
        "plateNumber": "京A12345",
        "entryTime": "2026-07-18T08:30:00",
        "durationMinutes": 360,
        "vehicleType": "TEMPORARY",
        "isMonthlyPass": false,
        "isFixedSpace": false,
        "parkingRecordId": 5001
      }
    ],
    "total": 50
  }
}
```

### 6.5 历史通行记录查询（新）

**端点**：`GET /api/v1/booth/vehicles/history`

**权限**：`@RequirePermission("booth:view")`

**参数**：`parkingLotId`（必填）, `plateNumber`（可选，模糊匹配）, `startTime`, `endTime`, `page`, `size`

**响应**：分页返回 `RecognitionEvent` 列表，包含 plateNumber/entryTime/exitTime/feeAmount/paymentStatus/laneName。

---

## 7. 前端组件树变更

### 7.1 布局重构（任务包 4-3）

**改造文件**：`booth-web/src/views/monitor/index.vue`（可能重命名为 `dashboard/index.vue` 或保持 monitor）

当前：单页面全宽布局，顶部栏 + 主体（左侧车道+右侧事件/告警）
目标：三段式布局：

```
┌─────────────────────────────────────────────────────┐
│ Header: logo | 在线状态 | 当前时间 | 刷新 | 退出    │
├────────────┬────────────────────────────────────────┤
│            │  ┌─────────────────────────────────┐  │
│  车场列表  │  │     视频预览区（占位卡片）       │  │
│  (固定)   │  │     "视频接入中，敬请期待"        │  │
│            │  └─────────────────────────────────┘  │
│ ● 车场A   │  ┌─────────────────────────────────┐  │
│   车场B   │  │ Tab: 通行监控 | 车辆查询 | ...    │  │
│   车场C   │  │                                  │  │
│            │  │  [车道网格|事件列表|异常提醒]    │  │
│            │  └─────────────────────────────────┘  │
└────────────┴────────────────────────────────────────┘
```

**左侧车场列表**（`ParkingLotSidebar.vue`，新建组件）：
- 从 `GET /api/v1/booth/parking-lots` 获取授权车场列表
- 当前选中车场高亮（蓝色背景+左边框）
- 点击切换触发：断开当前 WebSocket → `store.reset()` → `store.loadSnapshot(newLotId)` → 重建 WebSocket 连接
- 显示每个车场的当前在场车辆数 badge

**右上视频预览区**（保留为占位卡片）：
- 固定高度约 300px，灰色背景
- 居中显示"视频接入中，敬请期待"文字 + 空摄像头图标
- Phase 7-2 替换为真实 `<video>` 元素

**右下操作面板**（`MonitorTabs.vue`，新建或重构）：
- Tab 1: 通行监控（迁移现有车道网格+事件列表+异常提醒+收费面板触发逻辑）
- Tab 2: 车辆查询（新增 `VehicleQuery.vue`，见 §7.4）
- Tab 3: 交接班（新增 `ShiftHandover.vue`，见 §7.3）

### 7.2 手动开闸改造（任务包 4-1）

**改造文件**：`booth-web/src/components/ManualReleaseModal.vue`

**单通道模式新增字段**：
- "是否计费" `a-switch` 开关（默认关闭）
- 计费金额 `a-input-number`（开关打开时显示，单位元，转换为分发给后端）
- 车牌号显示（已有，禁用输入框）
- 备注/原因（已改为必填）

```
单通道表单：
┌──────────────────────────────────┐
│ 放行车辆：[京A12345]（disabled） │
│ 是否计费：[开关 ○──────●]       │
│ 计费金额：[__15.00__] 元         │  ← 仅开关打开时显示
│ 放行原因：[________必填________] │
│                      [确认放行]  │
└──────────────────────────────────┘
```

**批量模式**：保持现有 `isCharge`/`amount` 参数支持不变。

**开闸按钮离线禁用**：在 `index.vue` 的 `handleManualOpenGate()` 中，调用前检查 `laneCards` 中对应车道的 `isOffline` 状态，离线时 `message.warning("设备离线，无法操作")` 并 return。

### 7.3 收费面板改造（任务包 4-1）

**改造文件**：`booth-web/src/components/ChargePanel.vue`

**变更点**：

1. **隐藏扫码支付入口**：WeChat/Alipay 的 `<a-radio-button>` 保留代码但用 `v-if="false"` 注释，仅展示"现金"支付方式。添加注释 `<!-- Phase 3: 扫码支付暂不启用 -->`

2. **新增费用减免功能**：
   - 在支付方式卡片下方新增"费用减免"按钮（仅当用户拥有 `fee:reduce` 权限时显示）
   - 点击弹出 `a-modal`：显示原金额、减免金额输入框（`a-input-number`，单位元，最小值 0，最大值=原金额）、减免原因（必填）
   - 确认后调用 `POST /api/v1/booth/charge/fee-reduction`，更新 `currentChargeInfo.feeCents` 和 `feeAmount`
   - 超阈值二次确认：若原金额 > `booth.fee_reduction_threshold_cents`，提交减免前弹出 `a-modal.confirm` 二次确认："减免金额较大（原金额 ¥{original}，减免后 ¥{reduced}），确认提交？"

3. **权限控制**：
   - 从登录时返回的 `permissions` 数组中检查是否包含 `fee:reduce`
   - 前端 store 中新增 `hasPermission(code: string): boolean` 方法
   - 减免按钮 `v-if="store.hasPermission('fee:reduce')"`

4. **收费成功后自动开闸**：保持现有逻辑（submitCharge → manualOpenGate → 刷新车道状态）

### 7.4 交接班页面（任务包 4-2，新建）

**新建文件**：`booth-web/src/views/monitor/ShiftHandover.vue`（作为 MonitorTabs 的 Tab 内容）

**页面结构**：

```
┌──────────────────────────────────────────┐
│  当前班次信息                             │
│  ┌──────────────────────────────────────┐│
│  │ 班次：早班 | 开始：08:00 | 已工作：4h││
│  │ 入场：42 辆 | 出场：38 辆 | 在场：4  ││
│  │ 系统应收：¥350.00                    ││
│  │ 现金实收：[___350.00___] 元  [校正]  ││
│  │ 差额：¥0.00                          ││
│  │ 欠费订单：2 笔（¥40.00）             ││
│  └──────────────────────────────────────┘│
│                                          │
│  [校正实收] → 弹出校正弹窗（输入金额+原因）│
│  [交班确认] → 二次确认弹窗 → 调用close   │
│                                          │
│  ─── 历史交接班记录 ───                   │
│  [表格：时间|班次|操作员|应收|实收|差额]  │
└──────────────────────────────────────────┘
```

**状态管理**：在 `stores/monitor.ts` 中新增 shift 相关状态：
```typescript
const currentShift = ref<ShiftRecordVO | null>(null)
const shiftHistory = ref<ShiftRecordVO[]>([])
const shiftLoading = ref(false)
```

**交互流程**：
1. 页面加载时调用 `GET /api/v1/shift-records/current` 获取当前班次
2. 若无当前班次，显示"开班"按钮和班次类型选择（早/中/晚）
3. 开班后实时显示系统统计（每分钟轮询 current 接口更新入场/出场/应收数据）
4. 操作员可点击"校正实收"修改 `cashAmount`，弹出输入框 + 原因
5. 点击"交班确认"弹出二次确认 Modal，展示汇总数据，确认后调用 `POST /api/v1/shift-records/close`
6. 交班成功后刷新为"已交班"状态，显示"开班"按钮重新开始

**欠费订单交接展示**：
- 交班确认弹窗中展示本班欠费订单列表（plateNumber + feeCents）
- 标记"以下欠费订单将交接至下一班次"

### 7.5 车辆查询页（任务包 4-3，新建）

**新建文件**：`booth-web/src/views/monitor/VehicleQuery.vue`（作为 MonitorTabs 的 Tab 内容）

**页面结构**：

```
┌──────────────────────────────────────────┐
│  Tab: 在场车辆 | 历史记录                 │
│  ┌──────────────────────────────────────┐│
│  │ [在场车辆]                            ││
│  │ 排序：[入场时间 ▼] [降序 ▼]          ││
│  │ ┌──────────────────────────────────┐ ││
│  │ │ 京A12345 | 08:30 | 6h | 临时车   │ ││
│  │ │ 京B67890 | 09:00 | 5h30m | 月卡   │ ││
│  │ │ ...                              │ ││
│  │ └──────────────────────────────────┘ ││
│  └──────────────────────────────────────┘│
│  ┌──────────────────────────────────────┐│
│  │ [历史记录]                            ││
│  │ 车牌：[________] 时间：[起]~[止]     ││
│  │ [查询]                               ││
│  │ ┌──────────────────────────────────┐ ││
│  │ │ 京A12345 | 入场 08:00 | 出场 14:00│││
│  │ │          | 费用 ¥15.00 | 已支付   │ ││
│  │ └──────────────────────────────────┘ ││
│  └──────────────────────────────────────┘│
└──────────────────────────────────────────┘
```

**在场车辆列表**：
- 调用 `GET /api/v1/booth/vehicles/present`（带排序参数）
- 每行显示：车牌号、入场时间、停车时长、车辆类型标签（临时车/月卡车/固定车位）、是否月卡标记
- 排序切换：入场时间倒序（默认）/ 停车时长倒序

**历史通行记录**：
- 调用 `GET /api/v1/booth/vehicles/history`
- 筛选条件：车牌号（模糊匹配）、时间范围
- 每行显示：车牌号、入场时间、出场时间、费用、支付状态标签
- 支持分页

### 7.6 识别失败弹窗改造（任务包 4-3）

**改造文件**：`booth-web/src/views/monitor/index.vue`（移除 `TempPlateAlertModal` 改为 notification）

**改动**：
```typescript
// 原逻辑：onAlert 中 recognitionFailedAlert.value = {...} → TempPlateAlertModal 模态框
// 新逻辑：使用 ant-design-vue 的 notification API

import { notification } from 'ant-design-vue'

function showRecognitionFailedAlert(payload: any) {
  const key = `recognition-failed-${payload.eventId}`
  notification.warning({
    message: '识别失败',
    description: `${payload.laneName} | ${payload.eventTime} | ${payload.message || '车牌未识别'}`,
    placement: 'bottomRight',
    duration: 0, // 不自动关闭，需手动确认
    btn: () => h('a-button', { size: 'small', onClick: () => {
      notification.close(key)
      // 打开无牌车处理抽屉
      tempPlateDrawerOpen.value = true
    }}, '处理'),
    key,
  })
}
```

**未处理计数 badge**：在顶栏"历史通知"按钮旁新增 badge 显示未处理的识别失败数量。`unhandledRecognitionFailedCount` 通过 store 管理。

### 7.7 登录态改造（任务包 4-3）

**改造文件**：
- `booth-web/src/router/index.ts`：`localStorage.getItem(TOKEN_KEY)` → `sessionStorage.getItem(TOKEN_KEY)`
- `booth-web/src/utils/request.ts`：同上
- `booth-web/src/layout/index.vue`：`localStorage.removeItem(TOKEN_KEY)` → `sessionStorage.removeItem(TOKEN_KEY)`
- `booth-web/src/views/monitor/index.vue`：`localStorage.getItem(LOT_ID_KEY)` → `sessionStorage.getItem(LOT_ID_KEY)`；`localStorage.setItem` → `sessionStorage.setItem`

**效果**：关闭浏览器 Tab/窗口后 token 丢失，重新打开必须重新登录。

---

## 8. 后端变更清单

### 8.1 新建文件

| 文件 | 说明 |
| :--- | :--- |
| `V20260719001__phase4_shift_record_extend.sql` | Flyway 迁移：shift_record 表新增 3 列 |
| `V20260719002__phase4_fee_reduce_permission.sql` | Flyway 迁移：新增 fee:reduce 权限 |
| `parking-system/.../controller/BoothVehicleController.java` | 在场车辆/历史通行记录查询控制器 |
| `parking-system/.../service/BoothVehicleService.java` + impl | 车辆查询服务 |
| `parking-system/.../controller/BoothFeeReductionController.java` | 费用减免控制器 |
| `parking-system/.../service/FeeReductionService.java` + impl | 减免服务（含审计日志写入） |

### 8.2 修改文件

| 文件 | 改动内容 |
| :--- | :--- |
| `ShiftRecord.java` | 新增 `adjustReason`、`arrearsCount`、`handoverOrderCount` 字段 |
| `ShiftRecordVO.java` | 新增对应字段 + 欠费订单列表（仅交班时填充） |
| `ShiftRecordServiceImpl.java` | `closeShift()` 中增加统计计算逻辑；`getCurrentShift()` 中增加实时统计查询 |
| `ShiftRecordController.java` | `closeShift` 接口接受 `confirmedCashAmount`、`adjustReason` |
| `ShiftCloseCmd.java` | 新增 `confirmedCashAmount`、`adjustReason` 字段 |
| `RecognitionEventController.java` | `manualOpenGate` 接口接受 `isCharge`、`feeCents`、`plateNumber` |
| `DeviceCommandAudit.java` | 无需改表结构——`fee_cents` 和 `plate_number` 列已存在，`request_payload` 记录完整 JSON |
| `ManualGateRecordAdminVO.java` | 无需改动——已含 plateNumber/feeCents/operatorName 等七元素字段 |
| `BoothMonitorController.java` | 快照接口扩展：返回授权车场列表（含在场车辆数） |
| `BoothParkingLotController.java` | 在返回车场列表响应中新增 `currentVehicles` 字段 |

---

## 9. 前端文件变更清单

### 9.1 新建文件

| 文件 | 说明 |
| :--- | :--- |
| `booth-web/src/views/monitor/ParkingLotSidebar.vue` | 左侧固定车场列表组件 |
| `booth-web/src/views/monitor/MonitorTabs.vue` | 右下操作面板 Tab 容器（通行监控/车辆查询/交接班） |
| `booth-web/src/views/monitor/ShiftHandover.vue` | 交接班页面（当前班次+历史+交班确认） |
| `booth-web/src/views/monitor/VehicleQuery.vue` | 车辆查询页面（在场车辆+历史记录） |
| `booth-web/src/api/shift.ts` | 交接班 API 封装 |
| `booth-web/src/api/vehicle-query.ts` | 车辆查询 API 封装 |

### 9.2 修改文件

| 文件 | 改动内容 |
| :--- | :--- |
| `booth-web/src/views/monitor/index.vue` | **重大重构**：三段式布局；左侧引入 ParkingLotSidebar；右侧上下分区（视频占位卡片 + MonitorTabs）；识别失败弹窗改为 notification；开闸按钮离线禁用检查 |
| `booth-web/src/components/ChargePanel.vue` | 隐藏扫码支付入口；新增费用减免按钮+弹窗（权限控制+超阈值二次确认）；freeCharge→success 流程保持 |
| `booth-web/src/components/ManualReleaseModal.vue` | 单通道模式新增"是否计费"开关+金额输入；原因改为必填 |
| `booth-web/src/stores/monitor.ts` | 新增 `hasPermission()` 方法；新增 shift 相关状态（currentShift/shiftHistory）；新增 selectedLotId 追踪 |
| `booth-web/src/api/monitor-types.ts` | 新增 `ShiftRecordVO`、`VehicleQueryResult`、`FeeReductionRequest` 等类型定义 |
| `booth-web/src/api/charge.ts` | 新增 `submitFeeReduction()` 函数 |
| `booth-web/src/router/index.ts` | Token 存储从 `localStorage` 改为 `sessionStorage` |
| `booth-web/src/utils/request.ts` | Token 读取从 `localStorage` 改为 `sessionStorage` |
| `booth-web/src/layout/index.vue` | Token 清除从 `localStorage` 改为 `sessionStorage` |

---

## 10. 权限与安全

### 10.1 新增权限码

| 权限码 | 名称 | 分配角色 | 控制范围 |
| :--- | :--- | :--- | :--- |
| `fee:reduce` | 费用减免 | `booth_operator`, `parking_manager` | 前端显/隐减免按钮；后端 `@RequirePermission` 拦截未授权调用 |

### 10.2 现有权限码沿用

| 权限码 | 本次使用场景 |
| :--- | :--- |
| `gate:manual` | 手动开闸（单通道/批量）、车牌修正 |
| `booth:operate` | 开班、交班、手动开闸、手动关闸 |
| `booth:view` | 当前班次查询、交接班历史查询、在场车辆查询、历史通行记录查询 |

### 10.3 安全约束

- **费用减免二次确认**：前端 `feeCents > threshold` 时弹出 Modal.confirm；后端记录完整审计日志（操作人、原金额、减免后金额、原因）
- **开闸离线禁用**：前端检查设备在线状态，离线时禁用开闸按钮并提示"设备离线，无法操作"
- **开闸幂等**：重复下发同一指令直接响应（Phase 1 已实现）
- **sessionStorage**：Token 不在 localStorage 持久化，关闭页面即清除
- **车场切换隔离**：切换车场时断开旧 WebSocket、清空 store 数据、重新加载新快照

---

## 11. 测试策略

### 11.1 单元测试

| 测试类 | 测试点 |
| :--- | :--- |
| `ShiftRecordServiceImplTest` | ① closeShift 统计入场/出场/应收/欠费数据正确；② confirmedCashAmount 传参与不传参两种路径；③ adjustReason 在差额不为 0 时强制要求填写（BusinessException） |
| `FeeReductionServiceTest` | ① 减免金额 > 应收金额时抛出 BusinessException；② 减免金额 = 0 时视为免费放行（不抛异常）；③ 审计记录正确写入 device_command_audit |
| `BoothVehicleServiceTest` | ① 在场车辆按车牌+车场过滤；② 排序逻辑（entryTime desc / duration desc）正确；③ 历史记录分页+时间范围过滤 |

### 11.2 集成测试

| 场景 | 验证点 |
| :--- | :--- |
| 手动开闸全字段记录 | 单通道模式下开启"是否计费"+输入金额 → device_command_audit.fee_cents 正确写入；运营端开闸记录页展示 plateNumber/operatorName/laneName/reason/feeCents |
| 费用减免完整链路 | ① 无 `fee:reduce` 权限用户看不到减免按钮；② 直接调用接口返回 403；③ 有权限用户减免 5 元 → 应收从 25 元变为 20 元 → 现金收费 20 元成功 → 开闸放行；④ 超 500 元阈值触发二次确认弹窗 |
| 交接班钱箱核对 | ① 开班→模拟 3 笔现金收费（共 45 元）→ 交班确认 → shift_record.fee_amount=45, cash_amount=45；② 操作员校正实收为 40 → adjust_reason="少收 5 元" → cash_amount=40, online_amount=5 |
| 交接班欠费订单 | 本班产生 2 笔欠费 → 交班确认弹窗展示欠费订单列表 → shift_record.arrears_count=2 → 下一班 current 接口可查到 handover_order_count |
| 车场切换全刷新 | 车场 A → 切换到车场 B → WebSocket 断开重连新 topic → store.lanes/store.events 清空重载 → 车道网格展示车场 B 的车道 |
| Session 登录态 | 登录 → 关闭浏览器 Tab → 重新打开 → 跳转到 /login 页（token 已清除） |
| 识别失败弹窗非阻塞 | WebSocket 推送识别失败 → 右下角 notification 弹出 → 操作员可同时操作其他功能 → 点击"处理"打开无牌车抽屉 |

### 11.3 前端组件测试

| 组件 | 测试点 |
| :--- | :--- |
| `ManualReleaseModal` | 单通道模式"是否计费"开关切换 → 金额输入框显隐；金额输入框 min=0 校验 |
| `ChargePanel` | 微信/支付宝按钮不可见；费用减免按钮权限控制；超阈值二次确认弹窗 |
| `ShiftHandover` | 当前班次数据显示；校正实收交互；交班确认二次弹窗 |
| `VehicleQuery` | 在场车辆排序切换；历史记录筛选查询分页 |
| `ParkingLotSidebar` | 车场列表渲染；当前选中高亮；点击切换触发数据刷新 |

---

## 12. 风险与缓解

### 风险 1：交接班统计查询性能

- **严重程度**：中
- **影响**：`closeShift()` 中对 `parking_order`/`parking_record`/`exit_record` 三表的 COUNT/SUM 查询随数据量增长可能变慢
- **缓解措施**：
  - 查询均限制 `parking_lot_id` + `created_at BETWEEN startTime AND endTime`，利用现有索引（`parking_order` 已有 `idx_lot_time`、`parking_record` 有 `idx_lot_entry`）
  - 单班次通常不超过 8 小时，数据量有限（几百到几千条）
  - 若未来性能退化，可引入 Redis 实时计数器在收费成功/入场/出场时原子递增，交班时直接读取（本期不做）

### 风险 2：费用减免与订单状态一致性

- **严重程度**：中
- **影响**：费用减免修改了 `parking_session.fee_amount` 后，若在减免与收费之间又有出场识别产生新计费，可能导致金额不一致
- **缓解措施**：
  - 减免接口校验 `session.status == PENDING_PAYMENT`，一旦进入支付流程或超时关闭则拒绝减免
  - 减免和收费操作在 `ChargePanel` 中是互斥的（减免后 `currentChargeInfo` 更新，收费使用最新 feeCents）
  - 后端使用 `@Transactional` 保证减免+审计日志写入原子性

### 风险 3：车场切换时 WebSocket 状态竞争

- **严重程度**：低
- **影响**：快速切换车场时，旧 WebSocket 断开和新 WebSocket 连接可能产生竞态，导致短暂的 store 数据混合
- **缓解措施**：
  - 切换车场时先调用 `wsClient.disconnect()` → `store.reset()` → `store.loadSnapshot(newLotId)` → 重建 WebSocket
  - `store.reset()` 同步清空所有 lanes/events/alerts/deviceStatuses
  - `loadSnapshot` 是异步的，在它完成前 WebSocket 不会连接（通过 Promise chain 串行化）

### 风险 4：sessionStorage 与多 Tab

- **严重程度**：低
- **影响**：同一浏览器打开多个岗亭端 Tab 时，sessionStorage 在相同域名下共享，登出一个 Tab 可能导致其他 Tab 也被清除 token
- **缓解措施**：
  - V1.1 需求明确要求"每台岗亭终端打开浏览器即显示登录页"，预期的使用场景是每个物理终端仅打开一个 Tab
  - 不做多 Tab 支持，文档中注明岗亭端预期单 Tab 使用

---

## 13. 决策总结

- 手动开闸审计**扩展 `device_command_audit`**，不新建表；单通道模式新增 `isCharge`/`feeCents` 字段，写入已有列。
- 新增独立权限码 `fee:reduce`，默认分配给 `booth_operator` 和 `parking_manager`；前端按权限显/隐减免按钮，后端 `@RequirePermission` 校验。
- `shift_record` 列语义重定义：`fee_amount`=系统应收，`cash_amount`=操作员确认实收，`online_amount`=差额；新增 `adjust_reason`、`arrears_count`、`handover_order_count` 三列。
- 交接班统计在 `closeShift()` 时一次性计算（查询本班时间范围内的 orders/records），不在开班过程中实时累加。
- Token 从 `localStorage` 迁移到 `sessionStorage`，关闭 Tab 即失效。
- booth-web 监控页重构为三段式布局：左侧车场列表（新建 `ParkingLotSidebar.vue`）+ 右侧上视频占位 + 右下 Tab 操作面板（新建 `MonitorTabs.vue`）。
- 识别失败弹窗从模态框改为右下角 `notification`，非阻塞。
- 扫码支付（微信/支付宝）入口隐藏（代码保留），仅保留现金支付。
- 车辆查询（在场车辆列表 + 历史通行记录）新建独立页面作为 MonitorTabs Tab 内容。
- 交接班页面（当前班次 + 钱箱核对 + 交班确认 + 历史）新建独立页面作为 MonitorTabs Tab 内容。
- 车场切换触发完整数据刷新：断开 WebSocket → reset store → loadSnapshot → 重建 WebSocket。

---

## 14. 验收标准

### 任务包 4-1：通行作业与收费处理

| # | 验收项 | 验收方法 |
|---|--------|----------|
| 1 | 手动开闸单通道模式下，开启"是否计费"并输入金额，`device_command_audit.fee_cents` 正确写入 | 执行开闸 → 查询数据库验证 |
| 2 | 运营端开闸记录页展示完整七要素（操作人/操作时间/车道/原因/车牌/是否计费/金额） | 打开 admin-web 开闸记录页验证 |
| 3 | 无 `fee:reduce` 权限用户不可见费用减免按钮 | 使用 booth_operator_without_fee_reduce 账号验证 |
| 4 | 费用减免超 `booth.fee_reduction_threshold_cents` 时弹出二次确认 | 配置阈值=100分(1元) → 对10元订单减免 → 验证弹窗 |
| 5 | 扫码支付（微信/支付宝）入口在收费面板不可见 | 打开收费面板验证仅显示"现金" |
| 6 | 现金收费→开闸→订单状态变更链路完整 | 完整操作流程验证 |
| 7 | 设备离线时开闸按钮不可点击并提示"设备离线，无法操作" | 模拟设备离线验证 |

### 任务包 4-2：交接班钱箱核对

| # | 验收项 | 验收方法 |
|---|--------|----------|
| 1 | 交班时 `shift_record.fee_amount` 等于本班现金收费订单汇总 | 执行 3 笔收费后交班，数据库验证 |
| 2 | 操作员校正实收金额后 `cash_amount` + `adjust_reason` 正确写入 | 校正后交班，数据库验证 |
| 3 | 差额 `online_amount` = `fee_amount` - `cash_amount` | 数学验证 |
| 4 | 本班欠费订单计入 `arrears_count`，下一班可查询交接订单列表 | 创建 2 笔欠费后交班 → 下一班 current 接口验证 |
| 5 | 交接班历史分页查询正常（按车场/时间/操作员筛选） | API 调用验证 |
| 6 | 岗亭端完整交班流程可用（开班→操作→查看统计→校正→交班确认） | UI 操作验证 |

### 任务包 4-3：岗亭布局与查询页

| # | 验收项 | 验收方法 |
|---|--------|----------|
| 1 | 左侧车场列表正确展示授权车场，当前选中高亮 | 视觉验证 |
| 2 | 切换车场触发全部数据刷新（车道/事件/车位/设备状态） | 切换后检查 UI 数据变化 |
| 3 | 视频预览区显示占位卡片"视频接入中，敬请期待" | 视觉验证 |
| 4 | 右下 Tab 可切换：通行监控/车辆查询/交接班 | UI 操作验证 |
| 5 | 关闭浏览器 Tab 后重新打开 → 显示登录页 | 操作验证 |
| 6 | 在场车辆列表按入场时间倒序/停车时长倒序均可排序 | 切换排序方式验证数据顺序 |
| 7 | 历史通行记录按车牌+时间范围筛选正确 | 输入筛选条件查询验证 |
| 8 | 识别失败弹窗出现在右下角，非阻塞，有未处理计数 badge | WebSocket 推送识别失败验证 |

---

## 附录 A：数据流图——车场切换

```
用户点击车场B
    │
    ▼
wsClient.disconnect()         // 断开当前车场A的WebSocket
    │
    ▼
store.reset()                 // 清空 lanes/events/alerts/deviceStatuses
    │
    ▼
store.loadSnapshot(lotBId)    // GET /api/v1/booth/monitor/snapshot?lotId=B
    │
    ▼
buildWsClient(lotBId)         // 重建 WebSocket，订阅 /topic/booth/{lotBId}/*
    │
    ▼
sessionStorage.setItem('booth_selected_lot_id', lotBId)
```

## 附录 B：数据流图——费用减免

```
操作员点击"费用减免"
    │
    ▼
前端检查 permissions.includes('fee:reduce')
    │ (无权限 → 按钮不显示)
    ▼ (有权限)
弹出减免 Modal（原金额/减免后金额/原因）
    │
    ▼
操作员输入减免金额 + 原因 → 确认
    │
    ▼
原金额 > threshold? ──Yes──→ 二次确认弹窗
    │                        │
    │ No                     │ 确认
    ▼                        ▼
POST /api/v1/booth/charge/fee-reduction
    │
    ▼
后端验证 session.status == PENDING_PAYMENT
后端验证 reducedFeeCents <= originalFeeCents
后端写入 device_command_audit(FEE_REDUCTION)
后端更新 parking_session.fee_amount = reducedFeeCents
    │
    ▼
前端更新 currentChargeInfo.feeCents / feeAmount
收费面板显示减免后金额
```

## 附录 C：数据流图——交接班

```
操作员点击"交班确认"
    │
    ▼
后端 closeShift() 计算本班统计：
  ● SUM(parking_order.fee_amount WHERE pay_method=CASH AND paid_at BETWEEN startTime AND now)
  ● COUNT(parking_record WHERE entry_time BETWEEN startTime AND now)
  ● COUNT(exit_record WHERE exit_time BETWEEN startTime AND now)
  ● COUNT(parking_order WHERE status=ARREARS AND created_at BETWEEN ...)
  ● COUNT(parking_order WHERE status IN (PENDING_PAYMENT, ARREARS))
    │
    ▼
shift_record 写入：
  fee_amount = 应收汇总
  cash_amount = 操作员确认（或等于应收）
  online_amount = 差额
  arrears_count = 欠费订单数
  handover_order_count = 未支付+欠费订单数
  handover_status = CLOSED
  end_time = now()
    │
    ▼
前端展示交班完成状态
下发欠费订单列表给下一班（通过 /current 接口查询上一班的 handover_order_count）
```
