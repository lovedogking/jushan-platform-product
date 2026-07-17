# Monthly Pass Standalone Entity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the facade-based monthly pass system (sys_vehicle.vehicleType=MONTHLY tag) with a standalone `monthly_pass` table managing full lifecycle, payment tracking, and monthly pass orders.

**Architecture:** New `monthly_pass` table independent from `sys_vehicle`. Vehicle type decision reads `monthly_pass` before `sys_vehicle` chain. Admin creates monthly pass + generates PAID order in one transaction. Expiry managed by scheduled job.

**Tech Stack:** Java 21, Spring Boot 3.x, MyBatis-Plus, MySQL 8, Flyway, JUnit 5, Mockito, AssertJ

**Spec:** `docs/superpowers/specs/2026-07-17-monthly-pass-standalone-entity-design.md`

---

## File Structure

### Create
| File | Purpose |
|------|---------|
| `parking-system/.../system/entity/MonthlyPass.java` | Entity (extends BaseEntity) |
| `parking-system/.../system/mapper/MonthlyPassMapper.java` | MyBatis-Plus mapper + custom queries |
| `parking-system/.../system/job/MonthlyPassExpiryJob.java` | Daily expiry scheduled task |
| `parking-boot/.../db/migration/V20260826001__create_monthly_pass.sql` | Flyway DDL + data migration |

### Modify
| File | Changes |
|------|---------|
| `parking-system/.../system/dto/MonthlyPassCreateRequest.java` | Add payMethod, paidAmountCents, vehicleType |
| `parking-system/.../system/vo/MonthlyPassVO.java` | Add payMethod, paidAmountCents, source, applicantId, orderId |
| `parking-system/.../system/service/MonthlyPassService.java` | Full rewrite — base on monthly_pass table |
| `parking-system/.../system/controller/MonthlyPassController.java` | Full rewrite — delegate to new service |
| `parking-system/.../vehicle/service/impl/VehicleTypeDecisionServiceImpl.java` | Add monthly_pass lookup in decide(); remove MONTHLY branch |
| `parking-system/.../system/entity/ParkingOrder.java` | Add MONTHLY_PASS constant |
| `parking-system/.../system/entity/VehicleRenewalLog.java` | Add monthlyPassId field |
| `parking-system/.../platform/task/OverstayBlacklistService.java` | Remove TYPE_MONTHLY from FIXED_VEHICLE_TYPES; add monthly_pass check |
| `parking-system/.../test/.../service/MonthlyPassServiceTest.java` | Rewrite for new entity |
| `parking-system/.../test/.../impl/VehicleTypeDecisionServiceImplTest.java` | Add monthly_pass test cases; adapt MONTHLY case |

---

### Task 1: Flyway Migration Script

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260826001__create_monthly_pass.sql`

- [ ] **Step 1: Write the Flyway migration script**

```sql
-- =============================================================================
-- 任务包 3-1：月卡独立实体 — 建表 + 存量迁移 + vehicle_renewal_log 扩展
-- =============================================================================

