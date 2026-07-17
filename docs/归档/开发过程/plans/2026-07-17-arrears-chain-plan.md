# 欠费链路（出场→展示→补缴→再出场拦截）实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 补齐出场未支付放行、欠费订单展示、补缴、再次出场拦截的完整业务闭环。

**Architecture:** ExitService.resolveReleaseDecision 读取 exit.unpaid_strategy 参数驱动 BLOCK/ALLOW_ARREARS；handleExit 新增 checkArrearsByPlate → MUST_PAY 创建合并 PENDING_PAY 订单（arrearsOrderIds 存储关联）/ REMIND_ONLY 放行提醒；MockPaymentService.confirmPay 扩展接受 ARREARS 并处理合并订单关联补缴；小程序端 MiniParkingRecordVO 新增 ARREARS payStatus + orderStatus；mini_message 新增 ARREARS_RELEASED / ARREARS_REMIND / ARREARS_PAID 消息类型。

**Tech Stack:** Java 21, Spring Boot 3, MyBatis-Plus, MySQL 8, Flyway, Redis

**Spec:** `docs/superpowers/specs/2026-07-17-arrears-chain-design.md`

---

### Task 1: Flyway 数据库迁移 — parking_order 新增 arrears_order_ids 列

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260717002__add_arrears_order_ids.sql`

- [ ] **Step 1: 创建迁移文件**

```sql
-- 新增 arrears_order_ids 字段，存储合并订单关联的欠费订单 ID 列表（JSON 数组）
ALTER TABLE parking_order
    ADD COLUMN arrears_order_ids TEXT NULL COMMENT '关联的欠费订单ID列表（JSON数组），合并计费时记录，如 [101,102]' AFTER recalc_source_order_id;
```

- [ ] **Step 2: 编译验证迁移可加载**

```bash
mvn clean compile -pl parking-boot -am
```

预期: BUILD SUCCESS，Flyway 迁移校验通过。

- [ ] **Step 3: 提交**

```bash
git add parking-boot/src/main/resources/db/migration/V20260717002__add_arrears_order_ids.sql
git commit -m "feat: add arrears_order_ids column to parking_order (task 2-3)"
```

---

### Task 2: ParkingOrder 实体新增 arrearsOrderIds 字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java`

- [ ] **Step 1: 在 `exitLaneId` 字段之后（约第 117 行），`version` 字段之前插入新字段**

使用 edit 工具，在 `private Long exitLaneId;` 之后、`@Version` 之前插入：

```java
    /** 关联的欠费订单ID列表（JSON数组），合并计费时记录，如 "[101,102]"。支付后逐条补缴。任务包 2-3 */
    private String arrearsOrderIds;
```

- [ ] **Step 2: 在 `setExitLaneId` 方法之后（约第 259 行），`getVersion` 之前插入 getter/setter**

在 `public void setExitLaneId(Long exitLaneId) { ... }` 之后插入：

```java

    public String getArrearsOrderIds() { return arrearsOrderIds; }
    public void setArrearsOrderIds(String arrearsOrderIds) { this.arrearsOrderIds = arrearsOrderIds; }
```

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java
git commit -m "feat: add arrearsOrderIds field to ParkingOrder entity (task 2-3)"
```

---

### Task 3: ParkingOrderMapper 新增 findArrearsByPlate 查询方法

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/mapper/ParkingOrderMapper.java`

- [ ] **Step 1: 在接口末尾 `}` 之前，最后一条查询方法之后新增方法**

在 `int updatePendingOrderAmount(...)` 方法（第 105 行 `}`）之后、`}` 之前插入：

```java
    /**
     * 按车牌和车场查询所有欠费中订单（ARREARS 状态，未删除）。
     * 用于再次出场时检测车辆是否存在未补缴的欠费订单。
     * 任务包 2-3。
     */
    @Select("SELECT * FROM parking_order WHERE parking_lot_id = #{parkingLotId} " +
            "AND plate_number = #{plateNumber} AND status = 'ARREARS' AND deleted_at IS NULL " +
            "ORDER BY created_at DESC")
    List<ParkingOrder> selectArrearsByPlate(@Param("parkingLotId") Long parkingLotId,
                                             @Param("plateNumber") String plateNumber);
```

需要在已有 import 中确认 `java.util.List` 已存在（通常在文件开头有 `import java.util.List;`）。

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/mapper/ParkingOrderMapper.java
git commit -m "feat: add selectArrearsByPlate query to ParkingOrderMapper (task 2-3)"
```

---

### Task 4: ParkingOrderService 新增 getArrearsOrdersByPlate 查询方法

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java`

- [ ] **Step 1: 在 `getById` 方法之后（约第 830 行）新增方法**

在 `public ParkingOrder getById(Long orderId) { ... }` 方法之后、类的 `}` 之前新增：

```java
    /**
     * 按车牌和车场查询所有欠费中订单。
     * <p>
     * 用于再次出场时检测是否存在未补缴的欠费，根据 arrears.reexit_strategy 决定处理方式。
     * 任务包 2-3。
     *
     * @param plateNumber  标准化车牌号
     * @param parkingLotId 停车场 ID
     * @return 欠费中订单列表（按创建时间倒序），无数据则空列表
     */
    public List<ParkingOrder> getArrearsOrdersByPlate(String plateNumber, Long parkingLotId) {
        return orderMapper.selectArrearsByPlate(parkingLotId, plateNumber);
    }
```

- [ ] **Step 2: 确认 `java.util.List` 已导入（通常文件开头已有 `import java.util.List;`）**

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java
git commit -m "feat: add getArrearsOrdersByPlate to ParkingOrderService (task 2-3)"
```

---

### Task 5: ReleaseDecision 新增欠费放行决策

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ReleaseDecision.java`

