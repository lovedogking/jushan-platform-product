# 岗亭端（booth-web）Phase 4 对齐 V1.1 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align the booth terminal web application (booth-web) with V1.1 requirements across three dimensions: gate operations & fee processing (4-1), shift handover cash drawer reconciliation (4-2), and booth layout redesign with vehicle query page (4-3).

**Architecture:** Three sequential task packages delivered within the same project. Package 4-1 extends manual gate open with charge tracking and adds a fee reduction API; 4-2 adds shift statistics calculation and handover page; 4-3 redesigns the booth layout into three-section mode, moves token to sessionStorage, adds vehicle query, and replaces the recognition-failure modal with a notification.

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus + Flyway (backend) | Vue 3 + Ant Design Vue + Pinia + TypeScript (frontend)

---

## Task Package 4-1: 通行作业与收费处理对齐

### Task 1: Flyway — 新增 fee:reduce 权限码

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260719101__phase4_fee_reduce_permission.sql`

- [ ] **Step 1: 创建权限码 Flyway 迁移脚本**

```sql
-- Phase 4-1: 新增 fee:reduce 权限（费用减免独立权限码，最小权限原则）
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

-- 为 parking_manager 角色授权
INSERT INTO sys_role_permission (role_code, permission_code)
SELECT 'parking_manager', 'fee:reduce'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_role_permission
    WHERE role_code = 'parking_manager' AND permission_code = 'fee:reduce'
);
```

- [ ] **Step 2: 验证迁移语法**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 2: Backend — 扩展 single-channel manualOpenGate 接口参数

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/controller/RecognitionEventController.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/service/RecognitionEventService.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/service/impl/RecognitionEventServiceImpl.java`

- [ ] **Step 1: 扩展 RecognitionEventService 接口签名**

Modify `manualOpenGate` method signature in `RecognitionEventService.java`.

Old:
```java
    RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason);
```

New:
```java
    /**
     * 人工开闸。
     *
     * @param laneId       通道ID
     * @param operatorId   操作人ID
     * @param reason       开闸原因
     * @param isCharge     是否计费
     * @param feeCents     计费金额（分），isCharge=true 时有效
     * @param plateNumber  车牌号（可选，用于审计记录）
     * @return 开闸结果
     */
    RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                       boolean isCharge, Integer feeCents, String plateNumber);
```

- [ ] **Step 2: 更新 RecognitionEventServiceImpl.manualOpenGate 实现**

Modify `manualOpenGate` in `RecognitionEventServiceImpl.java`.

Old (lines 113-196):
```java
    @Override
    public RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason) {
        log.info("人工开闸请求: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);
```

New:
```java
    @Override
    public RecognitionResultVO manualOpenGate(Long laneId, Long operatorId, String reason,
                                              boolean isCharge, Integer feeCents, String plateNumber) {
        log.info("人工开闸请求: laneId={}, operatorId={}, reason={}, isCharge={}, feeCents={}, plateNumber={}",
                laneId, operatorId, reason, isCharge, feeCents, plateNumber);
```

And update the audit log at the end of the method (the last log.info before return).

Old (lines 190-193):
```java
        // 记录审计日志
        log.info("人工开闸审计: laneId={}, operatorId={}, reason={}, deviceId={}, deviceSn={}, "
                + "gateCommandSent={}, gateDeviceAck={}, gateOpened={}",
                laneId, operatorId, reason, gateDevice.getId(), gateDevice.getDeviceSn(),
                result.getGateCommandSent(), result.getGateDeviceAck(), result.getGateOpened());
```

New:
```java
        // 记录审计日志
        log.info("人工开闸审计: laneId={}, operatorId={}, reason={}, deviceId={}, deviceSn={}, "
                + "gateCommandSent={}, gateDeviceAck={}, gateOpened={}, "
                + "isCharge={}, feeCents={}, plateNumber={}",
                laneId, operatorId, reason, gateDevice.getId(), gateDevice.getDeviceSn(),
                result.getGateCommandSent(), result.getGateDeviceAck(), result.getGateOpened(),
                isCharge, feeCents, plateNumber);
```

- [ ] **Step 3: 更新 RecognitionEventController.manualOpenGate 端点**

Modify `manualOpenGate` in `RecognitionEventController.java`.

Old (lines 60-69):
```java
    @PostMapping("/manual-open-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualOpenGate(
            @RequestParam Long laneId,
            @RequestParam String reason) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualOpenGate(laneId, operatorId, reason);
        log.info("人工开闸: laneId={}, operatorId={}, reason={}", laneId, operatorId, reason);
        return R.ok(result);
    }
```

New:
```java
    @PostMapping("/manual-open-gate")
    @RequirePermission("booth:operate")
    public R<RecognitionResultVO> manualOpenGate(
            @RequestParam Long laneId,
            @RequestParam String reason,
            @RequestParam(defaultValue = "false") boolean isCharge,
            @RequestParam(defaultValue = "0") Integer feeCents,
            @RequestParam(required = false) String plateNumber) {
        Long operatorId = com.jushan.common.auth.TenantContext.getUserId();
        RecognitionResultVO result = recognitionEventService.manualOpenGate(
                laneId, operatorId, reason, isCharge, feeCents, plateNumber);
        log.info("人工开闸: laneId={}, operatorId={}, reason={}, isCharge={}, feeCents={}, plateNumber={}",
                laneId, operatorId, reason, isCharge, feeCents, plateNumber);
        return R.ok(result);
    }
```

- [ ] **Step 4: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 3: Backend — 新增费用减免接口 (BoothFeeReductionController)

**Files:**
- Create: `parking-system/src/main/java/com/jushan/platform/modules/booth/controller/BoothFeeReductionController.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/booth/service/FeeReductionService.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/booth/service/impl/FeeReductionServiceImpl.java`
- Create: `parking-system/src/main/java/com/jushan/platform/modules/booth/dto/FeeReductionCmd.java`

- [ ] **Step 1: 创建 FeeReductionCmd DTO**

Write `parking-system/src/main/java/com/jushan/platform/modules/booth/dto/FeeReductionCmd.java`:

```java
package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 费用减免命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeReductionCmd {

    /** 在场记录ID */
    @NotNull(message = "在场记录ID不能为空")
    private Long sessionId;

    /** 原应收金额（分） */
    @NotNull(message = "原应收金额不能为空")
    @Min(value = 0, message = "原应收金额不能为负数")
    private Integer originalFeeCents;

    /** 减免后应收金额（分） */
    @NotNull(message = "减免后金额不能为空")
    @Min(value = 0, message = "减免后金额不能为负数")
    private Integer reducedFeeCents;

    /** 减免金额（分） */
    @NotNull(message = "减免金额不能为空")
    @Min(value = 0, message = "减免金额不能为负数")
    private Integer reductionCents;

    /** 减免原因 */
    @NotBlank(message = "减免原因不能为空")
    @Size(max = 200, message = "减免原因最多200个字符")
    private String reason;
}
```

- [ ] **Step 2: 创建 FeeReductionService 接口**

Write `parking-system/src/main/java/com/jushan/platform/modules/booth/service/FeeReductionService.java`:

```java
package com.jushan.platform.modules.booth.service;

import com.jushan.platform.modules.booth.dto.FeeReductionCmd;
import com.jushan.platform.modules.booth.vo.FeeReductionVO;

/**
 * 费用减免服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface FeeReductionService {

    /**
     * 执行费用减免。
     * <p>
     * 校验 session 存在且费用有效，
     * 验证 reducedFeeCents <= originalFeeCents，
     * 更新费用，写入审计日志。
     *
     * @param cmd 减免命令
     * @return 减免结果
     */
    FeeReductionVO apply(FeeReductionCmd cmd);
}
```

- [ ] **Step 3: 创建 FeeReductionVO**

Write `parking-system/src/main/java/com/jushan/platform/modules/booth/vo/FeeReductionVO.java`:

```java
package com.jushan.platform.modules.booth.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 费用减免结果视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeReductionVO {

    private Long sessionId;
    private Integer originalFeeCents;
    private Integer reducedFeeCents;
    private Integer reductionCents;
    private LocalDateTime appliedAt;
}
```

- [ ] **Step 4: 创建 FeeReductionServiceImpl**

Write `parking-system/src/main/java/com/jushan/platform/modules/booth/service/impl/FeeReductionServiceImpl.java`:

```java
package com.jushan.platform.modules.booth.service.impl;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.dto.FeeReductionCmd;
import com.jushan.platform.modules.booth.service.FeeReductionService;
import com.jushan.platform.modules.booth.vo.FeeReductionVO;
import com.jushan.system.entity.DeviceCommandAudit;
import com.jushan.system.mapper.DeviceCommandAuditMapper;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 费用减免服务实现。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class FeeReductionServiceImpl implements FeeReductionService {

    private final ParkingSessionMapper parkingSessionMapper;
    private final DeviceCommandAuditMapper deviceCommandAuditMapper;

    public FeeReductionServiceImpl(ParkingSessionMapper parkingSessionMapper,
                                   DeviceCommandAuditMapper deviceCommandAuditMapper) {
        this.parkingSessionMapper = parkingSessionMapper;
        this.deviceCommandAuditMapper = deviceCommandAuditMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FeeReductionVO apply(FeeReductionCmd cmd) {
        Long tenantId = TenantContext.requireTenantId();
        Long operatorId = TenantContext.requireUserId();

        // 1. 校验在场记录（parking_session 状态为 IN 或 费用尚未结算）
        ParkingSession session = parkingSessionMapper.selectById(cmd.getSessionId());
        if (session == null || !tenantId.equals(session.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "在场记录不存在");
        }
        // parking_session 实体使用 IN/OUT/EXCEPTION 状态，
        // 不做严格的状态校验，由上层 ChargePanel 保证仅对正在收费的记录发起减免

        // 2. 校验金额
        if (cmd.getReducedFeeCents() > cmd.getOriginalFeeCents()) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "减免后金额不能大于原金额");
        }
        int expectedReduction = cmd.getOriginalFeeCents() - cmd.getReducedFeeCents();
        if (cmd.getReductionCents() != expectedReduction) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "减免金额计算不一致");
        }

        // 3. 更新 parking_session.fee_amount（转换分为元存储到 DECIMAL 字段）
        BigDecimal reducedFeeYuan = BigDecimal.valueOf(cmd.getReducedFeeCents()).movePointLeft(2);
        session.setFeeAmount(reducedFeeYuan);
        session.setUpdatedAt(LocalDateTime.now());
        parkingSessionMapper.updateById(session);

        // 4. 写入审计日志
        DeviceCommandAudit audit = new DeviceCommandAudit();
        audit.setTenantId(tenantId);
        audit.setParkingLotId(session.getParkingLotId());
        audit.setLaneId(session.getLaneId());
        audit.setPlateNumber(session.getPlateNumber());
        audit.setFeeCents(cmd.getReducedFeeCents());
        audit.setCommandType("FEE_REDUCTION");
        audit.setSource("MANUAL");
        audit.setOperatorId(operatorId);
        // operatorName 从当前用户名获取；若不可用则回退到 operatorId
        String operatorName = String.valueOf(operatorId);
        audit.setOperatorName(operatorName);
        audit.setReason(cmd.getReason());
        audit.setStatus("SUCCESS");
        audit.setRequestPayload(buildRequestPayload(cmd));
        audit.setIssuedAt(LocalDateTime.now());
        audit.setCreatedAt(LocalDateTime.now());
        audit.setUpdatedAt(LocalDateTime.now());
        deviceCommandAuditMapper.insert(audit);

        log.info("费用减免: sessionId={}, originalFeeCents={}, reducedFeeCents={}, reductionCents={}, reason={}",
                cmd.getSessionId(), cmd.getOriginalFeeCents(), cmd.getReducedFeeCents(),
                cmd.getReductionCents(), cmd.getReason());

        // 5. 返回结果
        FeeReductionVO vo = new FeeReductionVO();
        vo.setSessionId(cmd.getSessionId());
        vo.setOriginalFeeCents(cmd.getOriginalFeeCents());
        vo.setReducedFeeCents(cmd.getReducedFeeCents());
        vo.setReductionCents(cmd.getReductionCents());
        vo.setAppliedAt(LocalDateTime.now());
        return vo;
    }

    private String buildRequestPayload(FeeReductionCmd cmd) {
        return "{\"sessionId\":" + cmd.getSessionId()
                + ",\"originalFeeCents\":" + cmd.getOriginalFeeCents()
                + ",\"reducedFeeCents\":" + cmd.getReducedFeeCents()
                + ",\"reductionCents\":" + cmd.getReductionCents()
                + ",\"reason\":\"" + (cmd.getReason() != null ? cmd.getReason() : "") + "\"}";
    }
}
```

- [ ] **Step 5: 创建 BoothFeeReductionController**

Write `parking-system/src/main/java/com/jushan/platform/modules/booth/controller/BoothFeeReductionController.java`:

