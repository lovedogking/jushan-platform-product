# 超时关单重新计费 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐超时关单后出口重识别的幂等防重、原单关联、重算留痕链路。

**Architecture:** ExitService.handleExit 建单逻辑收敛为两条路径——既有 PENDING_PAY 时更新金额，仅 CANCELLED 时新建并关联；加 RedisDistributedLock 防并发；扩展 billing_rule_recalc_log 作为审计载体。

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, MySQL 8, Flyway, Redis（分布式锁）

**Spec:** `docs/superpowers/specs/2026-07-17-timeout-recalc-design.md`

---

### Task 1: Flyway 数据库迁移

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260717001__extend_billing_rule_recalc_log.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
-- 扩展 billing_rule_recalc_log 支持超时重算场景
ALTER TABLE billing_rule_recalc_log
    ADD COLUMN original_order_id      BIGINT       NULL COMMENT '原订单 ID（重算来源）' AFTER parking_record_id,
    ADD COLUMN original_amount_cents  INT          NULL COMMENT '原订单金额（分）' AFTER fee_cents,
    ADD COLUMN new_amount_cents       INT          NULL COMMENT '重新计算金额（分）' AFTER original_amount_cents,
    ADD COLUMN trigger_reason         VARCHAR(64)  NULL COMMENT '重算触发原因：EXIT_RESCAN=出场重识别, TIMEOUT_RECALC=超时关单后重算' AFTER new_amount_cents;

CREATE INDEX idx_recalc_original_order_id ON billing_rule_recalc_log(original_order_id);
CREATE INDEX idx_recalc_trigger_reason   ON billing_rule_recalc_log(trigger_reason);

-- 超时重算场景下无需规则版本和操作人，改为可为 NULL
ALTER TABLE billing_rule_recalc_log
    MODIFY COLUMN rule_version_id BIGINT NULL COMMENT '切换后的规则版本 ID（超时重算场景可为 NULL）',
    MODIFY COLUMN operator_id     BIGINT NULL COMMENT '操作人 ID（系统触发可为 NULL）';
```

- [ ] **Step 2: 编译验证迁移可加载**

```bash
mvn clean compile -pl parking-boot -am
```

预期: BUILD SUCCESS，Flyway 迁移校验通过。

- [ ] **Step 3: 提交**

```bash
git add parking-boot/src/main/resources/db/migration/V20260717001__extend_billing_rule_recalc_log.sql
git commit -m "feat: extend billing_rule_recalc_log for timeout recalc audit (task 2-2)"
```

---

### Task 2: BillingRuleRecalcLog 实体新增字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/BillingRuleRecalcLog.java`

- [ ] **Step 1: 新增字段及 getter/setter**

在 BillingRuleRecalcLog 实体类的 `operatorId` 字段之后（约第 78 行），插入以下字段。以 `edit` 工具在 `private Long operatorId;` 之后、`private LocalDateTime createdAt;` 之前插入：

```java
/** 原订单 ID（重算来源，EXIT_RESCAN 场景下与当前订单相同） */
private Long originalOrderId;

/** 原订单金额（分） */
private Integer originalAmountCents;

/** 重新计算金额（分） */
private Integer newAmountCents;

/** 重算触发原因：EXIT_RESCAN=出场重识别, TIMEOUT_RECALC=超时关单后重算 */
private String triggerReason;
```

在 `getOperatorId` / `setOperatorId` 之后、`getCreatedAt` 之前插入 getter/setter:

```java
public Long getOriginalOrderId() { return originalOrderId; }
public void setOriginalOrderId(Long originalOrderId) { this.originalOrderId = originalOrderId; }

public Integer getOriginalAmountCents() { return originalAmountCents; }
public void setOriginalAmountCents(Integer originalAmountCents) { this.originalAmountCents = originalAmountCents; }

public Integer getNewAmountCents() { return newAmountCents; }
public void setNewAmountCents(Integer newAmountCents) { this.newAmountCents = newAmountCents; }

public String getTriggerReason() { return triggerReason; }
public void setTriggerReason(String triggerReason) { this.triggerReason = triggerReason; }
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/BillingRuleRecalcLog.java
git commit -m "feat: add recalc audit fields to BillingRuleRecalcLog (task 2-2)"
```