- [ ] **Step 1: 在 `exception` 方法之前（约第 41 行）新增两个工厂方法**

在 `public static ReleaseDecision noRecord() { ... }` 之后、`public static ReleaseDecision exception(String reason) { ... }` 之前插入：

```java
    /** 欠费放行：出口未支付但车场策略允许欠费出场 */
    public static ReleaseDecision arrearsAllowed() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ARREARS_ALLOWED, true, "车场配置允许欠费放行");
    }

    /** 欠费提醒放行：再次出场时有欠费订单，策略为 REMIND_ONLY */
    public static ReleaseDecision arrearsRemind() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ARREARS_REMIND, true, "欠费提醒放行，订单保持欠费中");
    }

    /** 欠费合并计费：再次出场时存在欠费订单，需一并补缴 */
    public static ReleaseDecision arrearsMustPay() {
        return new ReleaseDecision(com.jushan.system.entity.ExitRecord.DECISION_ARREARS_MUST_PAY, false, "存在欠费订单，需补缴欠费+本次费用");
    }
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。如果 ExitRecord 的常量尚未定义，编译会失败——这会在 Task 6 中解决。

- [ ] **Step 3: 提交（暂不提交，与 Task 6 一起提交）**

---

### Task 6: ExitRecord 新增欠费决策常量

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ExitRecord.java`

- [ ] **Step 1: 在 DECISION_EXCEPTION 之后（约第 90 行）新增三个常量**

在 `public static final String DECISION_EXCEPTION = "EXCEPTION";` 之后、常量区域结束前插入：

```java
    /** 欠费放行：车场策略 ALLOW_ARREARS，开闸放行并记录欠费 */
    public static final String DECISION_ARREARS_ALLOWED = "ARREARS_ALLOWED";
    /** 欠费提醒放行：REMIND_ONLY 策略，放行并推送提醒 */
    public static final String DECISION_ARREARS_REMIND = "ARREARS_REMIND";
    /** 欠费合并计费：MUST_PAY 策略，拦截并提示补缴 */
    public static final String DECISION_ARREARS_MUST_PAY = "ARREARS_MUST_PAY";
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。Task 5 的引用此时应解析通过。

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/ReleaseDecision.java parking-system/src/main/java/com/jushan/system/entity/ExitRecord.java
git commit -m "feat: add arrears release decision constants (task 2-3)"
```

---

### Task 7: ExitService — 集成欠费逻辑

这是最大的改动任务。`resolveReleaseDecision` 需读取 `exit.unpaid_strategy`；`handleExit` 需新增欠费检测分支。

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ExitService.java`

- [ ] **Step 1: 注入 ParamResolver 依赖**

在 ExitService 的依赖注入中新增 ParamResolver。在类字段区（约第 70 行，`BillingRuleRecalcLogMapper recalcLogMapper;` 之后）新增：

```java
    private final ParamResolver paramResolver;
```

在构造函数参数列表末尾（约第 83 行，`BillingRuleRecalcLogMapper recalcLogMapper` 之后）新增：

```java
                        ParamResolver paramResolver) {
```

在构造函数赋值块末尾（约第 95 行，`this.recalcLogMapper = recalcLogMapper;` 之后）新增：

```java
        this.paramResolver = paramResolver;
```

确认 `import com.jushan.system.service.ParamResolver;` 已存在。如果不存在则添加：

```java
import com.jushan.system.service.ParamResolver;
```

同时确认 `import com.jushan.system.constant.ParamKeys;` 已存在。

- [ ] **Step 2: 重写 resolveReleaseDecision 方法，集成 ALLOW_ARREARS 策略**

替换现有的 `resolveReleaseDecision` 方法（约第 346-357 行）为：

```java
    /**
     * 决定放行策略。
     * <p>
     * 任务包 2-3：根据车场参数 exit.unpaid_strategy 决定未支付车辆的处理方式。
     */
    private ReleaseDecision resolveReleaseDecision(int feeCents, ParkingOrder order) {
        if (order == null) {
            return ReleaseDecision.pendingPayment();
        }
        if (feeCents == 0) {
            return ReleaseDecision.zeroFee();
        }
        // 已支付订单
        if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
            return ReleaseDecision.paid();
        }
        // P020 扩展：月卡/白名单等授权放行
        // 未支付订单：读取车场未支付出场策略
        String unpaidStrategy = paramResolver.getString(
                ParamKeys.EXIT_UNPAID_STRATEGY, order.getParkingLotId());
        if (ParamKeys.EXIT_UNPAID_ALLOW_ARREARS.equals(unpaidStrategy)) {
            // ALLOW_ARREARS：开闸放行，订单转 ARREARS
            String triggerSource = OrderStatusLog.TRIGGER_SYSTEM;
            parkingOrderService.allowArrears(order.getId(), triggerSource, null,
                    "未支付欠费放行（车场策略）");
            log.info("欠费放行: orderId={} plate={} strategy={}",
                    order.getId(), order.getPlateNumber(), unpaidStrategy);
            return ReleaseDecision.arrearsAllowed();
        }
        // BLOCK 或其他未知值：拦截不放行
        return ReleaseDecision.pendingPayment();
    }
```

注意：`OrderStatusLog.TRIGGER_SYSTEM` 需要确认 import。如果不存在，需要在文件头添加：

```java
import com.jushan.system.entity.OrderStatusLog;
```

- [ ] **Step 3: 在 handleExit 中新增欠费检测分支**

在 `handleExit` 方法中，`// 3. 获取订单` 注释块之前（约第 150 行），在 `// 2b. 计算费用` 完成之后，插入欠费检测逻辑。具体位置：在 `feeCents = billingEngine.calculateFee(...)` 处的 else 分支结束之后（约第 146 行），`// 3. 获取订单` 注释行之前插入：