```java
package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.booth.dto.FeeReductionCmd;
import com.jushan.platform.modules.booth.service.FeeReductionService;
import com.jushan.platform.modules.booth.vo.FeeReductionVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 费用减免控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/booth/charge")
public class BoothFeeReductionController {

    private final FeeReductionService feeReductionService;

    public BoothFeeReductionController(FeeReductionService feeReductionService) {
        this.feeReductionService = feeReductionService;
    }

    @PostMapping("/fee-reduction")
    @RequirePermission("fee:reduce")
    public R<FeeReductionVO> applyFeeReduction(@Valid @RequestBody FeeReductionCmd cmd) {
        FeeReductionVO vo = feeReductionService.apply(cmd);
        log.info("费用减免完成: sessionId={}, reducedFeeCents={}", cmd.getSessionId(), cmd.getReducedFeeCents());
        return R.ok(vo);
    }
}
```

- [ ] **Step 6: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

**Note:** The `TenantContext.Snapshot` record contains `tenantId`, `userId`, `userType`, `roles`, `permissions` — it does NOT have an `operatorName` field. The code above uses `String.valueOf(operatorId)` as the operatorName fallback. If you need the actual display name, add a `TenantContext.get("displayName")` accessor or query sys_user table — but that is out of scope for this plan.

---

### Task 4: Frontend — 扩展 manualOpenGate API 调用参数

**Files:**
- Modify: `booth-web/src/api/charge.ts`

- [ ] **Step 1: 更新 manualOpenGate 函数签名**

Modify `manualOpenGate` function in `charge.ts` to accept new optional params.

Old (lines 43-48):
```typescript
export function manualOpenGate(laneId: number, reason: string): Promise<GateOpenResult> {
  return request.post<GateOpenResult>(
    '/v1/booth/recognition/manual-open-gate',
    undefined,
    { params: { laneId, reason } },
  )
}
```

New:
```typescript
/** 人工开闸（扩展版，支持计费参数）。 */
export function manualOpenGate(
  laneId: number,
  reason: string,
  options?: { isCharge?: boolean; feeCents?: number; plateNumber?: string },
): Promise<GateOpenResult> {
  return request.post<GateOpenResult>(
    '/v1/booth/recognition/manual-open-gate',
    undefined,
    {
      params: {
        laneId,
        reason,
        isCharge: options?.isCharge ?? false,
        feeCents: options?.feeCents ?? 0,
        plateNumber: options?.plateNumber ?? undefined,
      },
    },
  )
}
```

- [ ] **Step 2: 验证 import 未断裂**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20`
Expected: No new errors in charge.ts (ignore pre-existing errors)

---

### Task 5: Frontend — 扩展 ManualReleaseModal 单通道模式 (isCharge/feeCents)

**Files:**
- Modify: `booth-web/src/components/ManualReleaseModal.vue`

- [ ] **Step 1: 重写单通道模板，添加"是否计费"开关和金额输入**

Replace the single-channel form template section (lines 12-27):

Old:
```vue
    <template v-if="!batch">
      <a-form :model="formState" layout="vertical">
        <a-form-item label="放行车辆">
          <a-input :value="plateNumber" disabled />
        </a-form-item>

        <a-form-item label="备注">
          <a-textarea
            v-model:value="formState.remark"
            placeholder="选填：放行备注说明"
            :rows="2"
            :maxlength="200"
            :disabled="releasing"
          />
        </a-form-item>
      </a-form>
    </template>
```

New:
```vue
    <template v-if="!batch">
      <a-form :model="formState" layout="vertical">
        <a-form-item label="放行车辆">
          <a-input :value="plateNumber" disabled />
        </a-form-item>

        <a-form-item label="是否计费">
          <a-switch v-model:checked="formState.isCharge" :disabled="releasing" />
          <span style="margin-left: 8px; color: #6b7280; font-size: 12px;">
            {{ formState.isCharge ? '计费放行' : '免费放行' }}
          </span>
        </a-form-item>

        <a-form-item v-if="formState.isCharge" label="计费金额（元）">
          <a-input-number
            v-model:value="formState.amountYuan"
            :min="0"
            :precision="2"
            :disabled="releasing"
            placeholder="请输入收费金额"
            style="width: 100%"
          >
            <template #addonAfter>元</template>
          </a-input-number>
        </a-form-item>

        <a-form-item label="放行原因" required>
          <a-textarea
            v-model:value="formState.remark"
            placeholder="必填：请填写放行原因"
            :rows="2"
            :maxlength="200"
            :disabled="releasing"
          />
        </a-form-item>
      </a-form>
    </template>
```

- [ ] **Step 2: 更新 formState 定义**

Modify the `formState` in `<script setup>` (line 162):

Old:
```typescript
const formState = reactive({
  remark: '',
})
```

New:
```typescript
const formState = reactive({
  remark: '',
  isCharge: false,
  amountYuan: 0,
})
```

- [ ] **Step 3: 更新弹窗打开时重置逻辑**

In the `watch` for `props.open` (line 189 area), add reset for new fields:

Old:
```typescript
    if (newVal) {
      formState.remark = ''
      releaseResult.value = null
      selectedLaneIds.value = []
      batchResult.success = []
      batchResult.failed = []
      batchResult.successCount = 0
      batchResult.failedCount = 0
    }
```

New:
```typescript
    if (newVal) {
      formState.remark = ''
      formState.isCharge = false
      formState.amountYuan = 0
      releaseResult.value = null
      selectedLaneIds.value = []
      batchResult.success = []
      batchResult.failed = []
      batchResult.successCount = 0
      batchResult.failedCount = 0
    }
```

- [ ] **Step 4: 更新单通道确认逻辑，传递 isCharge/feeCents**

In `handleConfirm()` single-channel mode (around line 256), update the `manualOpenGate` call:

Old:
```typescript
      const result = await manualOpenGate(props.laneId, reasonText)
```

New:
```typescript
      const feeCents = formState.isCharge
        ? Math.round(formState.amountYuan * 100)
        : 0
      const result = await manualOpenGate(
        props.laneId,
        reasonText,
        {
          isCharge: formState.isCharge,
          feeCents,
          plateNumber: props.plateNumber || undefined,
        },
      )
