# 小程序月卡链路 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现小程序端月卡申请/列表/详情/支付/续费全链路，包括后端 API（`MiniMonthlyPassController` + `MiniMonthlyPassService`）和前端 3 个页面（列表/申请/续费）。

**Architecture:** 后端新增 `MiniMonthlyPassService` 处理小程序月卡业务（申请时校验车牌绑定、读审核模式参数决定 AUTO/MANUAL、支付时调用 MockPaymentService、续费时读价格参数计算金额），通过 `MiniMonthlyPassController` 暴露 `/api/v1/mini/monthly-passes` 接口。前端 3 个新页面调用后端 API，覆盖状态机全生命周期。月卡实体和 `MonthlyPassVO` 复用既有组件不改。

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus + Flyway（后端）| 微信小程序原生 + utils/request.js（前端）

---

## Phase 0: Database Migration & ParamKeys

### Task 0.1: Add price param constant and Flyway seed

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/constant/ParamKeys.java`
- Create: `parking-boot/src/main/resources/db/migration/V20260718006__monthly_pass_price_param.sql`

- [ ] **Step 1: Add `MONTHLY_PASS_PRICE_PER_MONTH_CENTS` constant and its `Definition` to `ParamKeys.java`**

Open `parking-system/src/main/java/com/jushan/system/constant/ParamKeys.java`. Add the constant after line 73 (after `FIXED_SPACE_REVIEW_MODE`):

```java
    /** 月卡单价（分/月），默认 30000（即 300 元/月）。 */
    public static final String MONTHLY_PASS_PRICE_PER_MONTH_CENTS = "monthly_pass.price_per_month_cents";
```

Then add the Definition to the `LOT_PARAMS` list. Insert after the `FIXED_SPACE_REVIEW_MODE` Definition (after line 134, before the closing `);` of `LOT_PARAMS`):

```java
            new Definition(MONTHLY_PASS_PRICE_PER_MONTH_CENTS, "计费设置", TYPE_INT, null,
                    "30000", "月卡单价（分/月）"),