```java

        // 2c. 欠费检测（任务包 2-3）：检测该车牌是否有未补缴的欠费订单
        // 仅在非固定车位且非月卡/白名单时检测
        if (!isFixedSpace) {
            List<ParkingOrder> arrearsOrders = parkingOrderService.getArrearsOrdersByPlate(
                    standardizedPlate, parkingLotId);
            if (!arrearsOrders.isEmpty()) {
                ExitResult arrearsResult = handleArrearsReentry(record, arrearsOrders, feeCents,
                        exitTime, payload);
                if (arrearsResult != null) {
                    return arrearsResult;
                }
            }
        }
```

- [ ] **Step 4: 新增 handleArrearsReentry 私有方法**

在类的末尾（`}` 之前）新增完整方法：

```java
    /**
     * 处理欠费车辆再次出场（任务包 2-3）。
     * <p>
     * 根据车场参数 arrears.reexit_strategy 决定处理方式：
     * MUST_PAY → 创建合并订单拦截补缴
     * REMIND_ONLY → 放行并推送提醒
     *
     * @param record        当前停车记录
     * @param arrearsOrders 该车未补缴的欠费订单列表
     * @param feeCents      本次出场计费金额（分）
     * @param exitTime      出场时间
     * @param payload       识别事件载荷
     * @return EXIT 结果（若短路直接返回），null 表示走正常后续流程
     */
    private ExitResult handleArrearsReentry(ParkingRecord record,
                                             List<ParkingOrder> arrearsOrders,
                                             int feeCents,
                                             LocalDateTime exitTime,
                                             RecognitionEventPayload payload) {
        String reexitStrategy = paramResolver.getString(
                ParamKeys.ARREARS_REEXIT_STRATEGY, record.getParkingLotId());

        // 计算欠费总额
        int arrearsTotalCents = arrearsOrders.stream()
                .mapToInt(o -> o.getPayableAmount() != null ? o.getPayableAmount() : 0)
                .sum();

        if (ParamKeys.ARREARS_MUST_PAY.equals(reexitStrategy)) {
            // MUST_PAY：创建合并计费订单
            int totalCents = arrearsTotalCents + feeCents;

            // 构建 arrearsOrderIds JSON 数组
            StringBuilder idsJson = new StringBuilder("[");
            for (int i = 0; i < arrearsOrders.size(); i++) {
                if (i > 0) idsJson.append(",");
                idsJson.append(arrearsOrders.get(i).getId());
            }
            idsJson.append("]");

            // 创建合并订单（PENDING_PAY，金额 = 欠费 + 本次）
            ParkingOrder mergedOrder = parkingOrderService.createOrderInternal(
                    record, totalCents, null,
                    ParkingOrder.PAY_SCENE_AT_EXIT, payload.getLaneId(), null,
                    "欠费合并计费：欠费" + arrearsTotalCents + "分 + 本次" + feeCents + "分");
            mergedOrder.setArrearsOrderIds(idsJson.toString());
            // 更新合并订单的 arrearsOrderIds（createOrderInternal 未设该字段，需回写）
            com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ParkingOrder> updateWrapper =
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<>();
            updateWrapper.set("arrears_order_ids", idsJson.toString())
                    .eq("id", mergedOrder.getId());
            orderMapper.update(null, updateWrapper);

            log.info("欠费合并计费: mergedOrderId={} plate={} arrearsTotal={} currentFee={} mergedTotal={} arrearsIds={}",
                    mergedOrder.getId(), record.getStandardizedPlate(),
                    arrearsTotalCents, feeCents, totalCents, idsJson);

            // 创建出场记录（拦截）
            ExitRecord exitRecord = new ExitRecord();
            exitRecord.setTenantId(record.getTenantId());
            exitRecord.setParkingLotId(record.getParkingLotId());
            exitRecord.setParkingRecordId(record.getId());
            exitRecord.setExitEventId(payload.getLogId());
            exitRecord.setLaneId(payload.getLaneId());
            exitRecord.setDeviceId(payload.getDeviceId());
            exitRecord.setStandardizedPlate(record.getStandardizedPlate());
            exitRecord.setExitTime(exitTime);
            exitRecord.setFeeCents(totalCents);
            exitRecord.setPaidCents(0);
            exitRecord.setReleaseDecision(ExitRecord.DECISION_ARREARS_MUST_PAY);
            exitRecord.setOrderId(mergedOrder.getId());
            exitRecord.setReason("欠费合并计费：欠费" + (arrearsTotalCents / 100.0) + "元 + 本次" + (feeCents / 100.0) + "元 = " + (totalCents / 100.0) + "元");
            exitRecord.setCreatedAt(LocalDateTime.now());
            exitRecord.setUpdatedAt(LocalDateTime.now());
            exitRecordMapper.insert(exitRecord);

            // 推送 WebSocket 通知岗亭端
            boothWebSocketPublisher.sendRemoteGateAlert(record.getParkingLotId(),
                    record.getStandardizedPlate(),
                    "欠费车辆出场，需补缴欠费" + (arrearsTotalCents / 100.0) + "元",
                    "ARREARS_MUST_PAY");

            return ExitResult.of(ReleaseDecision.arrearsMustPay(),
                    exitRecord.getId(), mergedOrder.getId(), totalCents);
        }

        // REMIND_ONLY（默认回退）：放行，推送提醒
        log.info("欠费提醒放行: plate={} arrearsCount={} arrearsTotal={}",
                record.getStandardizedPlate(), arrearsOrders.size(), arrearsTotalCents);

        // 直接放行（不入 createOrder 流程，也不转 ARREARS 状态）
        boolean recordCompleted = completeParkingRecord(record, payload, exitTime);
        if (recordCompleted) {
            decrementVehicleCount(record.getParkingLotId());
        }
        syncParkingSessionExit(record, payload, exitTime, feeCents, null);

        ExitRecord exitRecord = createExitRecordForArrears(record, payload, feeCents,
                ExitRecord.DECISION_ARREARS_REMIND,
                "欠费提醒放行，待补缴欠费" + (arrearsTotalCents / 100.0) + "元", exitTime);

        // 推送 WebSocket 通知
        boothWebSocketPublisher.sendRemoteGateAlert(record.getParkingLotId(),
                record.getStandardizedPlate(),
                "欠费提醒放行（待补缴" + (arrearsTotalCents / 100.0) + "元）",
                "ARREARS_REMIND");

        return ExitResult.of(ReleaseDecision.arrearsRemind(),
                exitRecord.getId(), null, feeCents);
    }

    /**
     * 为欠费场景创建出场记录（简化版，不依赖 ParkingOrder）。
     */
    private ExitRecord createExitRecordForArrears(ParkingRecord record,
                                                   RecognitionEventPayload payload,
                                                   int feeCents,
                                                   String decisionCode,
                                                   String reason,
                                                   LocalDateTime exitTime) {
        ExitRecord exitRecord = new ExitRecord();
        exitRecord.setTenantId(record.getTenantId());
        exitRecord.setParkingLotId(record.getParkingLotId());
        exitRecord.setParkingRecordId(record.getId());
        exitRecord.setExitEventId(payload.getLogId());
        exitRecord.setLaneId(payload.getLaneId());
        exitRecord.setDeviceId(payload.getDeviceId());
        exitRecord.setStandardizedPlate(record.getStandardizedPlate());
        exitRecord.setExitTime(exitTime);
        exitRecord.setFeeCents(feeCents);
        exitRecord.setPaidCents(0);
        exitRecord.setReleaseDecision(decisionCode);
        exitRecord.setOrderId(0L);
        exitRecord.setReason(reason);
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());
        exitRecordMapper.insert(exitRecord);
        return exitRecord;
    }
```

