# 无牌车临时车牌处理 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 当相机车牌识别返回空结果时，根据车场参数自动或手动生成临时车牌，全链路参与停车计费（入场→在场→计费→出场）。

**Architecture:** 在 `RecognitionEventConsumer` 内联路由识别失败事件；通过 `TempPlateNumberGenerator`（Redis INCR）按车场+日期生成临时车牌号；`TempPlateService` 处理岗亭端手动入场/出场匹配；`BoothTempPlateController` 提供 REST API；三张表新增 `temp_plate_flag` 字段区分记录类型。

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Flyway + Redis + RabbitMQ + WebSocket | Vue 3 + Ant Design Vue

**Commit format:** `[BOOTH-005] feat: description`

---

## 文件结构总览

### 新建文件
| 文件 | 职责 |
| :--- | :--- |
| `db/migration/V20260717005__add_temp_plate_flag.sql` | Flyway：三表新增 tempPlateFlag 列 |
| `entity/ParkingRecord.java` 新增字段 | tempPlateFlag |
| `entity/ParkingOrder.java` 新增字段 | tempPlateFlag |
| `entity/RecognitionEventLog.java` 新增字段 | tempPlateFlag |
| `service/TempPlateNumberGenerator.java` | Redis INCR 临时车牌号生成 |
| `service/TempPlateService.java` | 岗亭端入场/出场业务逻辑 |
| `dto/TempPlateEntryRequest.java` | 入场请求体 DTO |
| `dto/TempPlateExitRequest.java` | 出场匹配请求体 DTO |
| `controller/BoothTempPlateController.java` | 岗亭端临时车牌 REST API |

### 修改文件
| 文件 | 改动 |
| :--- | :--- |
| `event/RecognitionEventConsumer.java` | 注入新依赖；空车牌→识别失败接管；新增 handleRecognitionFailure/handleManualAlert/handleAutoRelease；updateEventLog 新增 RECOGNITION_FAILED 分支；ProcessingResult 新增字段 |
| `ws/BoothWebSocketPublisher.java` | 新增 sendRecognitionFailedAlert() |
| `vo/ParkingRecordAdminVO.java` | 新增 tempPlateFlag 字段 |
| `controller/ParkingRecordAdminController.java` | 查询返回 tempPlateFlag、筛选支持 |
| 岗亭端 booth-web | 告警弹窗 + 无牌车处理区 |
| 运营端 admin-web | 通行记录列表 "临" 标签 |

---

### Task 1: Flyway 数据库迁移 — 三表新增 temp_plate_flag 列

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260717005__add_temp_plate_flag.sql`

- [ ] **Step 1: 创建 Flyway 迁移脚本**

```sql
-- =============================================================================
-- 任务包 3-4：无牌车临时车牌 — 三表新增 temp_plate_flag 字段
-- =============================================================================

ALTER TABLE parking_record
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE parking_order
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE recognition_event_log
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';
```

- [ ] **Step 2: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-boot/src/main/resources/db/migration/V20260717005__add_temp_plate_flag.sql
git commit -m "[BOOTH-005] feat: add temp_plate_flag column to parking_record, parking_order, recognition_event_log"
```

---

### Task 2: Entity 层新增 tempPlateFlag 字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ParkingRecord.java`
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java`
- Modify: `parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java`

- [ ] **Step 1: ParkingRecord.java — 在 `deletedAt` 字段声明之前添加 tempPlateFlag**

Read the file first, locate the section near `private LocalDateTime deletedAt;` around line 84.

Add this field just before `private LocalDateTime createdAt;` (around line 79):

```java
    /** 临时车牌标记：0=正式车牌, 1=临时车牌 */
    private Integer tempPlateFlag;
```

Then add getter/setter after the existing `setDeletedAt` (after line 137):

```java
    public Integer getTempPlateFlag() { return tempPlateFlag; }
    public void setTempPlateFlag(Integer tempPlateFlag) { this.tempPlateFlag = tempPlateFlag; }
```

- [ ] **Step 2: ParkingOrder.java — 在 `deletedAt` 字段声明之前添加 tempPlateFlag**

Locate near `private LocalDateTime deletedAt;` around line 127.

Add this field just before `private LocalDateTime createdAt;` (around line 129):

```java
    /** 临时车牌标记：0=正式车牌, 1=临时车牌 */
    private Integer tempPlateFlag;
```

Then add getter/setter after the `setArrearsOrderIds` method (after line 269):

```java
    public Integer getTempPlateFlag() { return tempPlateFlag; }
    public void setTempPlateFlag(Integer tempPlateFlag) { this.tempPlateFlag = tempPlateFlag; }
```

- [ ] **Step 3: RecognitionEventLog.java — 在 `createdAt` 字段声明之前添加 tempPlateFlag**

Locate near `private LocalDateTime createdAt;` around line 85.

Add this field just before `private LocalDateTime createdAt;`:

```java
    /** 临时车牌标记：0=正式车牌, 1=临时车牌 */
    private Integer tempPlateFlag;
```

Then add getter/setter after the `setCreatedAt` method (after line 145):

```java
    public Integer getTempPlateFlag() { return tempPlateFlag; }
    public void setTempPlateFlag(Integer tempPlateFlag) { this.tempPlateFlag = tempPlateFlag; }
```

- [ ] **Step 4: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/ParkingRecord.java \
        parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java \
        parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java
git commit -m "[BOOTH-005] feat: add tempPlateFlag field to ParkingRecord, ParkingOrder, RecognitionEventLog entities"
```

---

