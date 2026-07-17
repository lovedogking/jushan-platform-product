# 设计规格：超时关单后重新计费

> 需求依据：V1.1 §3.1.12（ADMIN-012）、§7.3-3
> 任务包：2-2

## 1. 目标（Goal）

补齐「待支付订单超时关闭后，车辆再次被出口识别时重新计费」链路的缺失部分：**幂等防重**、**原单关联**、**重算审计留痕**。任务包 2-1 已实现 PAID 超期重算；本任务覆盖 PENDING_PAY 超时关单后重算。

## 2. 非目标（Non-Goals）

- 不涉及月卡、固定车位、白名单车辆——它们不生成临停订单，走现有放行路径
- 不修改 MockPaymentService.confirmPay 或 pay 主流程
- 不修改设备通信层（Device Access / MQTT）
- 不修改岗亭端、小程序端
- 不涉及真实支付对接

## 3. 背景（Context）

### 3.1 当前代码基线

```
MockPaymentService.timeoutClose                     ExitService.handleExit
┌─────────────────────────────┐                  ┌────────────────────────────────┐
│ @Scheduled(fixedRate=300s)  │                  │ findActiveRecord               │
│ → 查 expired_at < NOW()     │                  │ → 计费（BillingEngine）          │
│   AND status IN (PENDING_PAY,│                  │ → findReusableOrderForRecord    │
│   PAYING)                   │                  │    查 PRE/PENDING_PAY/PAYING/PAID│
│ → cancelExpiredOrder →      │                  │     PENDING_PAY → 复用不更新金额  │ ← Gap 1
│   CANCELLED                 │                  │     CANCELLED   → 不在范围       │
│ → markRecordTimeout         │                  │ → createOrder (全新，无关联)     │ ← Gap 2
└─────────────────────────────┘                  └────────────────────────────────┘
```

**Gap 1 — PENDING_PAY 复用不重算金额**：当 `findReusableOrderForRecord` 返回已有 PENDING_PAY 订单时，ExitService 直接复用该订单，不按实际停车时长更新金额。如果车辆在出口停留了额外时间再被重新识别，金额不会反映最新停车时长。

**Gap 2 — CANCELLED 后新建不关联**：`findReusableOrderForRecord` 的 IN 子句不包含 CANCELLED，超时关闭的订单返回 null，ExitService 走 `createOrder` 建全新订单，新订单与原 CANCELLED 订单无任何关联关系。运营端无法追溯"这笔新订单是因为超时关单重算的"。

**Gap 3 — 无并发防重**：两个出口识别请求同时到达时，`findReusableOrderForRecord` 都返回 null，各自建单，同一在场记录出现多张有效待支付订单。

**Gap 4 — 无重算审计日志**：`billing_rule_recalc_log` 仅用于规则版本切换（P006），不支持超时重算场景。缺失字段：`original_order_id`、`original_amount_cents`、`new_amount_cents`、`trigger_reason`。

### 3.2 与任务包 2-1 的分工

| 场景 | 触发条件 | 处理方 |
|------|---------|--------|
| 已付 PAID 超期（窗口期过期） | `pay_window_deadline < NOW()` | 任务包 2-1 ✅ `createRecalcOrder` |
| 未付 PENDING_PAY 超时关闭 | `timeoutClose` → CANCELLED | 任务包 2-2（本任务） |
| 已有 PENDING_PAY 重识别 | 相机重复触发 / 并发 | 任务包 2-2（本任务） |

## 4. 架构设计（Proposed Architecture）

### 4.1 出口识别建单双路径