-- Step 1: 创建 monthly_pass 表
CREATE TABLE monthly_pass (
    id               BIGINT          NOT NULL PRIMARY KEY COMMENT '主键（Snowflake）',
    tenant_id        BIGINT          NOT NULL COMMENT '租户ID',
    parking_lot_id   BIGINT          NOT NULL COMMENT '车场ID',
    plate_number     VARCHAR(20)     NOT NULL COMMENT '车牌号（标准化大写）',
    plate_color      VARCHAR(10)     DEFAULT NULL COMMENT '车牌颜色',
    vehicle_type     VARCHAR(20)     DEFAULT NULL COMMENT '车辆类型（小型车/大型车等）',
    valid_start_date DATE            NOT NULL COMMENT '有效期起',
    valid_end_date   DATE            NOT NULL COMMENT '有效期止',
    amount_cents     INT             NOT NULL DEFAULT 0 COMMENT '费用（分）',
    paid_amount_cents INT            NOT NULL DEFAULT 0 COMMENT '实收金额（分）',
    pay_method       VARCHAR(20)     NOT NULL COMMENT '缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER',
    pass_status      VARCHAR(20)     NOT NULL DEFAULT 'ACTIVE' COMMENT '月卡状态：ACTIVE-生效中 / EXPIRED-已过期 / CANCELLED-已注销',
    applicant_id     BIGINT          DEFAULT NULL COMMENT '申请人ID（小程序用户ID）',
    source           VARCHAR(20)     NOT NULL COMMENT '来源：ADMIN-运营端 / MINIAPP-小程序端',
    owner_name       VARCHAR(30)     DEFAULT NULL COMMENT '车主姓名',
    owner_phone      VARCHAR(20)     DEFAULT NULL COMMENT '车主电话',
    remark           VARCHAR(200)    DEFAULT NULL COMMENT '备注',
    created_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at       DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    deleted_at       DATETIME(3)     DEFAULT NULL COMMENT '软删除时间',

    INDEX idx_tenant     (tenant_id),
    INDEX idx_plate      (plate_number),
    INDEX idx_lot        (parking_lot_id),
    INDEX idx_status     (pass_status),
    INDEX idx_lot_plate  (parking_lot_id, plate_number)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='月卡独立实体表';

-- Step 2: 存量数据迁移（sys_vehicle.vehicleType=MONTHLY → monthly_pass）
INSERT INTO monthly_pass (
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    amount_cents, paid_amount_cents, pay_method,
    pass_status, applicant_id, source,
    owner_name, owner_phone, remark,
    created_at, updated_at
)
SELECT
    tenant_id, parking_lot_id, plate_number,
    plate_color, valid_start_date, valid_end_date,
    0, 0, 'OTHER',
    CASE
        WHEN status = 'ACTIVE' AND valid_end_date >= CURDATE() THEN 'ACTIVE'
        ELSE 'EXPIRED'
    END,
    NULL, 'ADMIN',
    owner_name, owner_phone, remark,
    COALESCE(created_at, NOW()), NOW()
FROM sys_vehicle
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 3: 改写旧 MONTHLY 记录（旧判断口径下线）
UPDATE sys_vehicle
SET vehicle_type = 'FREE',
    status = 'EXPIRED',
    updated_at = NOW()
WHERE vehicle_type = 'MONTHLY' AND deleted_at IS NULL;

-- Step 4: vehicle_renewal_log 扩展
ALTER TABLE vehicle_renewal_log
    ADD COLUMN monthly_pass_id BIGINT DEFAULT NULL COMMENT '月卡ID（新体系）' AFTER vehicle_id;
```

- [ ] **Step 2: Commit**

```bash
git add parking-boot/src/main/resources/db/migration/V20260826001__create_monthly_pass.sql
git commit -m "feat: add monthly_pass table, data migration, and renewal_log extension (task 3-1)"
```

---

### Task 2: MonthlyPass Entity

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/entity/MonthlyPass.java`

- [ ] **Step 1: Write the entity**

```java
package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 月卡独立实体（任务包 3-1）。
 * <p>
 * 替代原有的 {@code sys_vehicle.vehicleType=MONTHLY} 标签化实现，
 * 管理月卡全生命周期：办理、续期、注销、过期。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monthly_pass")
public class MonthlyPass extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属车场ID */
    private Long parkingLotId;

    /** 车牌号（标准化大写） */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型（小型车/大型车等，选填） */
    private String vehicleType;

    /** 有效期起 */
    private LocalDate validStartDate;

    /** 有效期止 */
    private LocalDate validEndDate;

    /** 费用（分） */
    private Integer amountCents;

    /** 实收金额（分） */
    private Integer paidAmountCents;

    /** 缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER */
    private String payMethod;

    /** 月卡状态：ACTIVE-生效中 / EXPIRED-已过期 / CANCELLED-已注销 */
    private String passStatus;

    /** 申请人ID（小程序用户ID；运营端录入为NULL） */
    private Long applicantId;

    /** 来源：ADMIN-运营端 / MINIAPP-小程序端 */
    private String source;

    /** 车主姓名 */
    private String ownerName;

    /** 车主电话 */
    private String ownerPhone;

    /** 备注 */
    private String remark;

    // ==================== 常量 ====================

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String PAY_METHOD_CASH = "CASH";
    public static final String PAY_METHOD_OFFLINE_TRANSFER = "OFFLINE_TRANSFER";
    public static final String PAY_METHOD_SIMULATED_PAY = "SIMULATED_PAY";
    public static final String PAY_METHOD_OTHER = "OTHER";

    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_MINIAPP = "MINIAPP";
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/MonthlyPass.java
git commit -m "feat: add MonthlyPass entity (task 3-1)"
```

---

### Task 3: MonthlyPassMapper

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/mapper/MonthlyPassMapper.java`

- [ ] **Step 1: Write the mapper**

```java
package com.jushan.system.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.jushan.system.entity.MonthlyPass;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDate;

/**
 * 月卡 Mapper（任务包 3-1）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Mapper
public interface MonthlyPassMapper extends BaseMapper<MonthlyPass> {

    /**
     * 查询生效中的月卡（用于车辆类型判定）。
     *
     * @param tenantId    租户ID
     * @param plateNumber 车牌号（标准化大写）
     * @param today       当前日期
     * @return 生效中的月卡，无则 null
     */
    @Select("SELECT * FROM monthly_pass WHERE tenant_id = #{tenantId} AND plate_number = #{plateNumber} AND pass_status = 'ACTIVE' AND valid_start_date <= #{today} AND valid_end_date >= #{today} AND deleted_at IS NULL LIMIT 1")
    MonthlyPass selectActiveByPlate(@Param("tenantId") Long tenantId,
                                     @Param("plateNumber") String plateNumber,
                                     @Param("today") LocalDate today);

    /**
     * 过期扫描：将到期月卡批量置为 EXPIRED。
     *
     * @param today 当前日期
     * @return 更新行数
     */
    @Update("UPDATE monthly_pass SET pass_status = 'EXPIRED', updated_at = NOW() WHERE pass_status = 'ACTIVE' AND valid_end_date < #{today}")
    int expireActivePasses(@Param("today") LocalDate today);
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/mapper/MonthlyPassMapper.java
git commit -m "feat: add MonthlyPassMapper with active lookup and expiry queries (task 3-1)"
```

---

### Task 4: Constants — ParkingOrder + VehicleRenewalLog

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java` (add constant)
- Modify: `parking-system/src/main/java/com/jushan/system/entity/VehicleRenewalLog.java` (add field)

- [ ] **Step 1: Add MONTHLY_PASS constant to ParkingOrder**

In `ParkingOrder.java`, after line 137 (after `ORDER_TYPE_TOP_UP`), add:

```java
/** 月卡首次办理（任务包 3-1） */
public static final String ORDER_TYPE_MONTHLY_PASS = "MONTHLY_PASS";
```

- [ ] **Step 2: Add monthlyPassId field to VehicleRenewalLog**

In `VehicleRenewalLog.java`, after line 24 (after `private Long vehicleId;`), add:

```java
/** 月卡ID（新体系 monthly_pass.id；旧记录为 NULL） */
private Long monthlyPassId;
```

After line 48 (after `setVehicleId` getter/setter), add getter/setter:

```java
public Long getMonthlyPassId() { return monthlyPassId; }
public void setMonthlyPassId(Long monthlyPassId) { this.monthlyPassId = monthlyPassId; }
```

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java parking-system/src/main/java/com/jushan/system/entity/VehicleRenewalLog.java
git commit -m "feat: add MONTHLY_PASS order type and monthlyPassId field (task 3-1)"
```

---

### Task 5: DTO + VO Updates

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/dto/MonthlyPassCreateRequest.java`
- Modify: `parking-system/src/main/java/com/jushan/system/vo/MonthlyPassVO.java`

- [ ] **Step 1: Add fields to MonthlyPassCreateRequest**

After `private Integer amountCents;` (line 32), add:

```java
/** 实收金额（分，必填） */
@NotNull(message = "实收金额不能为空")
private Integer paidAmountCents;

/** 缴费方式：CASH / OFFLINE_TRANSFER / OTHER（必填） */
@NotBlank(message = "缴费方式不能为空")
private String payMethod;

/** 车辆类型（小型车/大型车等，选填） */
private String vehicleType;
```

Add getters/setters after `setAmountCents`:

```java
public Integer getPaidAmountCents() { return paidAmountCents; }
public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }
public String getPayMethod() { return payMethod; }
public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
public String getVehicleType() { return vehicleType; }
public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
```

- [ ] **Step 2: Rewrite MonthlyPassVO**

Replace the entire `MonthlyPassVO.java` content:

```java
package com.jushan.system.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 月卡视图 VO（任务包 3-1：基于 independent monthly_pass 实体）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public class MonthlyPassVO {

    private Long id;
    private String plateNumber;
    private String plateColor;
    private String vehicleType;
    private Long parkingLotId;
    private String parkingLotName;
    private LocalDate validStartDate;
    private LocalDate validEndDate;
    private Integer amountCents;
    private Integer paidAmountCents;
    private String payMethod;
    private String passStatus;
    private String source;
    private Long applicantId;
    private Long orderId;        // 关联首次办理订单ID
    private String ownerName;
    private String ownerPhone;
    private String remark;
    private LocalDateTime createdAt;

    // getters and setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }
    public String getPlateColor() { return plateColor; }
    public void setPlateColor(String plateColor) { this.plateColor = plateColor; }
    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public String getParkingLotName() { return parkingLotName; }
    public void setParkingLotName(String parkingLotName) { this.parkingLotName = parkingLotName; }
    public LocalDate getValidStartDate() { return validStartDate; }
    public void setValidStartDate(LocalDate validStartDate) { this.validStartDate = validStartDate; }
    public LocalDate getValidEndDate() { return validEndDate; }
    public void setValidEndDate(LocalDate validEndDate) { this.validEndDate = validEndDate; }
    public Integer getAmountCents() { return amountCents; }
    public void setAmountCents(Integer amountCents) { this.amountCents = amountCents; }
    public Integer getPaidAmountCents() { return paidAmountCents; }
    public void setPaidAmountCents(Integer paidAmountCents) { this.paidAmountCents = paidAmountCents; }
    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }
    public String getPassStatus() { return passStatus; }
    public void setPassStatus(String passStatus) { this.passStatus = passStatus; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public Long getApplicantId() { return applicantId; }
    public void setApplicantId(Long applicantId) { this.applicantId = applicantId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }
    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
```

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/dto/MonthlyPassCreateRequest.java parking-system/src/main/java/com/jushan/system/vo/MonthlyPassVO.java
git commit -m "feat: extend MonthlyPassCreateRequest and rewrite MonthlyPassVO for independent entity (task 3-1)"
```

---

### Task 6: MonthlyPassService Rewrite

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/MonthlyPassService.java`

- [ ] **Step 1: Replace entire MonthlyPassService**

```java
package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.VehicleRenewalLogMapper;
import com.jushan.system.vo.MonthlyPassVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 月卡管理服务（任务包 3-1：基于 independent monthly_pass 实体）。
 * <p>
 * 替代原 facade（sys_vehicle.vehicleType=MONTHLY），提供月卡全生命周期管理：
 * <ul>
 *   <li>录入（创建 monthly_pass + 生成已支付月卡订单）</li>
 *   <li>续期（延长有效期 + 生成续费订单 + 记录日志）</li>
 *   <li>注销（置 CANCELLED）</li>
 *   <li>分页列表 + 到期预警</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Service
public class MonthlyPassService {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassService.class);

    private final MonthlyPassMapper monthlyPassMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final VehicleRenewalLogMapper renewalLogMapper;
    private final ParamResolver paramResolver;

    public MonthlyPassService(MonthlyPassMapper monthlyPassMapper,
                              ParkingLotMapper parkingLotMapper,
                              ParkingOrderMapper parkingOrderMapper,
                              VehicleRenewalLogMapper renewalLogMapper,
                              ParamResolver paramResolver) {
        this.monthlyPassMapper = monthlyPassMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.paramResolver = paramResolver;
    }

    // ==================== 录入月卡 ====================

    /**
     * 录入月卡并生成已支付订单（一个事务）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO create(MonthlyPassCreateRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 1. 唯一性校验
        checkDuplicateActive(tenantId, request.getParkingLotId(), plate);

        // 2. 插入 monthly_pass
        MonthlyPass pass = new MonthlyPass();
        pass.setTenantId(tenantId);
        pass.setParkingLotId(request.getParkingLotId());
        pass.setPlateNumber(plate);
        pass.setPlateColor(request.getPlateColor());
        pass.setVehicleType(request.getVehicleType());
        pass.setValidStartDate(request.getValidStartDate());
        pass.setValidEndDate(request.getValidEndDate());
        pass.setAmountCents(request.getPaidAmountCents());
        pass.setPaidAmountCents(request.getPaidAmountCents());
        pass.setPayMethod(request.getPayMethod());
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setSource(MonthlyPass.SOURCE_ADMIN);
        pass.setApplicantId(null);
        pass.setOwnerName(request.getOwnerName());
        pass.setOwnerPhone(request.getOwnerPhone());
        pass.setRemark(request.getRemark());
        pass.setCreatedAt(LocalDateTime.now());
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.insert(pass);

        // 3. 生成已支付月卡订单
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(request.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(plate);
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTHLY_PASS);
        order.setAmountCents(request.getPaidAmountCents());
        order.setPaidAmount(request.getPaidAmountCents());
        order.setPayableAmount(request.getPaidAmountCents());
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(mapPayChannel(request.getPayMethod()));
        order.setPayTime(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        log.info("月卡录入成功: passId={} plate={} lotId={} orderId={} payMethod={} paid={}",
                pass.getId(), plate, request.getParkingLotId(), order.getId(),
                request.getPayMethod(), request.getPaidAmountCents());

        return toVO(pass, getParkingLotName(request.getParkingLotId()), order.getId());
    }

    // ==================== 续期 ====================

    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO renew(Long id, MonthlyPassRenewRequest request) {
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrow(id, tenantId);

        LocalDate oldValidEnd = pass.getValidEndDate();

        // 计算新有效期（从当前有效期末尾顺延）
        LocalDate newValidEnd = (oldValidEnd != null ? oldValidEnd : LocalDate.now())
                .plusMonths(request.getRenewalMonths());

        // 更新月卡有效期
        pass.setValidEndDate(newValidEnd);
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        // 创建续费订单（MONTH_RENEW）
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(tenantId);
        order.setParkingLotId(pass.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(pass.getPlateNumber());
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        order.setRenewalMonths(request.getRenewalMonths());
        order.setAmountCents(request.getAmountCents());
        order.setPaidAmount(request.getAmountCents());
        order.setPayableAmount(request.getAmountCents());
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
        order.setPayTime(LocalDateTime.now());
        order.setOperatorId(TenantContext.requireUserId());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        // 写入续费日志
        VehicleRenewalLog logEntry = new VehicleRenewalLog();
        logEntry.setTenantId(tenantId);
        logEntry.setParkingLotId(pass.getParkingLotId());
        logEntry.setMonthlyPassId(pass.getId());    // 新体系：关联 monthly_pass
        logEntry.setVehicleId(null);                 // 旧体系不再使用
        logEntry.setPlateNumber(pass.getPlateNumber());
        logEntry.setOrderId(order.getId());
        logEntry.setRenewalMonths(request.getRenewalMonths());
        logEntry.setAmountCents(request.getAmountCents());
        logEntry.setOldValidEnd(oldValidEnd);
        logEntry.setNewValidEnd(newValidEnd);
        logEntry.setOperatorId(TenantContext.requireUserId());
        logEntry.setRemark(request.getRemark());
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        renewalLogMapper.insert(logEntry);

        log.info("月卡续期成功: passId={} plate={} months={} oldEnd={} newEnd={}",
                pass.getId(), pass.getPlateNumber(), request.getRenewalMonths(),
                oldValidEnd, newValidEnd);

        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    // ==================== 注销 ====================

    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO cancel(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrow(id, tenantId);

        pass.setPassStatus(MonthlyPass.STATUS_CANCELLED);
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        log.info("月卡注销成功: passId={} plate={}", pass.getId(), pass.getPlateNumber());

        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    // ==================== 列表查询 ====================

    public IPage<MonthlyPassVO> pageList(String plateNumber, Long parkingLotId,
                                          String passStatus, LocalDate validEndFrom, LocalDate validEndTo,
                                          int page, int size) {
        Long tenantId = TenantContext.requireTenantId();

        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId);

        if (plateNumber != null && !plateNumber.isEmpty()) {
            query.like("plate_number", plateNumber.toUpperCase());
        }
        if (parkingLotId != null) {
            query.eq("parking_lot_id", parkingLotId);
        }
        if (passStatus != null && !passStatus.isEmpty()) {
            query.eq("pass_status", passStatus);
        }
        if (validEndFrom != null) {
            query.ge("valid_end_date", validEndFrom);
        }
        if (validEndTo != null) {
            query.le("valid_end_date", validEndTo);
        }
        query.orderByDesc("created_at");

        IPage<MonthlyPass> passPage = monthlyPassMapper.selectPage(new Page<>(page, size), query);
        if (passPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(passPage.getRecords());

        List<MonthlyPassVO> voList = passPage.getRecords().stream()
                .map(p -> toVO(p, lotNames.get(p.getParkingLotId()), null))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(passPage.getCurrent(), passPage.getSize(), passPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 到期预警 ====================

    public IPage<MonthlyPassVO> expiringList(int page, int size) {
        Long tenantId = TenantContext.requireTenantId();

        // 从车场参数读取提醒天数（默认 7）
        String daysStr = paramResolver.getString(ParamKeys.MONTHLY_PASS_EXPIRY_REMINDER_DAYS, null);
        int days = 7;
        try {
            if (daysStr != null) days = Integer.parseInt(daysStr);
        } catch (NumberFormatException ignored) { }

        LocalDate today = LocalDate.now();
        LocalDate deadline = today.plusDays(days);

        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("pass_status", MonthlyPass.STATUS_ACTIVE)
                .le("valid_end_date", deadline)
                .ge("valid_end_date", today)
                .orderByAsc("valid_end_date");

        IPage<MonthlyPass> passPage = monthlyPassMapper.selectPage(new Page<>(page, size), query);
        if (passPage.getRecords().isEmpty()) {
            return new Page<>(page, size);
        }

        Map<Long, String> lotNames = loadParkingLotNames(passPage.getRecords());
        List<MonthlyPassVO> voList = passPage.getRecords().stream()
                .map(p -> toVO(p, lotNames.get(p.getParkingLotId()), null))
                .collect(Collectors.toList());

        IPage<MonthlyPassVO> result = new Page<>(passPage.getCurrent(), passPage.getSize(), passPage.getTotal());
        result.setRecords(voList);
        return result;
    }

    // ==================== 详情 ====================

    public MonthlyPassVO detail(Long id) {
        Long tenantId = TenantContext.requireTenantId();
        MonthlyPass pass = getOrThrow(id, tenantId);
        return toVO(pass, getParkingLotName(pass.getParkingLotId()), null);
    }

    // ==================== 内部方法 ====================

    private MonthlyPass getOrThrow(Long id, Long tenantId) {
        MonthlyPass pass = monthlyPassMapper.selectById(id);
        if (pass == null || !tenantId.equals(pass.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        return pass;
    }

    private void checkDuplicateActive(Long tenantId, Long parkingLotId, String plate) {
        QueryWrapper<MonthlyPass> query = new QueryWrapper<MonthlyPass>()
                .eq("tenant_id", tenantId)
                .eq("parking_lot_id", parkingLotId)
                .eq("plate_number", plate)
                .eq("pass_status", MonthlyPass.STATUS_ACTIVE);
        Long count = monthlyPassMapper.selectCount(query);
        if (count != null && count > 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该车牌已在当前车场办理月卡，不能重复登记");
        }
    }

    /**
     * 缴费方式映射到订单 payChannel。
     */
    private String mapPayChannel(String payMethod) {
        return switch (payMethod) {
            case MonthlyPass.PAY_METHOD_CASH -> ParkingOrder.PAY_CHANNEL_CASH;
            default -> ParkingOrder.PAY_CHANNEL_BALANCE; // OFFLINE_TRANSFER / OTHER
        };
    }

    private String getParkingLotName(Long parkingLotId) {
        if (parkingLotId == null) return null;
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        return lot != null ? lot.getName() : null;
    }

    private Map<Long, String> loadParkingLotNames(List<MonthlyPass> passes) {
        List<Long> lotIds = passes.stream()
                .map(MonthlyPass::getParkingLotId)
                .distinct()
                .collect(Collectors.toList());
        if (lotIds.isEmpty()) return Collections.emptyMap();
        List<ParkingLot> lots = parkingLotMapper.selectBatchIds(lotIds);
        return lots.stream().collect(Collectors.toMap(ParkingLot::getId, ParkingLot::getName));
    }

    private MonthlyPassVO toVO(MonthlyPass pass, String parkingLotName, Long orderId) {
        MonthlyPassVO vo = new MonthlyPassVO();
        vo.setId(pass.getId());
        vo.setPlateNumber(pass.getPlateNumber());
        vo.setPlateColor(pass.getPlateColor());
        vo.setVehicleType(pass.getVehicleType());
        vo.setParkingLotId(pass.getParkingLotId());
        vo.setParkingLotName(parkingLotName);
        vo.setValidStartDate(pass.getValidStartDate());
        vo.setValidEndDate(pass.getValidEndDate());
        vo.setAmountCents(pass.getAmountCents());
        vo.setPaidAmountCents(pass.getPaidAmountCents());
        vo.setPayMethod(pass.getPayMethod());
        vo.setPassStatus(pass.getPassStatus());
        vo.setSource(pass.getSource());
        vo.setApplicantId(pass.getApplicantId());
        vo.setOrderId(orderId);
        vo.setOwnerName(pass.getOwnerName());
        vo.setOwnerPhone(pass.getOwnerPhone());
        vo.setRemark(pass.getRemark());
        vo.setCreatedAt(pass.getCreatedAt());
        return vo;
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl parking-system -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/service/MonthlyPassService.java
git commit -m "feat: rewrite MonthlyPassService for standalone monthly_pass entity (task 3-1)"
```

---

### Task 7: MonthlyPassController Rewrite

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/controller/MonthlyPassController.java`

- [ ] **Step 1: Replace entire MonthlyPassController**

```java
package com.jushan.system.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.service.MonthlyPassService;
import com.jushan.system.vo.MonthlyPassVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

/**
 * 月卡管理 Controller（任务包 3-1：基于 independent monthly_pass 实体）。
 * <p>
 * 所有接口需要 {@code monthly:manage} 权限。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/v1/monthly-passes")
public class MonthlyPassController {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassController.class);

    private final MonthlyPassService monthlyPassService;

    public MonthlyPassController(MonthlyPassService monthlyPassService) {
        this.monthlyPassService = monthlyPassService;
    }

    // ==================== 列表 ====================

    @GetMapping
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> pageList(
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String passStatus,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validEndFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate validEndTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MonthlyPassVO> result = monthlyPassService.pageList(
                plateNumber, parkingLotId, passStatus, validEndFrom, validEndTo, page, size);
        return R.ok(result);
    }

    // ==================== 到期预警 ====================

    @GetMapping("/expiring")
    @RequirePermission("monthly:manage")
    public R<IPage<MonthlyPassVO>> expiringList(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        IPage<MonthlyPassVO> result = monthlyPassService.expiringList(page, size);
        return R.ok(result);
    }

    // ==================== 详情 ====================

    @GetMapping("/{id}")
    @RequirePermission("monthly:manage")
    public R<MonthlyPassVO> detail(@PathVariable Long id) {
        MonthlyPassVO vo = monthlyPassService.detail(id);
        return R.ok(vo);
    }

    // ==================== 录入 ====================

    @PostMapping
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡录入", module = "monthly-pass", operationType = "CREATE",
            operationObject = "月卡", objectIdExpression = "#result?.data?.id")
    public R<MonthlyPassVO> create(@Valid @RequestBody MonthlyPassCreateRequest request) {
        MonthlyPassVO vo = monthlyPassService.create(request);
        log.info("月卡录入完成: id={} plate={}", vo.getId(), vo.getPlateNumber());
        return R.ok(vo);
    }

    // ==================== 续期 ====================

    @PutMapping("/{id}/renew")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡续期", module = "monthly-pass", operationType = "UPDATE",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> renew(@PathVariable Long id,
                                   @Valid @RequestBody MonthlyPassRenewRequest request) {
        MonthlyPassVO vo = monthlyPassService.renew(id, request);
        log.info("月卡续期完成: id={} plate={} newEnd={}", id, vo.getPlateNumber(), vo.getValidEndDate());
        return R.ok(vo);
    }

    // ==================== 注销 ====================

    @PutMapping("/{id}/cancel")
    @RequirePermission("monthly:manage")
    @BusinessLog(value = "月卡注销", module = "monthly-pass", operationType = "UPDATE",
            operationObject = "月卡", objectIdExpression = "#id")
    public R<MonthlyPassVO> cancel(@PathVariable Long id) {
        MonthlyPassVO vo = monthlyPassService.cancel(id);
        log.info("月卡注销完成: id={} plate={}", id, vo.getPlateNumber());
        return R.ok(vo);
    }
}
```

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl parking-system -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/MonthlyPassController.java
git commit -m "feat: rewrite MonthlyPassController for standalone monthly_pass entity (task 3-1)"
```

---

### Task 8: VehicleTypeDecisionService — Add monthly_pass lookup

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImpl.java`

- [ ] **Step 1: Add MonthlyPassMapper dependency**

At top of file, add import after existing imports:

```java
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.mapper.MonthlyPassMapper;
```

- [ ] **Step 2: Add constructor parameter**

Change the constructor signature (lines 48-56):

```java
private final MonthlyPassMapper monthlyPassMapper;

public VehicleTypeDecisionServiceImpl(SysVehicleMapper vehicleMapper,
                                      SysVehicleMultiPlateMapper multiPlateMapper,
                                      SysVehicleWalletMapper walletMapper,
                                      FixedSpaceService fixedSpaceService,
                                      MonthlyPassMapper monthlyPassMapper) {
    this.vehicleMapper = vehicleMapper;
    this.multiPlateMapper = multiPlateMapper;
    this.walletMapper = walletMapper;
    this.fixedSpaceService = fixedSpaceService;
    this.monthlyPassMapper = monthlyPassMapper;
}
```

- [ ] **Step 3: Add monthly_pass lookup in decide() method**

In `decide(String plateNumber, Long tenantId)`, after line 68 (`String standardizedPlate = plateNumber.toUpperCase();`), insert the monthly_pass lookup BEFORE the sys_vehicle query:

```java
// 0. 优先查询月卡（新体系：任务包 3-1）
MonthlyPass monthlyPass = monthlyPassMapper.selectActiveByPlate(
        tenantId, standardizedPlate, LocalDate.now());
if (monthlyPass != null) {
    VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
    result.setPlateNumber(standardizedPlate);
    result.setVehicleType("MONTHLY");
    result.setTypeDescription("月租车");
    result.setAllowEntry(true);
    result.setAllowExit(true);
    result.setNeedCharge(false);
    result.setValidStartDate(monthlyPass.getValidStartDate());
    result.setValidEndDate(monthlyPass.getValidEndDate());
    result.setExpired(false);
    result.setDecisionReason("月卡在有效期内，免费通行");
    return result;
}
```

- [ ] **Step 4: Remove case SysVehicle.TYPE_MONTHLY from applyPriorityChain()**

In `applyPriorityChain()`, remove lines 181-198 (the entire `case SysVehicle.TYPE_MONTHLY` block including the `isMonthlyExpired` call):

```java
// REMOVE this block:
// case SysVehicle.TYPE_MONTHLY -> {
//     boolean expired = isMonthlyExpired(vehicle);
//     result.setExpired(expired);
//     ...
// }
```

Also remove the `isMonthlyExpired()` method (lines 234-245) since it's no longer needed.

- [ ] **Step 5: Update FIXED_SPACE blacklist condition**

On line 145, where `SysVehicle.TYPE_MONTHLY` appears in the exclusion list for FIXED_SPACE check:

```java
// BEFORE (line 142-146):
if (!SysVehicle.TYPE_BLACKLIST.equals(type)
        && !SysVehicle.TYPE_SUPER.equals(type)
        && !SysVehicle.TYPE_VIP.equals(type)
        && !SysVehicle.TYPE_MONTHLY.equals(type)
        && !SysVehicle.TYPE_PREPAID.equals(type)) {

// AFTER: remove TYPE_MONTHLY line
if (!SysVehicle.TYPE_BLACKLIST.equals(type)
        && !SysVehicle.TYPE_SUPER.equals(type)
        && !SysVehicle.TYPE_VIP.equals(type)
        && !SysVehicle.TYPE_PREPAID.equals(type)) {
```

- [ ] **Step 6: Verify compilation**

```bash
mvn compile -pl parking-system -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImpl.java
git commit -m "feat: add monthly_pass priority lookup in VehicleTypeDecisionService (task 3-1)"
```

---

### Task 9: OverstayBlacklistService — Remove MONTHLY reference

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/task/OverstayBlacklistService.java`

- [ ] **Step 1: Remove TYPE_MONTHLY from FIXED_VEHICLE_TYPES**

On line 64, remove `SysVehicle.TYPE_MONTHLY,`:

```java
// BEFORE (lines 63-69):
private static final Set<String> FIXED_VEHICLE_TYPES = Set.of(
        SysVehicle.TYPE_MONTHLY,
        SysVehicle.TYPE_PREPAID,
        SysVehicle.TYPE_FREE,
        SysVehicle.TYPE_VIP,
        SysVehicle.TYPE_SUPER
);

// AFTER:
private static final Set<String> FIXED_VEHICLE_TYPES = Set.of(
        SysVehicle.TYPE_PREPAID,
        SysVehicle.TYPE_FREE,
        SysVehicle.TYPE_VIP,
        SysVehicle.TYPE_SUPER
);
```

Note: The SysVehicle.TYPE_MONTHLY import may now be unused — remove it from the import block if the compiler warns.

- [ ] **Step 2: Verify compilation**

```bash
mvn compile -pl parking-system -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/platform/task/OverstayBlacklistService.java
git commit -m "fix: remove TYPE_MONTHLY from OverstayBlacklistService FIXED_VEHICLE_TYPES (task 3-1)"
```

---

### Task 10: Scheduled Job — MonthlyPassExpiryJob

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/job/MonthlyPassExpiryJob.java`

- [ ] **Step 1: Write the job**

```java
package com.jushan.system.job;

import com.jushan.system.mapper.MonthlyPassMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * 月卡到期定时任务（任务包 3-1）。
 * <p>
 * 每日凌晨 2:00 扫描到期月卡，置为 EXPIRED。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Component
public class MonthlyPassExpiryJob {

    private static final Logger log = LoggerFactory.getLogger(MonthlyPassExpiryJob.class);

    private final MonthlyPassMapper monthlyPassMapper;

    public MonthlyPassExpiryJob(MonthlyPassMapper monthlyPassMapper) {
        this.monthlyPassMapper = monthlyPassMapper;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void expireMonthlyPasses() {
        int updated = monthlyPassMapper.expireActivePasses(LocalDate.now());
        if (updated > 0) {
            log.info("月卡到期处理完成: 更新 {} 条记录为 EXPIRED", updated);
        }
    }
}
```

- [ ] **Step 2: Enable scheduling**

Verify that `@EnableScheduling` is present in the project configuration. Search:

```bash
grep -r "EnableScheduling" parking-boot/src/main/java/
```

If not present in any `@Configuration` class, add to `parking-boot/src/main/java/com/jushan/ParkingBootApplication.java`:

```java
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class ParkingBootApplication { ... }
```

- [ ] **Step 3: Verify compilation**

```bash
mvn compile -pl parking-system -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/job/MonthlyPassExpiryJob.java
git commit -m "feat: add MonthlyPassExpiryJob for daily expiry scan (task 3-1)"
```

---

### Task 11: MonthlyPassServiceTest Rewrite

**Files:**
- Modify: `parking-system/src/test/java/com/jushan/system/service/MonthlyPassServiceTest.java`

- [ ] **Step 1: Replace entire test file**

```java
package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.dto.MonthlyPassCreateRequest;
import com.jushan.system.dto.MonthlyPassRenewRequest;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.VehicleRenewalLogMapper;
import com.jushan.system.vo.MonthlyPassVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link MonthlyPassService} 单元测试（任务包 3-1：基于 monthly_pass 实体）。
 */
@ExtendWith(MockitoExtension.class)
class MonthlyPassServiceTest {

    @Mock private MonthlyPassMapper monthlyPassMapper;
    @Mock private ParkingLotMapper parkingLotMapper;
    @Mock private ParkingOrderMapper parkingOrderMapper;
    @Mock private VehicleRenewalLogMapper renewalLogMapper;
    @Mock private ParamResolver paramResolver;

    private MonthlyPassService service;

    private static final Long TENANT_ID = 1L;
    private static final Long PARKING_LOT_ID = 10L;
    private static final String PLATE = "京A12345";

    @BeforeEach
    void setUp() {
        service = new MonthlyPassService(monthlyPassMapper, parkingLotMapper,
                parkingOrderMapper, renewalLogMapper, paramResolver);
        TenantContext.set(new TenantContext.Snapshot(TENANT_ID, 100L, "TENANT", "", ""));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 创建月卡 ====================

    @Test
    @DisplayName("录入月卡：创建 monthly_pass 并生成已支付 MONTHLY_PASS 订单")
    void shouldCreateMonthlyPassAndPaidOrder() {
        // given
        MonthlyPassCreateRequest req = buildCreateRequest();
        when(monthlyPassMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(PARKING_LOT_ID)).thenReturn(lot("测试车场"));

        // when
        MonthlyPassVO result = service.create(req);

        // then
        assertThat(result.getPlateNumber()).isEqualTo(PLATE);
        assertThat(result.getPassStatus()).isEqualTo("ACTIVE");
        assertThat(result.getSource()).isEqualTo("ADMIN");
        assertThat(result.getPayMethod()).isEqualTo("CASH");
        assertThat(result.getPaidAmountCents()).isEqualTo(30000);

        ArgumentCaptor<MonthlyPass> passCaptor = ArgumentCaptor.forClass(MonthlyPass.class);
        verify(monthlyPassMapper).insert(passCaptor.capture());
        MonthlyPass inserted = passCaptor.getValue();
        assertThat(inserted.getPlateNumber()).isEqualTo(PLATE);
        assertThat(inserted.getPassStatus()).isEqualTo(MonthlyPass.STATUS_ACTIVE);

        ArgumentCaptor<ParkingOrder> orderCaptor = ArgumentCaptor.forClass(ParkingOrder.class);
        verify(parkingOrderMapper).insert(orderCaptor.capture());
        ParkingOrder order = orderCaptor.getValue();
        assertThat(order.getOrderType()).isEqualTo(ParkingOrder.ORDER_TYPE_MONTHLY_PASS);
        assertThat(order.getStatus()).isEqualTo(ParkingOrder.STATUS_PAID);
    }

    @Test
    @DisplayName("重复办理同车场同车牌生效中月卡 → 拒绝")
    void shouldRejectDuplicateActiveMonthlyPass() {
        MonthlyPassCreateRequest req = buildCreateRequest();
        when(monthlyPassMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertThatThrownBy(() -> service.create(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复登记");
    }

    // ==================== 续期 ====================

    @Test
    @DisplayName("续期：有效期正确顺延，生成续费订单和日志")
    void shouldRenewAndCreateOrderAndLog() {
        MonthlyPass existing = buildMonthlyPass();
        when(monthlyPassMapper.selectById(existing.getId())).thenReturn(existing);
        when(monthlyPassMapper.updateById(any(MonthlyPass.class))).thenReturn(1);
        when(parkingLotMapper.selectById(PARKING_LOT_ID)).thenReturn(lot("测试车场"));

        MonthlyPassRenewRequest renewReq = new MonthlyPassRenewRequest();
        renewReq.setRenewalMonths(3);
        renewReq.setAmountCents(9000);
        renewReq.setRemark("续费3个月");

        MonthlyPassVO result = service.renew(existing.getId(), renewReq);

        assertThat(result.getValidEndDate()).isEqualTo(existing.getValidEndDate().plusMonths(3));

        ArgumentCaptor<ParkingOrder> orderCaptor = ArgumentCaptor.forClass(ParkingOrder.class);
        verify(parkingOrderMapper).insert(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getOrderType()).isEqualTo(ParkingOrder.ORDER_TYPE_MONTH_RENEW);

        ArgumentCaptor<VehicleRenewalLog> logCaptor = ArgumentCaptor.forClass(VehicleRenewalLog.class);
        verify(renewalLogMapper).insert(logCaptor.capture());
        assertThat(logCaptor.getValue().getMonthlyPassId()).isEqualTo(existing.getId());
        assertThat(logCaptor.getValue().getRenewalMonths()).isEqualTo(3);
    }

    // ==================== 注销 ====================

    @Test
    @DisplayName("注销：状态置为 CANCELLED")
    void shouldCancelMonthlyPass() {
        MonthlyPass existing = buildMonthlyPass();
        when(monthlyPassMapper.selectById(existing.getId())).thenReturn(existing);
        when(monthlyPassMapper.updateById(any(MonthlyPass.class))).thenReturn(1);
        when(parkingLotMapper.selectById(PARKING_LOT_ID)).thenReturn(lot("测试车场"));

        MonthlyPassVO result = service.cancel(existing.getId());

        assertThat(result.getPassStatus()).isEqualTo(MonthlyPass.STATUS_CANCELLED);
    }

    // ==================== 列表查询 ====================

    @Test
    @DisplayName("分页列表：查询 monthly_pass 表")
    void shouldPageListMonthlyPasses() {
        MonthlyPass pass = buildMonthlyPass();
        Page<MonthlyPass> mockPage = new Page<>(1, 20);
        mockPage.setRecords(List.of(pass));
        mockPage.setTotal(1);

        when(monthlyPassMapper.selectPage(any(Page.class), any(QueryWrapper.class))).thenReturn(mockPage);
        when(parkingLotMapper.selectBatchIds(anyList())).thenReturn(List.of(lot("测试车场")));

        IPage<MonthlyPassVO> result = service.pageList(null, null, null, null, null, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getRecords().get(0).getPlateNumber()).isEqualTo(PLATE);
    }

    // ==================== helpers ====================

    private MonthlyPassCreateRequest buildCreateRequest() {
        MonthlyPassCreateRequest req = new MonthlyPassCreateRequest();
        req.setPlateNumber(PLATE);
        req.setParkingLotId(PARKING_LOT_ID);
        req.setValidStartDate(LocalDate.now());
        req.setValidEndDate(LocalDate.now().plusMonths(1));
        req.setPaidAmountCents(30000);
        req.setPayMethod("CASH");
        req.setOwnerName("张三");
        req.setOwnerPhone("13800001111");
        return req;
    }

    private MonthlyPass buildMonthlyPass() {
        MonthlyPass pass = new MonthlyPass();
        pass.setId(1L);
        pass.setTenantId(TENANT_ID);
        pass.setParkingLotId(PARKING_LOT_ID);
        pass.setPlateNumber(PLATE);
        pass.setValidStartDate(LocalDate.now().minusDays(5));
        pass.setValidEndDate(LocalDate.now().plusMonths(1));
        pass.setPaidAmountCents(30000);
        pass.setPayMethod("CASH");
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setSource(MonthlyPass.SOURCE_ADMIN);
        pass.setOwnerName("张三");
        pass.setOwnerPhone("13800001111");
        pass.setCreatedAt(LocalDateTime.now());
        pass.setUpdatedAt(LocalDateTime.now());
        return pass;
    }

    private ParkingLot lot(String name) {
        ParkingLot lot = new ParkingLot();
        lot.setId(PARKING_LOT_ID);
        lot.setName(name);
        return lot;
    }
}
```

- [ ] **Step 2: Run tests**

```bash
mvn test -pl parking-system -am -Dtest="MonthlyPassServiceTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/test/java/com/jushan/system/service/MonthlyPassServiceTest.java
git commit -m "test: rewrite MonthlyPassServiceTest for standalone monthly_pass entity (task 3-1)"
```

---

### Task 12: VehicleTypeDecisionServiceImplTest — Add monthly_pass cases

**Files:**
- Modify: `parking-system/src/test/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImplTest.java`

- [ ] **Step 1: Add MonthlyPassMapper mock**

Add to the imports:

```java
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.mapper.MonthlyPassMapper;
```

Add mock field after `@Mock private FixedSpaceService fixedSpaceService;`:

```java
@Mock
private MonthlyPassMapper monthlyPassMapper;
```

Update constructor call in `setUp()`:

```java
decisionService = new VehicleTypeDecisionServiceImpl(
        vehicleMapper, multiPlateMapper, walletMapper, fixedSpaceService, monthlyPassMapper);
```

- [ ] **Step 2: Add monthly_pass test cases**

Add after the existing `shouldNotCheckFixedSpaceForBlacklist` test method and before `shouldDecideFixedSpaceWhenUnknownTypeWithBinding`:

```java
// ==================== MONTHLY_PASS 判定（任务包 3-1） ====================

@Test
@DisplayName("生效中月卡命中 → 返回 MONTHLY 判定（免费放行）")
void shouldDecideMonthlyWhenActiveMonthlyPassFound() {
    MonthlyPass pass = new MonthlyPass();
    pass.setId(1L);
    pass.setPlateNumber("京A12345");
    pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
    pass.setValidStartDate(java.time.LocalDate.now().minusDays(5));
    pass.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

    when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京A12345"), any()))
            .thenReturn(pass);
    // sys_vehicle 查询不应被调用（月卡短路返回）
    verifyNoInteractions(vehicleMapper);

    VehicleTypeDecisionVO result = decisionService.decide("京A12345");

    assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
    assertThat(result.getNeedCharge()).isFalse();
    assertThat(result.getAllowEntry()).isTrue();
    assertThat(result.getAllowExit()).isTrue();
    assertThat(result.getExpired()).isFalse();
    assertThat(result.getDecisionReason()).contains("月卡在有效期内");
}

@Test
@DisplayName("无生效月卡 → 回退 sys_vehicle 类型链判断")
void shouldFallbackToSysVehicleWhenNoMonthlyPass() {
    when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京A12345"), any()))
            .thenReturn(null);
    when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
    when(multiPlateMapper.selectByVehicleId(10L)).thenReturn(List.of());
    when(fixedSpaceService.hasActiveBindingByVehicleId(10L, 1L, 1L)).thenReturn(false);

    VehicleTypeDecisionVO result = decisionService.decide("京A12345");

    // vehicle is TYPE_FREE → should return FREE
    assertThat(result.getVehicleType()).isEqualTo("FREE");
    assertThat(result.getNeedCharge()).isFalse();
}

@Test
@DisplayName("月卡命中但车牌不在 sys_vehicle → 仍返回 MONTHLY（月卡短路）")
void shouldReturnMonthlyEvenWithoutSysVehicleRecord() {
    MonthlyPass pass = new MonthlyPass();
    pass.setId(2L);
    pass.setPlateNumber("京B88888");
    pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
    pass.setValidStartDate(java.time.LocalDate.now().minusDays(5));
    pass.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

    when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京B88888"), any()))
            .thenReturn(pass);
    // vehicleMapper should NOT be called

    VehicleTypeDecisionVO result = decisionService.decide("京B88888");

    assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
    assertThat(result.getNeedCharge()).isFalse();
    verifyNoInteractions(vehicleMapper);
}
```

- [ ] **Step 3: Update existing MONTHLY test case**

The existing `shouldNotCheckFixedSpaceForMonthly` test sets `vehicle.setVehicleType(SysVehicle.TYPE_MONTHLY)` and expects MONTHLY result from the sys_vehicle chain. After our change, the sys_vehicle MONTHLY branch is removed. This test must be adapted: set up a monthly_pass instead, and verify it returns MONTHLY via the monthly_pass path.

Replace the test body of `shouldNotCheckFixedSpaceForMonthly` (lines 118-131):

```java
@Test
@DisplayName("月卡生效中车辆不触发固定车位检查")
void shouldNotCheckFixedSpaceForMonthly() {
    // 任务包 3-1：月卡通过 monthly_pass 短路返回，不进入 sys_vehicle 链
    MonthlyPass pass = new MonthlyPass();
    pass.setId(1L);
    pass.setPlateNumber("京A12345");
    pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
    pass.setValidStartDate(java.time.LocalDate.now().minusDays(5));
    pass.setValidEndDate(java.time.LocalDate.now().plusMonths(3));

    when(monthlyPassMapper.selectActiveByPlate(eq(1L), eq("京A12345"), any()))
            .thenReturn(pass);

    VehicleTypeDecisionVO result = decisionService.decide("京A12345");

    assertThat(result.getVehicleType()).isEqualTo("MONTHLY");
    assertThat(result.getNeedCharge()).isFalse();
    // 月卡短路返回，不应调用 fixedSpaceService
    verifyNoInteractions(fixedSpaceService);
    verifyNoInteractions(vehicleMapper);
}
```

- [ ] **Step 4: Run tests**

```bash
mvn test -pl parking-system -am -Dtest="VehicleTypeDecisionServiceImplTest" -DfailIfNoTests=false
```

Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/test/java/com/jushan/platform/modules/vehicle/service/impl/VehicleTypeDecisionServiceImplTest.java
git commit -m "test: add monthly_pass scenarios to VehicleTypeDecisionServiceImplTest (task 3-1)"
```

---

### Task 13: Full Build & Test Verification

- [ ] **Step 1: Compile entire project**

```bash
mvn clean compile -pl parking-system -am -q
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Run all tests**

```bash
mvn test -pl parking-system -am
```

Expected: BUILD SUCCESS. Note: existing failing tests (RecognitionEventServiceImplTest, DeviceWebhookControllerTest) are known issues per AGENTS.md, not blockages.

- [ ] **Step 3: Verify no remaining TYPE_MONTHLY references in main code**

```bash
grep -rn "TYPE_MONTHLY" parking-system/src/main/java/ | grep -v "//\|import.*SysVehicle.TYPE_MONTHLY\|/\*"
```

Expected: only constant definition in `SysVehicle.java` remains (the constant itself is kept for backward compatibility but should not be referenced in business logic).

- [ ] **Step 4: Commit any cleanup**

```bash
git add -A
git commit -m "chore: final verification and cleanup for task 3-1 monthly_pass implementation"
```

---

## Task Dependency Graph

```
Task 1 (Flyway) ──→ Task 2 (Entity) ──→ Task 3 (Mapper) ──→ Task 6 (Service)
                    Task 4 (Constants) ──────────────────────→ Task 6 (Service)
                    Task 5 (DTO/VO) ─────────────────────────→ Task 6 (Service)
                                                                  │
                                                                  ▼
                                                            Task 7 (Controller)
                                                                  │
                    ┌─────────────────────────────────────────────┘
                    ▼
              Task 8 (VehicleTypeDecision)
              Task 9 (OverstayBlacklist)
              Task 10 (Scheduled Job)
                    │
                    ▼
              Task 11 (ServiceTest)
              Task 12 (DecisionTest)
                    │
                    ▼
              Task 13 (Full Build)
```

Tasks 1-5 can be done in parallel. Tasks 6-7 depend on 1-5. Task 8 must come after 6. Tasks 9-10 can be parallel with 8. Tasks 11-12 are last. Task 13 is final verification.