---

### Task 3: ParkingOrderMapper 新增查询方法

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/mapper/ParkingOrderMapper.java`

- [ ] **Step 1: 新增两个 Mapper 方法**

在 ParkingOrderMapper 接口末尾（`cancelExpiredOrder` 方法之后，闭合 `}` 之前）新增：

```java
/**
 * 查询停车记录下最近一条 CANCELLED 订单（用于超时重算关联原订单）。
 * 任务包 2-2。
 */
@Select("SELECT * FROM parking_order WHERE parking_record_id = #{parkingRecordId} " +
        "AND status = 'CANCELLED' AND deleted_at IS NULL ORDER BY created_at DESC LIMIT 1")
ParkingOrder selectLatestCancelledByRecordId(@Param("parkingRecordId") Long parkingRecordId);

/**
 * 更新待支付订单的金额和过期时间（出场重识别金额重算）。
 * 条件更新：仅当订单当前状态为 PENDING_PAY 或 PAYING 时更新。
 * 任务包 2-2。
 */
@Update("UPDATE parking_order SET amount_cents = #{amountCents}, payable_amount = #{amountCents}, " +
        "expired_at = #{expiredAt}, updated_at = NOW() " +
        "WHERE id = #{orderId} AND status IN ('PENDING_PAY', 'PAYING') AND deleted_at IS NULL")
int updatePendingOrderAmount(@Param("orderId") Long orderId,
                              @Param("amountCents") Integer amountCents,
                              @Param("expiredAt") LocalDateTime expiredAt);
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/mapper/ParkingOrderMapper.java
git commit -m "feat: add recalc query and update methods to ParkingOrderMapper (task 2-2)"
```

---

### Task 4: ParkingOrderService 新增业务方法

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java`

- [ ] **Step 1: 新增 findCancelledOrderForRecord 方法**

在 `findReusableOrderForRecord` 方法之后（约第 675 行后）插入：

```java
/**
 * 查询停车记录下最近一条 CANCELLED 订单，供超时重算路径使用，任务包 2-2。
 */
public ParkingOrder findCancelledOrderForRecord(Long parkingRecordId) {
    if (parkingRecordId == null) {
        return null;
    }
    return orderMapper.selectLatestCancelledByRecordId(parkingRecordId);
}
```

- [ ] **Step 2: 新增 updatePendingOrderAmount 方法**

在同一区域插入：

```java
/**
 * 更新待支付订单金额（出场重识别金额重算），任务包 2-2。
 * <p>
 * 条件更新：仅 PENDING_PAY/PAYING 状态下生效，返回受影响行数。
 *
 * @param orderId        订单 ID
 * @param newAmountCents 重算后的金额（分）
 * @param newExpiredAt   新的支付过期时间
 * @return true 更新成功
 */
@Transactional(rollbackFor = Exception.class)
public boolean updatePendingOrderAmount(Long orderId, int newAmountCents, LocalDateTime newExpiredAt) {
    int updated = orderMapper.updatePendingOrderAmount(orderId, newAmountCents, newExpiredAt);
    if (updated > 0) {
        ParkingOrder order = orderMapper.selectById(orderId);
        if (order != null) {
            orderStatusLogService.record(order, order.getStatus(), order.getStatus(),
                    currentTriggerSource(), currentOperatorId(), null,
                    "出场重识别金额重算，应付" + newAmountCents + "分");
        }
        log.info("订单金额已重算: orderId={} newAmount={}", orderId, newAmountCents);
    }
    return updated > 0;
}
```

- [ ] **Step 3: 新增 insertRecalcLog 方法**