```
handleExit(payload, standardizedPlate)
  │
  ├─ 1. findActiveRecord → record
  ├─ 2. 月卡/固定车位/白名单检查（不变）
  ├─ 3. 计费 feeCents（不变）
  │
  ├─ 4. 窗口期判定（2-1 已有，不变）
  │     findPaidOrderForRecord → windowPass / createRecalcOrder
  │
  ├─ 5. ★ 分布式锁锁住 parking_record_id
  │     RedisDistributedLock("exit:order:lock:{parkingRecordId}", wait=3s, hold=10s)
  │
  └─ 6. 订单复用/新建逻辑（★ 改动点）
        │
        ├─ 查该 record 下所有非终态订单
        │   status IN (PRE_ORDER, PENDING_PAY, PAYING)
        │
        ├─ 存在 PENDING_PAY / PAYING → 【路径 A：更新金额】
        │   (a) 按当前时长重算 feeCents
        │   (b) UPDATE order SET amount_cents=?, payable_amount=?, expired_at=?
        │   (c) INSERT billing_rule_recalc_log (trigger_reason='EXIT_RESCAN', 原金额→新金额)
        │
        ├─ 不存在有效订单，但存在 CANCELLED → 【路径 B：新建+关联】
        │   (a) 取最新一条 CANCELLED 订单作为原订单
        │   (b) 按当前时长重算 feeCents
        │   (c) INSERT new order (recalc_source_order_id=原CANCELLED.id)
        │   (d) INSERT billing_rule_recalc_log (trigger_reason='TIMEOUT_RECALC', 原→新)
        │
        └─ 完全无订单（兼容旧数据）→ createOrder（同现行逻辑）
```

### 4.2 决策依据

| 决策 | 方案 | 理由 |
|------|------|------|
| PENDING_PAY 更新 vs 取消+新建 | 更新金额 | 避免同一在场记录出现多张有效待支付订单；本质还是同一笔出口交易 |
| CANCELLED 后处理 | 新建 + recalc_source_order_id | CANCELLED 是终态，状态机不允许反向流转到 PENDING_PAY |
| 并发保护 | RedisDistributedLock | 遵循项目现有方案，Redis 不可用时降级（记录警告，不阻塞） |
| 重算日志表 | 扩展 billing_rule_recalc_log | 复用现有表，减少新表，语义扩展 |

## 5. 需变更的文件

### 5.1 新增文件

| 文件 | 类型 | 说明 |
|------|------|------|
| `parking-boot/src/main/resources/db/migration/V20260717001__extend_billing_rule_recalc_log.sql` | Flyway 迁移 | 扩展 billing_rule_recalc_log 表，新增 4 列 + 2 索引 |

### 5.2 修改文件

| 文件 | 改动说明 |
|------|---------|
| `parking-system/src/main/java/com/jushan/system/entity/BillingRuleRecalcLog.java` | 新增 4 个字段及其 getter/setter |
| `parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java` | 新增常量 `STATUS_PENDING_PAY` 等已存在，确认无遗漏；可能需要新增 `validOrderStatusesForReuse` 方法 |
| `parking-system/src/main/java/com/jushan/system/mapper/ParkingOrderMapper.java` | 新增查询方法：`findLatestCancelledByRecordId`，`updateAmountForReuse` |
| `parking-system/src/main/java/com/jushan/system/service/ExitService.java` | **核心改动**：handleExit 中建单逻辑收敛到两个路径；加分布式锁；引入 recalc log 写入 |
| `parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java` | 新增 `updatePendingOrderAmount` 方法（路径 A）；新增 `findCancelledOrderForRecord` 查询方法；新增 `insertRecalcLog` 方法 |
| `parking-system/src/main/java/com/jushan/system/service/BillingRuleRecalcLogService.java` | 可能需要新增或扩展现有服务，支持超时重算场景的日志写入 |
| `parking-system/src/main/java/com/jushan/system/constant/ParamKeys.java` | 无需改动（`MOCK_PAYMENT_TIMEOUT_MINUTES` 已定义） |
| `parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java` | timeoutClose 方法加显式 paramResolver 读取注释/校验（可选，小改动） |

## 6. 数据库变更

### 6.1 Flyway 迁移 V20260717001

```sql
-- 扩展 billing_rule_recalc_log 支持超时重算场景
ALTER TABLE billing_rule_recalc_log
    ADD COLUMN original_order_id BIGINT       NULL     COMMENT '原订单 ID（重算来源）' AFTER parking_record_id,
    ADD COLUMN original_amount_cents INT       NULL     COMMENT '原订单金额（分）' AFTER fee_cents,
    ADD COLUMN new_amount_cents    INT        NULL     COMMENT '重新计算金额（分）' AFTER original_amount_cents,
    ADD COLUMN trigger_reason      VARCHAR(64) NULL    COMMENT '重算触发原因：EXIT_RESCAN=出场重识别, TIMEOUT_RECALC=超时关单后重算' AFTER new_amount_cents;

CREATE INDEX idx_original_order_id ON billing_rule_recalc_log(original_order_id);
CREATE INDEX idx_trigger_reason   ON billing_rule_recalc_log(trigger_reason);

-- 现有 rule_version_id、operator_id 对超时场景可为 NULL，修改为允许 NULL
ALTER TABLE billing_rule_recalc_log
    MODIFY COLUMN rule_version_id BIGINT NULL COMMENT '切换后的规则版本 ID（超时重算场景可为 NULL）',
    MODIFY COLUMN operator_id     BIGINT NULL COMMENT '操作人 ID（系统触发可为 NULL）';
```