所需的 import 检查清单（如已存在则无需重复添加）：
- `import com.jushan.system.constant.ParamKeys;`
- `import com.jushan.system.entity.OrderStatusLog;`
- `import com.jushan.system.service.ParamResolver;`
- `import java.util.List;`

注意：`orderMapper` 实例变量在 ExitService 中尚未存在，需要在字段区和构造函数中注入。在 `recalcLogMapper` 之后新增：

字段（约第 70 行）:
```java
    private final ParkingOrderMapper orderMapper;
```

构造函数参数（约第 83 行，recalcLogMapper 之后）:
```java
                        ParkingOrderMapper orderMapper,
```

构造函数赋值（约第 95 行）:
```java
        this.orderMapper = orderMapper;
```

同时添加 import:
```java
import com.jushan.system.mapper.ParkingOrderMapper;
```

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。如遇编译错误，逐一检查 import 和字段引用。

- [ ] **Step 6: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/ExitService.java
git commit -m "feat: integrate arrears logic into ExitService (task 2-3)"
```

---

### Task 8: MockPaymentService — confirmPay 扩展接受 ARREARS 状态 + 处理合并订单补缴

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java`
- Modify: `parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java`

- [ ] **Step 1: 修改 confirmPay 状态校验，接受 ARREARS**

在 `MockPaymentService.confirmPay` 方法中（约第 138 行），将：

```java
        if (!ParkingOrder.STATUS_PENDING_PAY.equals(order.getStatus())
                && !ParkingOrder.STATUS_PAYING.equals(order.getStatus())) {
            log.warn("订单状态不允许支付: orderId={} status={}", orderId, order.getStatus());
            return false;
        }
```

替换为：

```java
        if (!ParkingOrder.STATUS_PENDING_PAY.equals(order.getStatus())
                && !ParkingOrder.STATUS_PAYING.equals(order.getStatus())
                && !ParkingOrder.STATUS_ARREARS.equals(order.getStatus())) {
            log.warn("订单状态不允许支付: orderId={} status={}", orderId, order.getStatus());
            return false;
        }
```

- [ ] **Step 2: 修改 confirmPay 中的 markPaidStatus 调用**

在 `confirmPay` 的 `markPaidStatus` 调用处（约第 168 行），ARREARS 订单的支付需要不同的处理。在 `// 更新订单状态为已支付` 注释下方，替换整个支付状态更新块为：

```java
        // 更新订单状态为已支付
        String paySerial = "MOCK-" + orderId + "-" + System.currentTimeMillis();
        int updated;
        if (ParkingOrder.STATUS_ARREARS.equals(order.getStatus())) {
            // ARREARS 订单支付：直接 payArrears → COMPLETED（不走 PAID 中间态）
            updated = orderMapper.update(null,
                    new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<ParkingOrder>()
                            .set("status", ParkingOrder.STATUS_COMPLETED)
                            .set("paid_amount", order.getPayableAmount())
                            .set("pay_time", LocalDateTime.now())
                            .set("pay_serial", paySerial)
                            .set("updated_at", LocalDateTime.now())
                            .eq("id", orderId)
                            .eq("status", ParkingOrder.STATUS_ARREARS)) ? 1 : 0;
        } else {
            updated = orderMapper.markPaidStatus(
                    orderId,
                    paySerial,
                    order.getPayableAmount(),
                    LocalDateTime.now()
            );
        }
```

- [ ] **Step 3: 修改 handlePostPay，处理合并订单的 arrearsOrderIds**