在同一区域插入。需要先在 ParkingOrderService 中注入 `BillingRuleRecalcLogMapper`：

在类字段声明区域（`private final OrderStatusLogService orderStatusLogService;` 之后，约第 59 行）新增：

```java
private final com.jushan.system.mapper.BillingRuleRecalcLogMapper recalcLogMapper;
```

在构造函数签名和构造函数体中将 `recalcLogMapper` 加入：

构造函数签名和体改为：

```java
public ParkingOrderService(ParkingOrderMapper orderMapper,
                           ParkingRecordMapper recordMapper,
                           OrderStatusLogService orderStatusLogService,
                           com.jushan.system.mapper.BillingRuleRecalcLogMapper recalcLogMapper) {
    this.orderMapper = orderMapper;
    this.recordMapper = recordMapper;
    this.orderStatusLogService = orderStatusLogService;
    this.recalcLogMapper = recalcLogMapper;
}
```

然后在方法区域插入 `insertRecalcLog`：

```java
/**
 * 写入计费重算审计日志，任务包 2-2。
 *
 * @param record             停车记录
 * @param originalOrderId    原订单 ID（EXIT_RESCAN 时与 newOrderId 相同）
 * @param newOrderId         新/更新后的订单 ID
 * @param originalAmountCents 原金额（分）
 * @param newAmountCents      新金额（分）
 * @param triggerReason       触发原因（EXIT_RESCAN / TIMEOUT_RECALC）
 */
public void insertRecalcLog(ParkingRecord record, Long originalOrderId, Long newOrderId,
                             Integer originalAmountCents, Integer newAmountCents,
                             String triggerReason) {
    com.jushan.system.entity.BillingRuleRecalcLog recalcLog =
            new com.jushan.system.entity.BillingRuleRecalcLog();
    recalcLog.setTenantId(record.getTenantId());
    recalcLog.setParkingLotId(record.getParkingLotId());
    recalcLog.setParkingRecordId(record.getId());
    recalcLog.setPlateNumber(record.getStandardizedPlate());
    recalcLog.setOriginalOrderId(originalOrderId);
    recalcLog.setFeeCents(newAmountCents);
    recalcLog.setOriginalAmountCents(originalAmountCents);
    recalcLog.setNewAmountCents(newAmountCents);
    recalcLog.setTriggerReason(triggerReason);
    recalcLog.setRecalcTime(java.time.LocalDateTime.now());
    recalcLog.setCreatedAt(java.time.LocalDateTime.now());
    recalcLogMapper.insert(recalcLog);
    log.info("重算审计日志已写入: recordId={} originalOrderId={} newOrderId={} amount {}→{} reason={}",
            record.getId(), originalOrderId, newOrderId, originalAmountCents, newAmountCents, triggerReason);
}
```

- [ ] **Step 4: createOrderInternal 可见性从 private 改为 public**

定位 `ParkingOrderService.java` 约第 126 行，将:

```java
    private ParkingOrder createOrderInternal(ParkingRecord record, int feeCents,
```

改为:

```java
    public ParkingOrder createOrderInternal(ParkingRecord record, int feeCents,
```

说明: ExitService 路径 B（超时关单后重算）需要通过 `createOrderInternal` 直接建单（指定 recalcSourceOrderId 和 remark），所以需要改为 public。

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java
git commit -m "feat: add recalc service methods and expose createOrderInternal (task 2-2)"
```

---

### Task 5: ExitService 建单逻辑收敛（核心改动）

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ExitService.java`

- [ ] **Step 1: 新增依赖注入**

在 ExitService 类字段声明区域，新增 `DistributedLock`、`BillingRuleRecalcLogMapper`、`ParkingOrderService`（已有）引用。

在 `private final DeviceService deviceService;` 之后（约第 64 行）新增：

```java
private final com.jushan.framework.lock.DistributedLock distributedLock;
private final com.jushan.system.mapper.BillingRuleRecalcLogMapper recalcLogMapper;
```