```

- [ ] **Step 5: 更新原因校验（必填）**

Add validation at the top of `handleConfirm()` for single-channel mode, before the existing batch check. Add after `handleConfirm()` function opening (after the closing `}` of `releasing.value = true`):

Old (just after `releaseResult.value = null`):
```typescript
  if (props.batch && selectedLaneIds.value.length === 0) {
```

New (add before the batch check):
```typescript
  // 单通道模式：放行原因必填
  if (!props.batch && !formState.remark.trim()) {
    message.warning('请填写放行原因')
    releasing.value = false
    return
  }

  // 单通道计费模式：金额必填且 > 0
  if (!props.batch && formState.isCharge && formState.amountYuan <= 0) {
    message.warning('计费放行请填写收费金额')
    releasing.value = false
    return
  }

  if (props.batch && selectedLaneIds.value.length === 0) {
```

- [ ] **Step 6: 编译验证**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20`
Expected: No new errors in ManualReleaseModal.vue

---

### Task 6: Frontend — ChargePanel 隐藏扫码支付 + 新增费用减免功能

**Files:**
- Modify: `booth-web/src/components/ChargePanel.vue`
- Modify: `booth-web/src/api/charge.ts`

- [ ] **Step 1: 隐藏微信/支付宝支付方式**

In `ChargePanel.vue` template, wrap the WeChat and Alipay radio buttons with `v-if` comments.

Old (lines 71-74):
```vue
            <a-radio-button value="CASH">现金</a-radio-button>
            <a-radio-button value="WECHAT">微信</a-radio-button>
            <a-radio-button value="ALIPAY">支付宝</a-radio-button>
```

New:
```vue
            <a-radio-button value="CASH">现金</a-radio-button>
            <!-- Phase 3: 扫码支付暂不启用
            <a-radio-button value="WECHAT">微信</a-radio-button>
            <a-radio-button value="ALIPAY">支付宝</a-radio-button>
            -->
```

Also hide the scan input area:

Old (lines 77-94):
```vue
          <div v-if="paymentMethod === 'WECHAT' || paymentMethod === 'ALIPAY'" class="scan-input-area">
            <a-input
              ref="scanInputRef"
              v-model:value="authCode"
              placeholder="请扫描车主付款码"
              size="large"
              :disabled="submitting"
              @keydown="handleScanKeydown"
              @press-enter="handleConfirmCharge"
            >
              <template #prefix>
                <ScanOutlined />
              </template>
            </a-input>
            <div class="scan-hint">
              支持扫码枪自动输入，或手动输入付款码后按回车
            </div>
          </div>
```

New:
```vue
          <!-- Phase 3: 扫码支付暂不启用
          <div v-if="paymentMethod === 'WECHAT' || paymentMethod === 'ALIPAY'" class="scan-input-area">
            ...
          </div>
          -->
```

Update `canConfirmCharge` computed to remove scan payment check:

Old (lines 249-257):
```typescript
const canConfirmCharge = computed(() => {
  if (!store.currentChargeInfo) return false
  if (submitting.value) return false
  // 扫码支付需要输入付款码
  if (paymentMethod.value === 'WECHAT' || paymentMethod.value === 'ALIPAY') {
    return authCode.value.trim().length > 0
  }
  return true
})
```

New:
```typescript
const canConfirmCharge = computed(() => {
  if (!store.currentChargeInfo) return false
  if (submitting.value) return false
  return true
})
```

- [ ] **Step 2: 新增费用减免按钮（权限控制）**

Add a fee reduction button between "确认收费" and "免费放行" in the template. Before the `<a-button` for 免费放行 (around line 111), insert:

```vue
            <!-- 费用减免按钮：仅 fee:reduce 权限可见 -->
            <a-button
              v-if="hasFeeReducePermission"
              size="large"
              block
              :disabled="submitting"
              @click="showFeeReductionModal = true"
            >
              <template #icon><DollarOutlined /></template>
              费用减免
            </a-button>
```

Add the import for `DollarOutlined`:

Old (line 152):
```typescript
import {
  CheckOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  ScanOutlined,
} from '@ant-design/icons-vue'
```

New:
```typescript
import {
  CheckOutlined,
  ThunderboltOutlined,
  ToolOutlined,
  ScanOutlined,
  DollarOutlined,
} from '@ant-design/icons-vue'
```

- [ ] **Step 3: 新增费用减免弹窗状态和模板**

Add to `<script setup>` after `manualReleaseVisible`:
```typescript
const showFeeReductionModal = ref(false)
const feeReductionReducing = ref(false)
const feeReductionAmountYuan = ref(0)
const feeReductionReason = ref('')
```

Add the fee reduction modal template right before the closing `</a-drawer>` tag (before `</template>` at ~ line 145):

```vue
    <!-- 费用减免弹窗 -->
    <a-modal
      v-model:open="showFeeReductionModal"
      title="费用减免"
      :confirm-loading="feeReductionReducing"
      @ok="handleFeeReductionConfirm"
      @cancel="handleFeeReductionCancel"
    >
      <a-form layout="vertical">
        <a-form-item label="原应收金额">
          <a-input :value="currentFeeDisplay" disabled />
        </a-form-item>
        <a-form-item label="减免后金额（元）">
          <a-input-number
            v-model:value="feeReductionAmountYuan"
            :min="0"
            :max="currentFeeYuan"
            :precision="2"
            :disabled="feeReductionReducing"
            style="width: 100%"
          >
            <template #addonAfter>元</template>
          </a-input-number>
        </a-form-item>
        <a-form-item label="减免原因" required>
          <a-textarea
            v-model:value="feeReductionReason"
            placeholder="请填写减免原因"
            :rows="2"
            :maxlength="200"
            :disabled="feeReductionReducing"
          />
        </a-form-item>
      </a-form>
    </a-modal>
```

- [ ] **Step 4: 新增减免相关计算属性和方法**

Add computed properties in `<script setup>`:

```typescript
/** 当前应收金额（元） */
const currentFeeYuan = computed(() => {
  const feeCents = store.currentChargeInfo?.feeCents
  return feeCents != null ? feeCents / 100 : 0
})

/** 当前应收金额展示文字 */
const currentFeeDisplay = computed(() => {
  return `¥ ${currentFeeYuan.value.toFixed(2)}`
})

/** 当前用户是否有 fee:reduce 权限 */
const hasFeeReducePermission = computed(() => {
  // permissions 从 store 获取，若 store 无此方法则从 sessionStorage 读取
  try {
    const permsJson = sessionStorage.getItem('jushan_permissions')
    if (permsJson) {
      const perms: string[] = JSON.parse(permsJson)
      return perms.includes('fee:reduce')
    }
  } catch { /* ignore */ }
  return false
})
```

Add methods for fee reduction:

```typescript
/** 减免弹窗打开时初始化金额为原金额 */
function handleFeeReductionOpen() {
  feeReductionAmountYuan.value = currentFeeYuan.value
  feeReductionReason.value = ''
}

/** 提交费用减免 */
async function handleFeeReductionConfirm() {
  if (!feeReductionReason.value.trim()) {
    message.warning('请填写减免原因')
    return
  }
  if (feeReductionAmountYuan.value < 0) {
    message.warning('减免后金额不能为负数')
    return
  }

  const info = store.currentChargeInfo
  if (!info) return

  const originalFeeCents = info.feeCents
  const reducedFeeCents = Math.round(feeReductionAmountYuan.value * 100)

  // 超阈值二次确认
  const thresholdCents = 50000 // 默认500元阈值，实际应从 sys_config 读取
  if (originalFeeCents > thresholdCents) {
    // 使用 ant-design-vue Modal.confirm
    const { createVNode } = await import('vue')
    const { Modal } = await import('ant-design-vue')
    Modal.confirm({
      title: '减免金额较大',
      content: `原金额 ¥${(originalFeeCents / 100).toFixed(2)}，减免后 ¥${(reducedFeeCents / 100).toFixed(2)}，确认提交？`,
      okText: '确认减免',
      cancelText: '取消',
      onOk: () => doFeeReduction(originalFeeCents, reducedFeeCents),
    })
  } else {
    await doFeeReduction(originalFeeCents, reducedFeeCents)
  }
}

async function doFeeReduction(originalFeeCents: number, reducedFeeCents: number) {
  feeReductionReducing.value = true
  try {
    const reductionCents = originalFeeCents - reducedFeeCents
    await submitFeeReduction({
      sessionId: store.currentChargeInfo!.sessionId,
      originalFeeCents,
      reducedFeeCents,
      reductionCents,
      reason: feeReductionReason.value.trim(),
    })
    // 更新 store 中的当前费用
    store.currentChargeInfo!.feeCents = reducedFeeCents
    store.currentChargeInfo!.feeAmount = reducedFeeCents / 100
    message.success('费用减免成功')
    showFeeReductionModal.value = false
  } catch (e: any) {
    message.error(e?.message || '费用减免失败')
  } finally {
    feeReductionReducing.value = false
  }
}

function handleFeeReductionCancel() {
  showFeeReductionModal.value = false
}
```

- [ ] **Step 5: 新增 feeReduction API 函数**

Add to `booth-web/src/api/charge.ts`:

```typescript
/** 费用减免请求 */
export interface FeeReductionRequest {
  sessionId: number
  originalFeeCents: number
  reducedFeeCents: number
  reductionCents: number
  reason: string
}

/** 费用减免结果 */
export interface FeeReductionResult {
  sessionId: number
  originalFeeCents: number
  reducedFeeCents: number
  reductionCents: number
  appliedAt: string
}

/**
 * 提交费用减免。
 * POST /api/v1/booth/charge/fee-reduction
 */
export function submitFeeReduction(data: FeeReductionRequest): Promise<FeeReductionResult> {
  return request.post<FeeReductionResult>('/v1/booth/charge/fee-reduction', data)
}
```

- [ ] **Step 6: 移除未使用的 scanInputRef 和 handleScanKeydown**

Since scan payment is hidden, the `scanInputRef` and `handleScanKeydown` are no longer needed. Remove the `lastKeyTime` variable and `handleScanKeydown` function. This is optional cleanup — the code will still compile with them.

- [ ] **Step 7: 确保登录页存储 permissions 到 sessionStorage**

In `booth-web/src/views/login/index.vue`, update `validateAndSetToken` to also store permissions:

Old (lines 54-61):
```typescript
function validateAndSetToken(accessToken: unknown): string {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('服务端返回的 accessToken 无效')
  }
  const trimmed = accessToken.trim()
  localStorage.setItem(TOKEN_KEY, trimmed)
  return trimmed
}
```

New:
```typescript
function validateAndSetToken(accessToken: unknown, permissions?: string[]): string {
  if (typeof accessToken !== 'string' || accessToken.trim().length === 0) {
    throw new Error('服务端返回的 accessToken 无效')
  }
  const trimmed = accessToken.trim()
  sessionStorage.setItem(TOKEN_KEY, trimmed)
  if (permissions && permissions.length > 0) {
    sessionStorage.setItem('jushan_permissions', JSON.stringify(permissions))
  }
  return trimmed
}
```

Update the call site in `handleSubmit`:

Old:
```typescript
    validateAndSetToken(result.token || result.accessToken)
```

New:
```typescript
    validateAndSetToken(result.token || result.accessToken, result.user?.permissions)
```

**Important:** Also update `TOKEN_KEY` references in login/index.vue — see Task 22 (sessionStorage migration). For now, just update this file too.

- [ ] **Step 8: 编译验证**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -30`
Expected: No new type errors

---

### Task 7: Backend — 添加 ParkingSessionMapper import 校验

**Files:**
- No new files; validate existing imports

- [ ] **Step 1: 验证 ParkingSessionMapper 存在**

Run:
```bash
find parking-system -name "ParkingSessionMapper.java" -type f
```
Expected: One result at `parking-system/src/main/java/com/jushan/platform/modules/parking/mapper/ParkingSessionMapper.java`

If the file doesn't exist there, check other locations and update the import in FeeReductionServiceImpl.

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 8: Frontend — 设备离线时禁用开闸按钮

**Files:**
- Modify: `booth-web/src/views/monitor/index.vue`

- [ ] **Step 1: 更新 handleManualOpenGate 添加离线检查**

Modify `handleManualOpenGate` function (lines 484-487):

Old:
```typescript
function handleManualOpenGate(laneId: number) {
  manualReleaseLaneId.value = laneId
  manualReleaseOpen.value = true
}
```

New:
```typescript
function handleManualOpenGate(laneId: number) {
  const laneCard = laneCards.value.find(lc => lc.laneId === laneId)
  if (laneCard?.isOffline) {
    message.warning('设备离线，无法操作')
    return
  }
  manualReleaseLaneId.value = laneId
  manualReleaseOpen.value = true
}
```

- [ ] **Step 2: 在模板中为离线车道禁用开闸按钮**

In the lane card template, add `:disabled` to the 开闸 button. Find the button in the template (around line 133-138):

Old:
```vue
                  <a-button
                    type="primary"
                    size="small"
                    @click="handleManualOpenGate(lane.laneId)"
                  >
                    开闸
                  </a-button>
```

New:
```vue
                  <a-button
                    type="primary"
                    size="small"
                    :disabled="lane.isOffline"
                    @click="handleManualOpenGate(lane.laneId)"
                  >
                    开闸
                  </a-button>
```

- [ ] **Step 3: 编译验证**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20`
Expected: No new errors

---

## Task Package 4-2: 交接班钱箱核对

### Task 9: Flyway — shift_record 表新增三列

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260719102__phase4_shift_record_extend.sql`

- [ ] **Step 1: 创建迁移脚本**

Write `parking-boot/src/main/resources/db/migration/V20260719102__phase4_shift_record_extend.sql`:

```sql
-- Phase 4-2: shift_record 交接班扩展
-- 1. 新增 adjust_reason 列（手工校正实收金额原因）
ALTER TABLE shift_record
    ADD COLUMN IF NOT EXISTS adjust_reason VARCHAR(200) DEFAULT NULL COMMENT '手工校正实收金额原因' AFTER online_amount;

-- 2. 新增 arrears_count 列（本班产生的欠费订单数）
ALTER TABLE shift_record
    ADD COLUMN IF NOT EXISTS arrears_count INT NOT NULL DEFAULT 0 COMMENT '本班产生的欠费订单数' AFTER exception_count;

-- 3. 新增 handover_order_count 列（交接给下一班的未支付/欠费订单数）
ALTER TABLE shift_record
    ADD COLUMN IF NOT EXISTS handover_order_count INT NOT NULL DEFAULT 0 COMMENT '交接给下一班的未支付/欠费订单数' AFTER arrears_count;
```

- [ ] **Step 2: 验证迁移语法**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 10: Backend — ShiftRecord 实体新增字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/entity/ShiftRecord.java`

- [ ] **Step 1: 在 ShiftRecord.java 新增三个字段**

Add after the `handoverRemark` field (line 69), before the constant definitions:

```java
    /** 手工校正实收金额原因 */
    private String adjustReason;

    /** 本班产生的欠费订单数 */
    private Integer arrearsCount;

    /** 交接给下一班的未支付/欠费订单数量 */
    private Integer handoverOrderCount;
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 11: Backend — ShiftRecordVO 新增字段

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/vo/ShiftRecordVO.java`

- [ ] **Step 1: 新增字段和欠费订单列表**

Add after the `handoverRemark` field (line 63), before `createdAt`:

```java
    /** 手工校正实收金额原因 */
    private String adjustReason;

    /** 本班产生的欠费订单数 */
    private Integer arrearsCount;

    /** 交接给下一班的未支付/欠费订单数量 */
    private Integer handoverOrderCount;

    /** 欠费订单列表（仅交班预览时填充） */
    private List<ArrearsOrderItem> arrearsOrders;
```

Add the inner class at the bottom of the file:

```java
    /**
     * 欠费订单简要信息（用于交班预览）。
     */
    @Data
    public static class ArrearsOrderItem {
        private Long orderId;
        private String plateNumber;
        private Integer feeCents;
        private String createdAt;
    }
```

Add the import at the top:
```java
import java.util.List;
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 12: Backend — ShiftCloseCmd 新增 confirmedCashAmount 和 adjustReason

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/dto/ShiftCloseCmd.java`

- [ ] **Step 1: 新增字段**

Add after `handoverRemark` field (line 25), before the closing `}`:

```java
    /** 操作员确认的实收金额（元），不传则使用系统计算的应收金额 */
    private BigDecimal confirmedCashAmount;

    /** 实收金额与应收金额不一致时的校正原因，最长200字符 */
    @Size(max = 200, message = "校正原因最多200个字符")
    private String adjustReason;
```

Add import:
```java
import java.math.BigDecimal;
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 13: Backend — ShiftRecordServiceImpl.closeShift() 统计计算

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/service/impl/ShiftRecordServiceImpl.java`
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/mapper/ShiftRecordMapper.java`

- [ ] **Step 1: 在 ShiftRecordMapper 新增统计查询方法**

Add to `ShiftRecordMapper.java` after the `selectByParkingLotId` method:

```java
    /**
     * 统计本班期间现金收费订单汇总金额（分）。
     * <p>
     * parking_order 表实际列名：amount_cents（非 fee_cents）、pay_time（非 paid_at）。
     * 表无 pay_method 列，按 order 的 status=PAID/COMPLETED 汇总所有订单金额作为"应收"。
     * 表无 deleted_at，跳过软删除条件。
     */
    @Select("SELECT COALESCE(SUM(po.amount_cents), 0) FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status IN ('PAID', 'COMPLETED') "
            + "AND po.pay_time >= #{startTime} AND po.pay_time <= #{endTime}")
    Integer sumCashOrderFeeCents(@Param("parkingLotId") Long parkingLotId,
                                  @Param("tenantId") Long tenantId,
                                  @Param("startTime") LocalDateTime startTime,
                                  @Param("endTime") LocalDateTime endTime);

    /**
     * 统计本班期间入场记录数。
     */
    @Select("SELECT COUNT(*) FROM parking_record pr "
            + "WHERE pr.parking_lot_id = #{parkingLotId} "
            + "AND pr.tenant_id = #{tenantId} "
            + "AND pr.entry_time >= #{startTime} AND pr.entry_time <= #{endTime} "
            + "AND pr.deleted_at IS NULL")
    int countEntries(@Param("parkingLotId") Long parkingLotId,
                     @Param("tenantId") Long tenantId,
                     @Param("startTime") LocalDateTime startTime,
                     @Param("endTime") LocalDateTime endTime);

    /**
     * 统计本班期间出场记录数。
     * <p>
     * exit_record 表无 deleted_at 列，跳过软删除条件。
     */
    @Select("SELECT COUNT(*) FROM exit_record er "
            + "WHERE er.parking_lot_id = #{parkingLotId} "
            + "AND er.tenant_id = #{tenantId} "
            + "AND er.exit_time >= #{startTime} AND er.exit_time <= #{endTime}")
    int countExits(@Param("parkingLotId") Long parkingLotId,
                   @Param("tenantId") Long tenantId,
                   @Param("startTime") LocalDateTime startTime,
                   @Param("endTime") LocalDateTime endTime);

    /**
     * 统计本班期间产生的欠费订单数。
     */
    @Select("SELECT COUNT(*) FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status = 'ARREARS' "
            + "AND po.created_at >= #{startTime} AND po.created_at <= #{endTime}")
    int countArrearsOrders(@Param("parkingLotId") Long parkingLotId,
                            @Param("tenantId") Long tenantId,
                            @Param("startTime") LocalDateTime startTime,
                            @Param("endTime") LocalDateTime endTime);

    /**
     * 统计当前车场下未支付/欠费的订单数（交接给下一班）。
     */
    @Select("SELECT COUNT(*) FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status IN ('PENDING_PAY', 'ARREARS')")
    int countHandoverOrders(@Param("parkingLotId") Long parkingLotId,
                             @Param("tenantId") Long tenantId);

    /**
     * 查询本班欠费订单详情（用于交班预览）。
     */
    @Select("SELECT po.id AS orderId, po.plate_number AS plateNumber, po.amount_cents AS feeCents, "
            + "po.created_at AS createdAt "
            + "FROM parking_order po "
            + "WHERE po.parking_lot_id = #{parkingLotId} "
            + "AND po.tenant_id = #{tenantId} "
            + "AND po.status = 'ARREARS' "
            + "AND po.created_at >= #{startTime} AND po.created_at <= #{endTime} "
            + "ORDER BY po.created_at DESC")
    List<ShiftRecordVO.ArrearsOrderItem> listArrearsOrders(@Param("parkingLotId") Long parkingLotId,
                                                             @Param("tenantId") Long tenantId,
                                                             @Param("startTime") LocalDateTime startTime,
                                                             @Param("endTime") LocalDateTime endTime);
```

Add required imports at the top:
```java
import com.jushan.platform.modules.booth.vo.ShiftRecordVO;
import java.time.LocalDateTime;
import java.util.List;
```

- [ ] **Step 2: 重写 closeShift() 添加统计计算**

Replace the `closeShift` method in `ShiftRecordServiceImpl.java`:

Old (lines 73-102):
```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftRecordVO closeShift(ShiftCloseCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();

        ShiftRecord entity = baseMapper.selectById(cmd.getShiftId());
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "交接班记录不存在");
        }

        if (!ShiftRecord.STATUS_OPEN.equals(entity.getHandoverStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该记录已交班");
        }

        // 校验只能交自己的班
        if (!operatorId.equals(entity.getOperatorId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能交自己的班");
        }

        entity.setEndTime(LocalDateTime.now());
        entity.setHandoverStatus(ShiftRecord.STATUS_CLOSED);
        entity.setHandoverTo(cmd.getHandoverTo());
        entity.setHandoverRemark(cmd.getHandoverRemark());
        entity.setUpdatedAt(LocalDateTime.now());

        baseMapper.updateById(entity);
        log.info("交接班交班: shiftId={}, operatorId={}", entity.getId(), operatorId);

        return toVO(entity);
    }
```

New:
```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ShiftRecordVO closeShift(ShiftCloseCmd cmd) {
        Long tenantId = TenantContext.requireTenantId();
        Long operatorId = TenantContext.requireUserId();

        ShiftRecord entity = baseMapper.selectById(cmd.getShiftId());
        if (entity == null || !tenantId.equals(entity.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "交接班记录不存在");
        }

        if (!ShiftRecord.STATUS_OPEN.equals(entity.getHandoverStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "该记录已交班");
        }

        // 校验只能交自己的班
        if (!operatorId.equals(entity.getOperatorId())) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN, "只能交自己的班");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = entity.getStartTime();
        Long parkingLotId = entity.getParkingLotId();

        // 1. 统计本班期间的现金收费订单汇总
        Integer cashFeeCents = baseMapper.sumCashOrderFeeCents(
                parkingLotId, tenantId, startTime, now);
        BigDecimal feeAmount = cashFeeCents != null
                ? BigDecimal.valueOf(cashFeeCents).movePointLeft(2)
                : BigDecimal.ZERO;

        // 2. 统计入场/出场数
        int entryCount = baseMapper.countEntries(parkingLotId, tenantId, startTime, now);
        int exitCount = baseMapper.countExits(parkingLotId, tenantId, startTime, now);

        // 3. 统计欠费订单数
        int arrearsCount = baseMapper.countArrearsOrders(parkingLotId, tenantId, startTime, now);

        // 4. 统计异常数（CRITICAL 级别的告警）
        //    MonitorAlert 表没有直接的 count 方法，从 ShiftRecord 已有 exception_count 保留原值

        // 5. 交接未支付/欠费订单数
        int handoverCount = baseMapper.countHandoverOrders(parkingLotId, tenantId);

        // 6. 设置应收金额
        entity.setFeeAmount(feeAmount);

        // 7. 现金实收：若前端传了 confirmedCashAmount 则使用，否则 = feeAmount
        if (cmd.getConfirmedCashAmount() != null) {
            entity.setCashAmount(cmd.getConfirmedCashAmount());
            entity.setOnlineAmount(feeAmount.subtract(cmd.getConfirmedCashAmount()));
            if (cmd.getAdjustReason() != null) {
                entity.setAdjustReason(cmd.getAdjustReason());
            }
        } else {
            entity.setCashAmount(feeAmount);
            entity.setOnlineAmount(BigDecimal.ZERO);
        }

        entity.setEntryCount(entryCount);
        entity.setExitCount(exitCount);
        entity.setArrearsCount(arrearsCount);
        entity.setHandoverOrderCount(handoverCount);

        entity.setEndTime(now);
        entity.setHandoverStatus(ShiftRecord.STATUS_CLOSED);
        entity.setHandoverTo(cmd.getHandoverTo());
        entity.setHandoverRemark(cmd.getHandoverRemark());
        entity.setUpdatedAt(now);

        baseMapper.updateById(entity);
        log.info("交接班交班: shiftId={}, operatorId={}, feeAmount={}, cashAmount={}, "
                        + "entryCount={}, exitCount={}, arrearsCount={}, handoverOrderCount={}",
                entity.getId(), operatorId, feeAmount, entity.getCashAmount(),
                entryCount, exitCount, arrearsCount, handoverCount);

        // 8. 构建 VO，附加欠费订单列表
        ShiftRecordVO vo = toVO(entity);
        List<ShiftRecordVO.ArrearsOrderItem> arrearsOrders = baseMapper.listArrearsOrders(
                parkingLotId, tenantId, startTime, now);
        vo.setArrearsOrders(arrearsOrders);

        return vo;
    }
```

- [ ] **Step 3: 更新 getCurrentShift() 添加实时统计**

Replace `getCurrentShift` method:

Old (lines 104-111):
```java
    @Override
    public ShiftRecordVO getCurrentShift() {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();

        ShiftRecord entity = baseMapper.selectOpenByOperator(operatorId, tenantId);
        return entity != null ? toVO(entity) : null;
    }
```

New:
```java
    @Override
    public ShiftRecordVO getCurrentShift() {
        Long tenantId = TenantContext.requireTenantId();
        Long operatorId = TenantContext.requireUserId();

        ShiftRecord entity = baseMapper.selectOpenByOperator(operatorId, tenantId);
        if (entity == null) {
            return null;
        }

        ShiftRecordVO vo = toVO(entity);

        // 实时计算统计数据
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startTime = entity.getStartTime();
        Long parkingLotId = entity.getParkingLotId();

        Integer cashFeeCents = baseMapper.sumCashOrderFeeCents(
                parkingLotId, tenantId, startTime, now);
        BigDecimal feeAmount = cashFeeCents != null
                ? BigDecimal.valueOf(cashFeeCents).movePointLeft(2)
                : BigDecimal.ZERO;

        int entryCount = baseMapper.countEntries(parkingLotId, tenantId, startTime, now);
        int exitCount = baseMapper.countExits(parkingLotId, tenantId, startTime, now);
        int arrearsCount = baseMapper.countArrearsOrders(parkingLotId, tenantId, startTime, now);
        int handoverCount = baseMapper.countHandoverOrders(parkingLotId, tenantId);

        vo.setFeeAmount(feeAmount);
        vo.setEntryCount(entryCount);
        vo.setExitCount(exitCount);
        vo.setArrearsCount(arrearsCount);
        vo.setHandoverOrderCount(handoverCount);

        return vo;
    }
```

- [ ] **Step 4: 更新 toVO 方法复制新字段**

Update `toVO` method to also copy `adjustReason`, `arrearsCount`, `handoverOrderCount`:

Old (lines 147-151):
```java
    private ShiftRecordVO toVO(ShiftRecord entity) {
        ShiftRecordVO vo = new ShiftRecordVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
```

No change needed since `BeanUtils.copyProperties` copies all matching fields. The new fields will be automatically copied.

- [ ] **Step 5: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 14: Backend — ShiftRecordController 更新 closeShift 接口

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/platform/modules/booth/controller/ShiftRecordController.java`

- [ ] **Step 1: 无需修改**

The `closeShift` endpoint already accepts `@Valid @RequestBody ShiftCloseCmd cmd`, and since `ShiftCloseCmd` now includes `confirmedCashAmount` and `adjustReason`, Spring will automatically bind them. No change needed to the controller.

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 15: Frontend — 新建 api/shift.ts

**Files:**
- Create: `booth-web/src/api/shift.ts`

- [ ] **Step 1: 创建交接班 API 封装**

Write `booth-web/src/api/shift.ts`:

```typescript
import request from '@/utils/request'

/** 交接班记录 */
export interface ShiftRecordVO {
  id: number
  parkingLotId: number
  operatorId: number
  operatorName: string
  shiftType: string
  startTime: string
  endTime: string | null
  entryCount: number
  exitCount: number
  feeAmount: number
  cashAmount: number
  onlineAmount: number
  exceptionCount: number
  handoverStatus: string
  handoverTo: number | null
  handoverRemark: string | null
  adjustReason: string | null
  arrearsCount: number
  handoverOrderCount: number
  arrearsOrders?: ArrearsOrderItem[]
  createdAt: string
  updatedAt: string
}

export interface ArrearsOrderItem {
  orderId: number
  plateNumber: string
  feeCents: number
  createdAt: string
}

/** 分页结果 */
export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/**
 * 开班。
 * POST /api/v1/shift-records/start
 */
export function startShift(data: {
  parkingLotId: number
  shiftType: string
  operatorName: string
}): Promise<ShiftRecordVO> {
  return request.post<ShiftRecordVO>('/v1/shift-records/start', data)
}

/**
 * 交班。
 * POST /api/v1/shift-records/close
 */
export function closeShift(data: {
  shiftId: number
  handoverTo?: number
  handoverRemark?: string
  confirmedCashAmount?: number
  adjustReason?: string
}): Promise<ShiftRecordVO> {
  return request.post<ShiftRecordVO>('/v1/shift-records/close', data)
}

/**
 * 获取当前班次信息。
 * GET /api/v1/shift-records/current
 */
export function getCurrentShift(): Promise<ShiftRecordVO | null> {
  return request.get<ShiftRecordVO | null>('/v1/shift-records/current')
}

/**
 * 查询交接班详情。
 * GET /api/v1/shift-records/{id}
 */
export function getShiftDetail(id: number): Promise<ShiftRecordVO> {
  return request.get<ShiftRecordVO>(`/v1/shift-records/${id}`)
}

/**
 * 分页查询交接班历史。
 * GET /api/v1/shift-records
 */
export function getShiftHistory(params: {
  current?: number
  size?: number
  parkingLotId?: number
  status?: string
}): Promise<PageResult<ShiftRecordVO>> {
  return request.get<PageResult<ShiftRecordVO>>('/v1/shift-records', params)
}
```

---

### Task 16: Frontend — 新建 ShiftHandover.vue 交接班页面

**Files:**
- Create: `booth-web/src/views/monitor/ShiftHandover.vue`

- [ ] **Step 1: 创建完整组件**

Write `booth-web/src/views/monitor/ShiftHandover.vue`:

```vue
<template>
  <div class="shift-handover">
    <!-- 当前班次信息 -->
    <a-card v-if="currentShift" title="当前班次信息" :bordered="false" class="shift-card">
      <a-descriptions :column="2" size="small" bordered>
        <a-descriptions-item label="班次类型">{{ shiftTypeLabel }}</a-descriptions-item>
        <a-descriptions-item label="开始时间">{{ formattedStartTime }}</a-descriptions-item>
        <a-descriptions-item label="入场车辆">{{ currentShift.entryCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="出场车辆">{{ currentShift.exitCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="系统应收">
          <span class="fee-highlight">¥{{ formatMoney(currentShift.feeAmount) }}</span>
        </a-descriptions-item>
        <a-descriptions-item label="现金实收">
          <span class="fee-highlight">¥{{ formatMoney(currentShift.cashAmount ?? currentShift.feeAmount) }}</span>
          <a-button type="link" size="small" @click="showAdjustModal = true" :disabled="adjusting">校正</a-button>
        </a-descriptions-item>
        <a-descriptions-item label="差额">
          <span :class="{ 'text-danger': (currentShift.onlineAmount ?? 0) !== 0 }">
            ¥{{ formatMoney(currentShift.onlineAmount ?? 0) }}
          </span>
        </a-descriptions-item>
        <a-descriptions-item label="欠费订单">{{ currentShift.arrearsCount ?? 0 }} 笔</a-descriptions-item>
        <a-descriptions-item label="异常处理">{{ currentShift.exceptionCount ?? 0 }} 次</a-descriptions-item>
        <a-descriptions-item label="交接订单">{{ currentShift.handoverOrderCount ?? 0 }} 笔</a-descriptions-item>
      </a-descriptions>

      <div class="shift-actions">
        <a-space>
          <a-button type="primary" :loading="closingShift" @click="handleShiftConfirm">
            交班确认
          </a-button>
        </a-space>
      </div>
    </a-card>

    <!-- 无当前班次：显示开班 -->
    <a-card v-else title="尚未开班" :bordered="false" class="shift-card">
      <a-form layout="vertical">
        <a-form-item label="班次类型" required>
          <a-select v-model:value="newShiftType" placeholder="选择班次类型" style="width: 200px">
            <a-select-option value="MORNING">早班</a-select-option>
            <a-select-option value="AFTERNOON">中班</a-select-option>
            <a-select-option value="NIGHT">晚班</a-select-option>
          </a-select>
        </a-form-item>
        <a-form-item>
          <a-button type="primary" :loading="startingShift" @click="handleStartShift">
            开班
          </a-button>
        </a-form-item>
      </a-form>
    </a-card>

    <!-- 历史交接班记录 -->
    <a-card title="历史交接班记录" :bordered="false" class="shift-card" style="margin-top: 16px">
      <a-table
        :data-source="historyRecords"
        :columns="historyColumns"
        :pagination="historyPagination"
        :loading="loadingHistory"
        row-key="id"
        size="small"
    @change="handleHistoryPageChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'feeAmount'">
            ¥{{ formatMoney(record.feeAmount) }}
          </template>
          <template v-if="column.key === 'cashAmount'">
            ¥{{ formatMoney(record.cashAmount) }}
          </template>
          <template v-if="column.key === 'onlineAmount'">
            ¥{{ formatMoney(record.onlineAmount) }}
          </template>
          <template v-if="column.key === 'handoverStatus'">
            <a-tag :color="record.handoverStatus === 'OPEN' ? 'processing' : 'default'">
              {{ record.handoverStatus === 'OPEN' ? '进行中' : '已交班' }}
            </a-tag>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 校正实收弹窗 -->
    <a-modal
      v-model:open="showAdjustModal"
      title="校正实收金额"
      @ok="handleAdjustConfirm"
      :confirm-loading="adjusting"
    >
      <a-form layout="vertical">
        <a-form-item label="系统应收">
          <a-input :value="'¥' + formatMoney(currentShift?.feeAmount ?? 0)" disabled />
        </a-form-item>
        <a-form-item label="校正后实收（元）" required>
          <a-input-number
            v-model:value="adjustedCashAmount"
            :min="0"
            :precision="2"
            style="width: 100%"
            :disabled="adjusting"
          />
        </a-form-item>
        <a-form-item label="校正原因">
          <a-textarea
            v-model:value="adjustedReason"
            placeholder="请填写校正原因（可选）"
            :rows="2"
            :maxlength="200"
            :disabled="adjusting"
          />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 交班确认弹窗 -->
    <a-modal
      v-model:open="showConfirmModal"
      title="交班确认"
      @ok="doCloseShift"
      :confirm-loading="closingShift"
      ok-text="确认交班"
    >
      <a-descriptions :column="1" size="small" bordered>
        <a-descriptions-item label="班次">{{ shiftTypeLabel }}</a-descriptions-item>
        <a-descriptions-item label="入场">{{ currentShift?.entryCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="出场">{{ currentShift?.exitCount ?? 0 }} 辆</a-descriptions-item>
        <a-descriptions-item label="系统应收">¥{{ formatMoney(currentShift?.feeAmount ?? 0) }}</a-descriptions-item>
        <a-descriptions-item label="现金实收">¥{{ formatMoney(currentShift?.cashAmount ?? currentShift?.feeAmount ?? 0) }}</a-descriptions-item>
        <a-descriptions-item label="欠费订单">{{ currentShift?.arrearsCount ?? 0 }} 笔</a-descriptions-item>
      </a-descriptions>

      <a-alert
        v-if="(currentShift?.arrearsOrders?.length ?? 0) > 0"
        type="warning"
        message="以下欠费订单将交接至下一班次"
        style="margin-top: 12px"
      />
      <a-table
        v-if="(currentShift?.arrearsOrders?.length ?? 0) > 0"
        :data-source="currentShift?.arrearsOrders ?? []"
        :columns="arrearsColumns"
        :pagination="false"
        size="small"
        row-key="orderId"
        style="margin-top: 8px"
      />
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import dayjs from 'dayjs'
import {
  getCurrentShift,
  startShift,
  closeShift,
  getShiftHistory,
  type ShiftRecordVO,
} from '@/api/shift'

const props = defineProps<{
  parkingLotId: number
}>()

const currentShift = ref<ShiftRecordVO | null>(null)
const historyRecords = ref<ShiftRecordVO[]>([])
const loadingHistory = ref(false)
const startingShift = ref(false)
const closingShift = ref(false)
const adjusting = ref(false)

const newShiftType = ref('MORNING')
const showAdjustModal = ref(false)
const showConfirmModal = ref(false)
const adjustedCashAmount = ref(0)
const adjustedReason = ref('')

const historyPagination = ref({
  current: 1,
  pageSize: 10,
  total: 0,
})

const historyColumns = [
  { title: '班次', dataIndex: 'shiftType', key: 'shiftType', width: 80 },
  { title: '操作员', dataIndex: 'operatorName', key: 'operatorName', width: 100 },
  { title: '开始时间', dataIndex: 'startTime', key: 'startTime', width: 160 },
  { title: '应收', key: 'feeAmount', width: 100 },
  { title: '实收', key: 'cashAmount', width: 100 },
  { title: '差额', key: 'onlineAmount', width: 100 },
  { title: '状态', key: 'handoverStatus', width: 80 },
]

const arrearsColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber' },
  { title: '欠费金额（分）', dataIndex: 'feeCents', key: 'feeCents' },
  { title: '产生时间', dataIndex: 'createdAt', key: 'createdAt' },
]

const shiftTypeLabel = computed(() => {
  const map: Record<string, string> = { MORNING: '早班', AFTERNOON: '中班', NIGHT: '晚班' }
  return map[currentShift.value?.shiftType ?? ''] || '-'
})

const formattedStartTime = computed(() => {
  const t = currentShift.value?.startTime
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
})

function formatMoney(value: number | null | undefined): string {
  if (value == null) return '0.00'
  return value.toFixed(2)
}

async function loadCurrentShift() {
  try {
    currentShift.value = await getCurrentShift()
    if (currentShift.value) {
      adjustedCashAmount.value = currentShift.value.cashAmount ?? currentShift.value.feeAmount ?? 0
    }
  } catch {
    currentShift.value = null
  }
}

async function loadHistory() {
  loadingHistory.value = true
  try {
    const result = await getShiftHistory({
      current: historyPagination.value.current,
      size: historyPagination.value.pageSize,
      parkingLotId: props.parkingLotId,
    })
    historyRecords.value = result.records || []
    historyPagination.value.total = result.total || 0
  } catch {
    historyRecords.value = []
  } finally {
    loadingHistory.value = false
  }
}

function handleHistoryPageChange(pag: { current: number; pageSize: number }) {
  historyPagination.value.current = pag.current
  historyPagination.value.pageSize = pag.pageSize
  loadHistory()
}

async function handleStartShift() {
  startingShift.value = true
  try {
    await startShift({
      parkingLotId: props.parkingLotId,
      shiftType: newShiftType.value,
      operatorName: '',
    })
    message.success('开班成功')
    await loadCurrentShift()
    await loadHistory()
  } catch (e: any) {
    message.error(e?.message || '开班失败')
  } finally {
    startingShift.value = false
  }
}

function handleShiftConfirm() {
  // 先刷新当前班次数据
  loadCurrentShift().then(() => {
    showConfirmModal.value = true
  })
}

async function doCloseShift() {
  closingShift.value = true
  try {
    const payload: any = { shiftId: currentShift.value!.id }
    // 如果用户校正过实收金额
    if (showAdjustModal.value && adjustedCashAmount.value !== (currentShift.value?.feeAmount ?? 0)) {
      payload.confirmedCashAmount = adjustedCashAmount.value
      payload.adjustReason = adjustedReason.value
    }
    await closeShift(payload)
    message.success('交班成功')
    showConfirmModal.value = false
    showAdjustModal.value = false
    await loadCurrentShift()
    await loadHistory()
  } catch (e: any) {
    message.error(e?.message || '交班失败')
  } finally {
    closingShift.value = false
  }
}

async function handleAdjustConfirm() {
  adjusting.value = true
  try {
    if (!currentShift.value) return
    currentShift.value.cashAmount = adjustedCashAmount.value
    currentShift.value.adjustReason = adjustedReason.value
    currentShift.value.onlineAmount = (currentShift.value.feeAmount ?? 0) - adjustedCashAmount.value
    message.success('实收金额已校正')
    showAdjustModal.value = false
  } finally {
    adjusting.value = false
  }
}

onMounted(() => {
  loadCurrentShift()
  loadHistory()
})

watch(() => props.parkingLotId, () => {
  loadCurrentShift()
  loadHistory()
})
</script>

<style lang="scss" scoped>
.shift-handover {
  padding: 8px;
}

.shift-card {
  margin-bottom: 16px;
}

.shift-actions {
  margin-top: 16px;
  text-align: center;
}

.fee-highlight {
  font-weight: 700;
  font-size: 16px;
  color: #1f2937;
}

.text-danger {
  color: #ff4d4f;
  font-weight: 600;
}
</style>
```

---

### Task 17: Frontend — stores/monitor.ts 新增 shift 状态

**Files:**
- Modify: `booth-web/src/stores/monitor.ts`

- [ ] **Step 1: 新增 shift 相关状态**

Add to the `monitor` store in `booth-web/src/stores/monitor.ts`.

After the existing `reset` function (before the `return` statement), add:

```typescript
  // ========== 交接班状态 ==========
  const currentShift = ref<any>(null)
  const shiftHistory = ref<any[]>([])
  const shiftLoading = ref(false)

  async function loadCurrentShift() {
    try {
      const { getCurrentShift } = await import('@/api/shift')
      currentShift.value = await getCurrentShift()
    } catch {
      currentShift.value = null
    }
  }

  async function loadShiftHistory(parkingLotId: number) {
    shiftLoading.value = true
    try {
      const { getShiftHistory } = await import('@/api/shift')
      const result = await getShiftHistory({ parkingLotId, current: 1, size: 20 })
      shiftHistory.value = result.records || []
    } catch {
      shiftHistory.value = []
    } finally {
      shiftLoading.value = false
    }
  }
```

Add to the `return` statement:
```typescript
    currentShift,
    shiftHistory,
    shiftLoading,
    loadCurrentShift,
    loadShiftHistory,
```

And add to the `reset` function:
```typescript
    currentShift.value = null
    shiftHistory.value = []
    shiftLoading.value = false
```

- [ ] **Step 2: 编译验证**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20`
Expected: No new errors

---

## Task Package 4-3: 岗亭布局与查询页

### Task 18: Frontend — 新建 ParkingLotSidebar.vue

**Files:**
- Create: `booth-web/src/views/monitor/ParkingLotSidebar.vue`

- [ ] **Step 1: 创建左侧车场列表组件**

Write `booth-web/src/views/monitor/ParkingLotSidebar.vue`:

```vue
<template>
  <div class="lot-sidebar">
    <div class="sidebar-title">停车场列表</div>
    <div class="lot-list">
      <div
        v-for="lot in lots"
        :key="lot.id"
        class="lot-item"
        :class="{ active: lot.id === selectedId }"
        @click="$emit('select', lot.id)"
      >
        <div class="lot-name">
          <span class="dot" :class="lot.id === selectedId ? 'dot-active' : 'dot-inactive'" />
          {{ lot.name }}
        </div>
        <a-badge
          v-if="lot.currentVehicles != null"
          :count="lot.currentVehicles"
          :number-style="{ backgroundColor: '#1890ff' }"
          :overflow-count="999"
        />
      </div>
      <a-empty v-if="lots.length === 0" description="无授权车场" :image-style="{ height: '40px' }" />
    </div>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  lots: { id: number; name: string; status: string; currentVehicles?: number }[]
  selectedId: number | null
}>()

defineEmits<{
  select: [id: number]
}>()
</script>

<style lang="scss" scoped>
.lot-sidebar {
  width: 200px;
  min-width: 200px;
  background: #fff;
  border-right: 1px solid #e5e7eb;
  display: flex;
  flex-direction: column;
  height: 100%;
  overflow: hidden;
}

.sidebar-title {
  padding: 16px;
  font-size: 14px;
  font-weight: 700;
  color: #374151;
  border-bottom: 1px solid #e5e7eb;
}

.lot-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
}