在 `handlePostPay` 方法开头（约第 198 行），新增合并订单关联欠费补缴逻辑。将：

```java
    private void handlePostPay(ParkingOrder order, String paySerial, String paidBy) {
        String payScene = order.getPayScene();
```

替换为：

```java
    private void handlePostPay(ParkingOrder order, String paySerial, String paidBy) {
        // 任务包 2-3：合并订单支付后，逐条补缴关联的欠费订单
        String arrearsIds = order.getArrearsOrderIds();
        if (arrearsIds != null && !arrearsIds.isEmpty()) {
            processArrearsChainPayment(order, arrearsIds, paidBy);
        }

        String payScene = order.getPayScene();
```

- [ ] **Step 4: 新增 processArrearsChainPayment 私有方法**

在 `MockPaymentService` 类末尾（`}` 之前）新增：

```java
    /**
     * 处理合并订单的关联欠费补缴（任务包 2-3）。
     * <p>
     * 合并订单支付完成后，逐条将 arrearsOrderIds 中的欠费订单从 ARREARS → COMPLETED。
     * 单条失败记录日志不阻断，运营端订单中心可人工核实。
     */
    private void processArrearsChainPayment(ParkingOrder mergedOrder, String arrearsIdsJson, String paidBy) {
        try {
            // 解析 JSON 数组，格式如 "[101,102,103]"
            String cleaned = arrearsIdsJson.replace("[", "").replace("]", "").replace(" ", "");
            if (cleaned.isEmpty()) {
                return;
            }
            String[] parts = cleaned.split(",");
            int successCount = 0;
            int failCount = 0;
            for (String part : parts) {
                try {
                    Long arrearsOrderId = Long.parseLong(part.trim());
                    boolean ok = parkingOrderService.payArrears(arrearsOrderId,
                            OrderStatusLog.TRIGGER_USER, null);
                    if (ok) {
                        successCount++;
                        log.info("合并订单关联欠费补缴成功: mergedOrderId={} arrearsOrderId={}",
                                mergedOrder.getId(), arrearsOrderId);
                    } else {
                        failCount++;
                        log.error("合并订单关联欠费补缴失败（条件更新不匹配）: mergedOrderId={} arrearsOrderId={}",
                                mergedOrder.getId(), arrearsOrderId);
                    }
                } catch (NumberFormatException e) {
                    failCount++;
                    log.error("合并订单 arrearsOrderIds 格式异常: mergedOrderId={} part={}",
                            mergedOrder.getId(), part);
                }
            }
            log.info("合并订单欠费补缴完成: mergedOrderId={} success={} fail={}",
                    mergedOrder.getId(), successCount, failCount);
        } catch (Exception e) {
            log.error("合并订单欠费补缴异常: mergedOrderId={}", mergedOrder.getId(), e);
        }
    }
```

需要确认 import `com.jushan.system.entity.OrderStatusLog;` 存在。

- [ ] **Step 5: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 6: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/MockPaymentService.java
git commit -m "feat: extend MockPaymentService to accept ARREARS payments and process merged orders (task 2-3)"
```

---

### Task 9: MiniParkingRecordVO 新增 orderStatus 字段 + payStatus 扩展 ARREARS

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/miniapp/vo/MiniParkingRecordVO.java`

- [ ] **Step 1: 在 `status` 字段之后（约第 48 行）新增 `orderStatus` 字段**

在 `private String status;` 之后、类结尾 `}` 之前插入：

```java
    /** 订单原始状态（透传 ParkingOrder.status），如 PENDING_PAY, ARREARS, COMPLETED 等。前端用于区分欠费标识 */
    private String orderStatus;
```

同时更新 `payStatus` 字段的注释以反映扩展：

```java
    /** 支付状态：UNPAID-未支付, PAID-已支付, FREE-免费, ARREARS-欠费中 */
    private String payStatus;
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/miniapp/vo/MiniParkingRecordVO.java
git commit -m "feat: extend MiniParkingRecordVO with orderStatus and ARREARS payStatus (task 2-3)"
```

---

### Task 10: MiniUserServiceImpl — toMiniVO 扩展 ARREARS 映射

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/impl/MiniUserServiceImpl.java`

- [ ] **Step 1: 修改 toMiniVO 方法中的 payStatus 映射**

在 `toMiniVO` 方法中（约第 231-238 行），将现有的状态映射逻辑替换为包含 ARREARS 的处理：

将：
```java
            if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                    || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
                vo.setPayStatus("PAID");
            } else if (ParkingOrder.STATUS_CANCELLED.equals(order.getStatus())) {
                vo.setPayStatus("UNPAID");
            } else {
                vo.setPayStatus("UNPAID");
            }
```

替换为：
```java
            // 设置订单原始状态，前端用于区分欠费标识
            vo.setOrderStatus(order.getStatus());

            if (ParkingOrder.STATUS_PAID.equals(order.getStatus())
                    || ParkingOrder.STATUS_COMPLETED.equals(order.getStatus())) {
                vo.setPayStatus("PAID");
            } else if (ParkingOrder.STATUS_ARREARS.equals(order.getStatus())) {
                vo.setPayStatus("ARREARS");
            } else if (ParkingOrder.STATUS_CANCELLED.equals(order.getStatus())) {
                vo.setPayStatus("UNPAID");
            } else {
                vo.setPayStatus("UNPAID");
            }
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/impl/MiniUserServiceImpl.java
git commit -m "feat: add ARREARS mapping to MiniUserServiceImpl.toMiniVO (task 2-3)"
```

---

### Task 11: MiniMessage — 新增欠费消息类型常量

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/miniapp/entity/MiniMessage.java`

