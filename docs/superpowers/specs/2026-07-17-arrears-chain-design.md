# 设计规格：欠费链路（欠费出场→展示→补缴→再出场拦截）

> 需求依据：V1.1 §7.2-2、§7.2-3、§3.3.9（MINI-009）、附录欠费补缴流程、确认项 28
> 任务包：2-3

## 1. 目标（Goal）

补齐「出场未支付放行→欠费中订单→小程序补缴→再次出场拦截」完整链路。当前 `STATUS_ARREARS`、`allowArrears()`、`payArrears()`、`exit.unpaid_strategy` / `arrears.reexit_strategy` 参数已定义但**均未接入出口流程**。目标是将这些已准备好的基础设施串联成完整的业务闭环。

## 2. 非目标（Non-Goals）

- 不涉及真实支付对接（本期仅模拟支付）
- 不修改设备通信层（Device Access / MQTT）
- 不修改岗亭端 WebSocket 事件定义（现有 `sendRemoteGateAlert` 已覆盖）
- 微信订阅消息推送（任务包 5-3 统一接入，本期站内消息 `mini_message` 即可）
- 岗亭端收费面板的欠费处理 UI（属于岗亭端独立任务）
- 运营端订单中心欠费筛选 UI（仅保证后端接口支持筛选）

## 3. 背景（Context）

### 3.1 当前代码基线

```
ExitService.resolveReleaseDecision          ParkingOrderService
┌──────────────────────────┐              ┌─────────────────────────────┐
│ 当前: 硬编码拦截          │              │ allowArrears() ✅ 已实现     │
│ feeCents==0 → free        │              │ payArrears()   ✅ 已实现     │
│ PAID/COMPLETED → open     │              │ 但均未被 ExitService 调用   │
│ 其他 → pendingPayment()   │ ← Gap       └─────────────────────────────┘
│   → allowExit=false       │
└──────────────────────────┘

ParamKeys / ParamResolver                    MiniUserController
┌──────────────────────────┐              ┌─────────────────────────────┐
│ EXIT_UNPAID_STRATEGY  ✅  │              │ payStatus: UNPAID/PAID/FREE │
│ ARREARS_REEXIT_STRATEGY ✅│              │ ARREARS → UNPAID（丢失标识） │ ← Gap
│ 但未在 ExitService 读取   │ ← Gap       │ 无"待支付"标签页筛选        │ ← Gap
└──────────────────────────┘              └─────────────────────────────┘
```

**Gap 1 — 未支付拦截/放行未参数化**：`resolveReleaseDecision` 硬编码返回 `pendingPayment()`，不读取 `exit.unpaid_strategy` 参数。当策略为 `ALLOW_ARREARS` 时，应开闸放行并将订单转为 ARREARS。

**Gap 2 — 无欠费检测**：再次出场时，ExitService 不查询该车牌是否存在 ARREARS 订单。无法触发 `arrears.reexit_strategy` 策略分支。

**Gap 3 — ARREARS 无支付入口**：`MockPaymentService.confirmPay` 仅接受 `PENDING_PAY` / `PAYING` 状态，ARREARS 订单无法通过模拟支付完成补缴。`payArrears()` 虽是直接状态流转方法，但需前端触发入口。

**Gap 4 — 小程序端欠费不可见**：`MiniParkingRecordVO.toMiniVO` 将 ARREARS 映射为 `UNPAID`，前端无法区分"待支付"和"欠费中"。缺少"待支付"标签页合并展示欠费订单的逻辑。

**Gap 5 — 无欠费放行审计日志**：欠费放行、补缴等敏感操作缺少 `@BusinessLog` 记录和状态流转日志。

### 3.2 与任务包 1-2 的分工

| 基础设施 | 提供方 | 状态 |
|---------|--------|------|
| `STATUS_ARREARS` 常量 | 任务包 1-2 | ✅ 已定义 |
| `OrderStatus` ARREARS 状态机 | 任务包 1-2 | ✅ PENDING_PAY→ARREARS, ARREARS→COMPLETED |
| `allowArrears()` 方法 | 任务包 1-2 | ✅ 已实现但未调用 |
| `payArrears()` 方法 | 任务包 1-2 | ✅ 已实现但未调用 |
| `exit.unpaid_strategy` 参数 | 任务包 1-2 | ✅ ParamKeys 已定义 |
| `arrears.reexit_strategy` 参数 | 任务包 1-2 | ✅ ParamKeys 已定义 |
| **串联上述基础设施为业务闭环** | 任务包 2-3（本任务） | ❌ 待实现 |