### Task 3: TempPlateNumberGenerator — Redis INCR 临时车牌号生成器

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/service/TempPlateNumberGenerator.java`

- [ ] **Step 1: 创建 TempPlateNumberGenerator.java**

```java
package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * 临时车牌号生成器（任务包 3-4）。
 * <p>
 * 通过 Redis INCR 按车场+日期维度生成唯一临时车牌号，
 * 格式为 "临" + yyMMdd + 两位序号（如 "临26071701"）。
 * <p>
 * 单日单车场上限 99。Redis 不可用时降级为时间戳方案。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class TempPlateNumberGenerator {

    private static final Logger log = LoggerFactory.getLogger(TempPlateNumberGenerator.class);

    private static final int MAX_SEQ_PER_DAY = 99;
    private static final String KEY_PREFIX = "temp_plate:";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyMMdd");

    private final StringRedisTemplate redisTemplate;

    public TempPlateNumberGenerator(ObjectProvider<StringRedisTemplate> redisTemplateProvider) {
        this.redisTemplate = redisTemplateProvider.getIfAvailable();
    }

    /**
     * 生成临时车牌号（消耗序号）。
     *
     * @param parkingLotId 车场 ID
     * @return 临时车牌号（如 "临26071701"）
     * @throws BusinessException 当日序号超过 99 时抛出
     */
    public String generate(Long parkingLotId) {
        if (redisTemplate == null) {
            return fallbackGenerate(parkingLotId);
        }

        try {
            String today = LocalDate.now().format(DATE_FORMAT);
            String key = KEY_PREFIX + parkingLotId + ":" + today;

            Long seq = redisTemplate.opsForValue().increment(key);
            // 首次使用时设置过期：至当日 23:59:59 后自动清除
            if (seq != null && seq == 1) {
                long secondsUntilMidnight = ChronoUnit.SECONDS.between(
                        LocalDateTime.now(),
                        LocalDate.now().plusDays(1).atStartOfDay());
                redisTemplate.expire(key, Duration.ofSeconds(Math.max(secondsUntilMidnight, 60)));
            }

            if (seq == null || seq > MAX_SEQ_PER_DAY) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "当日临时车牌号已用完（已达" + MAX_SEQ_PER_DAY + "个）");
            }

            return "临" + today + String.format("%02d", seq);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis 临时车牌号生成失败，降级使用时间戳: lotId={} error={}", parkingLotId, e.getMessage());
            return fallbackGenerate(parkingLotId);
        }
    }

    /**
     * 获取建议临时车牌号（预览，不消耗序号）。
     *
     * @param parkingLotId 车场 ID
     * @return 建议临时车牌号
     */
    public String suggest(Long parkingLotId) {
        if (redisTemplate == null) {
            return fallbackGenerate(parkingLotId);
        }

        try {
            String today = LocalDate.now().format(DATE_FORMAT);
            String key = KEY_PREFIX + parkingLotId + ":" + today;
            String currentSeq = redisTemplate.opsForValue().get(key);
            long seq = (currentSeq != null ? Long.parseLong(currentSeq) : 0) + 1;
            if (seq > MAX_SEQ_PER_DAY) {
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        "当日临时车牌号已用完");
            }
            return "临" + today + String.format("%02d", seq);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Redis 建议车牌号生成失败: lotId={} error={}", parkingLotId, e.getMessage());
            return fallbackGenerate(parkingLotId);
        }
    }

    /**
     * 降级方案：使用时间戳后缀生成临时车牌。
     */
    private String fallbackGenerate(Long parkingLotId) {
        String today = LocalDate.now().format(DATE_FORMAT);
        String suffix = String.format("%04d", System.currentTimeMillis() % 10000);
        String plate = "临" + today + suffix;
        log.warn("临时车牌号降级生成（非 Redis 方案）: lotId={} plate={}", parkingLotId, plate);
        return plate;
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/TempPlateNumberGenerator.java
git commit -m "[BOOTH-005] feat: add TempPlateNumberGenerator with Redis INCR temp plate number generation"
```

---

### Task 4: DTO 请求体 — TempPlateEntryRequest + TempPlateExitRequest

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/dto/TempPlateEntryRequest.java`
- Create: `parking-system/src/main/java/com/jushan/system/dto/TempPlateExitRequest.java`

- [ ] **Step 1: 创建 TempPlateEntryRequest.java**

```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 岗亭端临时车牌入场请求体（任务包 3-4）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class TempPlateEntryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "车场ID不能为空")
    private Long parkingLotId;

    @NotNull(message = "车道ID不能为空")
    private Long laneId;

    /** 可选：岗亭端传入固定车牌号；为空时自动生成 */
    private String tempPlate;

    // ==================== getter / setter ====================

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public String getTempPlate() { return tempPlate; }
    public void setTempPlate(String tempPlate) { this.tempPlate = tempPlate; }
}
```

- [ ] **Step 2: 创建 TempPlateExitRequest.java**

```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 岗亭端临时车牌出场匹配请求体（任务包 3-4）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class TempPlateExitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "临时车牌号不能为空")
    private String tempPlate;

    @NotNull(message = "车场ID不能为空")
    private Long parkingLotId;

    @NotNull(message = "车道ID不能为空")
    private Long laneId;

    // ==================== getter / setter ====================

    public String getTempPlate() { return tempPlate; }
    public void setTempPlate(String tempPlate) { this.tempPlate = tempPlate; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }
}
```

- [ ] **Step 3: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/dto/TempPlateEntryRequest.java \
        parking-system/src/main/java/com/jushan/system/dto/TempPlateExitRequest.java
git commit -m "[BOOTH-005] feat: add TempPlateEntryRequest and TempPlateExitRequest DTOs"
```

---

### Task 5: TempPlateService — 岗亭端手动入场/出场业务逻辑

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/service/TempPlateService.java`

- [ ] **Step 1: 创建 TempPlateService.java**

```java
package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.ExitRecord;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.mapper.ExitRecordMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 临时车牌服务（任务包 3-4）。
 * <p>
 * 岗亭端手动操作临时车牌的业务逻辑：
 * <ul>
 *   <li>手动入场：创建在场记录 + 预订单 + 开闸</li>
 *   <li>出场匹配：查在场记录 → 计费 → 现金订单 → 完成记录 → 开闸 → 出场记录</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class TempPlateService {

    private static final Logger log = LoggerFactory.getLogger(TempPlateService.class);

    private final ParkingRecordMapper recordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingOrderMapper orderMapper;
    private final ParkingOrderService parkingOrderService;
    private final ExitRecordMapper exitRecordMapper;
    private final BillingEngine billingEngine;
    private final DeviceService deviceService;
    private final MockPaymentService mockPaymentService;

    public TempPlateService(ParkingRecordMapper recordMapper,
                             ParkingLotMapper parkingLotMapper,
                             ParkingOrderMapper orderMapper,
                             ParkingOrderService parkingOrderService,
                             ExitRecordMapper exitRecordMapper,
                             BillingEngine billingEngine,
                             DeviceService deviceService,
                             MockPaymentService mockPaymentService) {
        this.recordMapper = recordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.orderMapper = orderMapper;
        this.parkingOrderService = parkingOrderService;
        this.exitRecordMapper = exitRecordMapper;
        this.billingEngine = billingEngine;
        this.deviceService = deviceService;
        this.mockPaymentService = mockPaymentService;
    }

    // ==================== 手动入场 ====================

    /**
     * 岗亭手动无牌车入场：创建在场记录 + 预订单 + 开闸。
     *
     * @param parkingLotId 车场 ID
     * @param laneId       车道 ID
     * @param tempPlate    临时车牌号
     * @param boothUserId  岗亭操作员 ID
     * @return 创建的停车记录
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingRecord manualEntry(Long parkingLotId, Long laneId,
                                      String tempPlate, Long boothUserId) {
        // 1. 校验临时车牌格式
        validateTempPlateFormat(tempPlate);

        // 2. 检查是否已有同临牌的 PARKING 记录
        ParkingRecord existing = recordMapper.selectOne(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStandardizedPlate, tempPlate)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING));
        if (existing != null) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "该临时车牌已有在场记录: " + tempPlate);
        }

        // 3. 获取车场信息
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "停车场不存在");
        }

        // 4. 创建 ParkingRecord（tempPlateFlag=1）
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(lot.getTenantId());
        record.setParkingLotId(parkingLotId);
        record.setLaneId(laneId);
        record.setStandardizedPlate(tempPlate);
        record.setTempPlateFlag(1);
        record.setStatus(ParkingRecord.STATUS_PARKING);
        record.setEntryTime(LocalDateTime.now());
        record.setEntryImagePath(null);
        recordMapper.insert(record);

        // 5. 创建预订单
        ParkingOrder preOrder = parkingOrderService.createPreOrder(record);
        preOrder.setTempPlateFlag(1);
        orderMapper.updateById(preOrder);

        // 6. 更新停车场容量
        parkingLotMapper.update(null,
                new LambdaUpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles + 1")
                        .setSql("remaining_spaces = remaining_spaces - 1")
                        .eq(ParkingLot::getId, parkingLotId));

        // 7. 开闸放行
        try {
            deviceService.openGateByLane(laneId, "岗亭手动无牌车入场(" + tempPlate + ")");
        } catch (Exception e) {
            log.warn("岗亭手动入场开闸失败: plate={} laneId={} error={}",
                    tempPlate, laneId, e.getMessage());
        }

        log.info("岗亭手动无牌车入场: recordId={} tempPlate={} lotId={} userId={}",
                record.getId(), tempPlate, parkingLotId, boothUserId);
        return record;
    }

    // ==================== 出场匹配 ====================

    /**
     * 岗亭手动无牌车出场匹配计费。
     * <p>
     * 完整链路：查在场记录 → 计费 → 现金订单 → 模拟支付 → 完成记录 → 减容量 → 开闸 → 出场记录。
     *
     * @param tempPlate    临时车牌号
     * @param parkingLotId 车场 ID
     * @param laneId       车道 ID
     * @param boothUserId  岗亭操作员 ID
     * @return 出场处理结果（简化版，含 exitRecordId / orderId / feeCents）
     */
    @Transactional(rollbackFor = Exception.class)
    public ExitResult handleExitMatch(String tempPlate,
                                                    Long parkingLotId,
                                                    Long laneId,
                                                    Long boothUserId) {
        // 1. 校验临时车牌格式
        validateTempPlateFormat(tempPlate);

        // 2. 查询在场记录
        ParkingRecord record = recordMapper.selectOne(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStandardizedPlate, tempPlate)
                        .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING));
        if (record == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "未找到该临时车牌的在场记录: " + tempPlate);
        }

        // 3. 计算费用
        LocalDateTime exitTime = LocalDateTime.now();
        int feeCents;
        if (record.getRuleSnapshot() != null && !record.getRuleSnapshot().isBlank()) {
            try {
                feeCents = billingEngine.calculateFeeFromSnapshot(
                        record.getRuleSnapshot(), record.getEntryTime(), exitTime);
            } catch (BusinessException e) {
                feeCents = billingEngine.calculateFee(
                        parkingLotId, record.getEntryTime(), exitTime);
            }
        } else {
            feeCents = billingEngine.calculateFee(
                    parkingLotId, record.getEntryTime(), exitTime);
        }

        // 4. 创建订单（PENDING_PAY, payScene=AT_EXIT, payChannel=CASH）
        ParkingOrder order = parkingOrderService.createOrderInternal(
                record, feeCents, null,
                ParkingOrder.PAY_SCENE_AT_EXIT, laneId, null,
                "岗亭无牌车出场计费");
        order.setTempPlateFlag(1);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
        orderMapper.updateById(order);

        // 5. 模拟支付：preparePay → confirmPay
        mockPaymentService.preparePay(order);
        boolean paid = mockPaymentService.confirmPay(order.getId(),
                "BOOTH_USER_" + boothUserId);
        if (!paid) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "模拟现金支付失败，请重试");
        }

        // 6. 完成停车记录
        record.setStatus(ParkingRecord.STATUS_COMPLETED);
        record.setExitTime(exitTime);
        recordMapper.updateById(record);

        // 7. 完成订单（PAID → COMPLETED）
        parkingOrderService.completeOrder(order.getId(), exitTime);

        // 8. 减停车场容量
        parkingLotMapper.update(null,
                new UpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles - 1")
                        .setSql("remaining_spaces = remaining_spaces + 1")
                        .eq("id", parkingLotId));

        // 9. 开闸放行
        try {
            deviceService.openGateByLane(laneId, "岗亭手动出场开闸(" + tempPlate + ")");
        } catch (Exception e) {
            log.warn("岗亭手动出场开闸失败: plate={} laneId={} error={}",
                    tempPlate, laneId, e.getMessage());
        }

        // 10. 创建出场记录
        ExitRecord exitRecord = new ExitRecord();
        exitRecord.setTenantId(record.getTenantId());
        exitRecord.setParkingLotId(parkingLotId);
        exitRecord.setParkingRecordId(record.getId());
        exitRecord.setLaneId(laneId);
        exitRecord.setStandardizedPlate(tempPlate);
        exitRecord.setExitTime(exitTime);
        exitRecord.setFeeCents(Math.max(0, feeCents));
        exitRecord.setPaidCents(Math.max(0, feeCents));
        exitRecord.setReleaseDecision(ExitRecord.DECISION_PAID);
        exitRecord.setOrderId(order.getId());
        exitRecord.setReason("岗亭手动无牌车出场，现金支付");
        exitRecord.setCreatedAt(LocalDateTime.now());
        exitRecord.setUpdatedAt(LocalDateTime.now());
        exitRecordMapper.insert(exitRecord);

        log.info("岗亭手动无牌车出场: recordId={} tempPlate={} feeCents={} orderId={} userId={}",
                record.getId(), tempPlate, feeCents, order.getId(), boothUserId);

        return ExitResult.of(
                ReleaseDecision.paid(),
                exitRecord.getId(), order.getId(), feeCents);
    }

    // ==================== 车牌号格式校验 ====================

    /**
     * 校验临时车牌格式：非空 + 长度≤20 + 不以空格开头。
     */
    private void validateTempPlateFormat(String tempPlate) {
        if (tempPlate == null || tempPlate.isBlank()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "临时车牌号不能为空");
        }
        if (tempPlate.length() > 20) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "临时车牌号长度不能超过20位");
        }
        if (tempPlate.startsWith(" ")) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "临时车牌号不能以空格开头");
        }
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/TempPlateService.java
git commit -m "[BOOTH-005] feat: add TempPlateService for booth manual entry and exit match"
```

---

### Task 6: BoothTempPlateController — 岗亭端 REST API

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/BoothTempPlateController.java`

- [ ] **Step 1: 创建 BoothTempPlateController.java**

```java
package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.TempPlateEntryRequest;
import com.jushan.system.dto.TempPlateExitRequest;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.service.ExitResult;
import com.jushan.system.service.TempPlateNumberGenerator;
import com.jushan.system.service.TempPlateService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 岗亭端临时车牌处理接口（任务包 3-4）。
 * <p>
 * 提供岗亭端的无牌车处理能力：
 * <ul>
 *   <li>建议临时车牌号（预览）</li>
 *   <li>手动无牌车入场（创建记录 + 开闸）</li>
 *   <li>手动无牌车出场匹配计费</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/booth/temp-plate")
public class BoothTempPlateController {

    private final TempPlateService tempPlateService;
    private final TempPlateNumberGenerator numberGenerator;

    public BoothTempPlateController(TempPlateService tempPlateService,
                                      TempPlateNumberGenerator numberGenerator) {
        this.tempPlateService = tempPlateService;
        this.numberGenerator = numberGenerator;
    }

    /**
     * 生成建议临时车牌号（预览，不消耗序号）。
     */
    @GetMapping("/suggest")
    @RequirePermission("booth:monitor")
    public R<Map<String, String>> suggest(@RequestParam Long parkingLotId) {
        String suggested = numberGenerator.suggest(parkingLotId);
        return R.ok(Map.of("tempPlate", suggested));
    }

    /**
     * 岗亭手动无牌车入场。
     * <p>
     * 创建在场记录 + 预订单 + 开闸。若 tempPlate 为空则自动生成（消耗序号）。
     */
    @PostMapping("/entry")
    @RequirePermission("booth:monitor")
    @BusinessLog(value = "岗亭手动无牌车入场", module = "temp-plate", operationType = "CREATE")
    public R<Map<String, Object>> manualEntry(
            @RequestBody @Valid TempPlateEntryRequest request) {
        Long boothUserId = TenantContext.getUserId();

        String tempPlate = request.getTempPlate();
        if (tempPlate == null || tempPlate.isBlank()) {
            tempPlate = numberGenerator.generate(request.getParkingLotId());
        }

        ParkingRecord record = tempPlateService.manualEntry(
                request.getParkingLotId(), request.getLaneId(),
                tempPlate, boothUserId);

        return R.ok(Map.of(
                "recordId", record.getId(),
                "tempPlate", tempPlate,
                "entryTime", record.getEntryTime().toString()));
    }

    /**
     * 岗亭手动无牌车出场匹配计费。
     * <p>
     * 通过临时车牌匹配在场记录 → 计费 → 现金支付 → 开闸放行。
     */
    @PostMapping("/exit-match")
    @RequirePermission("booth:monitor")
    @BusinessLog(value = "岗亭手动无牌车出场", module = "temp-plate", operationType = "UPDATE")
    public R<Map<String, Object>> exitMatch(
            @RequestBody @Valid TempPlateExitRequest request) {
        Long boothUserId = TenantContext.getUserId();

        ExitResult result = tempPlateService.handleExitMatch(
                request.getTempPlate(), request.getParkingLotId(),
                request.getLaneId(), boothUserId);

        return R.ok(Map.of(
                "exitRecordId", result.getExitRecordId(),
                "orderId", result.getOrderId(),
                "feeCents", result.getFeeCents(),
                "message", result.getReason()));
    }
}
```

- [ ] **Step 2: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/BoothTempPlateController.java
git commit -m "[BOOTH-005] feat: add BoothTempPlateController REST API for booth temp plate operations"
```

---

### Task 7: BoothWebSocketPublisher — 新增 sendRecognitionFailedAlert 方法

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/ws/BoothWebSocketPublisher.java`

- [ ] **Step 1: 定位文件并添加方法**

In `BoothWebSocketPublisher.java`, after the `sendRemoteGateAlert` method (around line 222), and before the `private void send(...)` method (around line 227), add:

```java
    /**
     * 推送识别失败告警（无牌车处理）到岗亭端（任务包 3-4）。
     *
     * @param parkingLotId 停车场 ID（可信）
     * @param eventId      识别事件 UUID
     * @param logId        事件日志自增 ID
     * @param lotId        停车场 ID（冗余便于前端使用）
     * @param laneId       车道 ID
     * @param direction    方向（ENTRY/EXIT）
     * @param imagePath    全景图路径
     * @param eventTime    事件时间
     */
    public void sendRecognitionFailedAlert(Long parkingLotId, String eventId,
            Long logId, Long lotId, Long laneId, String direction,
            String imagePath, java.time.LocalDateTime eventTime) {
        if (parkingLotId == null) {
            return;
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "RECOGNITION_FAILED");
            payload.put("eventId", eventId);
            payload.put("logId", logId);
            payload.put("parkingLotId", lotId);
            payload.put("laneId", laneId);
            payload.put("direction", direction);
            payload.put("imagePath", imagePath);
            payload.put("eventTime", format(eventTime));
            payload.put("message", "入口识别失败，请手动处理无牌车辆");

            send(String.format(TOPIC_ALERTS, parkingLotId), payload);
            log.debug("识别失败告警已推送到岗亭: lotId={}, eventId={}", parkingLotId, eventId);
        } catch (Exception e) {
            log.warn("识别失败告警 WebSocket 推送失败（不影响主业务）: lotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }
```

Also need to add the import for `LocalDateTime` (already imported) and `java.util.HashMap` and `java.util.Map` — check if these are already imported at the top. Looking at the existing imports, `java.util.HashMap` and `java.util.Map` are already there. Good.

- [ ] **Step 2: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/ws/BoothWebSocketPublisher.java
git commit -m "[BOOTH-005] feat: add sendRecognitionFailedAlert method to BoothWebSocketPublisher"
```

---

### Task 8: RecognitionEventConsumer 改造 — 识别失败接管

这是本次改动中最重要的文件变更。需要：
1. 修改 `ProcessingResult` 内部类新增字段
2. 修改 `validateAndStandardize()` — 空车牌检查后移到设备/停车场校验之后
3. 在 `onRecognitionEvent()` 主流程中插入识别失败接管分支
4. 新增 `handleRecognitionFailure()`、`handleManualAlert()`、`handleAutoRelease()` 方法
5. 修改 `updateEventLog()` 支持 RECOGNITION_FAILED 状态
6. 新增依赖注入

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/event/RecognitionEventConsumer.java`

- [ ] **Step 1: 为 Consumer 新增字段和构造函数参数**

当前字段声明（lines 66-72）:
```java
    private final MessageIdempotency messageIdempotency;
    private final DeviceMapper deviceMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final EntryService entryService;
    private final ExitService exitService;
```

替换为（新增 `tempPlateNumberGenerator`, `boothWebSocketPublisher`, `monitorAlertService`, `paramResolver`, `recordMapper`, `transactionTemplate`）:

```java
    private final MessageIdempotency messageIdempotency;
    private final DeviceMapper deviceMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final EntryService entryService;
    private final ExitService exitService;
    private final TempPlateNumberGenerator tempPlateNumberGenerator;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final MonitorAlertService monitorAlertService;
    private final ParamResolver paramResolver;
    private final ParkingRecordMapper recordMapper;
    private final DeviceService deviceService;
```

当前构造函数 (lines 74-88) 替换为:

```java
    public RecognitionEventConsumer(MessageIdempotency messageIdempotency,
                                     DeviceMapper deviceMapper,
                                     ParkingLotMapper parkingLotMapper,
                                     ParkingLaneMapper laneMapper,
                                     RecognitionEventLogMapper eventLogMapper,
                                     EntryService entryService,
                                     ExitService exitService,
                                     TempPlateNumberGenerator tempPlateNumberGenerator,
                                     BoothWebSocketPublisher boothWebSocketPublisher,
                                     MonitorAlertService monitorAlertService,
                                     ParamResolver paramResolver,
                                     ParkingRecordMapper recordMapper,
                                     DeviceService deviceService) {
        this.messageIdempotency = messageIdempotency;
        this.deviceMapper = deviceMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.eventLogMapper = eventLogMapper;
        this.entryService = entryService;
        this.exitService = exitService;
        this.tempPlateNumberGenerator = tempPlateNumberGenerator;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.monitorAlertService = monitorAlertService;
        this.paramResolver = paramResolver;
        this.recordMapper = recordMapper;
        this.deviceService = deviceService;
    }
```

- [ ] **Step 2: 在类顶部新增 imports**

需要新增的 imports（排在现有 import 块的末尾）:

```java
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.service.DeviceService;
import com.jushan.system.service.MonitorAlertService;
import com.jushan.system.service.ParamResolver;
import com.jushan.system.service.TempPlateNumberGenerator;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.ws.BoothWebSocketPublisher;
```

- [ ] **Step 3: 修改 `ProcessingResult` 内部类 — 新增字段**

当前 `ProcessingResult` (lines 338-347):

```java
    private static class ProcessingResult {
        boolean success = false;
        String standardizedPlate;
        String failureReason;

        void fail(String reason) {
            this.success = false;
            this.failureReason = reason;
        }
    }
```

替换为:

```java
    private static class ProcessingResult {
        boolean success = false;
        boolean recognitionFailed = false;
        String standardizedPlate;
        String failureReason;
        Long parkingLotId;
        Long tenantId;
        Long laneId;
        Long deviceId;
        LocalDateTime eventTime;

        void fail(String reason) {
            this.success = false;
            this.failureReason = reason;
        }
    }
```

- [ ] **Step 4: 修改 `validateAndStandardize()` — 空车牌后移到设备/停车场校验之后**

当前方法体 `validateAndStandardize()` (lines 185-281)。需要将空车牌检查从开头位置（lines 189-193）移到设备/停车场/车道全部校验通过之后（line 273 之后）。

精确的替换方案：

**旧代码** (lines 185-281 — `validateAndStandardize` 完整方法):

```java
    private ProcessingResult validateAndStandardize(RecognitionEventPayload payload) {
        ProcessingResult result = new ProcessingResult();

        // 3.1 车牌标准化
        String rawPlate = payload.getPlateNumber();
        if (rawPlate == null || rawPlate.isBlank()) {
            result.fail("车牌号为空");
            return result;
        }

        String standardized = PlateStandardizer.normalize(rawPlate);
        if (standardized == null || standardized.isEmpty()) {
            result.fail("车牌号标准化后为空，原始值: "
                    + (rawPlate.length() > 50 ? rawPlate.substring(0, 50) + "..." : rawPlate));
            return result;
        }
        result.standardizedPlate = standardized;

        // 3.2 设备校验
        if (payload.getDeviceId() == null) {
            result.fail("设备 ID 为空");
            return result;
        }

        Device device = deviceMapper.selectByIdIgnoreTenant(payload.getDeviceId());
        if (device == null) {
            result.fail("设备不存在: deviceId=" + payload.getDeviceId());
            return result;
        }
        if (!"ENABLED".equals(device.getStatus())) {
            result.fail("设备已停用: deviceId=" + payload.getDeviceId());
            return result;
        }
        if (!"CAMERA".equals(device.getDeviceType())) {
            result.fail("设备类型不是相机: deviceType=" + device.getDeviceType());
            return result;
        }

        // 3.3 停车场校验（只验证存在性和跨租户/停车场一致性）
        //     停用状态是否允许入场由 EntryService 按 disableNewEntries 配置判断（T30）
        if (device.getParkingLotId() == null) {
            result.fail("设备未绑定停车场: deviceId=" + device.getId());
            return result;
        }

        ParkingLot parkingLot = parkingLotMapper.selectByIdIgnoreTenant(device.getParkingLotId());
        if (parkingLot == null) {
            result.fail("停车场不存在: parkingLotId=" + device.getParkingLotId());
            return result;
        }

        // 3.4 跨停车场/租户校验：消息中的 tenantId/parkingLotId 必须与可信记录一致
        if (payload.getTenantId() != null
                && !payload.getTenantId().equals(parkingLot.getTenantId())) {
            result.fail(String.format("租户不匹配: 消息声称=%d, 可信=%d",
                    payload.getTenantId(), parkingLot.getTenantId()));
            return result;
        }

        if (payload.getParkingLotId() != null
                && !payload.getParkingLotId().equals(parkingLot.getId())) {
            result.fail(String.format("停车场不匹配: 消息声称=%d, 可信=%d",
                    payload.getParkingLotId(), parkingLot.getId()));
            return result;
        }

        // 3.5 车道方向校验
        if (device.getLaneId() != null) {
            ParkingLane lane = laneMapper.selectByIdIgnoreTenant(device.getLaneId());
            if (lane == null) {
                result.fail("车道不存在: laneId=" + device.getLaneId());
                return result;
            }
            if (lane.getStatus() == null || lane.getStatus() != 1) {
                result.fail("车道已停用: " + lane.getName());
                return result;
            }
            String direction = payload.getDirection();
            if (direction != null && lane.getType() != null) {
                // type: 1=ENTRY, 2=EXIT, 3=MIXED
                if (lane.getType() != 3 && !direction.equals(
                        lane.getType() == 1 ? "ENTRY" : "EXIT")) {
                    result.fail(String.format("方向不匹配: 事件方向=%s, 车道方向=%d (lane=%s)",
                            direction, lane.getType(), lane.getName()));
                    return result;
                }
            }
        }

        // 3.6 基本格式校验（非阻塞性，仅记录）
        if (!PlateStandardizer.isValidFormat(standardized)) {
            log.info("车牌格式可能异常（非阻塞）: plate={} eventId={}", standardized, payload.getEventId());
        }

        result.success = true;
        return result;
    }
```

**新代码**（空车牌检查移到所有设备/停车场/车道校验之后，识别失败时保留上下文）:

```java
    private ProcessingResult validateAndStandardize(RecognitionEventPayload payload) {
        ProcessingResult result = new ProcessingResult();

        // 3.1 车牌标准化：空车牌不立即失败，仅跳过标准化
        String rawPlate = payload.getPlateNumber();
        boolean plateIsEmpty = (rawPlate == null || rawPlate.isBlank());
        String standardized = null;
        if (!plateIsEmpty) {
            standardized = PlateStandardizer.normalize(rawPlate);
            if (standardized == null || standardized.isEmpty()) {
                result.fail("车牌号标准化后为空，原始值: "
                        + (rawPlate.length() > 50 ? rawPlate.substring(0, 50) + "..." : rawPlate));
                return result;
            }
            result.standardizedPlate = standardized;
        }

        // 3.2 设备校验
        if (payload.getDeviceId() == null) {
            result.fail("设备 ID 为空");
            return result;
        }

        Device device = deviceMapper.selectByIdIgnoreTenant(payload.getDeviceId());
        if (device == null) {
            result.fail("设备不存在: deviceId=" + payload.getDeviceId());
            return result;
        }
        if (!"ENABLED".equals(device.getStatus())) {
            result.fail("设备已停用: deviceId=" + payload.getDeviceId());
            return result;
        }
        if (!"CAMERA".equals(device.getDeviceType())) {
            result.fail("设备类型不是相机: deviceType=" + device.getDeviceType());
            return result;
        }

        // 3.3 停车场校验
        if (device.getParkingLotId() == null) {
            result.fail("设备未绑定停车场: deviceId=" + device.getId());
            return result;
        }

        ParkingLot parkingLot = parkingLotMapper.selectByIdIgnoreTenant(device.getParkingLotId());
        if (parkingLot == null) {
            result.fail("停车场不存在: parkingLotId=" + device.getParkingLotId());
            return result;
        }

        // 3.4 跨停车场/租户校验
        if (payload.getTenantId() != null
                && !payload.getTenantId().equals(parkingLot.getTenantId())) {
            result.fail(String.format("租户不匹配: 消息声称=%d, 可信=%d",
                    payload.getTenantId(), parkingLot.getTenantId()));
            return result;
        }

        if (payload.getParkingLotId() != null
                && !payload.getParkingLotId().equals(parkingLot.getId())) {
            result.fail(String.format("停车场不匹配: 消息声称=%d, 可信=%d",
                    payload.getParkingLotId(), parkingLot.getId()));
            return result;
        }

        // 3.5 车道方向校验
        if (device.getLaneId() != null) {
            ParkingLane lane = laneMapper.selectByIdIgnoreTenant(device.getLaneId());
            if (lane == null) {
                result.fail("车道不存在: laneId=" + device.getLaneId());
                return result;
            }
            if (lane.getStatus() == null || lane.getStatus() != 1) {
                result.fail("车道已停用: " + lane.getName());
                return result;
            }
            String direction = payload.getDirection();
            if (direction != null && lane.getType() != null) {
                if (lane.getType() != 3 && !direction.equals(
                        lane.getType() == 1 ? "ENTRY" : "EXIT")) {
                    result.fail(String.format("方向不匹配: 事件方向=%s, 车道方向=%d (lane=%s)",
                            direction, lane.getType(), lane.getName()));
                    return result;
                }
            }
        }

        // 3.6 (新位置) 识别失败接管：空车牌 + 设备/停车场/车道校验全通过 → 识别失败
        if (plateIsEmpty) {
            result.recognitionFailed = true;
            result.parkingLotId = device.getParkingLotId();
            result.tenantId = parkingLot.getTenantId();
            result.laneId = payload.getLaneId() != null ? payload.getLaneId() : device.getLaneId();
            result.deviceId = payload.getDeviceId();
            result.eventTime = payload.getEventTime() != null ? payload.getEventTime() : LocalDateTime.now();
            return result;
        }

        // 3.7 基本格式校验（非阻塞性，仅正式车牌执行）
        if (!PlateStandardizer.isValidFormat(standardized)) {
            log.info("车牌格式可能异常（非阻塞）: plate={} eventId={}", standardized, payload.getEventId());
        }

        result.success = true;
        return result;
    }
```

- [ ] **Step 5: 在 `onRecognitionEvent` 主流程中插入识别失败接管分支**

在 `onRecognitionEvent()` (lines 100-178) 中，`ProcessingResult result = validateAndStandardize(payload);` 之后、`if (result.success)` 之前（当前 line 125-128），插入:

定位 `// ---- 4. T30 入场处理 / P004 出场处理 ----` 之前（line 127前后）。

替换 lines 125-163 的块为:

```java
            // ---- 3.5 识别失败接管（无牌车处理） ----
            if (result.recognitionFailed) {
                handleRecognitionFailure(payload, result);
                updateEventLog(payload, result);
                messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);
                return;
            }

            // ---- 4. T30 入场处理 / P004 出场处理 ----
            if (result.success) {
                if ("ENTRY".equals(payload.getDirection())) {
                    try {
                        entryService.handleEntry(payload, result.standardizedPlate);
                    } catch (BusinessException e) {
                        log.warn("入场处理失败: eventId={} plate={} reason={}",
                                eventId, result.standardizedPlate, e.getMessage());
                        result.fail("入场处理失败: " + e.getMessage());
                    }
                } else if ("EXIT".equals(payload.getDirection())) {
                    try {
                        exitService.handleExit(payload, result.standardizedPlate);
                    } catch (BusinessException e) {
                        log.warn("出场处理失败: eventId={} plate={} reason={}",
                                eventId, result.standardizedPlate, e.getMessage());
                        result.fail("出场处理失败: " + e.getMessage());
                    }
                } else {
                    result.fail("未知方向: " + payload.getDirection());
                }
            }
```

- [ ] **Step 6: 在类尾部（`updateEventLogFailed` 之后，`ProcessingResult` 内部类之前）添加三个新方法**

在 `updateEventLogFailed()` 方法结束后（当前 line 331），`ProcessingResult` 内部类声明前（当前 line 338），插入:

```java
    // ==================== 识别失败处理（任务包 3-4） ====================

    /**
     * 识别失败接管入口。
     */
    private void handleRecognitionFailure(RecognitionEventPayload payload, ProcessingResult result) {
        // 仅入场方向需要无牌车处理
        if (!"ENTRY".equals(payload.getDirection())) {
            result.fail("出场方向识别失败（无牌车出场由岗亭手动匹配）");
            return;
        }

        // 读取车场参数：recognition.fail_strategy
        String strategy = paramResolver.getString(
                ParamKeys.RECOGNITION_FAIL_STRATEGY, result.parkingLotId);

        if (ParamKeys.RECOGNITION_AUTO_RELEASE.equals(strategy)) {
            handleAutoRelease(payload, result);
        } else {
            handleManualAlert(payload, result);
        }
    }

    /**
     * MANUAL 策略：推送 WebSocket 告警 + 创建 MonitorAlert 记录。
     */
    private void handleManualAlert(RecognitionEventPayload payload, ProcessingResult result) {
        log.info("无牌车识别失败-MANUAL策略: eventId={} lotId={} laneId={}",
                payload.getEventId(), result.parkingLotId, result.laneId);

        // 1. 推送 WebSocket 告警到岗亭端
        boothWebSocketPublisher.sendRecognitionFailedAlert(
                result.parkingLotId,
                payload.getEventId(),
                payload.getLogId(),
                result.parkingLotId,
                result.laneId,
                payload.getDirection(),
                payload.getImagePath(),
                result.eventTime);

        // 2. 创建 MonitorAlert 记录
        ParkingLot lot = parkingLotMapper.selectByIdIgnoreTenant(result.parkingLotId);
        RecognitionEventLog eventLog = eventLogMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));
        if (lot != null && eventLog != null && monitorAlertService != null) {
            eventLog.setPlateNumber("无牌车");
            eventLog.setFailureReason("识别失败，等待岗亭处理");
            try {
                monitorAlertService.createRecognitionFailAlert(lot, eventLog);
            } catch (Exception e) {
                log.warn("创建识别失败告警 MonitorAlert 失败: eventId={} error={}",
                        payload.getEventId(), e.getMessage());
            }
        }
    }

    /**
     * AUTO_RELEASE 策略：自动生成临时车牌 + 创建在场记录 + 开闸放行。
     */
    private void handleAutoRelease(RecognitionEventPayload payload, ProcessingResult result) {
        log.info("无牌车识别失败-AUTO_RELEASE策略: eventId={} lotId={} laneId={}",
                payload.getEventId(), result.parkingLotId, result.laneId);

        // 1. 生成临时车牌号
        String tempPlate;
        try {
            tempPlate = tempPlateNumberGenerator.generate(result.parkingLotId);
        } catch (BusinessException e) {
            log.warn("临时车牌号生成失败-AUTO_RELEASE降级为MANUAL: eventId={} reason={}",
                    payload.getEventId(), e.getMessage());
            handleManualAlert(payload, result);
            return;
        }

        // 2. 创建在场记录（tempPlateFlag=1，无预订单）
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(result.tenantId);
        record.setParkingLotId(result.parkingLotId);
        record.setLaneId(result.laneId);
        record.setDeviceId(result.deviceId);
        record.setStandardizedPlate(tempPlate);
        record.setTempPlateFlag(1);
        record.setStatus(ParkingRecord.STATUS_PARKING);
        record.setEntryTime(result.eventTime != null ? result.eventTime : LocalDateTime.now());
        record.setEntryImagePath(payload.getImagePath());
        recordMapper.insert(record);

        result.standardizedPlate = tempPlate;

        // 3. 更新停车场容量
        parkingLotMapper.update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles + 1")
                        .setSql("remaining_spaces = remaining_spaces - 1")
                        .eq(ParkingLot::getId, result.parkingLotId));

        // 4. 开闸放行（失败不阻塞）
        try {
            deviceService.openGateByLane(result.laneId,
                    "无牌车自动放行(" + tempPlate + ")");
        } catch (Exception e) {
            log.warn("无牌车自动开闸失败（不影响记录创建）: plate={} laneId={} error={}",
                    tempPlate, result.laneId, e.getMessage());
        }

        log.info("无牌车自动放行完成: tempPlate={} recordId={} lotId={}",
                tempPlate, record.getId(), result.parkingLotId);
    }
```

- [ ] **Step 7: 修改 `updateEventLog()` 方法 — 新增 RECOGNITION_FAILED 状态分支**

旧 `updateEventLog()` (lines 290-307):

```java
    private void updateEventLog(RecognitionEventPayload payload, ProcessingResult result) {
        RecognitionEventLog update = new RecognitionEventLog();
        update.setStandardizedPlate(result.standardizedPlate);
        if (result.success) {
            update.setStatus("PROCESSED");
        } else {
            update.setStatus("FAILED");
            update.setFailureReason(result.failureReason);
        }

        int rows = eventLogMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));

        if (rows == 0) {
            log.warn("事件日志不存在，无法更新状态（可能 DB 事务未提交）: eventId={}", payload.getEventId());
        }
    }
```

替换为:

```java
    private void updateEventLog(RecognitionEventPayload payload, ProcessingResult result) {
        RecognitionEventLog update = new RecognitionEventLog();
        update.setStandardizedPlate(result.standardizedPlate);
        if (result.recognitionFailed) {
            update.setStatus("RECOGNITION_FAILED");
            update.setTempPlateFlag(1);
        } else if (result.success) {
            update.setStatus("PROCESSED");
            update.setTempPlateFlag(0);
        } else {
            update.setStatus("FAILED");
            update.setFailureReason(result.failureReason);
        }

        int rows = eventLogMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));

        if (rows == 0) {
            log.warn("事件日志不存在，无法更新状态（可能 DB 事务未提交）: eventId={}", payload.getEventId());
        }
    }
```

- [ ] **Step 8: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -10`
Expected: `BUILD SUCCESS`
If compilation errors: check added imports and field types (particularly `createRecognitionFailAlert` on MonitorAlertService)

- [ ] **Step 9: 提交**

Verify git status before committing:

```bash
git status
```

Then:

```bash
git add parking-system/src/main/java/com/jushan/system/event/RecognitionEventConsumer.java
git commit -m "[BOOTH-005] feat: add recognition failure handling to Consumer (MANUAL/AUTO_RELEASE strategies)"
```

---

### Task 9: ParkingRecordAdminVO — 新增 tempPlateFlag 字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/vo/ParkingRecordAdminVO.java`

- [ ] **Step 1: 在 ParkingRecordAdminVO 中添加字段和 getter/setter**

在 `private LocalDateTime updatedAt;` 之后（line 138 之前），添加:

```java
    /** 临时车牌标记：0=正式车牌, 1=临时车牌 */
    private Integer tempPlateFlag;
```

在 `setUpdatedAt` 方法之后（line 138 之后），添加 getter/setter:

```java
    public Integer getTempPlateFlag() { return tempPlateFlag; }
    public void setTempPlateFlag(Integer tempPlateFlag) { this.tempPlateFlag = tempPlateFlag; }
```

- [ ] **Step 2: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/vo/ParkingRecordAdminVO.java
git commit -m "[BOOTH-005] feat: add tempPlateFlag to ParkingRecordAdminVO"
```

---

### Task 10: ParkingRecordAdminController — 查询返回 tempPlateFlag + 筛选支持

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/controller/ParkingRecordAdminController.java`

- [ ] **Step 1: 读文件定位分页查询方法**

Read the controller file to find the main list query method. The method likely has parameters like `@RequestParam(required = false) String plateNumber` etc.

- [ ] **Step 2: 在分页查询方法的 QueryWrapper 构建处新增 tempPlateFlag 筛选**

在当前方法已有的 `QueryWrapper<ParkingRecord>` 构建位置，添加可选筛选:

```java
        // 临时车牌标记筛选（任务包 3-4）
        if (tempPlateFlag != null) {
            queryWrapper.eq("temp_plate_flag", tempPlateFlag);
        }
```

同时在该方法的参数列表中添加:

```java
@RequestParam(required = false) Integer tempPlateFlag
```

- [ ] **Step 3: 在 VO 构建代码中加入 tempPlateFlag 字段映射**

在 record → VO 的转换代码中，添加:

```java
        vo.setTempPlateFlag(record.getTempPlateFlag());
```

- [ ] **Step 4: 验证编译通过**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -5`
Expected: `BUILD SUCCESS`

- [ ] **Step 5: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/ParkingRecordAdminController.java
git commit -m "[BOOTH-005] feat: add tempPlateFlag filter and display to parking record admin list"
```

---

### Task 11: 后端集成验证 — 完整编译 + 运行测试

- [ ] **Step 1: 完整编译验证**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -10`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: 运行现有单元测试确保无回归**

Run: `mvn test -pl parking-boot -am 2>&1 | tail -20`
Expected: 所有已有测试通过（跳过因新依赖注入需要的 Spring 上下文初始化失败的测试，这属于正常现象——后续 Task 13 会修复）

- [ ] **Step 3: 提交（如果有任何追加改动）**

```bash
git status
```

---

### Task 12: 岗亭端前端 — WebSocket 告警监听 + 弹窗 + 无牌车处理区

**Files:**
- Modify: `booth-web/src/views/monitor/index.vue` (或告警处理的主页面)
- Modify: `booth-web/src/components/` (根据需要创建新组件)

- [ ] **Step 1: 确认 booth-web 项目的 WebSocket 连接逻辑位置**

Read `booth-web/src/views/monitor/index.vue` 了解当前 WebSocket 订阅和告警处理方式。

- [ ] **Step 2: 在 websocket 消息处理中添加 RECOGNITION_FAILED 类型处理**

在订阅 `/topic/booth/${lotId}/alerts` 的消息回调中，添加:

```javascript
if (data.type === 'RECOGNITION_FAILED') {
  // 触发无牌车告警弹窗
  store.commit('tempPlate/addAlert', {
    eventId: data.eventId,
    logId: data.logId,
    parkingLotId: data.parkingLotId,
    laneId: data.laneId,
    direction: data.direction,
    imagePath: data.imagePath,
    eventTime: data.eventTime,
    message: data.message,
  })
}
```

- [ ] **Step 3: 创建无牌车告警弹窗组件**

创建 `booth-web/src/components/TempPlateAlertModal.vue`，包含:
- 右下角小弹窗（非模态）
- 标题："入口识别失败 —— 无牌车辆"
- 显示：方向、车道名称、事件时间、抓拍图（如有）
- 临时车牌输入框（预填建议值，调用 `GET /api/booth/temp-plate/suggest`）
- "确认入场"按钮 → 调用 `POST /api/booth/temp-plate/entry`
- "忽略"按钮 → 关闭弹窗

- [ ] **Step 4: 在通行作业区增加"无牌车处理"入口**

在 `booth-web/src/views/monitor/index.vue` 或相应用车页面添加"无牌车处理"选项卡/按钮:
- 调用 API 获取当天 `tempPlateFlag=1` 的在场记录列表
- 展示：临时车牌号、入场时间、已停时长
- 点击某条记录弹出出场计费确认框 → 调用 `POST /api/booth/temp-plate/exit-match`

- [ ] **Step 5: 提交**

```bash
git add booth-web/src/views/monitor/index.vue booth-web/src/components/TempPlateAlertModal.vue
git commit -m "[BOOTH-005] feat: add booth temp plate alert modal and processing panel"
```

---

### Task 13: 运营端前端 — 通行记录列表"临"标签

**Files:**
- Modify: `admin-web/src/views/` (通行记录/订单列表页面)

- [ ] **Step 1: 找到通行记录和订单列表的前端页面文件**

- [ ] **Step 2: 在表格列定义中添加 tempPlateFlag 列或标签**

对于 `tempPlateFlag === 1` 的行，在车牌号旁边显示一个橙色的 `a-tag` 标签，文字为"临"，Hover 提示 "临时车牌记录"。

在 Ant Design Vue 表格列模板中:

```vue
<template #plateNumber="{ record }">
  <span>{{ record.plateNumber }}</span>
  <a-tag v-if="record.tempPlateFlag === 1" color="orange" title="临时车牌记录">临</a-tag>
</template>
```

- [ ] **Step 3: 在筛选器中添加 tempPlateFlag 下拉筛选**

```vue
<a-select v-model:value="searchParams.tempPlateFlag" placeholder="车牌类型" allowClear style="width: 120px">
  <a-select-option :value="0">正式车牌</a-select-option>
  <a-select-option :value="1">临时车牌</a-select-option>
</a-select>
```

- [ ] **Step 4: 提交**

```bash
git add admin-web/src/views/
git commit -m "[BOOTH-005] feat: add temp plate tag and filter to admin record list"
```

---

### Task 14: 最终验证 — 完整编译 + 全部测试

- [ ] **Step 1: 完整后端编译**

Run: `mvn clean compile -pl parking-system -am 2>&1 | tail -10`
Expected: `BUILD SUCCESS`

- [ ] **Step 2: 运行全部测试**

Run: `mvn test -pl parking-boot -am 2>&1 | tail -30`
Expected: 全部测试通过（可能需要检查 `RecognitionEventConsumer` 的现有测试是否因为新增依赖而需要更新）

- [ ] **Step 3: 检查 Consumer 现有测试**

Run: `mvn test -pl parking-boot -am -Dtest="*RecognitionEvent*" 2>&1 | tail -20`

如果测试因为新增构造函数参数而失败，需要更新测试中的 Consumer mock/构造。

- [ ] **Step 4: 提交所有最终改动**

```bash
git add -A
git status
# 检查改动是否正确
git commit -m "[BOOTH-005] feat: complete temp plate full-stack integration (entry, exit, alert, admin view)"
```

---

## 自检清单

在提交整体改动前，逐项确认:

- [ ] 三张实体表的 `tempPlateFlag` 字段已添加（DB + Entity）
- [ ] `TempPlateNumberGenerator` Redis INCR 已实现，含降级方案
- [ ] `TempPlateService` 入场/出场逻辑已实现
- [ ] `BoothTempPlateController` 三个接口（GET suggest / POST entry / POST exit-match）已添加
- [ ] `BoothWebSocketPublisher.sendRecognitionFailedAlert()` 已添加
- [ ] `RecognitionEventConsumer` 空车牌后移到校验之后、识别失败接管分支已添加
- [ ] Consumer 新增依赖已注入
- [ ] `updateEventLog` 支持 RECOGNITION_FAILED 状态
- [ ] `ParkingRecordAdminVO` 含 tempPlateFlag
- [ ] 运营端通行记录列表支持筛选和显示"临"标签
- [ ] 岗亭端告警弹窗 + 无牌车处理区已实现
- [ ] 编译通过
- [ ] 现有测试通过（或已适配）