### 6.2 实体字段映射

| Java 字段 | DB 列 | 类型 | 说明 |
|-----------|-------|------|------|
| `originalOrderId` | `original_order_id` | Long | 路径 A：与当前订单相同；路径 B：原 CANCELLED 订单 ID |
| `originalAmountCents` | `original_amount_cents` | Integer | 原订单金额（分） |
| `newAmountCents` | `new_amount_cents` | Integer | 新计算金额（分） |
| `triggerReason` | `trigger_reason` | VARCHAR(64) | `EXIT_RESCAN` 或 `TIMEOUT_RECALC` |

## 7. 流程描述

### 7.1 超时关单→重算主流程

```
T0  车辆入场 → PRE_ORDER（预订单）
T1  出口识别 → PRE_ORDER 计费 20元 → PENDING_PAY（expired_at = T1+15min）
T2  timeoutClose 扫描（T1+16min）
    → 条件更新：WHERE status IN (PENDING_PAY,PAYING) AND expired_at < NOW()
    → 更新为 CANCELLED
    → order_status_log 记录："支付超时自动关闭"
    → MockPaymentRecord → TIMEOUT
T3  车辆再次被出口相机识别
    → ExitService.handleExit()
    → findActiveRecord → 找到 PARKING record
    → findPaidOrderForRecord → null（无 PAID 订单）
    → 获取 RedisDistributedLock("exit:order:lock:{recordId}")
    → 查该 record 下 PENDING_PAY/PAYING → 无
    → 查该 record 下 CANCELLED → 找到 order_A (id=100, amount=20元)
    → 重新计费（entry=T0, exit=T3）→ 25元
    → createOrderInternal(record, 25元, recalcSourceOrderId=100, remark="超时关单后重算")
    → INSERT billing_rule_recalc_log:
       original_order_id=100, original_amount_cents=2000,
       new_amount_cents=2500, trigger_reason='TIMEOUT_RECALC'
    → 释放锁
    → WebSocket 推送出场通知
T4  车主缴费 25元 → PAID → 开闸 → 离场
```

### 7.2 PENDING_PAY 重识别流程（并发/重复触发）

```
T1  出口识别 → PENDING_PAY order_B（20元）
T2  相机再次触发识别（同一辆车、同一出口）
    → 获取 RedisDistributedLock
    → 查该 record 下 PENDING_PAY → 找到 order_B
    → 当前停车时长已多 3 分钟，重新计费 → 22元
    → UPDATE order_B SET amount_cents=2200, payable_amount=2200, expired_at=NOW()+15min
    → INSERT billing_rule_recalc_log:
       original_order_id=order_B.id, original_amount_cents=2000,
       new_amount_cents=2200, trigger_reason='EXIT_RESCAN'
    → 释放锁
    → 决策：order_B 仍为 PENDING_PAY → pendingPayment 放行决策
```

### 7.3 timeoutClose 定时任务流程

```
每 5 分钟触发
→ 查询：status IN (PENDING_PAY, PAYING) AND expired_at < NOW()
→ 对于每个过期订单：
    → 条件更新：cancelExpiredOrder(orderId) → CANCELLED
    → order_status_log 记录："支付超时自动关闭"
    → markRecordTimeout → MockPaymentRecord → TIMEOUT
```

> **备注**：timeoutClose 无需重新读取参数——expired_at 在 `preparePay` 时已通过 `paramResolver.getInt(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, parkingLotId, 15)` 设置。但为了代码可读性，可在 Javadoc 和日志中显式标注参数来源。

## 8. 测试策略

### 8.1 集成测试场景