## 4. 架构设计（Proposed Architecture）

### 4.1 核心流程总览

```
【一次出场】车辆无 ARREARS 订单，有 PENDING_PAY 订单
  ExitService.resolveReleaseDecision()
    → paramResolver.getString("exit.unpaid_strategy", lotId)
    ├─ BLOCK: allowExit=false（保持现有行为）
    └─ ALLOW_ARREARS:
         allowExit=true → 开闸
         orderService.allowArrears(orderId) → PENDING_PAY→ARREARS
         @BusinessLog("欠费放行")
         mini_message: type=ARREARS_RELEASED

【再次入场】欠费车辆 → EntryService.handleEntry()
  → 正常生成 ParkingRecord + PRE_ORDER（不受限，ARREARS 不影响入场）

【再次出场】ExitService.handleExit() — 新增 arrears 检测分支
  → checkArrearsByPlate(standardizedPlate, lotId)
  ├─ 无 ARREARS 订单 → 正常计费流程（不变）
  └─ 有 ARREARS 订单:
       paramResolver.getString("arrears.reexit_strategy", lotId)
       ├─ MUST_PAY（默认）:
       │    前置: 正常计费得本次 feeCents
       │    → createMergedArrearsOrder(arrearsOrders, currentFeeCents)
       │        SUM(所有 ARREARS.payableAmount) + 本次 feeCents
       │        INSERT PENDING_PAY (amount=合并总额, arrearsOrderIds=JSON)
       │        @BusinessLog("欠费合并计费")
       │    → resolveReleaseDecision 返回 pendingPayment（拦截）
       │    → 前端展示："补缴欠费 ¥X + 本次 ¥Y = ¥Z"
       │    → 用户支付合并订单 → handlePostPay:
       │        遍历 arrearsOrderIds → payArrears(逐条) → COMPLETED
       │        合并订单自身 → COMPLETED
       │        开闸放行
       │        推送补缴成功站内消息
       └─ REMIND_ONLY:
            allowExit=true → 开闸放行
            @BusinessLog("欠费提醒放行")
            mini_message: type=ARREARS_REMIND（提醒补缴）
            ARREARS 订单保持原状

【小程序端】欠费展示 & 补缴
  MiniParkingRecordVO
    → payStatus 新增值: "ARREARS"（前端显示"欠费"标签）
    → orderStatus 字段: 透传原始订单状态
    → 停车记录列表: status IN ('PENDING_PAY','PAYING','ARREARS')
  MiniUserController 不变，接口兼容扩展
  MockPaymentService.confirmPay 扩展接受 ARREARS 状态

【运营端】订单中心
  → 后端 OrderAdminController 查询条件已支持 ARREARS 标签
  → ParkingOrderStatusLog 记录完整流转
```

### 4.2 决策依据

| 决策 | 方案 | 理由 |
|------|------|------|
| MUST_PAY 合并计费 | 创建新合并订单 | 语义清晰：新订单金额 = 欠费 + 本次；原 ARREARS 订单不变（审计完整）；通过 `arrearsOrderIds` JSON 字段关联 |
| 欠费放行日志 | 复用 BusinessLog + StatusLog | 项目已有 `sys_business_log` + `parking_order_status_log` 双日志体系；allowArrears/payArrears 已自动写 StatusLog |
| 小程序欠费展示 | 合并列表 + 欠费标识 | PENDING_PAY 和 ARREARS 本质都是"等待支付"，合并在"待支付"标签下，前端按 payStatus 渲染角标 |
| 合并订单支付后处理 | 逐条 payArrears + 合并订单 COMPLETED | 每条 ARREARS 订单独立状态流转，日志完整；即使部分失败不影响已成功的 |
| 小程序补缴支付入口 | 复用 MockPaymentService.confirmPay | 补缴也是模拟支付，仅状态校验从 PENDING_PAY/PAYING 扩展到 ARREARS |
| 站内消息类型 | 扩展现有 mini_message.type | VARCHAR 字段无需 DDL 变更；新类型 ARREARS_RELEASED / ARREARS_REMIND / ARREARS_PAID |

## 5. 需变更的文件