- [ ] **Step 1: 在类型常量区域（约第 25 行）新增三个常量**

在 `public static final String TYPE_SYSTEM = "SYSTEM";` 之后新增：

```java
    /** 欠费放行通知 */
    public static final String TYPE_ARREARS_RELEASED = "ARREARS_RELEASED";
    /** 欠费提醒通知 */
    public static final String TYPE_ARREARS_REMIND = "ARREARS_REMIND";
    /** 欠费补缴成功通知 */
    public static final String TYPE_ARREARS_PAID = "ARREARS_PAID";
```

- [ ] **Step 2: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/miniapp/entity/MiniMessage.java
git commit -m "feat: add arrears message type constants to MiniMessage (task 2-3)"
```

---

### Task 12: MiniMessageService — 新增欠费消息创建方法

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/MiniMessageService.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/impl/MiniMessageServiceImpl.java`

- [ ] **Step 1: 在 MiniMessageService 接口中新增方法声明**

在 `createPaySuccessMessage` 方法之后（约第 28 行，`}` 之前）新增：

```java
    /**
     * 创建欠费放行通知消息。
     *
     * @param wxUserId    微信用户ID
     * @param tenantId    租户ID
     * @param orderId     欠费订单ID
     * @param plateNumber 车牌号
     * @param amountCents 欠费金额（分）
     * @return 创建的消息记录
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createArrearsReleasedMessage(Long wxUserId, Long tenantId, Long orderId,
                                              String plateNumber, Integer amountCents);

    /**
     * 创建欠费提醒通知消息。
     *
     * @param wxUserId    微信用户ID
     * @param tenantId    租户ID
     * @param plateNumber 车牌号
     * @param amountCents 欠费金额（分）
     * @return 创建的消息记录
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createArrearsRemindMessage(Long wxUserId, Long tenantId, Long plateNumber,
                                            Integer amountCents);

    /**
     * 创建欠费补缴成功通知消息。
     *
     * @param wxUserId    微信用户ID
     * @param tenantId    租户ID
     * @param orderId     订单ID
     * @param plateNumber 车牌号
     * @param amountCents 补缴金额（分）
     * @return 创建的消息记录
     */
    @Transactional(rollbackFor = Exception.class)
    MiniMessage createArrearsPaidMessage(Long wxUserId, Long tenantId, Long orderId,
                                          String plateNumber, Integer amountCents);
```

注意：接口中第二个方法 `createArrearsRemindMessage` 的参数 `Long plateNumber` 有误，应修正为 `String plateNumber`。
在写入文件时请修正为：
```java
    MiniMessage createArrearsRemindMessage(Long wxUserId, Long tenantId, String plateNumber,
                                            Integer amountCents);
```

- [ ] **Step 2: 在 MiniMessageServiceImpl 中实现新方法**