| 场景 | 测试步骤 | 期望结果 |
|------|---------|---------|
| **TC-1: 超时关单→重算→缴费** | ① 入场→PRE_ORDER；② 出口识别→PENDING_PAY(20元)；③ 手动将 expired_at 设过去→timeoutClose→CANCELLED；④ 再次出口识别；⑤ 支付新订单 | ④ 生成新 PENDING_PAY（金额按实际时长重算）；⑤recalc_source_order_id=原订单ID；billing_rule_recalc_log 有记录 |
| **TC-2: 并发防重** | 两个线程同时出口识别同一车牌 | 仅产生一张有效 PENDING_PAY 订单；第二个请求复用/更新 |
| **TC-3: PENDING_PAY 重识别更新金额** | ① PENDING_PAY(20元)；② 修改系统时间（模拟额外停车）；③ 再次出口识别 | 订单金额更新为新计费金额；recalc_log 记录 EXIT_RESCAN |
| **TC-4: 月卡/固定车位不进入逻辑** | 月卡车辆出口识别 | 走现有放行路径；不生成订单；不进 recalc 逻辑 |
| **TC-5: 完全无订单兼容** | 旧在场数据（无任何订单）出口识别 | 按现有 createOrder 逻辑建单，不受影响 |
| **TC-6: 多次超时关单** | 订单 A→CANCELLED→重算订单 B→超时→CANCELLED→重算订单 C | 订单 C.recalc_source_order_id=B.id（关联最近一次 CANCELLED）；日志链完整 |

### 8.2 单元测试覆盖

- `ExitServiceTest`：新增路径 A 和路径 B 的测试用例
- `ParkingOrderServiceTest`：`updatePendingOrderAmount`、`findCancelledOrderForRecord`
- 现有 `MockPaymentServiceTest.timeoutClose` 测试保持通过

### 8.3 验证命令

```bash
# 后端编译
mvn clean compile -pl parking-system -am

# 运行测试
mvn test -pl parking-system -am

# 集成测试
mvn test -pl parking-boot -am -Dtest="ExitServiceTest,MockPaymentServiceTest"
```

## 9. 风险与缓解（Risks And Mitigations）

| 风险 | 影响 | 缓解措施 |
|------|------|---------|
| **Redis 不可用时分布式锁降级** | 极小概率下并发请求可能重复建单 | 锁获取失败记录 WARN 日志；并发概率本身极低；事后可通过 billing_rule_recalc_log 审计发现 |
| **billing_rule_recalc_log 表扩展影响现有规则切换功能** | 现有 P006 功能（规则版本切换重算）写入该表时多几个 NULL 列 | 新增列全部 DEFAULT NULL；现有 `BillingRuleRecalcLogService` 写入逻辑不变 |
| **路径 A 更新金额覆盖了用户已看到的金额** | 用户扫码看到 20元，再扫码变成 22元，体验不好 | 这是边界场景（同一辆车在同一次出场中被重复识别），实际极少发生；重算日志可追溯 |
| **CANCELLED 订单可能有多个** | 选取哪个作为原订单 | 取 `created_at` 最新的 CANCELLED 订单（即最近一次超时关闭的） |

## 10. 决策摘要（Decision Summary）

- PENDING_PAY 重复识别：**更新金额**而非新建，避免多张有效待支付订单
- CANCELLED 后重算：**新建订单**并通过 `recalc_source_order_id` 关联原订单
- 并发保护：使用项目现有的 **RedisDistributedLock**，key = `exit:order:lock:{parkingRecordId}`
- 审计留痕：**扩展 billing_rule_recalc_log** 表（新增 original_order_id、original_amount_cents、new_amount_cents、trigger_reason），而非新建表
- timeoutClose：**保持现有定时扫描逻辑**，无需大改（expired_at 已基于 paramResolver 设置）
- 月卡/固定车位/白名单：**不进入该逻辑**，由 ExitService 现有 isFixedSpace 排除
- 运营端展示：在订单详情组件中，当 `recalc_source_order_id` 不为 null 时展示关联信息（在 plan writing 阶段细化）
- 金额单位：**分（int）**，沿用项目规范
- 数据库变更：**Flyway 迁移**，文件命名遵循 `V{yyyyMMddNN}__{description}.sql`