### 5.1 新增文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `parking-boot/src/main/resources/db/migration/V20260717002__add_arrears_order_ids.sql` | Flyway 迁移 | parking_order 表新增 `arrears_order_ids` TEXT 列 |

### 5.2 修改文件

| 文件 | 改动说明 |
|------|---------|
| `parking-system/src/main/java/com/jushan/system/service/ExitService.java` | **核心改动**：`resolveReleaseDecision` 读取 `exit.unpaid_strategy`；新增 `checkArrearsByPlate`、`createMergedArrearsOrder`；re-exit 分支处理 |
| `parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java` | `confirmPay` 状态校验扩展接受 ARREARS；`handlePostPay` 处理合并订单的关联 ARREARS 订单补缴 |
| `parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java` | 新增 `arrearsOrderIds` 字段（String/JSON，存储关联的欠费订单 ID 列表） |
| `parking-system/src/main/java/com/jushan/system/mapper/ParkingOrderMapper.java` | 新增查询：`findArrearsByPlate`（按车牌+tenant+lotId 查 ARREARS 订单） |
| `parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java` | 新增 `getArrearsOrdersByPlate` 查询方法；确认 `allowArrears` / `payArrears` 的幂等性 |
| `parking-system/src/main/java/com/jushan/platform/modules/miniapp/vo/MiniParkingRecordVO.java` | 新增 `orderStatus` 字段；`payStatus` 扩展 ARREARS 映射 |
| `parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/impl/MiniUserServiceImpl.java` | `toMiniVO` 方法扩展 ARREARS 映射 |
| `parking-system/src/main/java/com/jushan/system/service/MiniMessageService.java` 或相关消息服务 | 新增方法或扩展：发送 ARREARS_RELEASED / ARREARS_REMIND / ARREARS_PAID 消息 |
| `parking-system/src/main/java/com/jushan/system/controller/ExitController.java` 或相关 Controller | `@BusinessLog` 注解添加（若欠费放行逻辑在 Controller 层触发） |

## 6. 数据库变更

### 6.1 Flyway 迁移 V20260717002

```sql
-- parking_order 表新增 arrears_order_ids，存储合并订单关联的欠费订单 ID 列表（JSON 数组）
ALTER TABLE parking_order
    ADD COLUMN arrears_order_ids TEXT NULL COMMENT '关联的欠费订单ID列表（JSON数组），合并计费时记录，如 [101,102,103]' AFTER recalc_source_order_id;
```

### 6.2 字段说明

| Java 字段 | DB 列 | 类型 | 说明 |
|-----------|-------|------|------|
| `arrearsOrderIds` | `arrears_order_ids` | String (TEXT) | JSON 数组格式，如 `"[101,102]"`；仅在合并订单（MUST_PAY 策略创建的新订单）中非空；解析后用于支付完成时批量调用 `payArrears` |

## 7. 流程描述

### 7.1 欠费放行主流程（ALLOW_ARREARS）

```
T0  车辆入场 → ParkingRecord(PARKING) + PRE_ORDER
T1  出口识别 → 计费 20元 → PENDING_PAY
T2  ExitService.resolveReleaseDecision()
    → paramResolver.getString("exit.unpaid_strategy", lotId) = "ALLOW_ARREARS"
    → allowExit = true
    → orderService.allowArrears(orderId, "SYSTEM", null, "未支付欠费放行")
    → PENDING_PAY → ARREARS
    → order_status_log: "允许欠费：未支付欠费放行"
    → sys_business_log: "欠费放行" (module=arrears, 记录车场/车牌/金额)
    → mini_message INSERT: type=ARREARS_RELEASED, content="您的停车订单已转为欠费记录，请在30天内补缴"
    → 开闸放行
    → ParkingRecord → COMPLETED
```

### 7.2 再次入场（不受限）

```
T3  欠费车辆再次入场
    → EntryService.handleEntry()
    → 不检查 ARREARS 订单（按需求：不受限）
    → 正常生成 ParkingRecord(PARKING) + PRE_ORDER
```

### 7.3 再次出场 — MUST_PAY 合并计费