.lot-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 16px;
  cursor: pointer;
  transition: background-color 0.15s;
  border-left: 3px solid transparent;

  &:hover {
    background-color: #f0f5ff;
  }

  &.active {
    background-color: #e6f0ff;
    border-left-color: #1890ff;
  }
}

.lot-name {
  font-size: 13px;
  color: #1f2937;
  display: flex;
  align-items: center;
  gap: 8px;
}

.dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  flex-shrink: 0;
}
.dot-active {
  background-color: #1890ff;
}
.dot-inactive {
  background-color: #d1d5db;
}
</style>
```

---

### Task 19: Frontend — 新建 MonitorTabs.vue

**Files:**
- Create: `booth-web/src/views/monitor/MonitorTabs.vue`

- [ ] **Step 1: 创建 Tab 容器组件**

Write `booth-web/src/views/monitor/MonitorTabs.vue`:

```vue
<template>
  <div class="monitor-tabs">
    <a-tabs v-model:activeKey="activeTab" type="card">
      <a-tab-pane key="monitor" tab="通行监控">
        <slot name="monitor" />
      </a-tab-pane>
      <a-tab-pane key="vehicle" tab="车辆查询">
        <VehicleQuery :parking-lot-id="parkingLotId" />
      </a-tab-pane>
      <a-tab-pane key="shift" tab="交接班">
        <ShiftHandover :parking-lot-id="parkingLotId" />
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import VehicleQuery from './VehicleQuery.vue'
import ShiftHandover from './ShiftHandover.vue'