在构造函数签名中添加参数：

构造函数声明从（约第 66-70 行）：

```java
public ExitService(ParkingRecordMapper recordMapper,
                    ExitRecordMapper exitRecordMapper,
                    ParkingOrderService parkingOrderService,
                    ParkingLotMapper parkingLotMapper,
                    BillingEngine billingEngine,
                    BoothWebSocketPublisher boothWebSocketPublisher,
                    ParkingSessionService parkingSessionService,
                    PrepaidDeductionService prepaidDeductionService,
                    FixedSpaceService fixedSpaceService,
                    DeviceService deviceService) {
```

改为：

```java
public ExitService(ParkingRecordMapper recordMapper,
                    ExitRecordMapper exitRecordMapper,
                    ParkingOrderService parkingOrderService,
                    ParkingLotMapper parkingLotMapper,
                    BillingEngine billingEngine,
                    BoothWebSocketPublisher boothWebSocketPublisher,
                    ParkingSessionService parkingSessionService,
                    PrepaidDeductionService prepaidDeductionService,
                    FixedSpaceService fixedSpaceService,
                    DeviceService deviceService,
                    com.jushan.framework.lock.DistributedLock distributedLock,
                    com.jushan.system.mapper.BillingRuleRecalcLogMapper recalcLogMapper) {
```

构造函数体中新增赋值：

```java
this.distributedLock = distributedLock;
this.recalcLogMapper = recalcLogMapper;
```

文件头部新增 import：

```java
import java.util.concurrent.TimeUnit;
import com.jushan.framework.lock.DistributedLock;
import com.jushan.system.mapper.BillingRuleRecalcLogMapper;
import com.jushan.system.entity.BillingRuleRecalcLog;
```

### Task 4.5: 更新 ExitServiceTest 构造函数以匹配新参数

**Files:**
- Modify: `parking-boot/src/test/java/com/jushan/boot/service/ExitServiceTest.java`

- [ ] **Step 1: 新增 @Mock 字段**

在 ExitServiceTest 类中已有的 `@Mock private DeviceService deviceService;` 之后新增:

```java
    @Mock
    private com.jushan.framework.lock.DistributedLock distributedLock;

    @Mock
    private com.jushan.system.mapper.BillingRuleRecalcLogMapper recalcLogMapper;
```

- [ ] **Step 2: 更新 setUp() 中的 ExitService 构造**

将 `setUp()` 方法中的构造调用从:

```java
        exitService = new ExitService(recordMapper, exitRecordMapper, parkingOrderService,
                parkingLotMapper, billingEngine, boothWebSocketPublisher, parkingSessionService,
                prepaidDeductionService, fixedSpaceService, deviceService);
```

改为:

```java
        exitService = new ExitService(recordMapper, exitRecordMapper, parkingOrderService,
                parkingLotMapper, billingEngine, boothWebSocketPublisher, parkingSessionService,
                prepaidDeductionService, fixedSpaceService, deviceService,
                distributedLock, recalcLogMapper);
```

- [ ] **Step 3: 编译验证**

```bash
mvn test-compile -pl parking-boot -am
```

预期: BUILD SUCCESS（新增的 Mock 不会影响现有测试）

- [ ] **Step 4: 提交**

```bash
git add parking-boot/src/test/java/com/jushan/boot/service/ExitServiceTest.java
git commit -m "test: add DistributedLock and RecalcLogMapper mocks to ExitServiceTest (task 2-2)"
```

---

### Task 5: ExitService 建单逻辑收敛（核心改动）

- [ ] **Step 2: 重构建单逻辑 — 替换 lines 167-188**

**定位**: `ExitService.java` 中的建单逻辑块（从 `ParkingOrder existing = parkingOrderService.findReusableOrderForRecord(record.getId());` 到该 if-else 块结束，约 lines 167-188）。

**旧代码** (lines 167-188):