```
T4  欠费车辆再次出场
    → ExitService.handleExit()
    → 计费得本次 feeCents = 15元
    → checkArrearsByPlate(plate, lotId) → 找到 ARREARS order_A (100, 20元)
    → paramResolver.getString("arrears.reexit_strategy", lotId) = "MUST_PAY"
    → createMergedArrearsOrder([order_A], 15元):
        totalAmount = 20元 + 15元 = 35元
        INSERT ParkingOrder:
          status = PENDING_PAY
          amount_cents = 3500
          payable_amount = 3500
          arrears_order_ids = "[100]"
        @BusinessLog("欠费合并计费")
    → resolveReleaseDecision → pendingPayment（拦截）
    → WebSocket 推送: 含 arrears 标识
    → 前端收费面板展示: "欠费 20元 + 本次 15元 = 合计 35元"
    
T5  用户支付 35元
    → MockPaymentService.confirmPay(mergedOrderId)
    → PENDING_PAY → PAYING → PAID
    → handlePostPay:
        JSONArray ids = parse(mergedOrder.arrearsOrderIds) → [100]
        orderService.payArrears(100, "SUPPLEMENTARY_PAY", userId)
          → ARREARS → COMPLETED
          → order_status_log: "补缴完成"
        mergedOrder → COMPLETED
        开闸放行
        mini_message: type=ARREARS_PAID, content="欠费已补缴成功，金额 ¥20.00"
        ParkingRecord → COMPLETED
```

### 7.4 再次出场 — REMIND_ONLY 仅提醒

```
T4' 欠费车辆再次出场
    → checkArrearsByPlate(plate, lotId) → 找到 ARREARS order_A
    → paramResolver.getString("arrears.reexit_strategy", lotId) = "REMIND_ONLY"
    → allowExit = true → 开闸放行
    → @BusinessLog("欠费提醒放行")
    → mini_message INSERT: type=ARREARS_REMIND, content="您有欠费订单未支付，请及时补缴"
    → ARREARS order_A 保持原状
    → ParkingRecord → COMPLETED
    → 欠费订单永久保留，等待后续补缴或下次出场 MUST_PAY 拦截
```

### 7.5 小程序端补缴流程

```
T6  车主打开小程序 → "停车记录" → "待支付"标签页
    → GET /api/v1/mini/parking-records?statuses=PENDING_PAY,PAYING,ARREARS
    → 列表展示:
        order_A: status=ARREARS, payStatus="ARREARS" → 前端渲染"欠费"角标
        order_B: status=PENDING_PAY, payStatus="UNPAID" → 正常待支付
    → 点击 ARREARS 订单 → 进入支付确认页
    → 确认支付 → POST /api/v1/mini/pay/confirm (orderId=100)
    → MockPaymentService.confirmPay:
        校验 order.status = ARREARS ✓（扩展逻辑）
        执行支付 → PAID
        handlePostPay:
          非合并订单 → 直接 payArrears(orderId) → COMPLETED
    → 前端展示支付成功
    → 站内消息推送: type=ARREARS_PAID
```

## 8. 测试策略

### 8.1 集成测试场景

| 编号 | 场景 | 测试步骤 | 期望结果 |
|------|------|---------|---------|
| **TC-1** | ALLOW_ARREARS → ARREARS | ① 入场→出场计费；② exit.unpaid_strategy=ALLOW_ARREARS；③ 出场未支付 | ③ 订单→ARREARS；开闸放行；mini_message ARREARS_RELEASED；business_log 欠费放行 |
| **TC-2** | BLOCK 默认策略 | ① exit.unpaid_strategy=BLOCK；② 出场未支付 | ② 不开闸，订单保持 PENDING_PAY |
| **TC-3** | MUST_PAY 合并计费 | ① 已有 ARREARS 订单(20元)；② 再次入场出场(本次15元)；③ arrears.reexit_strategy=MUST_PAY | ③ 创建新合并订单(35元)；arrearsOrderIds=[原订单ID]；不开闸；business_log 欠费合并计费 |
| **TC-4** | MUST_PAY 支付补缴 | ① 合并订单 PENDING_PAY(35元)；② 支付 | ② 合并订单→COMPLETED；原 ARREARS→COMPLETED；开闸；ARREARS_PAID 消息 |
| **TC-5** | REMIND_ONLY 放行 | ① 已有 ARREARS 订单；② 再次出场；③ arrears.reexit_strategy=REMIND_ONLY | ③ 开闸放行；ARREARS 订单不变；ARREARS_REMIND 消息 |
| **TC-6** | 小程序补缴 | ① ARREARS 订单；② 小程序待支付列表可见；③ 点击支付 | ② payStatus=ARREARS 带欠费标识；③ 支付成功→COMPLETED；ARREARS_PAID 消息 |
| **TC-7** | 多欠费合并 | ① 两笔 ARREARS(20元+30元)；② 再次出场(本次15元)；③ MUST_PAY | ③ 合并订单金额=65元；arrearsOrderIds=[id1,id2]；支付后两笔均 COMPLETED |
| **TC-8** | ARREARS 永久保留 | ① ARREARS 订单；② 尝试删除/取消 | ② 操作被拒绝（欠费订单不可删除不可取消） |
| **TC-9** | 小程序列表 | ① 同时有 PENDING_PAY + ARREARS；② 请求待支付列表 | ② 两条均返回；ARREARS 订单 payStatus="ARREARS"、orderStatus="ARREARS" |
| **TC-10** | 欠费车辆入场不受限 | ① 有 ARREARS 订单；② 再次入场 | ② 正常入场，生成新 ParkingRecord + PRE_ORDER，不报错 |