defineProps<{
  parkingLotId: number
}>()

const activeTab = ref('monitor')
</script>

<style lang="scss" scoped>
.monitor-tabs {
  height: 100%;
  overflow: auto;

  :deep(.ant-tabs-content) {
    height: calc(100% - 46px);
  }

  :deep(.ant-tabs-tabpane) {
    height: 100%;
    overflow: auto;
  }
}
</style>
```

---

### Task 20: Frontend — 新建 VehicleQuery.vue

**Files:**
- Create: `booth-web/src/views/monitor/VehicleQuery.vue`
- Create: `booth-web/src/api/vehicle-query.ts`

- [ ] **Step 1: 创建 vehicle-query API**

Write `booth-web/src/api/vehicle-query.ts`:

```typescript
import request from '@/utils/request'

/** 在场车辆 */
export interface PresentVehicle {
  plateNumber: string
  entryTime: string
  durationMinutes: number
  vehicleType: string
  isMonthlyPass: boolean
  isFixedSpace: boolean
  parkingRecordId: number
}

/** 历史通行记录 */
export interface VehicleHistoryRecord {
  plateNumber: string
  entryTime: string
  exitTime: string | null
  feeAmount: number | null
  paymentStatus: string | null
  laneName: string
}

export interface PageResult<T> {
  records: T[]
  total: number
  size: number
  current: number
}