```java
                ParkingOrder existing = parkingOrderService.findReusableOrderForRecord(record.getId());
                if (existing == null) {
                    // 兼容期：旧在场记录无预订单，保持原出场建单逻辑
                    order = parkingOrderService.createOrder(record, feeCents, null,
                            ParkingOrder.PAY_SCENE_AT_EXIT);
                } else if (ParkingOrder.STATUS_PRE_ORDER.equals(existing.getStatus())) {
                    if (feeCents <= 0) {
                        // 免费放行：预订单直接完成（PRE_ORDER → COMPLETED）
                        parkingOrderService.preOrderToCompleted(existing.getId(), exitTime);
                    } else {
                        // 出场计费：预订单 → 待支付（PRE_ORDER → PENDING_PAY），场景 AT_EXIT
                        parkingOrderService.preOrderToPending(existing.getId(), feeCents,
                                LocalDateTime.now().plusMinutes(15),
                                ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId());
                    }
                    order = parkingOrderService.getById(existing.getId());
                } else {
                    // 提前缴费/岗亭等已建订单（待支付/支付中），直接复用，避免重复建单
                    order = existing;
                }
```

**新代码**:

```java
                // 任务包 2-2：建单逻辑收敛——加分布式锁防重复建单，PENDING_PAY 更新金额、
                // CANCELLED 新建并关联原订单。
                String lockKey = "exit:order:lock:" + record.getId();
                boolean locked = distributedLock.tryLock(lockKey, 3, 10, TimeUnit.SECONDS);
                if (!locked) {
                    log.warn("获取出口分布式锁失败，降级处理: recordId={}", record.getId());
                }
                try {
                    ParkingOrder existing = parkingOrderService.findReusableOrderForRecord(record.getId());

                    // 路径 A：存在有效待支付/支付中订单 → 按当前时长重算金额并更新
                    if (existing != null && (ParkingOrder.STATUS_PENDING_PAY.equals(existing.getStatus())
                            || ParkingOrder.STATUS_PAYING.equals(existing.getStatus()))) {
                        int originalAmount = existing.getAmountCents() != null ? existing.getAmountCents() : 0;
                        // 以实际停车时长重新计费
                        int recalcFeeCents;
                        if (record.getRuleSnapshot() != null && !record.getRuleSnapshot().isBlank()) {
                            try {
                                recalcFeeCents = billingEngine.calculateFeeFromSnapshot(
                                        record.getRuleSnapshot(), record.getEntryTime(), exitTime);
                            } catch (com.jushan.common.BusinessException e) {
                                recalcFeeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), exitTime);
                            }
                        } else {
                            recalcFeeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), exitTime);
                        }
                        parkingOrderService.updatePendingOrderAmount(existing.getId(), recalcFeeCents,
                                LocalDateTime.now().plusMinutes(15));
                        parkingOrderService.insertRecalcLog(record, existing.getId(), existing.getId(),
                                originalAmount, recalcFeeCents, "EXIT_RESCAN");
                        order = parkingOrderService.getById(existing.getId());
                        log.info("出场重识别：更新已有 PENDING_PAY 订单金额 {}→{} orderId={}",
                                originalAmount, recalcFeeCents, existing.getId());
                    }

                    // 路径 B：存在 CANCELLED 历史订单，无有效待支付 → 新建并关联
                    else if (existing == null) {
                        ParkingOrder cancelled = parkingOrderService.findCancelledOrderForRecord(record.getId());
                        if (cancelled != null) {
                            int cancelledAmount = cancelled.getAmountCents() != null ? cancelled.getAmountCents() : 0;
                            // 以实际停车时长重新计费
                            int recalcFeeCents;
                            if (record.getRuleSnapshot() != null && !record.getRuleSnapshot().isBlank()) {
                                try {
                                    recalcFeeCents = billingEngine.calculateFeeFromSnapshot(
                                            record.getRuleSnapshot(), record.getEntryTime(), exitTime);
                                } catch (com.jushan.common.BusinessException e) {
                                    recalcFeeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), exitTime);
                                }
                            } else {
                                recalcFeeCents = billingEngine.calculateFee(parkingLotId, record.getEntryTime(), exitTime);
                            }
                            order = parkingOrderService.createOrderInternal(record, recalcFeeCents, null,
                                    ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId(), cancelled.getId(),
                                    "超时关单后重算，原订单:" + cancelled.getId());
                            parkingOrderService.insertRecalcLog(record, cancelled.getId(), order.getId(),
                                    cancelledAmount, recalcFeeCents, "TIMEOUT_RECALC");
                            log.info("超时关单后重算：原订单 {} cancelledAmount={} 新订单 {} newAmount={}",
                                    cancelled.getId(), cancelledAmount, order.getId(), recalcFeeCents);
                        } else {
                            // 兼容期：旧在场记录无任何订单
                            order = parkingOrderService.createOrder(record, feeCents, null,
                                    ParkingOrder.PAY_SCENE_AT_EXIT);
                        }
                    }

                    // PRE_ORDER 路径（不变）
                    else if (ParkingOrder.STATUS_PRE_ORDER.equals(existing.getStatus())) {
                        if (feeCents <= 0) {
                            parkingOrderService.preOrderToCompleted(existing.getId(), exitTime);
                        } else {
                            parkingOrderService.preOrderToPending(existing.getId(), feeCents,
                                    LocalDateTime.now().plusMinutes(15),
                                    ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId());
                        }
                        order = parkingOrderService.getById(existing.getId());
                    }
                } finally {
                    if (locked) {
                        distributedLock.unlock(lockKey);
                    }
                }
```