在 `sendSubscribeMessage` 方法之后（约第 93 行，`}` 之前）新增三个实现方法：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createArrearsReleasedMessage(Long wxUserId, Long tenantId, Long orderId,
                                                     String plateNumber, Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_ARREARS_RELEASED);
        message.setIsRead(false);
        message.setRelatedOrderId(orderId);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = String.format("%.2f", (amountCents != null ? amountCents : 0) / 100.0);
        message.setTitle("欠费记录通知");
        message.setContent("您的停车费用 ¥" + amountYuan + " 已转为欠费记录，请在方便时补缴。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.insert(message);
        log.info("欠费放行消息已创建: wxUserId={} orderId={} plate={}", wxUserId, orderId, plateNumber);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createArrearsRemindMessage(Long wxUserId, Long tenantId, String plateNumber,
                                                   Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_ARREARS_REMIND);
        message.setIsRead(false);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = String.format("%.2f", (amountCents != null ? amountCents : 0) / 100.0);
        message.setTitle("欠费提醒");
        message.setContent("您有欠费订单未支付（¥" + amountYuan + "），请及时补缴以免影响后续通行。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.insert(message);
        log.info("欠费提醒消息已创建: wxUserId={} plate={}", wxUserId, plateNumber);
        return message;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniMessage createArrearsPaidMessage(Long wxUserId, Long tenantId, Long orderId,
                                                 String plateNumber, Integer amountCents) {
        MiniMessage message = new MiniMessage();
        message.setTenantId(tenantId);
        message.setWxUserId(wxUserId);
        message.setType(MiniMessage.TYPE_ARREARS_PAID);
        message.setIsRead(false);
        message.setRelatedOrderId(orderId);
        message.setRelatedPlate(plateNumber);
        message.setRelatedAmount(amountCents);

        String amountYuan = String.format("%.2f", (amountCents != null ? amountCents : 0) / 100.0);
        message.setTitle("欠费补缴成功");
        message.setContent("您的欠费 ¥" + amountYuan + " 已补缴成功，感谢您的配合。");

        message.setCreatedAt(LocalDateTime.now());
        message.setUpdatedAt(LocalDateTime.now());
        miniMessageMapper.insert(message);
        log.info("欠费补缴成功消息已创建: wxUserId={} orderId={} plate={}", wxUserId, orderId, plateNumber);
        return message;
    }
```

- [ ] **Step 3: 编译验证**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS。

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/MiniMessageService.java parking-system/src/main/java/com/jushan/platform/modules/miniapp/service/impl/MiniMessageServiceImpl.java
git commit -m "feat: add arrears message creation methods to MiniMessageService (task 2-3)"
```

---

### Task 13: ParkingOrderService — 确认 allowArrears/payArrears 幂等性

这个任务是对已有方法的安全审查，确保 ALLOW_ARREARS 场景下重复调用不会产生副作用。

**Files:**
- Read only: `parking-system/src/main/java/com/jushan/system/service/ParkingOrderService.java`

- [ ] **Step 1: 审查 allowArrears 幂等性**

阅读 `allowArrears` 方法（第 498-521 行）。确认：
- 第 504-506 行：`if (ParkingOrder.STATUS_ARREARS.equals(from)) { return true; }` — 已在 ARREARS 状态时直接返回 true（幂等）
- 第 507 行：`OrderStatus.assertCanTransition` 守卫仅接受 PENDING_PAY → ARREARS
- 第 509-513 行：条件更新 `eq("status", ParkingOrder.STATUS_PENDING_PAY)` — 仅当状态匹配时更新

**结论：幂等性正确，无需修改。**

- [ ] **Step 2: 审查 payArrears 幂等性**

阅读 `payArrears` 方法（第 533-558 行）。确认：
- 第 539-541 行：`if (ParkingOrder.STATUS_COMPLETED.equals(from)) { return true; }` — 已补缴时直接返回 true（幂等）
- 第 542 行：`OrderStatus.assertCanTransition` 守卫仅接受 ARREARS → COMPLETED
- 第 544-550 行：条件更新 `eq("status", ParkingOrder.STATUS_ARREARS)` — 仅当状态匹配时更新

**结论：幂等性正确，无需修改。**

- [ ] **Step 3: 审查记录（无代码变更，不提交）**

---

### Task 14: 单元测试 — ParkingOrderMapper.selectArrearsByPlate

**Files:**
- 确认测试文件位置：`parking-boot/src/test/java/com/jushan/system/mapper/ParkingOrderMapperTest.java` 或类似路径
- 如不存在则新建：`parking-boot/src/test/java/com/jushan/system/mapper/ArrearsOrderMapperTest.java`

- [ ] **Step 1: 检查现有 Mapper 测试结构**

```bash
find parking-boot/src/test -name "*OrderMapper*" -o -name "*ParkingOrder*Test*" 2>/dev/null | head -5
```

根据结果确定测试文件的命名和位置。如果存在 `ParkingOrderMapperTest`，则在其中新增测试方法；否则新建测试类。

- [ ] **Step 2: 编写测试方法**

如果已有测试类，新增测试方法：

```java
    @Test
    void shouldFindArrearsOrdersByPlate() {
        // 场景：同车牌存在 ARREARS 订单
        // 1. 插入一条 ARREARS 状态的订单
        ParkingOrder arrearsOrder = new ParkingOrder();
        arrearsOrder.setTenantId(1L);
        arrearsOrder.setParkingLotId(100L);
        arrearsOrder.setParkingRecordId(1L);
        arrearsOrder.setOrderNo("O10020260717001");
        arrearsOrder.setOrderType(ParkingOrder.ORDER_TYPE_PARKING);
        arrearsOrder.setPlateNumber("TESTARREARS");
        arrearsOrder.setAmountCents(2000);
        arrearsOrder.setPayableAmount(2000);
        arrearsOrder.setStatus(ParkingOrder.STATUS_ARREARS);
        arrearsOrder.setCreatedAt(LocalDateTime.now());
        arrearsOrder.setUpdatedAt(LocalDateTime.now());
        orderMapper.insert(arrearsOrder);

        // 2. 查询
        List<ParkingOrder> result = orderMapper.selectArrearsByPlate(100L, "TESTARREARS");

        // 3. 断言
        assertThat(result).isNotEmpty();
        assertThat(result.get(0).getStatus()).isEqualTo(ParkingOrder.STATUS_ARREARS);
    }
```

- [ ] **Step 3: 运行测试**

```bash
mvn test -pl parking-boot -am -Dtest="ParkingOrderMapperTest#shouldFindArrearsOrdersByPlate"
```

预期: Tests PASS。

- [ ] **Step 4: 提交**

```bash
git add parking-boot/src/test/java/com/jushan/system/mapper/*Test*.java
git commit -m "test: add selectArrearsByPlate test (task 2-3)"
```

---

### Task 15: 单元测试 — ExitService arrears 分支

**Files:**
- Modify/Create: `parking-boot/src/test/java/com/jushan/system/service/ExitServiceArrearsTest.java`

- [ ] **Step 1: 创建测试类框架**

新建文件 `parking-boot/src/test/java/com/jushan/system/service/ExitServiceArrearsTest.java`：

```java
package com.jushan.system.service;

import com.jushan.system.constant.ParamKeys;
import com.jushan.system.entity.ExitRecord;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.ParkingOrderMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ExitService 欠费链路集成测试。
 */
@SpringBootTest
@Transactional
class ExitServiceArrearsTest {

    @Autowired private ExitService exitService;
    @Autowired private ParkingOrderMapper orderMapper;
    @Autowired private ParamResolver paramResolver;
    // 根据需要注入其他 Mapper

    private Long testLotId = 100L;
    private String testPlate = "EXITARREARSTEST";

    @BeforeEach
    void setUp() {
        // 设置车场参数
        // 插入测试用 ParkingRecord（PARKING 状态）
    }
}
```

- [ ] **Step 2: 编写 TC-2: BLOCK 默认策略不卡测试（现有行为覆盖）**

```java
    @Test
    @DisplayName("BLOCK 策略：未支付应拦截不开闸")
    void shouldBlockExitWhenUnpaidAndStrategyIsBlock() {
        // 设置参数为 BLOCK（默认值）
        // 创建 PENDING_PAY 订单
        // 调用 handleExit
        // 断言：allowExit=false, 订单仍为 PENDING_PAY
    }
```

- [ ] **Step 3: 编写 TC-1: ALLOW_ARREARS 策略 — 欠费放行**

```java
    @Test
    @DisplayName("ALLOW_ARREARS 策略：未支付应放行并转为 ARREARS")
    void shouldAllowArrearsWhenStrategyIsAllowArrears() {
        // 设置 exit.unpaid_strategy = ALLOW_ARREARS
        // 创建 PENDING_PAY 订单
        // 调用 handleExit
        // 断言：allowExit=true, 订单状态变为 ARREARS, exitRecord.decision = ARREARS_ALLOWED
    }
```

- [ ] **Step 4: 编写 TC-3: MUST_PAY 合并计费**

```java
    @Test
    @DisplayName("MUST_PAY 策略：欠费车辆再出场应创建合并订单")
    void shouldCreateMergedOrderWhenMustPay() {
        // 创建 ARREARS 订单（20元）
        // 设置 arrears.reexit_strategy = MUST_PAY
        // 调用 handleExit（本次计费 15元）
        // 断言：生成新 PENDING_PAY 订单，amount=3500, arrearsOrderIds 包含原订单ID
        // assertThat(mergedOrder.getArrearsOrderIds()).contains("原订单ID");
    }
```

- [ ] **Step 5: 编写 TC-5: REMIND_ONLY 放行**

```java
    @Test
    @DisplayName("REMIND_ONLY 策略：欠费车辆再出场应放行提醒")
    void shouldRemindAndReleaseWhenRemindOnly() {
        // 创建 ARREARS 订单
        // 设置 arrears.reexit_strategy = REMIND_ONLY
        // 调用 handleExit
        // 断言：allowExit=true, ARREARS 订单不变, exitRecord.decision = ARREARS_REMIND
    }
```

- [ ] **Step 6: 运行测试**

```bash
mvn test -pl parking-boot -am -Dtest="ExitServiceArrearsTest"
```

预期: 新测试 PASS（如果有数据库依赖可能需要调整测试环境）。

- [ ] **Step 7: 提交**

```bash
git add parking-boot/src/test/java/com/jushan/system/service/ExitServiceArrearsTest.java
git commit -m "test: add ExitService arrears scenario tests (task 2-3)"
```

---

### Task 16: 单元测试 — MockPaymentService ARREARS 支付

**Files:**
- Modify: `parking-boot/src/test/java/com/jushan/system/service/MockPaymentServiceTest.java`

- [ ] **Step 1: 编写 ARREARS 订单直接补缴测试**

在已有测试类中新增：

```java
    @Test
    @DisplayName("ARREARS 订单通过模拟支付补缴 → COMPLETED")
    void shouldPayArrearsOrderViaConfirmPay() {
        // 创建 ARREARS 订单
        // 调用 confirmPay
        // 断言：订单状态 → COMPLETED
    }

    @Test
    @DisplayName("合并订单支付后关联欠费订单逐一补缴")
    void shouldProcessChainedArrearsPayments() {
        // 创建 ARREARS 订单 A, B
        // 创建合并订单，arrearsOrderIds="[A_id,B_id]"
        // 调用 confirmPay(合并订单)
        // 断言：合并订单 → COMPLETED, A → COMPLETED, B → COMPLETED
    }
```

- [ ] **Step 2: 运行测试**

```bash
mvn test -pl parking-boot -am -Dtest="MockPaymentServiceTest#shouldPayArrearsOrderViaConfirmPay+shouldProcessChainedArrearsPayments"
```

预期: Tests PASS。

- [ ] **Step 3: 提交**

```bash
git add parking-boot/src/test/java/com/jushan/system/service/MockPaymentServiceTest.java
git commit -m "test: add arrears payment and merged order tests (task 2-3)"
```

---

### Task 17: 全编译 + 全测试验证

- [ ] **Step 1: 全量编译**

```bash
mvn clean compile -pl parking-system -am
```

预期: BUILD SUCCESS，无编译错误。

- [ ] **Step 2: 运行已有全量测试确保无回归**

```bash
mvn test -pl parking-boot -am
```

预期: 已知失败测试（RecognitionEventServiceImplTest、DeviceWebhookControllerTest）不阻塞，其余全部 PASS。

- [ ] **Step 3: 运行新增测试**

```bash
mvn test -pl parking-boot -am -Dtest="ExitServiceArrearsTest,MockPaymentServiceTest"
```

预期: 新测试 PASS。

- [ ] **Step 4: 提交（如有测试修正）**

```bash
git add -A
git commit -m "test: verify all tests pass after arrears chain implementation (task 2-3)"
```

---

## Plan Summary

| Task | 内容 | 变更类型 |
|------|------|---------|
| 1 | Flyway 迁移：arrears_order_ids 列 | 新增 |
| 2 | ParkingOrder 实体新增字段 | 修改 |
| 3 | ParkingOrderMapper 新增查询 | 修改 |
| 4 | ParkingOrderService 新增查询 | 修改 |
| 5 | ReleaseDecision 新增欠费决策 | 修改 |
| 6 | ExitRecord 新增决策常量 | 修改 |
| 7 | ExitService 核心欠费逻辑集成 | 修改（核心） |
| 8 | MockPaymentService ARREARS 支付 | 修改 |
| 9 | MiniParkingRecordVO 字段扩展 | 修改 |
| 10 | MiniUserServiceImpl 映射扩展 | 修改 |
| 11 | MiniMessage 消息类型常量 | 修改 |
| 12 | MiniMessageService 消息方法 | 修改 |
| 13 | allowArrears/payArrears 幂等审查 | 审查（只读） |
| 14 | Mapper 单元测试 | 新增 |
| 15 | ExitService 欠费场景测试 | 新增 |
| 16 | MockPaymentService ARREARS 测试 | 修改 |
| 17 | 全量编译 + 测试验证 | 验证 |