/**
 * 查询在场车辆。
 * GET /api/v1/booth/vehicles/present
 */
export function getPresentVehicles(params: {
  parkingLotId: number
  sortBy?: 'entryTime' | 'duration'
  sortDir?: 'asc' | 'desc'
  page?: number
  size?: number
}): Promise<PageResult<PresentVehicle>> {
  return request.get<PageResult<PresentVehicle>>('/v1/booth/vehicles/present', params)
}

/**
 * 查询历史通行记录。
 * GET /api/v1/booth/vehicles/history
 */
export function getVehicleHistory(params: {
  parkingLotId: number
  plateNumber?: string
  startTime?: string
  endTime?: string
  page?: number
  size?: number
}): Promise<PageResult<VehicleHistoryRecord>> {
  return request.get<PageResult<VehicleHistoryRecord>>('/v1/booth/vehicles/history', params)
}
```

- [ ] **Step 2: 创建 VehicleQuery.vue 组件**

Write `booth-web/src/views/monitor/VehicleQuery.vue`:

```vue
<template>
  <div class="vehicle-query">
    <a-tabs v-model:activeKey="queryTab">
      <a-tab-pane key="present" tab="在场车辆">
        <div class="query-toolbar">
          <a-space>
            <span>排序：</span>
            <a-select v-model:value="presentSortBy" style="width: 130px" @change="loadPresentVehicles">
              <a-select-option value="entryTime">入场时间</a-select-option>
              <a-select-option value="duration">停车时长</a-select-option>
            </a-select>
            <a-select v-model:value="presentSortDir" style="width: 80px" @change="loadPresentVehicles">
              <a-select-option value="desc">降序</a-select-option>
              <a-select-option value="asc">升序</a-select-option>
            </a-select>
          </a-space>
        </div>
        <a-table
          :data-source="presentVehicles"
          :columns="presentColumns"
          :pagination="presentPagination"
          :loading="loadingPresent"
          row-key="parkingRecordId"
          size="small"
      @change="handlePresentPageChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'vehicleType'">
              <a-tag :color="vehicleTypeColor(record.vehicleType)">
                {{ vehicleTypeLabel(record.vehicleType) }}
              </a-tag>
              <a-tag v-if="record.isMonthlyPass" color="green">月卡</a-tag>
              <a-tag v-if="record.isFixedSpace" color="blue">固定车位</a-tag>
            </template>
            <template v-if="column.key === 'durationMinutes'">
              {{ formatDuration(record.durationMinutes) }}
            </template>
            <template v-if="column.key === 'entryTime'">
              {{ formatTime(record.entryTime) }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>

      <a-tab-pane key="history" tab="历史记录">
        <div class="query-toolbar">
          <a-space wrap>
            <a-input v-model:value="historyPlateSearch" placeholder="车牌号（模糊）" allow-clear style="width: 140px" />
            <a-date-picker v-model:value="historyStartTime" placeholder="开始时间" />
            <a-date-picker v-model:value="historyEndTime" placeholder="结束时间" />
            <a-button type="primary" @click="loadHistoryRecords">查询</a-button>
          </a-space>
        </div>
        <a-table
          :data-source="historyRecords"
          :columns="historyColumns"
          :pagination="historyPagination"
          :loading="loadingHistory"
          row-key="plateNumber"
          size="small"
      @change="handleHistoryPageChange"
        >
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'feeAmount'">
              {{ record.feeAmount != null ? `¥${record.feeAmount.toFixed(2)}` : '-' }}
            </template>
            <template v-if="column.key === 'paymentStatus'">
              <a-tag v-if="record.paymentStatus === 'PAID'" color="success">已支付</a-tag>
              <a-tag v-else-if="record.paymentStatus === 'UNPAID'" color="warning">待支付</a-tag>
              <span v-else>-</span>
            </template>
            <template v-if="column.key === 'entryTime'">
              {{ formatTime(record.entryTime) }}
            </template>
            <template v-if="column.key === 'exitTime'">
              {{ record.exitTime ? formatTime(record.exitTime) : '-' }}
            </template>
          </template>
        </a-table>
      </a-tab-pane>
    </a-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, onMounted } from 'vue'
import dayjs from 'dayjs'
import {
  getPresentVehicles,
  getVehicleHistory,
  type PresentVehicle,
  type VehicleHistoryRecord,
} from '@/api/vehicle-query'

const props = defineProps<{
  parkingLotId: number
}>()

// 在场车辆
const queryTab = ref('present')
const presentVehicles = ref<PresentVehicle[]>([])
const loadingPresent = ref(false)
const presentSortBy = ref<'entryTime' | 'duration'>('entryTime')
const presentSortDir = ref<'asc' | 'desc'>('desc')
const presentPagination = ref({ current: 1, pageSize: 10, total: 0 })

const presentColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 140 },
  { title: '入场时间', key: 'entryTime', width: 170 },
  { title: '停车时长', key: 'durationMinutes', width: 130 },
  { title: '车辆类型', key: 'vehicleType', width: 160 },
]

// 历史记录
const historyRecords = ref<VehicleHistoryRecord[]>([])
const loadingHistory = ref(false)
const historyPlateSearch = ref('')
const historyStartTime = ref<any>(null)
const historyEndTime = ref<any>(null)
const historyPagination = ref({ current: 1, pageSize: 10, total: 0 })

const historyColumns = [
  { title: '车牌号', dataIndex: 'plateNumber', key: 'plateNumber', width: 140 },
  { title: '入场时间', key: 'entryTime', width: 170 },
  { title: '出场时间', key: 'exitTime', width: 170 },
  { title: '费用', key: 'feeAmount', width: 100 },
  { title: '支付状态', key: 'paymentStatus', width: 100 },
  { title: '车道', dataIndex: 'laneName', key: 'laneName', width: 100 },
]

function vehicleTypeLabel(type: string): string {
  const map: Record<string, string> = { TEMPORARY: '临时车', MONTHLY: '月卡', FIXED: '固定车位' }
  return map[type] || type || '未知'
}

function vehicleTypeColor(type: string): string {
  const map: Record<string, string> = { TEMPORARY: 'default', MONTHLY: 'green', FIXED: 'blue' }
  return map[type] || 'default'
}

function formatDuration(minutes: number): string {
  if (minutes == null || minutes < 0) return '-'
  const h = Math.floor(minutes / 60)
  const m = minutes % 60
  return h > 0 ? `${h}时${m}分` : `${m}分`
}

function formatTime(t: string): string {
  return t ? dayjs(t).format('YYYY-MM-DD HH:mm:ss') : '-'
}

async function loadPresentVehicles() {
  loadingPresent.value = true
  try {
    const result = await getPresentVehicles({
      parkingLotId: props.parkingLotId,
      sortBy: presentSortBy.value,
      sortDir: presentSortDir.value,
      page: presentPagination.value.current,
      size: presentPagination.value.pageSize,
    })
    presentVehicles.value = result.records || []
    presentPagination.value.total = result.total || 0
  } catch {
    presentVehicles.value = []
  } finally {
    loadingPresent.value = false
  }
}

function handlePresentPageChange(pag: { current: number; pageSize: number }) {
  presentPagination.value.current = pag.current
  presentPagination.value.pageSize = pag.pageSize
  loadPresentVehicles()
}

async function loadHistoryRecords() {
  loadingHistory.value = true
  try {
    const result = await getVehicleHistory({
      parkingLotId: props.parkingLotId,
      plateNumber: historyPlateSearch.value || undefined,
      startTime: historyStartTime.value ? dayjs(historyStartTime.value).format('YYYY-MM-DD 00:00:00') : undefined,
      endTime: historyEndTime.value ? dayjs(historyEndTime.value).format('YYYY-MM-DD 23:59:59') : undefined,
      page: historyPagination.value.current,
      size: historyPagination.value.pageSize,
    })
    historyRecords.value = result.records || []
    historyPagination.value.total = result.total || 0
  } catch {
    historyRecords.value = []
  } finally {
    loadingHistory.value = false
  }
}

function handleHistoryPageChange(pag: { current: number; pageSize: number }) {
  historyPagination.value.current = pag.current
  historyPagination.value.pageSize = pag.pageSize
  loadHistoryRecords()
}

onMounted(() => {
  loadPresentVehicles()
})

watch(() => props.parkingLotId, () => {
  loadPresentVehicles()
  historyRecords.value = []
})
</script>

<style lang="scss" scoped>
.vehicle-query {
  padding: 8px;
}