> `createOrderInternal` 可见性已在 Task 4 Step 4 中改为 public。

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/ExitService.java parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java
git commit -m "feat: converge exit order creation with recalc and idempotency (task 2-2)"
```

---

### Task 6: MockPaymentService Javadoc 更新（微小改动）

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java`

- [ ] **Step 1: timeoutClose Javadoc 完善**

定位 `timeoutClose` 方法（约第 314 行），将其 Javadoc 从：

```java
    /**
     * 每5分钟扫描一次超时订单。
     * <p>
     * 查找所有待支付（PENDING_PAY/PAYING）且已超过过期时间的订单，
     * 将其标记为 CANCELLED，并将关联的模拟支付记录标记为 TIMEOUT。
     */
```

改为：

```java
    /**
     * 每 5 分钟扫描一次超时订单（任务包 2-2）。
     * <p>
     * 查找所有待支付（PENDING_PAY/PAYING）且已超过过期时间的订单，
     * 将其标记为 CANCELLED，并将关联的模拟支付记录标记为 TIMEOUT。
     * <p>
     * <strong>超时阈值来源</strong>：订单的 {@code expired_at} 在 {@link #preparePay(ParkingOrder)}
     * 时通过 {@code paramResolver.getInt(ParamKeys.MOCK_PAYMENT_TIMEOUT_MINUTES, parkingLotId, 15)}
     * 设置（车场级参数权威源，任务包 1-1）。
     * 超时关闭的订单将在车辆再次出口识别时由 {@link ExitService#handleExit} 重新计费，
     * 新订单通过 {@code recalc_source_order_id} 关联原 CANCELLED 订单。
     */
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java
git commit -m "docs: clarify timeoutClose param authority and recalc linkage (task 2-2)"
```

---

### Task 7: 集成测试

**Files:**
- Modify: `parking-boot/src/test/java/com/jushan/boot/service/ExitServiceTest.java`

**前置**: Task 4.5 已完成 ExitServiceTest 的 @Mock 注入和 setUp 更新。

- [ ] **Step 1: 新增测试用例 TC-1 — 超时关单→重算→关联原订单**

在 ExitServiceTest 末尾（最后一个 `}` 之前）新增测试方法。测试使用项目现有的 `activeRecord()` 和 `exitPayload()` 辅助方法，遵循相同模式。