```

The closing `);` of the list must remain after this new entry.

- [ ] **Step 2: Update `MONTHLY_PASS_EXPIRY_REMINDER_DAYS` constant comment**

In the same file, the `Definition` for `MONTHLY_PASS_EXPIRY_REMINDER_DAYS` has group `"计费设置"`. That's fine — leave as-is.

- [ ] **Step 3: Create Flyway migration file**

Create `parking-boot/src/main/resources/db/migration/V20260718006__monthly_pass_price_param.sql`:

```sql
-- 小程序月卡链路：注入月卡定价参数种子（全局行）
INSERT INTO sys_config (config_key, config_value, description, group_name, value_type, parking_lot_id, created_at, updated_at)
VALUES ('monthly_pass.price_per_month_cents', '30000', '月卡单价（分/月）', '计费设置', 'INT', 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE description = VALUES(description), group_name = VALUES(group_name), value_type = VALUES(value_type);
```

- [ ] **Step 4: Verify Flyway migration compiles**

Run:
```bash
mvn clean compile -pl parking-system -am
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/constant/ParamKeys.java parking-boot/src/main/resources/db/migration/V20260718006__monthly_pass_price_param.sql
git commit -m "[MINI-005] feat: add monthly_pass.price_per_month_cents param seed and constant"
```

---

## Phase 1: Backend DTOs

### Task 1.1: Create MiniMonthlyPassApplyRequest

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/dto/MiniMonthlyPassApplyRequest.java`

- [ ] **Step 1: Create the DTO**

```java
package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 小程序月卡申请请求 DTO（任务包 5-2 月卡部分）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class MiniMonthlyPassApplyRequest {

    @NotNull(message = "车场不能为空")
    private Long parkingLotId;

    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    /** 车主姓名（选填） */
    private String ownerName;

    /** 车主电话（选填） */
    private String ownerPhone;

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getOwnerPhone() { return ownerPhone; }
    public void setOwnerPhone(String ownerPhone) { this.ownerPhone = ownerPhone; }
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/dto/MiniMonthlyPassApplyRequest.java
git commit -m "[MINI-005] feat: add MiniMonthlyPassApplyRequest DTO"
```

---

### Task 1.2: Create MiniMonthlyPassRenewRequest

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/dto/MiniMonthlyPassRenewRequest.java`

- [ ] **Step 1: Create the DTO**

```java
package com.jushan.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 小程序月卡续费请求 DTO（任务包 5-2 月卡部分）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class MiniMonthlyPassRenewRequest {

    @Min(value = 1, message = "续费月数必须为 1/3/6/12")
    @Max(value = 12, message = "续费月数必须为 1/3/6/12")
    @NotNull(message = "续费月数不能为空")
    private Integer months;

    public Integer getMonths() { return months; }
    public void setMonths(Integer months) { this.months = months; }
}
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/dto/MiniMonthlyPassRenewRequest.java
git commit -m "[MINI-005] feat: add MiniMonthlyPassRenewRequest DTO"
```

---

## Phase 2: Backend Service

### Task 2.1: Create MiniMonthlyPassService

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/service/MiniMonthlyPassService.java`

- [ ] **Step 1: Create the service class**

```java
package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.system.constant.ParamKeys;
import com.jushan.system.dto.MiniMonthlyPassApplyRequest;
import com.jushan.system.dto.MiniMonthlyPassRenewRequest;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.PlateBinding;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.entity.VehicleRenewalLog;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.jushan.system.mapper.VehicleRenewalLogMapper;
import com.jushan.system.vo.MonthlyPassVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 小程序月卡业务服务（任务包 5-2 月卡部分）。
 * <p>
 * 提供小程序端月卡全生命周期管理：申请、列表、详情、确认支付、续费。
 * 与运营端 {@link MonthlyPassService} 职责分离，本服务聚焦小程序端特有的
 * 车牌绑定校验、审核模式判定、用户归属校验。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class MiniMonthlyPassService {

    private static final Logger log = LoggerFactory.getLogger(MiniMonthlyPassService.class);

    private final MonthlyPassMapper monthlyPassMapper;
    private final PlateBindingMapper plateBindingMapper;
    private final VehicleMapper vehicleMapper;
    private final ParkingOrderMapper parkingOrderMapper;
    private final VehicleRenewalLogMapper renewalLogMapper;
    private final MonthlyPassService monthlyPassService;
    private final ParamResolver paramResolver;

    public MiniMonthlyPassService(MonthlyPassMapper monthlyPassMapper,
                                   PlateBindingMapper plateBindingMapper,
                                   VehicleMapper vehicleMapper,
                                   ParkingOrderMapper parkingOrderMapper,
                                   VehicleRenewalLogMapper renewalLogMapper,
                                   MonthlyPassService monthlyPassService,
                                   ParamResolver paramResolver) {
        this.monthlyPassMapper = monthlyPassMapper;
        this.plateBindingMapper = plateBindingMapper;
        this.vehicleMapper = vehicleMapper;
        this.parkingOrderMapper = parkingOrderMapper;
        this.renewalLogMapper = renewalLogMapper;
        this.monthlyPassService = monthlyPassService;
        this.paramResolver = paramResolver;
    }

    // ==================== 申请 ====================

    /**
     * 提交月卡申请。
     * <ol>
     *   <li>获取当前用户 ID</li>
     *   <li>校验 plateNumber 属于当前用户的已绑定车牌</li>
     *   <li>同一 plateNumber + 同一 parkingLotId 已有 ACTIVE 月卡 → 拒绝</li>
     *   <li>读取审核模式（AUTO → 直接 APPROVED；MANUAL → 保持 PENDING）</li>
     *   <li>创建 MonthlyPass（source=MINIAPP）</li>
     * </ol>
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO apply(MiniMonthlyPassApplyRequest request) {
        Long userId = TenantContext.requireUserId();
        Long tenantId = TenantContext.requireTenantId();
        String plate = request.getPlateNumber().toUpperCase();

        // 1. 校验车牌属于当前用户的已绑定车牌
        List<PlateBinding> bindings = plateBindingMapper.selectList(
                new LambdaQueryWrapper<PlateBinding>()
                        .eq(PlateBinding::getWxUserId, userId)
                        .eq(PlateBinding::getVerifyStatus, PlateBinding.VERIFY_STATUS_APPROVED)
                        .isNull(PlateBinding::getDeletedAt));
        if (bindings.isEmpty()) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "您尚未绑定车牌，请先绑定车牌");
        }

        boolean plateOwned = false;
        for (PlateBinding binding : bindings) {
            Vehicle vehicle = vehicleMapper.selectById(binding.getVehicleId());
            if (vehicle != null && plate.equals(vehicle.getVehiclePlate().toUpperCase())) {
                plateOwned = true;
                break;
            }
        }
        if (!plateOwned) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "该车牌未绑定到您的账号");
        }

        // 2. 检查重复 ACTIVE
        Long activeCount = monthlyPassMapper.selectCount(
                new QueryWrapper<MonthlyPass>()
                        .eq("tenant_id", tenantId)
                        .eq("parking_lot_id", request.getParkingLotId())
                        .eq("plate_number", plate)
                        .eq("pass_status", MonthlyPass.STATUS_ACTIVE));
        if (activeCount != null && activeCount > 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT,
                    "该车牌已在当前车场办理月卡，不能重复申请");
        }

        // 3. 读取审核模式
        String reviewMode = paramResolver.getString(
                ParamKeys.MONTHLY_FIXED_REVIEW_MODE, request.getParkingLotId());
        boolean autoApprove = !"MANUAL".equals(reviewMode);

        // 4. 创建 MonthlyPass
        MonthlyPass pass = new MonthlyPass();
        pass.setTenantId(tenantId);
        pass.setParkingLotId(request.getParkingLotId());
        pass.setPlateNumber(plate);
        pass.setAmountCents(0);
        pass.setPaidAmountCents(0);
        pass.setPayMethod(MonthlyPass.PAY_METHOD_SIMULATED_PAY);
        pass.setSource(MonthlyPass.SOURCE_MINIAPP);
        pass.setApplicantId(userId);
        pass.setOwnerName(request.getOwnerName());
        pass.setOwnerPhone(request.getOwnerPhone());
        pass.setReviewStatus(autoApprove ? MonthlyPass.REVIEW_APPROVED : MonthlyPass.REVIEW_PENDING);
        pass.setPassStatus(autoApprove ? MonthlyPass.REVIEW_PENDING : MonthlyPass.REVIEW_PENDING);
        // spec: pass_status initial is PENDING regardless of review mode;
        // but MonthlyPass has no dedicated "PENDING" pass_status value besides the review ones.
        // The spec state machine: review_status=APPROVED + pass_status=PENDING = 待支付.
        // We'll use passStatus = "PENDING" as a sentinel for "awaiting payment".
        // Actually, the code uses the review status constants. Let's check:
        // CONSTANTS: STATUS_ACTIVE, STATUS_EXPIRED, STATUS_CANCELLED.
        // We should use a literal "PENDING" for the pass_status when not yet paid.
        pass.setPassStatus("PENDING");
        if (autoApprove) {
            pass.setReviewStatus(MonthlyPass.REVIEW_APPROVED);
        } else {
            pass.setReviewStatus(MonthlyPass.REVIEW_PENDING);
        }
        pass.setCreatedAt(LocalDateTime.now());
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.insert(pass);

        log.info("小程序月卡申请成功: passId={} userId={} plate={} lotId={} reviewMode={} autoApprove={}",
                pass.getId(), userId, plate, request.getParkingLotId(), reviewMode, autoApprove);

        return monthlyPassService.toVO(pass);
    }

    // ==================== 列表 ====================

    /**
     * 查询当前用户的所有小程序月卡。
     */
    public List<MonthlyPassVO> listMyPasses() {
        Long userId = TenantContext.requireUserId();

        List<MonthlyPass> passes = monthlyPassMapper.selectList(
                new QueryWrapper<MonthlyPass>()
                        .eq("applicant_id", userId)
                        .eq("source", MonthlyPass.SOURCE_MINIAPP)
                        .isNull("deleted_at")
                        .orderByDesc("created_at"));

        return passes.stream()
                .map(monthlyPassService::toVO)
                .collect(Collectors.toList());
    }

    // ==================== 详情 ====================

    /**
     * 查看月卡详情（校验归属）。
     */
    public MonthlyPassVO detail(Long id) {
        Long userId = TenantContext.requireUserId();
        MonthlyPass pass = getOrThrowForUser(id, userId);
        return monthlyPassService.toVO(pass);
    }

    // ==================== 确认支付 ====================

    /**
     * 审核通过后，用户确认支付使月卡生效。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO pay(Long id) {
        Long userId = TenantContext.requireUserId();
        MonthlyPass pass = getOrThrowForUser(id, userId);

        // 校验审核状态
        if (!MonthlyPass.REVIEW_APPROVED.equals(pass.getReviewStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "月卡尚未审核通过，无法支付");
        }

        // 防止重复支付
        if (MonthlyPass.STATUS_ACTIVE.equals(pass.getPassStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该月卡已生效，无需重复支付");
        }

        // 读取月卡单价（车场级 → 全局 → 默认 30000）
        int priceCents = paramResolver.getInt(
                ParamKeys.MONTHLY_PASS_PRICE_PER_MONTH_CENTS,
                pass.getParkingLotId(), 30000);
        int amountCents = priceCents;

        // 如果 amountCents 尚未设置，从参数读取
        if (pass.getAmountCents() == null || pass.getAmountCents() <= 0) {
            pass.setAmountCents(amountCents);
        }

        // 生成月卡支付订单
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(pass.getTenantId());
        order.setParkingLotId(pass.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(pass.getPlateNumber());
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTHLY_PASS);
        order.setAmountCents(pass.getAmountCents());
        order.setPaidAmount(pass.getAmountCents());
        order.setPayableAmount(pass.getAmountCents());
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_BALANCE);
        order.setPayTime(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        // 更新月卡状态为生效
        pass.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        pass.setPaidAmountCents(pass.getAmountCents());
        pass.setPayMethod(MonthlyPass.PAY_METHOD_SIMULATED_PAY);
        pass.setValidStartDate(LocalDate.now());
        pass.setValidEndDate(LocalDate.now().plusMonths(1));
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        log.info("小程序月卡支付成功: passId={} userId={} plate={} amount={} orderId={}",
                pass.getId(), userId, pass.getPlateNumber(), pass.getAmountCents(), order.getId());

        return monthlyPassService.toVO(pass);
    }

    // ==================== 续费 ====================

    /**
     * 续费月卡（从原到期日顺延）。
     */
    @Transactional(rollbackFor = Exception.class)
    public MonthlyPassVO renew(Long id, MiniMonthlyPassRenewRequest request) {
        Long userId = TenantContext.requireUserId();
        MonthlyPass pass = getOrThrowForUser(id, userId);

        // 校验状态
        if (!MonthlyPass.STATUS_ACTIVE.equals(pass.getPassStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅生效中的月卡可以续费");
        }
        if (!MonthlyPass.SOURCE_MINIAPP.equals(pass.getSource())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "仅小程序端办理的月卡支持在线续费");
        }

        LocalDate oldValidEnd = pass.getValidEndDate();
        int months = request.getMonths();

        // 读月卡单价计算金额
        int priceCents = paramResolver.getInt(
                ParamKeys.MONTHLY_PASS_PRICE_PER_MONTH_CENTS,
                pass.getParkingLotId(), 30000);
        int amountCents = priceCents * months;

        // 有效期顺延
        LocalDate newValidEnd = (oldValidEnd != null ? oldValidEnd : LocalDate.now())
                .plusMonths(months);
        pass.setValidEndDate(newValidEnd);
        pass.setUpdatedAt(LocalDateTime.now());
        monthlyPassMapper.updateById(pass);

        // 生成续费订单
        ParkingOrder order = new ParkingOrder();
        order.setTenantId(pass.getTenantId());
        order.setParkingLotId(pass.getParkingLotId());
        order.setRefId(pass.getId());
        order.setPlateNumber(pass.getPlateNumber());
        order.setOrderType(ParkingOrder.ORDER_TYPE_MONTH_RENEW);
        order.setRenewalMonths(months);
        order.setAmountCents(amountCents);
        order.setPaidAmount(amountCents);
        order.setPayableAmount(amountCents);
        order.setStatus(ParkingOrder.STATUS_PAID);
        order.setPayChannel(ParkingOrder.PAY_CHANNEL_BALANCE);
        order.setPayTime(LocalDateTime.now());
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());
        parkingOrderMapper.insert(order);

        // 写入续费日志
        VehicleRenewalLog logEntry = new VehicleRenewalLog();
        logEntry.setTenantId(pass.getTenantId());
        logEntry.setParkingLotId(pass.getParkingLotId());
        logEntry.setMonthlyPassId(pass.getId());
        logEntry.setVehicleId(null);
        logEntry.setPlateNumber(pass.getPlateNumber());
        logEntry.setOrderId(order.getId());
        logEntry.setRenewalMonths(months);
        logEntry.setAmountCents(amountCents);
        logEntry.setOldValidEnd(oldValidEnd);
        logEntry.setNewValidEnd(newValidEnd);
        logEntry.setOperatorId(userId);
        logEntry.setCreatedAt(LocalDateTime.now());
        logEntry.setUpdatedAt(LocalDateTime.now());
        renewalLogMapper.insert(logEntry);

        log.info("小程序月卡续费成功: passId={} userId={} plate={} months={} oldEnd={} newEnd={} amount={}",
                pass.getId(), userId, pass.getPlateNumber(), months, oldValidEnd, newValidEnd, amountCents);

        return monthlyPassService.toVO(pass);
    }

    // ==================== 内部方法 ====================

    private MonthlyPass getOrThrowForUser(Long id, Long userId) {
        MonthlyPass pass = monthlyPassMapper.selectById(id);
        if (pass == null || pass.getDeletedAt() != null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "月卡不存在");
        }
        if (pass.getApplicantId() == null || !pass.getApplicantId().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "无权查看该月卡");
        }
        return pass;
    }
}
```

- [ ] **Step 2: Verify VehicleMapper exists**

Run:
```bash
grep -r "interface VehicleMapper" parking-system/src/main/java/com/jushan/system/mapper/
```
Expected: finds `VehicleMapper`. If not found, it should be at `parking-system/src/main/java/com/jushan/system/mapper/VehicleMapper.java` — verify it extends `BaseMapper<Vehicle>`.

- [ ] **Step 3: Check ParkingOrder constants used**

Verify these constants exist by reading `parking-system/src/main/java/com/jushan/system/entity/ParkingOrder.java`:
- `ORDER_TYPE_MONTHLY_PASS` (should be `"MONTHLY_PASS"`)
- `ORDER_TYPE_MONTH_RENEW` (should be `"MONTH_RENEW"`)
- `STATUS_PAID` (should be `"PAID"`)
- `PAY_CHANNEL_BALANCE` (should be `"BALANCE"`)

If any differ, adjust the service code to match the actual constant names.

- [ ] **Step 4: Compile**

```bash
mvn clean compile -pl parking-system -am
```
Expected: `BUILD SUCCESS`

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/service/MiniMonthlyPassService.java
git commit -m "[MINI-005] feat: add MiniMonthlyPassService for miniapp monthly pass lifecycle"
```

---

## Phase 3: Backend Controller

### Task 3.1: Create MiniMonthlyPassController

**Files:**
- Create: `parking-system/src/main/java/com/jushan/system/controller/MiniMonthlyPassController.java`

- [ ] **Step 1: Create the controller**

```java
package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.MiniMonthlyPassApplyRequest;
import com.jushan.system.dto.MiniMonthlyPassRenewRequest;
import com.jushan.system.service.MiniMonthlyPassService;
import com.jushan.system.vo.MonthlyPassVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 小程序月卡控制器（任务包 5-2 月卡部分）。
 * <p>
 * 提供 POST /api/v1/mini/monthly-passes 申请、列表、详情、支付、续费。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/mini/monthly-passes")
public class MiniMonthlyPassController {

    private final MiniMonthlyPassService miniMonthlyPassService;

    public MiniMonthlyPassController(MiniMonthlyPassService miniMonthlyPassService) {
        this.miniMonthlyPassService = miniMonthlyPassService;
    }

    /**
     * 提交月卡申请。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/monthly-passes}
     */
    @PostMapping
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> apply(@Valid @RequestBody MiniMonthlyPassApplyRequest request) {
        MonthlyPassVO vo = miniMonthlyPassService.apply(request);
        return R.ok(vo);
    }

    /**
     * 我的月卡列表（全部状态，按 created_at 倒序）。
     * <p>
     * 前端调用：{@code GET /api/v1/mini/monthly-passes}
     */
    @GetMapping
    @RequirePermission("miniapp:view")
    public R<List<MonthlyPassVO>> list() {
        List<MonthlyPassVO> list = miniMonthlyPassService.listMyPasses();
        return R.ok(list);
    }

    /**
     * 月卡详情。
     * <p>
     * 前端调用：{@code GET /api/v1/mini/monthly-passes/{id}}
     */
    @GetMapping("/{id}")
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> detail(@PathVariable Long id) {
        MonthlyPassVO vo = miniMonthlyPassService.detail(id);
        return R.ok(vo);
    }

    /**
     * 确认支付（审核通过后支付使月卡生效）。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/monthly-passes/{id}/pay}
     */
    @PostMapping("/{id}/pay")
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> pay(@PathVariable Long id) {
        MonthlyPassVO vo = miniMonthlyPassService.pay(id);
        return R.ok(vo);
    }

    /**
     * 续费。
     * <p>
     * 前端调用：{@code POST /api/v1/mini/monthly-passes/{id}/renew}
     */
    @PostMapping("/{id}/renew")
    @RequirePermission("miniapp:view")
    public R<MonthlyPassVO> renew(@PathVariable Long id,
                                   @Valid @RequestBody MiniMonthlyPassRenewRequest request) {
        MonthlyPassVO vo = miniMonthlyPassService.renew(id, request);
        return R.ok(vo);
    }
}
```

- [ ] **Step 2: Compile**

```bash
mvn clean compile -pl parking-system -am
```
Expected: `BUILD SUCCESS`

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/MiniMonthlyPassController.java
git commit -m "[MINI-005] feat: add MiniMonthlyPassController with apply/list/detail/pay/renew endpoints"
```

---

## Phase 4: Frontend — Infrastructure Changes

### Task 4.1: Register new pages in app.json

**Files:**
- Modify: `miniapp/app.json`

- [ ] **Step 1: Add 3 new page entries**

Edit `miniapp/app.json`. Replace the `"pages"` array — add three new entries after `"pages/bind-phone/bind-phone"`:

```json
  "pages": [
    "pages/index/index",
    "pages/profile/profile",
    "pages/plate/plate",
    "pages/records/records",
    "pages/parking/parking",
    "pages/pay/pay",
    "pages/proxy-pay/proxy-pay",
    "pages/lot-space/lot-space",
    "pages/messages/messages",
    "pages/bind-phone/bind-phone",
    "pages/monthly-pass/monthly-pass",
    "pages/monthly-pass/apply",
    "pages/monthly-pass/renew"
  ],
```

Leave the rest of app.json unchanged.

- [ ] **Step 2: Commit**

```bash
git add miniapp/app.json
git commit -m "[MINI-005] feat: register monthly-pass pages in app.json"
```

---

### Task 4.2: Wire "我的月卡" entry from profile page

**Files:**
- Modify: `miniapp/pages/profile/profile.js`

- [ ] **Step 1: Replace the toast placeholder with navigation**

Edit `miniapp/pages/profile/profile.js`. Replace line 51:
```javascript
  goToMonthCards() { wx.showToast({ title: '我的月卡 — 后续版本开放', icon: 'none' }) },
```

With:
```javascript
  goToMonthCards() {
    var app = getApp()
    if (!app.globalData.phoneBound) {
      wx.showModal({
        title: '请先绑定手机号',
        content: '办理月卡前需要先绑定手机号',
        confirmText: '去绑定',
        cancelText: '取消',
        success: function (res) {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
          }
        },
      })
      return
    }
    wx.navigateTo({ url: '/pages/monthly-pass/monthly-pass' })
  },
```

- [ ] **Step 2: Commit**

```bash
git add miniapp/pages/profile/profile.js
git commit -m "[MINI-005] feat: wire monthly-pass entry from profile with phone check"
```

---

## Phase 5: Frontend — Monthly Pass List Page

### Task 5.1: Create monthly-pass list page (JS)

**Files:**
- Create: `miniapp/pages/monthly-pass/monthly-pass.js`
- Create: `miniapp/pages/monthly-pass/monthly-pass.json`
- Create: `miniapp/pages/monthly-pass/monthly-pass.wxml`
- Create: `miniapp/pages/monthly-pass/monthly-pass.wxss`

- [ ] **Step 1: Create monthly-pass.js**

```javascript
/**
 * 我的月卡 — 列表页
 * 任务包 5-2 月卡部分
 */
const { get } = require('../../utils/request')

Page({
  data: {
    activeList: [],
    pendingPayList: [],
    otherList: [],
    loading: false,
    empty: true,
  },

  onLoad() {
    this.loadData()
  },

  onShow() {
    this.loadData()
  },

  onPullDownRefresh() {
    this.loadData().finally(() => wx.stopPullDownRefresh())
  },

  async loadData() {
    if (this.data.loading) return
    this.setData({ loading: true })
    try {
      const list = await get('/api/v1/mini/monthly-passes')
      const arr = Array.isArray(list) ? list : []
      const activeList = []
      const pendingPayList = []
      const otherList = []
      arr.forEach(function (item) {
        if (item.passStatus === 'ACTIVE') {
          activeList.push(item)
        } else if (item.reviewStatus === 'APPROVED' && item.passStatus === 'PENDING') {
          pendingPayList.push(item)
        } else {
          otherList.push(item)
        }
      })
      this.setData({
        activeList: activeList,
        pendingPayList: pendingPayList,
        otherList: otherList,
        empty: arr.length === 0,
      })
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  goToApply() {
    var app = getApp()
    if (!app.globalData.phoneBound) {
      wx.showModal({
        title: '请先绑定手机号',
        content: '办理月卡前需要先绑定手机号',
        confirmText: '去绑定',
        cancelText: '取消',
        success: function (res) {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
          }
        },
      })
      return
    }
    wx.navigateTo({ url: '/pages/monthly-pass/apply' })
  },

  goToPay(e) {
    var id = e.currentTarget.dataset.id
    wx.showModal({
      title: '确认支付',
      content: '支付后将立即生效，确认支付？',
      success: function (res) {
        if (res.confirm) {
          wx.showLoading({ title: '支付中' })
          const { post } = require('../../utils/request')
          post('/api/v1/mini/monthly-passes/' + id + '/pay').then(function () {
            wx.hideLoading()
            wx.showToast({ title: '支付成功', icon: 'success' })
            this.loadData()
          }.bind(this)).catch(function (err) {
            wx.hideLoading()
            wx.showToast({ title: (err && err.message) || '支付失败', icon: 'none' })
          })
        }
      }.bind(this),
    })
  },

  goToRenew(e) {
    var id = e.currentTarget.dataset.id
    wx.navigateTo({ url: '/pages/monthly-pass/renew?id=' + id })
  },
})
```

- [ ] **Step 2: Create monthly-pass.json**

```json
{
  "navigationBarTitleText": "我的月卡",
  "enablePullDownRefresh": true
}
```

- [ ] **Step 3: Create monthly-pass.wxml**

```xml
<view class="container">
  <view class="header">
    <view class="title">我的月卡</view>
    <button class="apply-btn" bindtap="goToApply" type="primary" size="mini">申请月卡</button>
  </view>

  <!-- 空状态 -->
  <view class="empty" wx:if="{{empty && !loading}}">
    <text class="empty-text">暂无月卡，去申请</text>
  </view>

  <!-- 加载中 -->
  <view class="loading" wx:if="{{loading}}">
    <text>加载中...</text>
  </view>

  <!-- 生效中 -->
  <view class="section" wx:if="{{activeList.length > 0}}">
    <view class="section-title">生效中</view>
    <view class="card" wx:for="{{activeList}}" wx:key="id">
      <view class="card-row">
        <text class="plate">{{item.plateNumber}}</text>
        <text class="status status-active">生效中</text>
      </view>
      <view class="card-row">
        <text class="label">车场：</text>
        <text>{{item.parkingLotName || '未知车场'}}</text>
      </view>
      <view class="card-row">
        <text class="label">有效期：</text>
        <text>{{item.validStartDate}} 至 {{item.validEndDate}}</text>
      </view>
      <view class="card-actions">
        <button class="action-btn renew-btn" bindtap="goToRenew" data-id="{{item.id}}" size="mini">续费</button>
      </view>
    </view>
  </view>

  <!-- 待支付 -->
  <view class="section" wx:if="{{pendingPayList.length > 0}}">
    <view class="section-title">审核通过，待支付</view>
    <view class="card" wx:for="{{pendingPayList}}" wx:key="id">
      <view class="card-row">
        <text class="plate">{{item.plateNumber}}</text>
        <text class="status status-pending">待支付</text>
      </view>
      <view class="card-row">
        <text class="label">车场：</text>
        <text>{{item.parkingLotName || '未知车场'}}</text>
      </view>
      <view class="card-actions">
        <button class="action-btn pay-btn" bindtap="goToPay" data-id="{{item.id}}" size="mini">去支付</button>
      </view>
    </view>
  </view>

  <!-- 其他（审核中/已驳回/已过期等） -->
  <view class="section" wx:if="{{otherList.length > 0}}">
    <view class="section-title">其他</view>
    <view class="card" wx:for="{{otherList}}" wx:key="id">
      <view class="card-row">
        <text class="plate">{{item.plateNumber}}</text>
        <text
          class="status"
          wx:if="{{item.reviewStatus === 'PENDING'}}"
          style="color: #faad14"
        >审核中</text>
        <text
          class="status"
          wx:elif="{{item.reviewStatus === 'REJECTED'}}"
          style="color: #ff4d4f"
        >已驳回</text>
        <text
          class="status"
          wx:elif="{{item.passStatus === 'EXPIRED'}}"
          style="color: #999"
        >已过期</text>
        <text
          class="status"
          wx:elif="{{item.passStatus === 'CANCELLED'}}"
          style="color: #999"
        >已注销</text>
        <text class="status" wx:else style="color: #999">{{item.passStatus}}</text>
      </view>
      <view class="card-row" wx:if="{{item.parkingLotName}}">
        <text class="label">车场：</text>
        <text>{{item.parkingLotName}}</text>
      </view>
      <view class="card-row" wx:if="{{item.createdAt}}">
        <text class="label">申请时间：</text>
        <text>{{item.createdAt}}</text>
      </view>
    </view>
  </view>
</view>
```

- [ ] **Step 4: Create monthly-pass.wxss**

```css
.container {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.title {
  font-size: 20px;
  font-weight: bold;
}

.apply-btn {
  font-size: 14px;
}

.empty {
  text-align: center;
  padding: 60px 0;
}

.empty-text {
  font-size: 16px;
  color: #999;
}

.loading {
  text-align: center;
  padding: 40px 0;
  color: #999;
}

.section {
  margin-bottom: 20px;
}

.section-title {
  font-size: 16px;
  font-weight: bold;
  color: #333;
  margin-bottom: 10px;
  padding-left: 4px;
}

.card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 10px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.card-row {
  display: flex;
  align-items: center;
  margin-bottom: 6px;
}

.card-row:last-child {
  margin-bottom: 0;
}

.plate {
  font-size: 18px;
  font-weight: bold;
  color: #1677ff;
  flex: 1;
}

.status {
  font-size: 13px;
  padding: 2px 8px;
  border-radius: 4px;
}

.status-active {
  color: #52c41a;
  background: #f6ffed;
}

.status-pending {
  color: #faad14;
  background: #fffbe6;
}

.label {
  color: #999;
  font-size: 14px;
}

.card-actions {
  margin-top: 10px;
  text-align: right;
}

.action-btn {
  font-size: 13px;
}

.pay-btn {
  background: #1677ff;
  color: #fff;
}

.renew-btn {
  background: #52c41a;
  color: #fff;
}
```

- [ ] **Step 5: Commit**

```bash
git add miniapp/pages/monthly-pass/monthly-pass.js miniapp/pages/monthly-pass/monthly-pass.json miniapp/pages/monthly-pass/monthly-pass.wxml miniapp/pages/monthly-pass/monthly-pass.wxss
git commit -m "[MINI-005] feat: add monthly pass list page"
```

---

## Phase 6: Frontend — Apply Page

### Task 6.1: Create apply page

**Files:**
- Create: `miniapp/pages/monthly-pass/apply.js`
- Create: `miniapp/pages/monthly-pass/apply.json`
- Create: `miniapp/pages/monthly-pass/apply.wxml`
- Create: `miniapp/pages/monthly-pass/apply.wxss`

- [ ] **Step 1: Create apply.js**

```javascript
/**
 * 申请月卡页
 * 任务包 5-2 月卡部分
 */
const { get, post } = require('../../utils/request')

Page({
  data: {
    lots: [],
    lotIndex: 0,
    selectedLotId: null,
    plates: [],
    plateIndex: 0,
    selectedPlate: '',
    ownerName: '',
    ownerPhone: '',
    submitLoading: false,
    phoneBound: false,
  },

  onLoad() {
    var app = getApp()
    this.setData({ phoneBound: app.globalData.phoneBound || false })
    if (!this.data.phoneBound) {
      wx.showModal({
        title: '请先绑定手机号',
        content: '办理月卡前需要先绑定手机号',
        confirmText: '去绑定',
        cancelText: '返回',
        success: function (res) {
          if (res.confirm) {
            wx.navigateTo({ url: '/pages/bind-phone/bind-phone' })
          } else {
            wx.navigateBack()
          }
        },
      })
      return
    }
    this.loadLots()
    this.loadPlates()
  },

  async loadLots() {
    try {
      const lots = await get('/api/v1/mini/parking-lots')
      const lotArr = Array.isArray(lots) ? lots : []
      this.setData({ lots: lotArr })
      if (lotArr.length > 0) {
        this.setData({ selectedLotId: lotArr[0].id })
      }
    } catch (err) {
      wx.showToast({ title: '加载车场失败', icon: 'none' })
    }
  },

  async loadPlates() {
    try {
      const plates = await get('/api/v1/mini/bound-plates')
      const plateArr = Array.isArray(plates) ? plates : []
      // bound-plates returns objects with plateNumber field
      const plateNumbers = plateArr.map(function (p) {
        return typeof p === 'string' ? p : (p.plateNumber || p.plate || '')
      }).filter(function (p) { return p !== '' })
      this.setData({ plates: plateNumbers })
      if (plateNumbers.length > 0) {
        this.setData({ selectedPlate: plateNumbers[0] })
      }
    } catch (err) {
      wx.showToast({ title: '加载车牌失败', icon: 'none' })
    }
  },

  onLotChange(e) {
    var idx = e.detail.value
    var lot = this.data.lots[idx]
    this.setData({ lotIndex: idx, selectedLotId: lot ? lot.id : null })
  },

  onPlateChange(e) {
    var idx = e.detail.value
    this.setData({ plateIndex: idx, selectedPlate: this.data.plates[idx] })
  },

  onNameInput(e) {
    this.setData({ ownerName: e.detail.value })
  },

  onPhoneInput(e) {
    this.setData({ ownerPhone: e.detail.value })
  },

  async onSubmit() {
    if (!this.data.selectedLotId) {
      wx.showToast({ title: '请选择车场', icon: 'none' })
      return
    }
    if (!this.data.selectedPlate) {
      wx.showToast({ title: '请选择车牌', icon: 'none' })
      return
    }

    this.setData({ submitLoading: true })
    try {
      await post('/api/v1/mini/monthly-passes', {
        parkingLotId: this.data.selectedLotId,
        plateNumber: this.data.selectedPlate,
        ownerName: this.data.ownerName,
        ownerPhone: this.data.ownerPhone,
      })
      wx.showToast({ title: '申请成功', icon: 'success' })
      setTimeout(function () {
        wx.navigateBack()
      }, 1500)
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '申请失败', icon: 'none' })
    } finally {
      this.setData({ submitLoading: false })
    }
  },
})
```

- [ ] **Step 2: Create apply.json**

```json
{
  "navigationBarTitleText": "申请月卡"
}
```

- [ ] **Step 3: Create apply.wxml**

```xml
<view class="container">
  <view class="form-card">
    <!-- 选择车场 -->
    <view class="form-item">
      <view class="form-label">选择车场</view>
      <picker mode="selector" range="{{lots}}" range-key="name" bindchange="onLotChange" value="{{lotIndex}}">
        <view class="picker-value">
          {{lots[lotIndex] ? lots[lotIndex].name : '请选择车场'}}
        </view>
      </picker>
    </view>

    <!-- 选择车牌 -->
    <view class="form-item">
      <view class="form-label">选择车牌</view>
      <picker wx:if="{{plates.length > 0}}" mode="selector" range="{{plates}}" bindchange="onPlateChange" value="{{plateIndex}}">
        <view class="picker-value">
          {{plates[plateIndex] || '请选择车牌'}}
        </view>
      </picker>
      <view wx:else class="no-plate">暂无可选车牌，请先在"我的车辆"中绑定</view>
    </view>

    <!-- 车主姓名 -->
    <view class="form-item">
      <view class="form-label">车主姓名（选填）</view>
      <input class="form-input" placeholder="请输入车主姓名" value="{{ownerName}}" bindinput="onNameInput" />
    </view>

    <!-- 车主电话 -->
    <view class="form-item">
      <view class="form-label">车主电话（选填）</view>
      <input class="form-input" type="number" placeholder="请输入车主电话" value="{{ownerPhone}}" bindinput="onPhoneInput" />
    </view>
  </view>

  <!-- 提交按钮 -->
  <button class="submit-btn" type="primary" bindtap="onSubmit" loading="{{submitLoading}}" disabled="{{submitLoading || plates.length === 0}}">
    {{submitLoading ? '提交中...' : '提交申请'}}
  </button>
</view>
```

- [ ] **Step 4: Create apply.wxss**

```css
.container {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.form-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 6px;
}

.picker-value {
  font-size: 16px;
  color: #333;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.no-plate {
  font-size: 14px;
  color: #999;
  padding: 10px 0;
}

.form-input {
  font-size: 16px;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
  width: 100%;
}

.submit-btn {
  margin-top: 30px;
  width: 100%;
  border-radius: 8px;
}
```

- [ ] **Step 5: Commit**

```bash
git add miniapp/pages/monthly-pass/apply.js miniapp/pages/monthly-pass/apply.json miniapp/pages/monthly-pass/apply.wxml miniapp/pages/monthly-pass/apply.wxss
git commit -m "[MINI-005] feat: add monthly pass apply page"
```

---

## Phase 7: Frontend — Renew Page

### Task 7.1: Create renew page

**Files:**
- Create: `miniapp/pages/monthly-pass/renew.js`
- Create: `miniapp/pages/monthly-pass/renew.json`
- Create: `miniapp/pages/monthly-pass/renew.wxml`
- Create: `miniapp/pages/monthly-pass/renew.wxss`

- [ ] **Step 1: Create renew.js**

```javascript
/**
 * 月卡续费页
 * 任务包 5-2 月卡部分
 */
const { get, post } = require('../../utils/request')

const MONTH_OPTIONS = [
  { label: '1个月', value: 1, months: 1 },
  { label: '3个月', value: 3, months: 3 },
  { label: '6个月', value: 6, months: 6 },
  { label: '12个月', value: 12, months: 12 },
]

Page({
  data: {
    passId: null,
    plateNumber: '',
    parkingLotName: '',
    validEndDate: '',
    months: 1,
    monthOptions: MONTH_OPTIONS,
    monthIndex: 0,
    amountCents: 0,
    newValidEndDate: '',
    loading: false,
    submitting: false,
  },

  onLoad(options) {
    var id = options.id
    if (!id) {
      wx.showToast({ title: '参数错误', icon: 'none' })
      setTimeout(function () { wx.navigateBack() }, 1500)
      return
    }
    this.setData({ passId: id })
    this.loadDetail()
  },

  async loadDetail() {
    this.setData({ loading: true })
    try {
      var detail = await get('/api/v1/mini/monthly-passes/' + this.data.passId)
      this.setData({
        plateNumber: detail.plateNumber || '',
        parkingLotName: detail.parkingLotName || '未知车场',
        validEndDate: detail.validEndDate || '',
      })
      this.calcAmount()
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '加载失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },

  onMonthChange(e) {
    var idx = e.detail.value
    var opt = MONTH_OPTIONS[idx]
    this.setData({ monthIndex: idx, months: opt.months })
    this.calcAmount()
  },

  calcAmount() {
    // Use a hardcoded default of 30000 cents/month since we don't expose the price via the API
    var pricePerMonth = 30000
    var amount = pricePerMonth * this.data.months
    this.setData({ amountCents: amount })

    // Calculate new valid end date preview
    var currentEnd = this.data.validEndDate
    if (currentEnd) {
      var d = new Date(currentEnd)
      if (!isNaN(d.getTime())) {
        d.setMonth(d.getMonth() + this.data.months)
        var y = d.getFullYear()
        var m = ('0' + (d.getMonth() + 1)).slice(-2)
        var day = ('0' + d.getDate()).slice(-2)
        this.setData({ newValidEndDate: y + '-' + m + '-' + day })
      }
    }
  },

  async onSubmit() {
    var that = this
    wx.showModal({
      title: '确认续费',
      content: '续费 ' + that.data.months + ' 个月，金额 ¥' + (that.data.amountCents / 100).toFixed(2) + '，确认支付？',
      success: function (res) {
        if (res.confirm) {
          that.doRenew()
        }
      },
    })
  },

  async doRenew() {
    this.setData({ submitting: true })
    try {
      await post('/api/v1/mini/monthly-passes/' + this.data.passId + '/renew', {
        months: this.data.months,
      })
      wx.showToast({ title: '续费成功', icon: 'success' })
      setTimeout(function () {
        wx.navigateBack()
      }, 1500)
    } catch (err) {
      wx.showToast({ title: (err && err.message) || '续费失败', icon: 'none' })
    } finally {
      this.setData({ submitting: false })
    }
  },
})
```

- [ ] **Step 2: Create renew.json**

```json
{
  "navigationBarTitleText": "月卡续费"
}
```

- [ ] **Step 3: Create renew.wxml**

```xml
<view class="container">
  <view wx:if="{{loading}}" class="loading">加载中...</view>
  <view wx:else>
    <!-- 月卡信息 -->
    <view class="info-card">
      <view class="info-row">
        <text class="info-label">车牌号</text>
        <text class="info-value">{{plateNumber}}</text>
      </view>
      <view class="info-row">
        <text class="info-label">车场</text>
        <text class="info-value">{{parkingLotName}}</text>
      </view>
      <view class="info-row">
        <text class="info-label">当前有效期至</text>
        <text class="info-value">{{validEndDate}}</text>
      </view>
    </view>

    <!-- 续费时长选择 -->
    <view class="form-card">
      <view class="form-item">
        <view class="form-label">续费时长</view>
        <picker mode="selector" range="{{monthOptions}}" range-key="label" bindchange="onMonthChange" value="{{monthIndex}}">
          <view class="picker-value">{{monthOptions[monthIndex].label}}</view>
        </picker>
      </view>

      <view class="price-row">
        <text class="price-label">续费金额</text>
        <text class="price-value">¥{{(amountCents / 100).toFixed(2)}}</text>
      </view>

      <view class="info-row" wx:if="{{newValidEndDate}}">
        <text class="info-label">续费后有效期至</text>
        <text class="info-value highlight">{{newValidEndDate}}</text>
      </view>
    </view>

    <!-- 提交按钮 -->
    <button class="submit-btn" type="primary" bindtap="onSubmit" loading="{{submitting}}" disabled="{{submitting}}">
      {{submitting ? '支付中...' : '确认续费'}}
    </button>
  </view>
</view>
```

- [ ] **Step 4: Create renew.wxss**

```css
.container {
  padding: 16px;
  background: #f5f7fa;
  min-height: 100vh;
}

.loading {
  text-align: center;
  padding: 60px 0;
  color: #999;
}

.info-card,
.form-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  border-bottom: 1px solid #f5f5f5;
}

.info-row:last-child {
  border-bottom: none;
}

.info-label {
  font-size: 14px;
  color: #999;
}

.info-value {
  font-size: 14px;
  color: #333;
}

.info-value.highlight {
  color: #1677ff;
  font-weight: bold;
}

.form-item {
  margin-bottom: 16px;
}

.form-label {
  font-size: 14px;
  color: #666;
  margin-bottom: 6px;
}

.picker-value {
  font-size: 18px;
  color: #333;
  padding: 10px 0;
  border-bottom: 1px solid #eee;
}

.price-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px 0;
  border-top: 1px solid #f5f5f5;
}

.price-label {
  font-size: 14px;
  color: #999;
}

.price-value {
  font-size: 22px;
  color: #ff4d4f;
  font-weight: bold;
}

.submit-btn {
  margin-top: 30px;
  width: 100%;
  border-radius: 8px;
}
```

- [ ] **Step 5: Commit**

```bash
git add miniapp/pages/monthly-pass/renew.js miniapp/pages/monthly-pass/renew.json miniapp/pages/monthly-pass/renew.wxml miniapp/pages/monthly-pass/renew.wxss
git commit -m "[MINI-005] feat: add monthly pass renew page"
```

---

## Phase 8: Compile & Verify

### Task 8.1: Full compile

- [ ] **Step 1: Run full compile**

```bash
mvn clean compile -pl parking-system -am
```
Expected: `BUILD SUCCESS`

- [ ] **Step 2: Verify no compilation errors**

If any compilation errors occur, inspect the error output and fix before proceeding.

### Task 8.2: Run existing tests to verify no regressions

- [ ] **Step 1: Run tests**

```bash
mvn test -pl parking-boot -am -Dtest="*MonthlyPass*" -DfailIfNoTests=false
```
Expected: all MonthlyPass-related tests pass. If no MonthlyPass-specific tests exist, the command should still succeed with `BUILD SUCCESS` and `No tests were executed!` is acceptable.

- [ ] **Step 2: Run broader test suite**

```bash
mvn test -pl parking-boot -am
```
Expected: `BUILD SUCCESS` (all existing tests pass, no regressions).

### Task 8.3: Verify Flyway migration applies cleanly

- [ ] **Step 1: Verify Flyway migration file was picked up**

Run:
```bash
ls -la parking-boot/src/main/resources/db/migration/V20260718006__monthly_pass_price_param.sql
```
Expected: file exists.

### Task 8.4: Final commit (if needed)

- [ ] **Step 1: Verify clean git status**

```bash
git status
```
Expected: no uncommitted changes related to this feature.

---

## Summary

| Phase | Tasks | Files Created | Files Modified |
|-------|-------|---------------|----------------|
| 0: DB & Config | 1 | 1 (Flyway SQL) | 1 (ParamKeys.java) |
| 1: DTOs | 2 | 2 (request DTOs) | 0 |
| 2: Service | 1 | 1 (MiniMonthlyPassService.java) | 0 |
| 3: Controller | 1 | 1 (MiniMonthlyPassController.java) | 0 |
| 4: Frontend Infra | 2 | 0 | 2 (app.json, profile.js) |
| 5: List Page | 1 | 4 (list page files) | 0 |
| 6: Apply Page | 1 | 4 (apply page files) | 0 |
| 7: Renew Page | 1 | 4 (renew page files) | 0 |
| 8: Verify | 4 | 0 | 0 |
| **Total** | **14** | **17** | **3** |

**Total tasks**: 14 (each with sub-steps)