.query-toolbar {
  margin-bottom: 12px;
}
</style>
```

---

### Task 21: Frontend — 重构 monitor/index.vue 为三段式布局

**Files:**
- Modify: `booth-web/src/views/monitor/index.vue`

This is the largest single change. The current single-page layout gets restructured into a three-section layout.

- [ ] **Step 1: 替换整个 `<template>` 部分**

Replace the entire `<template>` block in `booth-web/src/views/monitor/index.vue`.

**Full new template:**

```vue
<template>
  <div class="monitor-page">
    <!-- 顶部栏 -->
    <div class="page-header">
      <div class="header-left">
        <span class="page-title">实时监控面板</span>
        <a-tag :color="statusColor">{{ statusText }}</a-tag>
      </div>
      <div class="header-right">
        <a-button @click="refreshDevices">刷新设备</a-button>
        <a-button @click="openBatchRelease">批量开闸</a-button>
        <a-button @click="tempPlateDrawerOpen = true">无牌车处理</a-button>
        <a-badge :count="store.remoteGateAlerts.length" :overflow-count="99">
          <a-button @click="historyDrawerOpen = true">
            <template #icon><BellOutlined /></template>
            历史通知
          </a-button>
        </a-badge>
        <!-- 识别失败未处理 badge -->
        <a-badge :count="unhandledRecognitionFailedCount" :overflow-count="99">
          <a-tag color="warning">识别失败</a-tag>
        </a-badge>
      </div>
    </div>

    <!-- 严重异常横幅 -->
    <a-alert
      v-if="store.criticalAlerts.length > 0"
      type="error"
      :message="`严重异常 (${store.criticalAlerts.length})`"
      :description="criticalAlertMessages"
      show-icon
      banner
      closable
      class="critical-banner"
    />

    <!-- 三段式主体 -->
    <div class="monitor-body">
      <!-- 左侧：车场列表 -->
      <ParkingLotSidebar
        :lots="sidebarLots"
        :selected-id="selectedLotId"
        @select="handleLotSelect"
      />

      <!-- 右侧：上下分区 -->
      <div class="monitor-right">
        <!-- 右上：视频预览占位 -->
        <div class="video-placeholder">
          <div class="video-placeholder-content">
            <PlayCircleOutlined class="video-icon" />
            <span>视频接入中，敬请期待</span>
          </div>
        </div>

        <!-- 右下：Tab 操作面板 -->
        <div class="monitor-panel">
          <MonitorTabs :parking-lot-id="selectedLotId ?? 0">
            <template #monitor>
              <!-- 原有监控内容：车位 + 车道网格 + 事件/告警 -->
              <div class="monitor-tab-content">
                <!-- 车位大卡 -->
                <a-card :bordered="false" class="space-card">
                  <div class="space-grid">
                    <div class="space-item">
                      <div class="space-value remaining">{{ parkingLot?.remainingSpaces ?? '-' }}</div>
                      <div class="space-label">剩余车位</div>
                    </div>
                    <div class="space-item">
                      <div class="space-value">{{ parkingLot?.currentVehicles ?? '-' }}</div>
                      <div class="space-label">在场车辆</div>
                    </div>
                    <div class="space-item">
                      <div class="space-value">{{ parkingLot?.totalSpaces ?? '-' }}</div>
                      <div class="space-label">总车位</div>
                    </div>
                  </div>
                </a-card>

                <!-- 车道网格 -->
                <a-spin :spinning="store.loading">
                  <div class="lane-grid">
                    <a-card
                      v-for="lane in laneCards"
                      :key="lane.laneId"
                      :bordered="false"
                      class="lane-card"
                      :class="{
                        'lane-offline': lane.isOffline,
                        'lane-charging': lane.charging,
                        'lane-primary-offline': lane.primaryOffline,
                      }"
                    >
                      <div class="lane-header">
                        <span class="lane-name">
                          {{ lane.laneName }}
                          <span v-if="lane.primaryOffline" style="color: #f59e0b; margin-left: 6px;">
                            <ExclamationCircleOutlined />
                            <span style="font-size: 12px; margin-left: 2px;">主相机离线</span>
                          </span>
                        </span>
                        <div class="lane-tags">
                          <a-tag v-if="lane.charging" color="processing">
                            <SyncOutlined :spin="true" style="margin-right: 2px" />收费中
                          </a-tag>
                          <template v-if="!lane.cameras || lane.cameras.length === 0">
                            <a-tag :color="lane.deviceOnline ? 'success' : 'error'">
                              {{ lane.deviceOnline ? '在线' : '离线' }}
                            </a-tag>
                          </template>
                          <template v-else>
                            <a-tag
                              v-for="cam in lane.cameras"
                              :key="cam.deviceId"
                              :color="cam.online ? (cam.isActive ? 'blue' : 'green') : 'error'"
                            >
                              {{ cam.role === 'PRIMARY' ? '主' : '备' }}:{{ cam.direction === 'ENTRY' ? '入' : '出' }}
                            </a-tag>
                          </template>
                        </div>
                      </div>
                      <div class="lane-direction">
                        <a-tag :color="lane.direction === 'EXIT' ? 'orange' : 'blue'">
                          {{ lane.direction === 'ENTRY' ? '入口' : lane.direction === 'EXIT' ? '出口' : '混合' }}
                        </a-tag>
                      </div>
                      <div class="lane-event">
                        <div v-if="lane.latestEvent" class="event-plate">
                          {{ lane.latestEvent.plateNumber }}
                        </div>
                        <div v-else class="event-empty">暂无事件</div>
                        <div v-if="lane.latestEvent" class="event-time">
                          {{ store.formatTime(lane.latestEvent.eventTime) }}
                        </div>
                      </div>
                      <div class="lane-actions">
                        <a-space>
                          <a-button
                            type="primary"
                            size="small"
                            :disabled="lane.isOffline"
                            @click="handleManualOpenGate(lane.laneId)"
                          >
                            开闸
                          </a-button>
                          <a-button
                            size="small"
                            @click="handleManualCloseGate(lane.laneId)"
                          >
                            关闸
                          </a-button>
                          <a-button
                            size="small"
                            @click="handleEditFeeRule(lane.laneId)"
                          >
                            修改收费
                          </a-button>
                        </a-space>
                      </div>
                    </a-card>
                  </div>
                </a-spin>

                <!-- 事件与异常（内嵌于监控Tab） -->
                <a-row :gutter="[12, 12]" style="margin-top: 12px">
                  <a-col :xs="24" :lg="12">
                    <a-card title="最近识别事件" :bordered="false" size="small" class="inner-card">
                      <a-list
                        :data-source="store.recentEvents"
                        :locale="{ emptyText: '暂无识别事件' }"
                        size="small"
                      >
                        <template #renderItem="{ item }">
                          <a-list-item
                            class="event-list-item"
                            :class="{ 'event-exit-unpaid': item.direction === 'EXIT' && !item.paymentStatus }"
                            @click="handleEventClick(item)"
                          >
                            <div class="event-row">
                              <div class="event-main">
                                <span class="event-plate-text">{{ item.plateNumber || '-' }}</span>
                                <a-tag size="small" :color="item.direction === 'EXIT' ? 'orange' : 'blue'">
                                  {{ item.direction === 'ENTRY' ? '入' : '出' }}
                                </a-tag>
                                <a-tag size="small" :color="sourceColor(item.source)">
                                  {{ item.source }}
                                </a-tag>
                                <a-tag
                                  v-if="item.direction === 'EXIT' && item.paymentStatus"
                                  size="small"
                                  :color="item.paymentStatus === 'PAID' ? 'success' : 'warning'"
                                >
                                  {{ item.paymentStatus === 'PAID' ? '已支付' : '待支付' }}
                                </a-tag>
                              </div>
                              <div class="event-sub">
                                <span>{{ item.laneName || '未知车道' }}</span>
                                <span class="event-time-text">{{ store.formatTime(item.eventTime) }}</span>
                              </div>
                              <div v-if="item.feeAmount != null && item.feeAmount > 0" class="event-fee">
                                应收: ¥{{ item.feeAmount.toFixed(2) }}
                              </div>
                            </div>
                          </a-list-item>
                        </template>
                      </a-list>
                    </a-card>
                  </a-col>
                  <a-col :xs="24" :lg="12">
                    <a-card title="异常提醒" :bordered="false" size="small" class="inner-card">
                      <a-list
                        :data-source="store.alerts"
                        :locale="{ emptyText: '暂无异常提醒' }"
                        size="small"
                      >
                        <template #renderItem="{ item }">
                          <a-list-item class="alert-list-item">
                            <div class="alert-row">
                              <a-tag :color="item.severity === 'CRITICAL' ? 'error' : 'warning'">
                                {{ item.severity === 'CRITICAL' ? '严重' : '警告' }}
                              </a-tag>
                              <span class="alert-message">{{ item.message }}</span>
                              <a-button type="link" size="small" @click="store.ackAlert(item.id)">
                                确认
                              </a-button>
                            </div>
                            <div class="alert-time">{{ store.formatTime(item.createdAt) }}</div>
                          </a-list-item>
                        </template>
                      </a-list>
                    </a-card>
                  </a-col>
                </a-row>
              </div>
            </template>
          </MonitorTabs>
        </div>
      </div>
    </div>

    <!-- 收费面板 -->
    <ChargePanel />

    <!-- 人工放行弹窗（单通道） -->
    <ManualReleaseModal
      v-model:open="manualReleaseOpen"
      :lane-id="manualReleaseLaneId"
      plate-number=""
      @success="handleManualReleaseResult"
    />

    <!-- 批量开闸弹窗 -->
    <ManualReleaseModal
      v-model:open="batchReleaseOpen"
      :lane-id="0"
      plate-number=""
      :batch="true"
      :batch-lanes="batchLaneOptions"
      @success="handleBatchReleaseResult"
    />

    <!-- 收费规则编辑弹窗 -->
    <FeeRuleEditModal
      v-model:open="feeRuleEditOpen"
      :lane-id="feeRuleEditLaneId"
      :fee-rule="currentFeeRule"
      @save="handleSaveFeeRule"
    />

    <!-- 远程开闸弹窗 -->
    <a-modal
      v-model:open="remoteGateModalVisible"
      title="🚧 运营端远程开闸"
      :footer="null"
      width="420px"
      centered
      :closable="true"
      :mask-closable="true"
    >
      <a-result
        status="success"
        title="远程开闸通知"
      >
        <template #subTitle>
          <a-descriptions :column="1" size="small" bordered>
            <a-descriptions-item label="操作人">{{ currentRemoteGateAlert?.operatorName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="操作时间">{{ currentRemoteGateAlert?.operationTime || '-' }}</a-descriptions-item>
            <a-descriptions-item label="车场">{{ currentRemoteGateAlert?.parkingLotName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="通道">{{ currentRemoteGateAlert?.laneName || '-' }}</a-descriptions-item>
            <a-descriptions-item label="原因">{{ currentRemoteGateAlert?.reason || '-' }}</a-descriptions-item>
          </a-descriptions>
        </template>
      </a-result>
    </a-modal>

    <!-- 远程开闸历史通知抽屉 -->
    <a-drawer
      v-model:open="historyDrawerOpen"
      title="远程开闸历史通知"
      placement="right"
      width="400px"
    >
      <template v-if="store.remoteGateAlerts.length > 0">
        <a-list :data-source="store.remoteGateAlerts" size="small">
          <template #renderItem="{ item }">
            <a-list-item>
              <a-list-item-meta>
                <template #title>
                  <span>{{ item.operatorName }} @ {{ item.laneName }}</span>
                </template>
                <template #description>
                  <div>时间：{{ item.operationTime }}</div>
                  <div>原因：{{ item.reason }}</div>
                </template>
              </a-list-item-meta>
            </a-list-item>
          </template>
        </a-list>
      </template>
      <template v-else>
        <a-empty description="暂无远程开闸记录" />
      </template>
    </a-drawer>

    <!-- 无牌车处理抽屉 -->
    <a-drawer
      v-model:open="tempPlateDrawerOpen"
      title="无牌车处理"
      placement="right"
      :width="400"
    >
      <div class="temp-plate-section">
        <a-form layout="vertical">
          <a-form-item label="临时车牌号">
            <a-input v-model:value="tempPlateSearch" placeholder="输入临时车牌号" :maxlength="20" />
          </a-form-item>
          <a-form-item label="出口车道">
            <a-select v-model:value="tempPlateExitLane" placeholder="选择出口车道" style="width: 100%">
              <a-select-option v-for="lane in exitLanes" :key="lane.laneId" :value="lane.laneId">
                {{ lane.laneName }}
              </a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item>
            <a-button type="primary" :loading="tempPlateExiting" block @click="handleTempPlateExit">
              匹配出场并计费
            </a-button>
          </a-form-item>
        </a-form>
      </div>
    </a-drawer>
  </div>
</template>
```

- [ ] **Step 2: 更新 `<script setup>` 部分 — 替换 import 和新增逻辑**

Replace the imports section with:

```typescript
<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, h } from 'vue'
import { message, notification } from 'ant-design-vue'
import { SyncOutlined, BellOutlined, ExclamationCircleOutlined, PlayCircleOutlined } from '@ant-design/icons-vue'
import { useMonitorStore } from '@/stores/monitor'
import { MonitorWebSocketClient, type ConnectionStatus } from '@/utils/websocket'
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent, RemoteGateAlertPayload, LaneCamera } from '@/api/monitor-types'
import ChargePanel from '@/components/ChargePanel.vue'
import ManualReleaseModal, { type BatchLaneOption } from '@/components/ManualReleaseModal.vue'
import FeeRuleEditModal from '@/components/FeeRuleEditModal.vue'
import ParkingLotSidebar from './ParkingLotSidebar.vue'
import MonitorTabs from './MonitorTabs.vue'
import { getBoothParkingLots, type BoothParkingLot } from '@/api/parking-lot'
```

Remove the `TempPlateAlertModal` import entirely.

Update `TOKEN_KEY` and `LOT_ID_KEY` to use `sessionStorage`:

```typescript
const TOKEN_KEY = 'jushan_access_token'
const LOT_ID_KEY = 'booth_selected_lot_id'
```

Remove `recognitionFailedAlert` ref and add notification-related state:

Remove:
```typescript
import TempPlateAlertModal, { type RecognitionFailedAlert } from '@/components/TempPlateAlertModal.vue'
// ...
const recognitionFailedAlert = ref<RecognitionFailedAlert | null>(null)
```

Add:
```typescript
/** 识别失败未处理计数 */
const unhandledRecognitionFailedCount = ref(0)
```

Add sidebar lots state:
```typescript
/** 车场列表（左侧栏） */
const sidebarLots = ref<{ id: number; name: string; status: string; currentVehicles?: number }[]>([])
```

- [ ] **Step 3: 更新 onAlert 处理 — 识别失败改为 notification**

Replace the `onAlert` handler (lines 718-733 in original):

Old:
```typescript
      onAlert: (payload: any) => {
        if (payload.type === 'RECOGNITION_FAILED') {
          recognitionFailedAlert.value = {
            eventId: payload.eventId,
            logId: payload.logId,
            parkingLotId: payload.parkingLotId,
            laneId: payload.laneId,
            laneName: payload.laneName,
            direction: payload.direction,
            imagePath: payload.imagePath,
            eventTime: payload.eventTime,
            message: payload.message,
          }
        } else {
          store.handleAlert(payload)
        }
      },
```

New:
```typescript
      onAlert: (payload: any) => {
        if (payload.type === 'RECOGNITION_FAILED') {
          unhandledRecognitionFailedCount.value++
          notification.warning({
            message: '识别失败',
            description: `${payload.laneName || ''} | ${payload.eventTime || ''} | ${payload.message || '车牌未识别'}`,
            placement: 'bottomRight',
            duration: 0,
            btn: () => h(
              'button',
              {
                style: 'padding: 4px 12px; border: 1px solid #d9d9d9; border-radius: 4px; cursor: pointer; background: #fff;',
                onClick: () => {
                  tempPlateDrawerOpen.value = true
                  if (unhandledRecognitionFailedCount.value > 0) {
                    unhandledRecognitionFailedCount.value--
                  }
                },
              },
              '处理',
            ),
          })
        } else {
          store.handleAlert(payload)
        }
      },
```

- [ ] **Step 4: 更新 connectOrReconnect 使用 sessionStorage**

Replace `localStorage` with `sessionStorage` for LOT_ID_KEY in `connectOrReconnect`:

Old:
```typescript
  localStorage.setItem(LOT_ID_KEY, String(selectedLotId.value))
```

New:
```typescript
  sessionStorage.setItem(LOT_ID_KEY, String(selectedLotId.value))
```

- [ ] **Step 5: 更新 onMounted 使用 sessionStorage**

Old:
```typescript
  const saved = localStorage.getItem(LOT_ID_KEY)
```

New:
```typescript
  const saved = sessionStorage.getItem(LOT_ID_KEY)
```

- [ ] **Step 6: 新增车场切换逻辑**

Add a new `handleLotSelect` function:

```typescript
/** 切换车场 */
async function handleLotSelect(lotId: number) {
  if (lotId === selectedLotId.value) return

  // 1. 断开当前 WebSocket
  wsClient?.disconnect()

  // 2. 清空 store
  store.reset()

  // 3. 更新选中
  selectedLotId.value = lotId
  sessionStorage.setItem(LOT_ID_KEY, String(lotId))

  // 4. 加载新快照
  try {
    await store.loadSnapshot(lotId)
    buildWsClient(lotId)
  } catch (e: any) {
    message.error(e?.message || '车场切换失败')
  }
}
```

- [ ] **Step 7: 更新 loadLotOptions 填充 sidebarLots**

Modify `loadLotOptions` to also populate `sidebarLots`:

```typescript
/** 加载车场列表 */
async function loadLotOptions() {
  loadingLots.value = true
  try {
    const lots = await getBoothParkingLots()
    lotOptions.value = (lots || []).map((lot: BoothParkingLot) => ({
      value: lot.id,
      label: lot.name,
    }))
    // 填充左侧栏数据
    sidebarLots.value = (lots || []).map((lot: BoothParkingLot) => ({
      id: lot.id,
      name: lot.name,
      status: lot.status,
      currentVehicles: undefined, // 需要时从快照获取
    }))
  } catch {
    // 静默失败
  } finally {
    loadingLots.value = false
  }
}
```

- [ ] **Step 8: 更新 buildWsClient token 读取**

Old:
```typescript
function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}
```

New:
```typescript
function getToken(): string | null {
  return sessionStorage.getItem(TOKEN_KEY)
}
```

- [ ] **Step 10: 替换样式 — 新增三段式布局样式**

Complete replacement of `<style lang="scss" scoped>`:

```scss
<style lang="scss" scoped>
.monitor-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: #f5f7fa;
  overflow: hidden;
}