新增 import（文件头部）:
```java
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
```

新增测试代码:
```java
    /**
     * TC-1（任务包 2-2）：超时关单后重算——创建新订单并关联原 CANCELLED 订单。
     */
    @Test
    @DisplayName("超时关单后重算：新建订单并关联原 CANCELLED 订单")
    void shouldCreateNewOrderAndLinkToCancelledWhenRecalcAfterTimeout() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);
        when(parkingOrderService.findReusableOrderForRecord(any())).thenReturn(null);

        ParkingOrder cancelledOrder = new ParkingOrder();
        cancelledOrder.setId(100L);
        cancelledOrder.setOrderNo("O1001-001");
        cancelledOrder.setStatus(ParkingOrder.STATUS_CANCELLED);
        cancelledOrder.setAmountCents(2000);
        cancelledOrder.setPayableAmount(2000);
        when(parkingOrderService.findCancelledOrderForRecord(record.getId())).thenReturn(cancelledOrder);

        when(billingEngine.calculateFee(anyLong(), any(), any())).thenReturn(2500);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any())).thenReturn(true);

        ParkingOrder newOrder = new ParkingOrder();
        newOrder.setId(200L);
        newOrder.setOrderNo("O1001-002");
        newOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        newOrder.setAmountCents(2500);
        newOrder.setPayableAmount(2500);
        newOrder.setRecalcSourceOrderId(100L);
        when(parkingOrderService.createOrderInternal(any(), eq(2500), isNull(),
                eq(ParkingOrder.PAY_SCENE_AT_EXIT), any(), eq(100L), any())).thenReturn(newOrder);

        ExitResult result = exitService.handleExit(
                exitPayload(100L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result).isNotNull();
        verify(parkingOrderService).insertRecalcLog(eq(record), eq(100L), eq(200L),
                eq(2000), eq(2500), eq("TIMEOUT_RECALC"));
        verify(parkingOrderService).createOrderInternal(any(), eq(2500), isNull(),
                eq(ParkingOrder.PAY_SCENE_AT_EXIT), any(), eq(100L), any());
        verify(distributedLock).unlock(anyString());
    }
```

- [ ] **Step 2: 新增测试用例 TC-2 — PENDING_PAY 重识别更新金额**

```java
    /**
     * TC-2（任务包 2-2）：PENDING_PAY 订单重识别时更新金额而非新建。
     */
    @Test
    @DisplayName("PENDING_PAY 重复识别：更新金额不新建订单")
    void shouldUpdateExistingPendingOrderAmountOnRescan() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);

        ParkingOrder pendingOrder = new ParkingOrder();
        pendingOrder.setId(150L);
        pendingOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        pendingOrder.setAmountCents(2000);
        pendingOrder.setPayableAmount(2000);
        when(parkingOrderService.findReusableOrderForRecord(any())).thenReturn(pendingOrder);
        when(billingEngine.calculateFee(anyLong(), any(), any())).thenReturn(2200);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any())).thenReturn(true);
        when(parkingOrderService.updatePendingOrderAmount(eq(150L), eq(2200), any())).thenReturn(true);
        when(parkingOrderService.getById(150L)).thenReturn(pendingOrder);

        ExitResult result = exitService.handleExit(
                exitPayload(100L, 100L, 1L, "粤B12345"), "粤B12345");

        assertThat(result).isNotNull();
        verify(parkingOrderService).updatePendingOrderAmount(eq(150L), eq(2200), any());
        verify(parkingOrderService).insertRecalcLog(eq(record), eq(150L), eq(150L),
                eq(2000), eq(2200), eq("EXIT_RESCAN"));
        verify(parkingOrderService, never()).createOrder(any(), anyInt(), anyString(), anyString());
        verify(distributedLock).unlock(anyString());
    }
```

- [ ] **Step 3: 新增测试用例 TC-3 — 并发防重**