### 8.2 单元测试覆盖

- `ExitServiceTest`：新增 resolveReleaseDecision 两种策略分支；checkArrearsByPlate；createMergedArrearsOrder；REMIND_ONLY 分支
- `MockPaymentServiceTest`：confirmPay 接受 ARREARS 状态；handlePostPay 合并订单处理
- `MiniUserServiceImplTest`：toMiniVO ARREARS 映射；待支付列表返回 ARREARS 订单
- `ParkingOrderServiceTest`：allowArrears / payArrears 幂等性；findArrearsByPlate 查询

### 8.3 验证命令

```bash
# 后端编译
mvn clean compile -pl parking-system -am

# 运行测试
mvn test -pl parking-system -am

# 集成测试
mvn test -pl parking-boot -am -Dtest="ExitServiceTest,MockPaymentServiceTest,MiniUserServiceImplTest"
```

## 9. 风险与缓解（Risks And Mitigations）

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| **MUST_PAY 合并订单支付后，部分 payArrears 失败** | 合并订单已 COMPLETED，但某条 ARREARS 订单未补缴 | payArrears 逐条调用，单条失败记录 ERROR 日志但不中断；运营端订单中心可人工补处理 |
| **arrearsOrderIds JSON 解析失败** | 合并订单支付后无法关联补缴 | 写入时做格式校验（JSON 数组）；读取时 try-catch 降级，记录 WARN 日志，仅完成合并订单自身 |
| **ALLOW_ARREARS 后车辆未离场即被再次识别** | 订单已是 ARREARS，再次识别可能触发重复 allowArrears | allowArrears 做幂等检查（status 已是 ARREARS → 直接返回成功） |
| **参数热更新** | 车场运营中修改 arrears 策略，已在出口的车辆受新策略影响 | 可接受——读取参数时机为出口识别的实时读取，天然支持热更新 |
| **欠费订单数量过多** | 合并订单 arrearsOrderIds JSON 超长 | TEXT 类型足够（MySQL TEXT 最大 65535 字节）；业务上单个车牌的欠费订单不会超过此限制 |

## 10. 决策摘要（Decision Summary）

- **一次出场未支付处理**：读取 `exit.unpaid_strategy` 参数，BLOCK 拦截 / ALLOW_ARREARS 放行转 ARREARS
- **再次出场欠费检测**：新增 `checkArrearsByPlate` 查询 ARREARS 订单，路由到 MUST_PAY 或 REMIND_ONLY 策略
- **MUST_PAY 合并计费**：创建新 PENDING_PAY 合并订单，金额 = Σ欠费 + 本次费用，`arrearsOrderIds` 存储关联
- **合并订单支付后处理**：逐条 `payArrears()` + 合并订单自身 COMPLETED
- **REMIND_ONLY**：放行 + 站内消息提醒，ARREARS 订单保持
- **小程序欠费展示**：合并到"待支付"列表，payStatus 新增 `ARREARS` 值，前端渲染欠费角标
- **小程序补缴入口**：`MockPaymentService.confirmPay` 状态校验扩展接受 ARREARS
- **欠费放行审计**：`@BusinessLog` + `ParkingOrderStatusLog` 双日志覆盖
- **站内消息**：`mini_message` type 新增 `ARREARS_RELEASED`、`ARREARS_REMIND`、`ARREARS_PAID`
- **数据库变更**：`parking_order` 表新增 `arrears_order_ids` TEXT 列，Flyway 迁移编号 V20260717002
- **金额单位**：分（int），沿用项目规范