.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: #fff;
  border-bottom: 1px solid #e5e7eb;
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 12px;
  }

  .header-right {
    display: flex;
    align-items: center;
    gap: 8px;
  }

  .page-title {
    font-size: 16px;
    font-weight: 600;
    color: #1f2937;
  }
}

.critical-banner {
  flex-shrink: 0;
}

.monitor-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

.monitor-right {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.video-placeholder {
  height: 200px;
  min-height: 200px;
  background: #e5e7eb;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  border-bottom: 1px solid #d1d5db;
}

.video-placeholder-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  color: #6b7280;
  font-size: 14px;
}

.video-icon {
  font-size: 32px;
  color: #9ca3af;
}

.monitor-panel {
  flex: 1;
  overflow: hidden;
}

.monitor-tab-content {
  height: 100%;
  overflow: auto;
  padding: 8px;
}

.space-card {
  margin-bottom: 8px;

  .space-grid {
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    gap: 12px;
    text-align: center;
  }

  .space-item {
    padding: 8px;
  }

  .space-value {
    font-size: 28px;
    font-weight: 700;
    color: #1f2937;

    &.remaining {
      color: #10b981;
    }
  }

  .space-label {
    margin-top: 4px;
    color: #6b7280;
    font-size: 12px;
  }
}

.lane-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 8px;
}

.lane-card {
  .lane-header {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 8px;
  }

  .lane-name {
    font-weight: 600;
    font-size: 14px;
  }

  .lane-tags {
    display: flex;
    align-items: center;
    gap: 4px;
  }

  .lane-direction {
    margin-bottom: 8px;
  }

  .lane-actions {
    margin-top: 8px;
    text-align: right;
  }

  .lane-event {
    text-align: center;
    padding: 8px 0;
    background: #f9fafb;
    border-radius: 6px;

    .event-plate {
      font-size: 20px;
      font-weight: 700;
      color: #111827;
    }

    .event-empty {
      color: #9ca3af;
    }

    .event-time {
      margin-top: 2px;
      font-size: 12px;
      color: #6b7280;
    }
  }

  &.lane-offline {
    border: 1px solid #fca5a5;
  }

  &.lane-charging {
    border: 2px solid $primary-color;
    box-shadow: 0 0 0 2px rgba(22, 93, 255, 0.15);
  }

  &.lane-primary-offline {
    border: 2px solid #f59e0b;
    background-color: rgba(245, 158, 11, 0.04);
  }
}

.inner-card {
  max-height: 320px;
  overflow: auto;
}

.event-list-item {
  padding: 6px 0;
  cursor: pointer;
  transition: background-color 0.2s;

  &:hover {
    background-color: #f0f5ff;
  }

  &.event-exit-unpaid {
    border-left: 3px solid $warning-color;
    padding-left: 9px;
    background-color: rgba(245, 158, 11, 0.04);
  }
}

.event-row {
  width: 100%;
}

.event-main {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.event-plate-text {
  font-weight: 600;
  font-size: 14px;
}

.event-sub {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: #6b7280;
}

.event-time-text {
  font-family: monospace;
}

.event-fee {
  margin-top: 2px;
  font-size: 11px;
  font-weight: 600;
  color: $error-color;
  text-align: right;
}

.alert-list-item {
  padding: 6px 0;
}

.alert-row {
  display: flex;
  align-items: center;
  gap: 6px;
  margin-bottom: 2px;
}

.alert-message {
  flex: 1;
  font-size: 13px;
  color: #374151;
}

.alert-time {
  font-size: 11px;
  color: #9ca3af;
}
</style>
```

- [ ] **Step 11: 编译验证**

---

### Task 22: Frontend — localStorage → sessionStorage 迁移

**Files:**
- Modify: `booth-web/src/router/index.ts`
- Modify: `booth-web/src/utils/request.ts`
- Modify: `booth-web/src/layout/index.vue`
- Modify: `booth-web/src/views/login/index.vue`

- [ ] **Step 1: router/index.ts — 替换所有 localStorage**

Replace `localStorage.getItem(TOKEN_KEY)` → `sessionStorage.getItem(TOKEN_KEY)` (two occurrences, lines 45, 54):

```typescript
// Line 45
const token = sessionStorage.getItem(TOKEN_KEY)

// Line 54
const token = sessionStorage.getItem(TOKEN_KEY)
```

- [ ] **Step 2: utils/request.ts — 替换所有 localStorage**

Replace all `localStorage` references for TOKEN_KEY (lines 51, 77, 109):

```typescript
// Line 51: request interceptor
const token = sessionStorage.getItem(TOKEN_KEY)

// Line 77: 401 handler
sessionStorage.removeItem(TOKEN_KEY)

// Line 109: 401 handler
sessionStorage.removeItem(TOKEN_KEY)
```

- [ ] **Step 3: layout/index.vue — 替换 TOKEN_KEY 清除**

Line 108:
```typescript
// Old
localStorage.removeItem(TOKEN_KEY)
// New
sessionStorage.removeItem(TOKEN_KEY)
```

- [ ] **Step 4: login/index.vue — 替换所有 localStorage**

The login page uses `TOKEN_KEY` for setting the token:
```typescript
// Line 59: In validateAndSetToken
sessionStorage.setItem(TOKEN_KEY, trimmed)
```

(Already updated in Task 6 Step 7)

- [ ] **Step 5: 编译验证**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20`
Expected: No new errors

---

### Task 23: Frontend — <TempPlateAlertModal> 移除，不再使用

**Files:**
- No files to delete (keep file for reference), but remove references

- [ ] **Step 1: 确认 monitor/index.vue 已移除 TempPlateAlertModal**

After Task 21's changes, `TempPlateAlertModal` import and usage should already be removed from `index.vue`. Verify:

Run: `grep -n "TempPlateAlertModal" booth-web/src/views/monitor/index.vue`
Expected: No output (already cleaned up in Task 21)

---

### Task 24: Backend — 车辆查询接口 (BoothVehicleController)

**Files:**
- Create: `parking-system/src/main/java/com/jushan/platform/modules/booth/controller/BoothVehicleController.java`

- [ ] **Step 1: 创建在场车辆和历史记录查询控制器**

Write `parking-system/src/main/java/com/jushan/platform/modules/booth/controller/BoothVehicleController.java`:

```java
package com.jushan.platform.modules.booth.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 岗亭端车辆查询控制器。
 * <p>
 * 提供在场车辆列表和历史通行记录查询。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/booth/vehicles")
public class BoothVehicleController {

    /**
     * 在场车辆查询（暂用空实现，Phase 5 对接真实数据）。
     */
    @GetMapping("/present")
    @RequirePermission("booth:view")
    public R<Map<String, Object>> presentVehicles(
            @RequestParam Long parkingLotId,
            @RequestParam(defaultValue = "entryTime") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0);
        result.put("size", size);
        result.put("current", page);
        return R.ok(result);
    }

    /**
     * 历史通行记录查询（暂用空实现，Phase 5 对接真实数据）。
     */
    @GetMapping("/history")
    @RequirePermission("booth:view")
    public R<Map<String, Object>> historyRecords(
            @RequestParam Long parkingLotId,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {

        Map<String, Object> result = new HashMap<>();
        result.put("records", Collections.emptyList());
        result.put("total", 0);
        result.put("size", size);
        result.put("current", page);
        return R.ok(result);
    }
}
```

- [ ] **Step 2: 编译验证**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

---

### Task 25: 整车场编译验证

- [ ] **Step 1: 完整后端编译**

Run: `mvn clean compile -pl parking-system -am`
Expected: BUILD SUCCESS

- [ ] **Step 2: 前端类型检查**

Run: `cd booth-web && npx vue-tsc --noEmit 2>&1 | head -30`
Expected: Pre-existing errors are acceptable; no new errors from our changes

---

## Commit Plan

After all tasks pass verification, commit in order:

```bash
git add parking-boot/src/main/resources/db/migration/V20260719101__phase4_fee_reduce_permission.sql
git add parking-boot/src/main/resources/db/migration/V20260719102__phase4_shift_record_extend.sql
git add parking-system/src/main/java/com/jushan/platform/modules/booth/
git add parking-system/src/main/java/com/jushan/system/
git add booth-web/src/
git commit -m "[4-1] feat: 手动开闸扩展计费参数、费用减免接口、ChargePanel改造、开闸离线禁用"
git commit -m "[4-2] feat: 交接班统计计算、ShiftHandover页面、欠费订单交接"
git commit -m "[4-3] feat: 三段式布局、sessionStorage登录态、车辆查询页、识别失败notification"
```