```java
    /**
     * TC-3（任务包 2-2）：分布式锁失败时降级不 crash。
     */
    @Test
    @DisplayName("分布式锁获取失败：降级不抛异常")
    void shouldDegradeGracefullyWhenLockAcquisitionFails() {
        ParkingRecord record = activeRecord(1L, 100L, 100L, "粤B12345");
        when(recordMapper.selectList(any())).thenReturn(Collections.singletonList(record));
        when(parkingOrderService.findPaidOrderForRecord(any())).thenReturn(null);
        when(distributedLock.tryLock(anyString(), anyLong(), anyLong(), any())).thenReturn(false);
        // 锁失败后仍进入锁内逻辑（降级）：findReusableOrderForRecord → null → 检查 cancelled → null → createOrder
        when(parkingOrderService.findReusableOrderForRecord(any())).thenReturn(null);
        ParkingOrder newOrder = new ParkingOrder();
        newOrder.setId(999L);
        newOrder.setStatus(ParkingOrder.STATUS_PENDING_PAY);
        when(parkingOrderService.createOrder(any(), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT)))
                .thenReturn(newOrder);

    assertThatCode(() -> exitService.handleExit(
            exitPayload(100L, 100L, 1L, "粤B12345"), "粤B12345"))
            .doesNotThrowAnyException();
    verify(parkingOrderService).createOrder(any(), anyInt(), isNull(), eq(ParkingOrder.PAY_SCENE_AT_EXIT));
    // 未获取锁，不调用 unlock
    verify(distributedLock, never()).unlock(anyString());
}
```
> 注意: 需要确保 `import static org.assertj.core.api.Assertions.assertThatCode;` 已添加。

- [ ] **Step 4: 运行 ExitServiceTest 全部测试**
    assertThatCode(() -> exitService.handleExit(payload, "京A12345"))
            .doesNotThrowAnyException();
    verify(distributedLock, never()).unlock(anyString());
}
```

- [ ] **Step 5: 运行测试**

```bash
mvn test -pl parking-boot -am -Dtest="ExitServiceTest"
```

预期: 所有测试通过（新增 3 个 + 现有测试全部通过）。

- [ ] **Step 6: 提交**

```bash
git add parking-boot/src/test/java/com/jushan/boot/service/ExitServiceTest.java
git commit -m "test: add timeout recalc and idempotency integration tests (task 2-2)"
```

---

### Task 8: 最终编译与全量测试验证

- [ ] **Step 1: 全量编译**

```bash
mvn clean compile -pl parking-system -am && mvn clean compile -pl parking-boot -am
```

预期: BUILD SUCCESS

- [ ] **Step 2: 运行全部测试**

```bash
mvn test -pl parking-system -am && mvn test -pl parking-boot -am
```

预期: Tests run: X, Failures: 0, Errors: 0, Skipped: 0

- [ ] **Step 3: 检查是否影响已知失败测试**

当前已知失败测试：`RecognitionEventServiceImplTest`、`DeviceWebhookControllerTest`。确认这些测试的失败数量与基线一致（不增加新失败）。

- [ ] **Step 4: 最终代码审查**

运行 `git diff origin/main --stat` 确认变更范围仅限于预期文件。

---

### Task 9: 验收核对

- [ ] **验收条目 1 — 超时订单自动关闭 → 再次出口识别 → 新订单金额=按实际时长重算且关联原订单**
  - 验证方式: 运行 TC-1 集成测试
  - 验收人: 开发自测

- [ ] **验收条目 2 — 重复识别/并发场景不产生重复有效订单**
  - 验证方式: 运行 TC-2 + TC-3 集成测试
  - 验收人: 开发自测

- [ ] **验收条目 3 — 全流程按 V1.1 附录出场流程图跑通**
  - 验证方式: 本地启动 parking-boot，模拟完整入场→出口识别→超时→重算→缴费→离场流程
  - 验收人: 开发自测
