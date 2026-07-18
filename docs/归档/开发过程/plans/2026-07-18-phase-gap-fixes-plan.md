# 开发阶段遗留项补充 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 实现三项开发包遗留补充——岗亭端车牌校正（BOOTH-005 任务包 4-1）、运营端订单详情重算关联展示（任务包 2-2）、Webhook 签名规范文档（任务包 0-2）。

**Architecture:** 三项修改完全独立，可并行实施。Fix 1 新增数据库字段、后端校正接口/Service、WebSocket 负载扩展及前端校正弹窗组件；Fix 2 扩展现有 VO 字段及 `convertToVO` 转换逻辑，前端详情弹窗新增"重算关联"区域；Fix 3 创建纯文档，无代码变更。

**Tech Stack:** Java 21 + Spring Boot 3.x + MyBatis-Plus + MySQL 8 + Flyway + Vue 3 + Ant Design Vue + TypeScript

---

## 文件结构总览

### Fix 1：车牌校正
| 文件 | 操作 | 说明 |
|------|------|------|
| `parking-boot/src/main/resources/db/migration/V20260902003__correct_plate_fields.sql` | 新增 | 向 `recognition_event_log` 添加 4 字段 |
| `parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java` | 修改 | 新增 4 字段及 getter/setter |
| `parking-system/src/main/java/com/jushan/system/service/RecognitionCorrectionService.java` | 新增 | 校正业务逻辑（校验 + 更新） |
| `parking-system/src/main/java/com/jushan/system/controller/BoothRecognitionController.java` | 新增 | POST /api/booth/recognition/{logId}/correct |
| `parking-system/src/main/java/com/jushan/system/ws/BoothWebSocketPublisher.java` | 修改 | sendRecognitionEvent 负载新增 correctedPlate/correctionType |
| `booth-web/src/components/PlateCorrectionModal.vue` | 新增 | 校正弹窗组件 |
| `booth-web/src/views/monitor/index.vue` | 修改 | 事件行点击增加校正入口 + 校正标记展示 |
| `booth-web/src/api/monitor-types.ts` | 修改 | RecognitionEvent / RecognitionEventPayload 新增字段 |
| `booth-web/src/api/monitor.ts` | 修改 | 新增 correctPlate API 函数 |

### Fix 2：订单重算关联展示
| 文件 | 操作 | 说明 |
|------|------|------|
| `parking-system/src/main/java/com/jushan/system/vo/OrderAdminVO.java` | 修改 | 新增 recalcSourceOrderId / recalcSourceOrderNo |
| `parking-system/src/main/java/com/jushan/system/controller/OrderAdminController.java` | 修改 | convertToVO 填充重算关联字段 + 批量优化 |
| `admin-web/src/api/order.ts` | 修改 | OrderAdminVO 接口新增字段 |
| `admin-web/src/views/order/index.vue` | 修改 | 详情弹窗新增"重算关联"区域 + openDetailById 方法 |

### Fix 3：Webhook 签名文档
| 文件 | 操作 | 说明 |
|------|------|------|
| `docs/设备接入/webhook签名规范.md` | 新增 | 签名规范文档 |

---

## Fix 1：岗亭端车牌校正

### Task 1.1: 创建 Flyway 迁移（数据库）

**文件:** 新增 `parking-boot/src/main/resources/db/migration/V20260902003__correct_plate_fields.sql`

- [ ] **Step 1: 编写迁移 SQL**

```sql
-- V20260902003__correct_plate_fields.sql
-- 向 recognition_event_log 添加车牌校正字段

ALTER TABLE recognition_event_log
    ADD COLUMN corrected_plate VARCHAR(20) NULL COMMENT '校正后车牌号' AFTER camera_source,
    ADD COLUMN correction_type VARCHAR(30) NULL COMMENT '校正类型：MANUAL_CORRECTION' AFTER corrected_plate,
    ADD COLUMN corrected_at DATETIME NULL COMMENT '校正操作时间' AFTER correction_type,
    ADD COLUMN corrector_id BIGINT NULL COMMENT '校正人ID（sys_user.id）' AFTER corrected_at;
```

- [ ] **Step 2: 验证迁移文件位置**

```bash
ls -la parking-boot/src/main/resources/db/migration/V20260902003__correct_plate_fields.sql
```

预期：文件存在且路径正确。

- [ ] **Step 3: 提交**

```bash
git add parking-boot/src/main/resources/db/migration/V20260902003__correct_plate_fields.sql
git commit -m "[BOOTH-005] feat: 添加 recognition_event_log 车牌校正字段迁移"
```

### Task 1.2: 扩展 RecognitionEventLog 实体

**文件:** 修改 `parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java`

- [ ] **Step 1: 在 `cameraSource` 字段声明之后、`createdAt` 字段之前新增 4 个字段声明**

在 `private String cameraSource;` 所在行（约第 92 行）之后插入：

```java
    /** 校正后车牌号（NULL 表示未校正） */
    private String correctedPlate;

    /** 校正类型：MANUAL_CORRECTION */
    private String correctionType;

    /** 校正操作时间 */
    private LocalDateTime correctedAt;

    /** 校正人 ID（sys_user.id，岗亭管理员） */
    private Long correctorId;
```

- [ ] **Step 2: 在 `getCameraSource()/setCameraSource()` 之后（约第 157 行）新增 getter/setter**

在 `public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }` 行之后插入：

```java
    public String getCorrectedPlate() { return correctedPlate; }
    public void setCorrectedPlate(String correctedPlate) { this.correctedPlate = correctedPlate; }

    public String getCorrectionType() { return correctionType; }
    public void setCorrectionType(String correctionType) { this.correctionType = correctionType; }

    public LocalDateTime getCorrectedAt() { return correctedAt; }
    public void setCorrectedAt(LocalDateTime correctedAt) { this.correctedAt = correctedAt; }

    public Long getCorrectorId() { return correctorId; }
    public void setCorrectorId(Long correctorId) { this.correctorId = correctorId; }
```

- [ ] **Step 3: 验证编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java
git commit -m "[BOOTH-005] feat: RecognitionEventLog 新增车牌校正字段"
```

### Task 1.3: 创建 RecognitionCorrectionService

**文件:** 新增 `parking-system/src/main/java/com/jushan/system/service/RecognitionCorrectionService.java`

- [ ] **Step 1: 创建 Service 类（完整内容）**

```java
package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.mapper.RecognitionEventLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 车牌校正服务（BOOTH-005 任务包 4-1）。
 * <p>
 * 岗亭管理员可对已处理的识别事件手动校正车牌号。
 * 校正记录永久保留不可变，不修改已生成的 parking_record。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Service
public class RecognitionCorrectionService {

    private static final Logger log = LoggerFactory.getLogger(RecognitionCorrectionService.class);

    /** 校正类型固定值 */
    private static final String CORRECTION_TYPE_MANUAL = "MANUAL_CORRECTION";

    /** 最小车牌号长度 */
    private static final int PLATE_MIN_LENGTH = 2;

    /** 最大车牌号长度 */
    private static final int PLATE_MAX_LENGTH = 20;

    /** 可校正的状态 */
    private static final String ALLOWED_STATUS = "PROCESSED";

    private final RecognitionEventLogMapper logMapper;
    private final ParkingLotScopeResolver scopeResolver;

    public RecognitionCorrectionService(RecognitionEventLogMapper logMapper,
                                         ParkingLotScopeResolver scopeResolver) {
        this.logMapper = logMapper;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 执行车牌校正。
     *
     * @param logId          识别事件日志 ID
     * @param correctedPlate 校正后车牌号
     * @param correctorId    校正人用户 ID
     * @return 更新后的事件日志实体（用于 WebSocket 推送）
     * @throws BusinessException 校验失败时抛出
     */
    public RecognitionEventLog correctPlate(Long logId, String correctedPlate, Long correctorId) {
        // 1. 查询事件日志
        RecognitionEventLog event = logMapper.selectById(logId);
        if (event == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "识别事件不存在");
        }

        // 2. 状态校验：仅 PROCESSED 可校正
        if (!ALLOWED_STATUS.equals(event.getStatus())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "仅已处理状态的识别事件可校正，当前状态: " + event.getStatus());
        }

        // 3. 车牌号格式校验
        String trimmed = correctedPlate.trim();
        if (trimmed.length() < PLATE_MIN_LENGTH || trimmed.length() > PLATE_MAX_LENGTH) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "校正车牌号长度需在" + PLATE_MIN_LENGTH + "-" + PLATE_MAX_LENGTH + "字符之间");
        }

        // 4. 有效性校验：校正值与原值相同视为无效
        if (trimmed.equals(event.getPlateNumber())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "校正车牌与原识别车牌相同");
        }

        // 5. 不可变约束：已校正记录拒绝再次校正
        if (event.getCorrectedPlate() != null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "该识别事件已校正，不可重复校正");
        }

        // 6. 数据范围校验
        scopeResolver.validateAccess(event.getParkingLotId());

        // 7. 更新字段
        event.setCorrectedPlate(trimmed);
        event.setCorrectionType(CORRECTION_TYPE_MANUAL);
        event.setCorrectedAt(LocalDateTime.now());
        event.setCorrectorId(correctorId);

        logMapper.updateById(event);

        log.info("车牌校正成功: logId={}, original={}, corrected={}, correctorId={}",
                logId, event.getPlateNumber(), trimmed, correctorId);

        return event;
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/service/RecognitionCorrectionService.java
git commit -m "[BOOTH-005] feat: 新增 RecognitionCorrectionService 车牌校正业务逻辑"
```

### Task 1.4: 创建 BoothRecognitionController

**文件:** 新增 `parking-system/src/main/java/com/jushan/system/controller/BoothRecognitionController.java`

- [ ] **Step 1: 创建 Controller 类（完整内容）**

```java
package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.service.RecognitionCorrectionService;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

/**
 * 岗亭端车牌校正接口（BOOTH-005 任务包 4-1）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/booth/recognition")
public class BoothRecognitionController {

    private static final Logger log = LoggerFactory.getLogger(BoothRecognitionController.class);

    private final RecognitionCorrectionService correctionService;
    private final BoothWebSocketPublisher wsPublisher;

    public BoothRecognitionController(RecognitionCorrectionService correctionService,
                                       BoothWebSocketPublisher wsPublisher) {
        this.correctionService = correctionService;
        this.wsPublisher = wsPublisher;
    }

    /**
     * 校正车牌号。
     *
     * @param logId   识别事件日志 ID
     * @param request 校正请求体
     * @return 成功响应
     */
    @PostMapping("/{logId}/correct")
    @RequirePermission("booth:monitor")
    @BusinessLog(value = "车牌校正", module = "recognition", operationType = "UPDATE",
            operationObject = "识别事件", objectIdExpression = "#logId")
    public R<Void> correctPlate(@PathVariable Long logId,
                                 @RequestBody CorrectPlateRequest request) {
        String correctedPlate = request.getCorrectedPlate();
        if (correctedPlate == null || correctedPlate.isBlank()) {
            return R.fail(com.jushan.common.CommonErrorCode.PARAM_ERROR.getCode(), "校正车牌号不能为空");
        }

        Long correctorId = TenantContext.getUserId();

        RecognitionEventLog updatedEvent = correctionService.correctPlate(logId, correctedPlate, correctorId);

        // 校正成功后通过 WebSocket 推送更新到岗亭前端
        try {
            wsPublisher.sendRecognitionEvent(updatedEvent.getParkingLotId(), updatedEvent);
        } catch (Exception e) {
            log.warn("车牌校正 WebSocket 推送失败（不影响主业务）: logId={}", logId, e);
        }

        return R.ok();
    }

    /**
     * 校正请求 DTO。
     */
    public static class CorrectPlateRequest {
        private String correctedPlate;

        public String getCorrectedPlate() { return correctedPlate; }
        public void setCorrectedPlate(String correctedPlate) { this.correctedPlate = correctedPlate; }
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/BoothRecognitionController.java
git commit -m "[BOOTH-005] feat: 新增 BoothRecognitionController 车牌校正接口"
```

### Task 1.5: 扩展 BoothWebSocketPublisher 负载

**文件:** 修改 `parking-system/src/main/java/com/jushan/system/ws/BoothWebSocketPublisher.java`

- [ ] **Step 1: 在 `sendRecognitionEvent` 方法的 payload 中新增 `correctedPlate` 和 `correctionType`**

在 `payload.put("plateImagePath", event.getPlateImagePath());` 行（第 83 行）之后插入两行：

```java
            payload.put("correctedPlate", event.getCorrectedPlate());
            payload.put("correctionType", event.getCorrectionType());
```

即在 `payload.put("plateImagePath", event.getPlateImagePath());` 后（该行末尾已有分号），新增：

```java
            payload.put("correctedPlate", event.getCorrectedPlate());
            payload.put("correctionType", event.getCorrectionType());
```

- [ ] **Step 2: 验证编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：`BUILD SUCCESS`

- [ ] **Step 3: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/ws/BoothWebSocketPublisher.java
git commit -m "[BOOTH-005] feat: WebSocket 识别事件推送新增 correctedPlate/correctionType 字段"
```

### Task 1.6: 扩展 monitor-types.ts（TypeScript 类型定义）

**文件:** 修改 `booth-web/src/api/monitor-types.ts`

- [ ] **Step 1: 在 `RecognitionEvent` 接口末尾（`feeAmount` 之后）新增字段**

在 `RecognitionEvent` 接口（第 35–56 行），`feeAmount?: number` 行（第 55 行）之后、`}` 之前插入：

```typescript
  /** 校正后车牌号（NULL 表示未校正） */
  correctedPlate?: string
  /** 校正类型：MANUAL_CORRECTION */
  correctionType?: string
```

- [ ] **Step 2: 在 `RecognitionEventPayload` 接口末尾（`plateImagePath` 之后）新增字段**

在 `RecognitionEventPayload` 接口（第 97–111 行），`plateImagePath: string` 行（第 110 行）之后、`}` 之前插入：

```typescript
  /** 校正后车牌号（NULL 表示未校正） */
  correctedPlate?: string
  /** 校正类型：MANUAL_CORRECTION */
  correctionType?: string
```

- [ ] **Step 3: 验证 TypeScript 编译**

```bash
cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20
```

预期：无类型错误。

- [ ] **Step 4: 提交**

```bash
git add booth-web/src/api/monitor-types.ts
git commit -m "[BOOTH-005] feat: RecognitionEvent / RecognitionEventPayload 新增 correctedPlate/correctionType 字段"
```

### Task 1.7: 新增 monitor.ts 中 correctPlate API 函数

**文件:** 修改 `booth-web/src/api/monitor.ts`

- [ ] **Step 1: 在文件末尾（`manualTempPlateExit` 函数之后）新增 `correctPlate` 函数**

在 `manualTempPlateExit` 导出函数之后（第 34 行后）追加：

```typescript
/** 车牌校正 */
export function correctPlate(logId: number, correctedPlate: string) {
  return request.post<void>(`/booth/recognition/${logId}/correct`, { correctedPlate })
}
```

- [ ] **Step 2: 验证 TypeScript 编译**

```bash
cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20
```

预期：无类型错误。

- [ ] **Step 3: 提交**

```bash
git add booth-web/src/api/monitor.ts
git commit -m "[BOOTH-005] feat: 新增 correctPlate API 函数"
```

### Task 1.8: 创建 PlateCorrectionModal 组件

**文件:** 新增 `booth-web/src/components/PlateCorrectionModal.vue`

- [ ] **Step 1: 创建组件（完整内容）**

```vue
<template>
  <a-modal
    v-model:open="visible"
    title="车牌校正"
    :confirm-loading="submitting"
    :ok-text="readonly ? '关闭' : '提交校正'"
    :cancel-text="readonly ? undefined : '取消'"
    :ok-button-props="readonly ? { style: { display: 'none' } } : undefined"
    :cancel-button-props="readonly ? { style: { display: 'none' } } : undefined"
    @ok="handleSubmit"
    @cancel="handleClose"
  >
    <a-descriptions :column="1" bordered size="small" style="margin-bottom: 16px">
      <a-descriptions-item label="原始识别车牌">{{ event?.plateNumber || '—' }}</a-descriptions-item>
      <a-descriptions-item label="车道">{{ event?.laneName || '—' }}</a-descriptions-item>
      <a-descriptions-item label="方向">
        <a-tag :color="event?.direction === 'ENTRY' ? 'blue' : 'orange'">
          {{ event?.direction === 'ENTRY' ? '入场' : '出场' }}
        </a-tag>
      </a-descriptions-item>
      <a-descriptions-item label="状态">
        <a-tag>{{ event?.status }}</a-tag>
      </a-descriptions-item>
    </a-descriptions>

    <template v-if="event?.correctedPlate">
      <a-alert
        type="info"
        show-icon
        message="该事件已校正"
        :description="`校正后车牌：${event.correctedPlate}`"
        style="margin-bottom: 12px"
      />
    </template>

    <template v-else>
      <a-form layout="vertical">
        <a-form-item label="校正后车牌号" required>
          <a-input
            v-model:value="correctedPlate"
            :maxlength="20"
            placeholder="请输入校正后的车牌号"
            :disabled="readonly"
          />
        </a-form-item>
      </a-form>
    </template>
  </a-modal>
</template>

<script setup lang="ts">
import { ref, watch, computed } from 'vue'
import { message } from 'ant-design-vue'
import { correctPlate } from '@/api/monitor'
import type { RecognitionEvent } from '@/api/monitor-types'

const props = defineProps<{
  open: boolean
  event: RecognitionEvent | null
}>()

const emit = defineEmits<{
  (e: 'update:open', value: boolean): void
  (e: 'corrected'): void
}>()

const visible = computed({
  get: () => props.open,
  set: (val) => emit('update:open', val),
})

const correctedPlate = ref('')
const submitting = ref(false)
const readonly = computed(() => props.event?.correctedPlate != null)

watch(
  () => props.open,
  (val) => {
    if (val) {
      correctedPlate.value = ''
    }
  }
)

async function handleSubmit() {
  if (readonly.value) {
    visible.value = false
    return
  }
  if (!correctedPlate.value.trim()) {
    message.warning('请输入校正后的车牌号')
    return
  }
  if (!props.event?.logId) {
    message.error('事件数据异常，无法校正')
    return
  }
  submitting.value = true
  try {
    await correctPlate(props.event.logId, correctedPlate.value.trim())
    message.success('车牌校正成功')
    emit('corrected')
    visible.value = false
  } catch {
    // error handled by interceptor
  } finally {
    submitting.value = false
  }
}

function handleClose() {
  visible.value = false
}
</script>
```

- [ ] **Step 2: 验证 TypeScript 编译**

```bash
cd booth-web && npx vue-tsc --noEmit 2>&1 | head -20
```

预期：无类型错误。

- [ ] **Step 3: 提交**

```bash
git add booth-web/src/components/PlateCorrectionModal.vue
git commit -m "[BOOTH-005] feat: 新增 PlateCorrectionModal 车牌校正弹窗组件"
```

### Task 1.9: 修改 monitor/index.vue（事件行交互）

**文件:** 修改 `booth-web/src/views/monitor/index.vue`

此任务需做 4 处修改：引入组件 + 模板中新增校正弹窗声明、事件列表项模板重构（ENTRY 直接点击 / EXIT 下拉菜单）、新增响应式变量和方法。

- [ ] **Step 1: 在模板中新增 PlateCorrectionModal 组件声明**

在 `</a-drawer>` 闭合标签（无牌车处理抽屉之后，约第 351 行）之后、`</div>` 页面根元素闭合前插入：

```html
    <!-- 车牌校正弹窗 -->
    <PlateCorrectionModal
      v-model:open="correctionModalOpen"
      :event="correctionTarget"
      @corrected="handleCorrectionDone"
    />
```

- [ ] **Step 2: 在 `<script setup>` 中新增 import**

在现有 import 行中追加 `PlateCorrectionModal` 的导入。在 `import ChargePanel from '@/components/ChargePanel.vue'` 行（约第 362 行）之后加入：

```typescript
import PlateCorrectionModal from '@/components/PlateCorrectionModal.vue'
```

- [ ] **Step 3: 新增响应式变量（在 `tempPlateExiting` 变量声明之后）**

在 `const tempPlateExiting = ref(false)` 行（约第 401 行）之后插入：

```typescript
// 车牌校正
const correctionModalOpen = ref(false)
const correctionTarget = ref<RecognitionEvent | null>(null)
```

需要同步追加 `RecognitionEvent` 的 import。在当前 `import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent, RemoteGateAlertPayload, LaneCamera } from '@/api/monitor-types'`（第 361 行）已包含 `RecognitionEvent`，无需额外操作。

- [ ] **Step 4: 重构事件列表项模板——移除 `<a-list-item>` 上的 `@click`，对 EXIT 事件使用 `a-dropdown` 下拉菜单**

对于识别事件列表区域（第 172–205 行），将整个 `<template #renderItem="{ item }">` 块替换为以下内容。

**旧代码**（第 172–205 行，`<template #renderItem="{ item }">` 整块）：
```html
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
```

**新代码**：
```html
                        <template #renderItem="{ item }">
                          <!-- EXIT 事件：点击弹出下拉菜单（收费处理 / 校正车牌） -->
                          <a-list-item
                            v-if="item.direction === 'EXIT'"
                            class="event-list-item"
                            :class="{ 'event-exit-unpaid': !item.paymentStatus }"
                          >
                            <a-dropdown :trigger="['click']">
                              <div class="event-row dropdown-trigger">
                                <div class="event-main">
                                  <span class="event-plate-text">{{ item.correctedPlate || item.plateNumber || '-' }}</span>
                                  <a-tag size="small" color="orange">出</a-tag>
                                  <a-tag v-if="item.correctedPlate" size="small" color="purple">已校正</a-tag>
                                  <a-tag size="small" :color="sourceColor(item.source)">{{ item.source }}</a-tag>
                                  <a-tag
                                    v-if="item.paymentStatus"
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
                              <template #overlay>
                                <a-menu @click="(e: any) => handleExitMenuClick(e, item)">
                                  <a-menu-item key="charge">收费处理</a-menu-item>
                                  <a-menu-item v-if="!item.correctedPlate" key="correct">校正车牌</a-menu-item>
                                </a-menu>
                              </template>
                            </a-dropdown>
                          </a-list-item>

                          <!-- ENTRY / MIXED 事件：点击直接打开校正弹窗 -->
                          <a-list-item
                            v-else
                            class="event-list-item"
                            @click="handleEventClick(item)"
                          >
                            <div class="event-row">
                              <div class="event-main">
                                <span class="event-plate-text">{{ item.correctedPlate || item.plateNumber || '-' }}</span>
                                <a-tag size="small" color="blue">入</a-tag>
                                <a-tag v-if="item.correctedPlate" size="small" color="purple">已校正</a-tag>
                                <a-tag size="small" :color="sourceColor(item.source)">{{ item.source }}</a-tag>
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
```

- [ ] **Step 5: 替换 `handleEventClick` 方法（简化为仅处理 ENTRY 事件）**

**旧代码**（约第 661–667 行）：
```typescript
/** 点击事件列表项：EXIT 事件打开收费面板 */
function handleEventClick(item: RecognitionEvent) {
  if (item.direction === 'EXIT') {
    store.showChargePanel(item.plateNumber, item.laneId).catch(() => {
      // 查询失败不阻塞
    })
  }
}
```

**新代码**：
```typescript
/** 点击事件列表项：ENTRY / MIXED 事件打开校正弹窗 */
function handleEventClick(item: RecognitionEvent) {
  correctionTarget.value = item
  correctionModalOpen.value = true
}
```

- [ ] **Step 6: 新增 `handleExitMenuClick` 方法（EXIT 事件下拉菜单分发）**

在 `handleEventClick` 函数之后新增：

```typescript
/** EXIT 事件下拉菜单点击处理 */
function handleExitMenuClick(e: { key: string }, item: RecognitionEvent) {
  if (e.key === 'charge') {
    store.showChargePanel(
      item.correctedPlate || item.plateNumber,
      item.laneId
    ).catch(() => {
      // 查询失败不阻塞
    })
  } else if (e.key === 'correct') {
    correctionTarget.value = item
    correctionModalOpen.value = true
  }
}
```

- [ ] **Step 7: 新增 `handleCorrectionDone` 方法**

在 `handleExitMenuClick` 函数之后新增：

```typescript
/** 校正完成后（WebSocket 自动推送更新事件到 store，此处无需额外操作） */
function handleCorrectionDone() {
  // WebSocket 推送的校正后事件会自动更新 store.recentEvents
}
```

- [ ] **Step 8: 在 `<style>` 区域新增下拉触发器样式**

在 `<style lang="scss" scoped>` 区域内，于 `.event-row` 样式后追加：

```scss
.dropdown-trigger {
  cursor: pointer;
  user-select: none;
}
```

- [ ] **Step 9: 验证前端编译**

```bash
cd booth-web && npx vite build --mode development 2>&1 | tail -5
```

预期：`✓` 编译成功，无错误。

- [ ] **Step 10: 提交**

```bash
git add booth-web/src/views/monitor/index.vue
git commit -m "[BOOTH-005] feat: 岗亭监控页新增车牌校正入口、EXIT 下拉菜单及校正标记展示"
```

---

## Fix 2：运营端订单详情重算关联展示

### Task 2.1: 扩展 OrderAdminVO

**文件:** 修改 `parking-system/src/main/java/com/jushan/system/vo/OrderAdminVO.java`

- [ ] **Step 1: 在 `updatedAt` 字段声明之后新增两个字段声明**

在 `private LocalDateTime updatedAt;` 行（约第 94 行）之后插入：

```java
    /** 重算来源订单 ID。超时关单后重算时，新订单记录关联的原订单主键 */
    private Long recalcSourceOrderId;

    /** 重算来源订单号。超时关单后重算时，新订单记录关联的原订单号 */
    private String recalcSourceOrderNo;
```

- [ ] **Step 2: 在 `getUpdatedAt()/setUpdatedAt()` 之后新增 getter/setter**

在 `public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }` 行（约第 165 行）之后插入：

```java
    public Long getRecalcSourceOrderId() { return recalcSourceOrderId; }
    public void setRecalcSourceOrderId(Long recalcSourceOrderId) { this.recalcSourceOrderId = recalcSourceOrderId; }

    public String getRecalcSourceOrderNo() { return recalcSourceOrderNo; }
    public void setRecalcSourceOrderNo(String recalcSourceOrderNo) { this.recalcSourceOrderNo = recalcSourceOrderNo; }
```

- [ ] **Step 3: 验证编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：`BUILD SUCCESS`

- [ ] **Step 4: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/vo/OrderAdminVO.java
git commit -m "[ADMIN-011] feat: OrderAdminVO 新增 recalcSourceOrderId/recalcSourceOrderNo 字段"
```

### Task 2.2: 修改 OrderAdminController.convertToVO

**文件:** 修改 `parking-system/src/main/java/com/jushan/system/controller/OrderAdminController.java`

此任务需做 3 处修改：`getDetail` 方法、`pageList` 方法、`exportOrders` 方法。

- [ ] **Step 1: 在类顶部新增 `java.util.*` 导入（如果尚未导入）**

检查当前 import 中已有 `import java.util.*;`（第 42 行），无需额外操作。

- [ ] **Step 2: 修改 `getDetail` 方法——在 convertToVO 调用前提前查询 recalc 原订单**

在 `getDetail` 方法中（约第 208–221 行），将最后的 `return R.ok(convertToVO(order));` 替换为：

**旧代码**（第 220 行）：
```java
        return R.ok(convertToVO(order));
```

**新代码**：
```java
        OrderAdminVO vo = convertToVO(order);
        // 单条查询：填充重算关联原订单号
        if (order.getRecalcSourceOrderId() != null) {
            vo.setRecalcSourceOrderId(order.getRecalcSourceOrderId());
            ParkingOrder sourceOrder = orderMapper.selectById(order.getRecalcSourceOrderId());
            if (sourceOrder != null) {
                vo.setRecalcSourceOrderNo(sourceOrder.getOrderNo());
            }
        }
        return R.ok(vo);
```

- [ ] **Step 3: 修改 `pageList` 方法——批量预查询 recalc 原订单**

在 `pageList` 方法的 `IPage<ParkingOrder> orderPage = ...` 查询后（约第 193 行），将 VO 转换段落：

**旧代码**（约第 195–202 行）：
```java
        List<OrderAdminVO> voList = orderPage.getRecords().stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());

        IPage<OrderAdminVO> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        result.setRecords(voList);

        return R.ok(result);
```

**新代码**：
```java
        // 批量预查询重算来源原订单号（避免 N+1）
        Set<Long> recalcIds = orderPage.getRecords().stream()
                .map(ParkingOrder::getRecalcSourceOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> recalcNoMap = Collections.emptyMap();
        if (!recalcIds.isEmpty()) {
            recalcNoMap = orderMapper.selectBatchIds(recalcIds).stream()
                    .collect(Collectors.toMap(ParkingOrder::getId, ParkingOrder::getOrderNo));
        }
        final Map<Long, String> finalRecalcNoMap = recalcNoMap;

        List<OrderAdminVO> voList = orderPage.getRecords().stream()
                .map(o -> {
                    OrderAdminVO vo = convertToVO(o);
                    if (o.getRecalcSourceOrderId() != null) {
                        vo.setRecalcSourceOrderId(o.getRecalcSourceOrderId());
                        String sourceOrderNo = finalRecalcNoMap.get(o.getRecalcSourceOrderId());
                        if (sourceOrderNo != null) {
                            vo.setRecalcSourceOrderNo(sourceOrderNo);
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());

        IPage<OrderAdminVO> result = new Page<>(orderPage.getCurrent(), orderPage.getSize(), orderPage.getTotal());
        result.setRecords(voList);

        return R.ok(result);
```

- [ ] **Step 4: 修改 `exportOrders` 方法——同样的批量预查询模式**

在 `exportOrders` 方法中，`List<ParkingOrder> orders = ...` 查询后（约第 380 行），将 VO 转换段落：

**旧代码**（约第 381–383 行）：
```java
        List<OrderAdminVO> voList = orders.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
```

**新代码**：
```java
        // 批量预查询重算来源原订单号（避免 N+1）
        Set<Long> recalcIds = orders.stream()
                .map(ParkingOrder::getRecalcSourceOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, String> recalcNoMap = Collections.emptyMap();
        if (!recalcIds.isEmpty()) {
            recalcNoMap = orderMapper.selectBatchIds(recalcIds).stream()
                    .collect(Collectors.toMap(ParkingOrder::getId, ParkingOrder::getOrderNo));
        }
        final Map<Long, String> finalRecalcNoMap = recalcNoMap;

        List<OrderAdminVO> voList = orders.stream()
                .map(o -> {
                    OrderAdminVO vo = convertToVO(o);
                    if (o.getRecalcSourceOrderId() != null) {
                        vo.setRecalcSourceOrderId(o.getRecalcSourceOrderId());
                        String sourceOrderNo = finalRecalcNoMap.get(o.getRecalcSourceOrderId());
                        if (sourceOrderNo != null) {
                            vo.setRecalcSourceOrderNo(sourceOrderNo);
                        }
                    }
                    return vo;
                })
                .collect(Collectors.toList());
```

- [ ] **Step 5: 验证编译**

```bash
mvn clean compile -pl parking-system -am
```

预期：`BUILD SUCCESS`

- [ ] **Step 6: 提交**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/OrderAdminController.java
git commit -m "[ADMIN-011] feat: convertToVO 批量填充 recalcSourceOrderNo 避免 N+1"
```

### Task 2.3: 扩展 admin-web/src/api/order.ts

**文件:** 修改 `admin-web/src/api/order.ts`

- [ ] **Step 1: 在 `OrderAdminVO` 接口末尾新增字段**

在 `OrderAdminVO` 接口（第 4–31 行），`updatedAt?: string` 行（第 30 行）之后、`}` 之前插入：

```typescript
  /** 重算来源订单 ID */
  recalcSourceOrderId?: number
  /** 重算来源订单号 */
  recalcSourceOrderNo?: string
```

- [ ] **Step 2: 验证 TypeScript 编译**

```bash
cd admin-web && npx vue-tsc --noEmit 2>&1 | head -20
```

预期：无类型错误。

- [ ] **Step 3: 提交**

```bash
git add admin-web/src/api/order.ts
git commit -m "[ADMIN-011] feat: OrderAdminVO 接口新增 recalcSourceOrderId/recalcSourceOrderNo"
```

### Task 2.4: 修改 admin-web/src/views/order/index.vue（详情弹窗）

**文件:** 修改 `admin-web/src/views/order/index.vue`

此任务需做 3 处修改：模板新增"重算关联"区域、脚本新增 `openDetailById` 方法、新增 `getOrderDetail` 导入。

- [ ] **Step 1: 在详情弹窗 `<a-descriptions>` 中新增"重算关联"行**

在详情弹窗的 `<a-descriptions>` 区块中（第 93–115 行），于 `<a-descriptions-item label="更新时间">...` 行（第 114 行）之后、`</a-descriptions>` 闭合标签（第 115 行）之前插入：

```html
        <a-descriptions-item label="重算关联" :span="2">
          <template v-if="detailRecord?.recalcSourceOrderNo && detailRecord?.recalcSourceOrderId">
            <a @click="openDetailById(detailRecord.recalcSourceOrderId)">
              查看原订单：{{ detailRecord.recalcSourceOrderNo }}
            </a>
          </template>
          <template v-else>—</template>
        </a-descriptions-item>
```

- [ ] **Step 2: 在 import 中新增 `getOrderDetail`**

在现有的 `import { getOrderPage, closeOrder, refundOrder, getOrderStatusLogs, exportOrders, ...` 行（约第 182–194 行）中，将 `getOrderPage` 所在 import 行后追加 `getOrderDetail`：

**旧代码**（约第 183–194 行，提取关键部分）：
```typescript
import {
  getOrderPage,
  closeOrder,
  refundOrder,
  getOrderStatusLogs,
  exportOrders,
  ORDER_STATUS_MAP,
  ORDER_TYPE_MAP,
  PAY_CHANNEL_MAP,
  type OrderAdminVO,
  type OrderStatusLogVO,
} from '@/api/order'
```

**新代码**：
```typescript
import {
  getOrderPage,
  getOrderDetail,
  closeOrder,
  refundOrder,
  getOrderStatusLogs,
  exportOrders,
  ORDER_STATUS_MAP,
  ORDER_TYPE_MAP,
  PAY_CHANNEL_MAP,
  type OrderAdminVO,
  type OrderStatusLogVO,
} from '@/api/order'
```

- [ ] **Step 3: 新增 `openDetailById` 方法**

在 `openDetail` 函数之后（约第 338 行之后）新增：

```typescript
/** 通过订单 ID 打开详情弹窗（用于重算关联跳转） */
async function openDetailById(id: number) {
  try {
    const detail = await getOrderDetail(id)
    detailRecord.value = detail
    detailOpen.value = true
    statusLogs.value = []
    logsLoading.value = true
    try {
      statusLogs.value = await getOrderStatusLogs(id)
    } catch {
      // 错误由拦截器处理
    } finally {
      logsLoading.value = false
    }
  } catch {
    // 错误由拦截器处理
  }
}
```

- [ ] **Step 4: 验证前端编译**

```bash
cd admin-web && npx vite build --mode development 2>&1 | tail -5
```

预期：`✓` 编译成功，无错误。

- [ ] **Step 5: 提交**

```bash
git add admin-web/src/views/order/index.vue
git commit -m "[ADMIN-011] feat: 订单详情弹窗新增重算关联展示及跳转"
```

---

## Fix 3：Webhook 签名规范文档

### Task 3.1: 创建 Webhook 签名规范文档

**文件:** 新增 `docs/设备接入/webhook签名规范.md`

- [ ] **Step 1: 创建目录**

```bash
mkdir -p docs/设备接入
```

预期：目录创建成功。

- [ ] **Step 2: 编写文档（完整内容）**

```markdown
# Webhook 签名规范

> 版本：v1.0 | 更新日期：2026-07-18 | 适用范围：Jushan 停车 SaaS 平台设备适配器对接

---

## 1. 概述

平台对外暴露的 Webhook 接入点（`POST /api/v1/device-webhook/*`）**强制要求 HMAC-SHA256 签名校验**。所有适配器向平台上报识别事件、设备状态时，必须携带合法的签名请求头。校验失败统一返回 `HTTP 401 Unauthorized`，不暴露具体失败原因。

签名校验实现类：`com.jushan.platform.modules.device.webhook.WebhookVerificationFilter`

---

## 2. 签名算法

```
sign = Base64(HMAC-SHA256(secret, timestamp + nonce + rawBody))
```

**参数说明：**

| 参数 | 说明 |
|------|------|
| `secret` | 停车场级的 Webhook Secret，由平台管理员在 `webhook_secret` 表中配置后分发给适配器部署方 |
| `timestamp` | Unix 毫秒时间戳字符串，对应请求头 `X-Timestamp` |
| `nonce` | 随机一次性字符串（建议 UUID v4），对应请求头 `X-Nonce` |
| `rawBody` | HTTP 请求原始 Body 字节（UTF-8），**不做任何预处理** |

> **注意：** 拼接顺序为 `timestamp + nonce + rawBody`（字符串拼接，无分隔符），再计算 HMAC。

---

## 3. 请求头规范

每个 Webhook 请求必须携带以下三个请求头：

| 请求头 | 类型 | 说明 | 示例 |
|--------|------|------|------|
| `X-Sign` | `string` | Base64 编码的签名字符串 | `dGhpcyBpcyBhbiBleGFtcGxl...` |
| `X-Timestamp` | `string` | Unix 毫秒时间戳（请求发起时） | `1752816000000` |
| `X-Nonce` | `string` | 随机串（建议 UUID v4），用于防重放 | `a1b2c3d4-e5f6-7890-abcd-ef1234567890` |

### 请求示例

```http
POST /api/v1/device-webhook/recognition HTTP/1.1
Host: your-platform.example.com
Content-Type: application/json; charset=UTF-8
X-Sign: J9nG7pQmLxR4tY8vK2wB5dF...
X-Timestamp: 1752816000000
X-Nonce: a1b2c3d4-e5f6-7890-abcd-ef1234567890

{"deviceSn":"DEV-2024-001","eventId":"evt-abcdef01","plateNumber":"京A12345","direction":"ENTRY","...":"..."}
```

---

## 4. 校验规则

平台在接收请求后依次执行以下校验（任一失败返回 401）：

### 4.1 请求头存在性

- `X-Sign`、`X-Timestamp`、`X-Nonce` 三者**任一缺失**或为空 → 401

### 4.2 时间戳偏差

- `X-Timestamp` 必须为合法的 Unix 毫秒时间戳
- 与服务器当前时间的偏差不得超过 **±5 分钟**（`TIMESTAMP_TOLERANCE = Duration.ofMinutes(5)`）
- 适配器端必须保证系统时钟与 NTP 同步

### 4.3 Nonce 防重放

- 平台使用 Redis `SETNX` 记录已使用的 nonce，TTL 为 **10 分钟**（`NONCE_TTL = Duration.ofMinutes(10)`）
- 同一 nonce 在 10 分钟内重复出现 → 401
- **适配器端每次请求必须生成新的 nonce**（推荐使用 UUID v4）

### 4.4 deviceSn 提取

- 请求体 JSON 中必须包含 `deviceSn` 字段
- 平台通过 `deviceSn` 查找对应设备记录（`device` 表），进而获取所属停车场的 secret
- `deviceSn` 不存在于请求体或设备记录未找到 → 401

### 4.5 签名比对

- 平台使用停车场级 secret 按上述算法计算签名，与 `X-Sign` 头值**严格匹配**（区分大小写）
- 不匹配 → 401

---

## 5. Secret 管理

- **粒度**：每个停车场（`parking_lot`）独立配置一个 `webhook_secret`
- **配置方式**：平台管理员在运营端系统参数中为停车场配置 secret，适配器部署方从管理员处获取
- **存储**：secret 存储于数据库 `webhook_secret` 表（`secret` 字段），平台在校验时按 `parking_lot_id` 查询
- **安全建议**：
  - Secret 长度建议 ≥ 32 字符，包含大小写字母、数字、特殊字符
  - 通过安全通道（如加密 IM、离线交付）分发给适配器部署方
  - 定期轮换 secret（轮换后通知适配器同步更新）

---

## 6. 错误响应

签名校验失败时，平台统一返回以下响应（不区分具体失败原因，防探测）：

```json
HTTP/1.1 401 Unauthorized
Content-Type: application/json; charset=UTF-8

{
  "code": 401,
  "message": "Unauthorized"
}
```

---

## 7. 签名开关

签名校验功能可通过配置项控制：

```yaml
device-access:
  webhook:
    signature-enabled: true   # false 时跳过签名校验（仅限开发/测试环境）
```

生产环境必须设为 `true`。

---

## 8. 示例代码（伪代码）

```java
// 适配器端签名构造伪代码（Java 风格）
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;
import java.util.UUID;

public class WebhookSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * 构造签名请求头。
     *
     * @param secret   停车场 Webhook Secret
     * @param rawBody  请求体 JSON 字符串
     * @return 包含 X-Sign、X-Timestamp、X-Nonce 的 Map
     */
    public static Map<String, String> sign(String secret, String rawBody) {
        long timestamp = System.currentTimeMillis();
        String nonce = UUID.randomUUID().toString();

        // 拼接 payload：timestamp + nonce + rawBody（无分隔符，注意顺序）
        String payload = timestamp + nonce + rawBody;

        // HMAC-SHA256
        String sign = computeHmacSha256(secret, payload);

        return Map.of(
            "X-Sign", sign,
            "X-Timestamp", String.valueOf(timestamp),
            "X-Nonce", nonce
        );
    }

    private static String computeHmacSha256(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("HMAC 计算失败", e);
        }
    }
}

// 使用示例：
// Map<String, String> headers = WebhookSigner.sign(
//     "your-parking-lot-secret-here",
//     "{\"deviceSn\":\"DEV-2024-001\",\"plateNumber\":\"京A12345\",\"direction\":\"ENTRY\"}"
// );
// httpClient.post(url, headers, rawBody);
```

---

## 9. 参考

- 平台源码实现：`parking-system/.../webhook/WebhookVerificationFilter.java`
- 安全管理配置：`parking-system/.../webhook/WebhookSecurityConfig.java`
- Redis Nonce 存储格式：`webhook:nonce:{nonce}`，值为 `"1"`，TTL 10 分钟
```

- [ ] **Step 3: 验证文件存在**

```bash
ls -la docs/设备接入/webhook签名规范.md
```

预期：文件存在且非空。

- [ ] **Step 4: 提交**

```bash
git add docs/设备接入/webhook签名规范.md
git commit -m "[docs] docs: 新增 Webhook 签名规范对接文档（基于 WebhookVerificationFilter 实现）"
```

---

## 集成验证

### Fix 1 验证

- [ ] **验证 1.1: 启动应用并校验 Flyway 迁移执行**

```bash
mvn clean package -pl parking-boot -am -DskipTests && \
java -jar parking-boot/target/parking-boot-*.jar 2>&1 | grep -i "V20260902003"
```

预期：日志显示 `V20260902003__correct_plate_fields.sql` 迁移执行成功。

- [ ] **验证 1.2: 手动调用校正接口（通过 curl）**

```bash
# 假设已有 PROCESSED 状态事件 id=1，token 从登录获取
curl -X POST http://localhost:8080/api/booth/recognition/1/correct \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"correctedPlate":"京A99999"}'
```

预期：返回 `{"code":200,"message":"success","data":null,"timestamp":"..."}`。

- [ ] **验证 1.3: 查询数据库验证字段写入**

```sql
SELECT id, plate_number, corrected_plate, correction_type, corrected_at, corrector_id
FROM recognition_event_log WHERE id = 1;
```

预期：`corrected_plate` = "京A99999"，`correction_type` = "MANUAL_CORRECTION"，`corrected_at` 和 `corrector_id` 非空。

- [ ] **验证 1.4: 重复校正拒绝**

重复执行验证 1.2 的 curl 命令。

预期：返回 `{"code":1000,"message":"该识别事件已校正，不可重复校正",...}`。

- [ ] **验证 1.5: 前端功能走查**

1. 打开岗亭监控页，选择停车场连接 WebSocket
2. 点击一条入场方向的识别事件行 → 弹出校正弹窗
3. 输入新牌号 → 提交 → 事件列表刷新显示新牌号和"已校正"标签
4. 再次点击该事件 → 弹窗为只读模式，展示原始/校正车牌

### Fix 2 验证

- [ ] **验证 2.1: 后端返回 recalcSourceOrderNo 字段**

```bash
# 假设有重算关联的订单 id=100（需 recalcSourceOrderId 非空）
curl -X GET http://localhost:8080/api/v1/admin/orders/100 \
  -H "Authorization: Bearer <token>"
```

预期：返回 JSON 包含 `"recalcSourceOrderId": ...` 和 `"recalcSourceOrderNo": "..."`。

- [ ] **验证 2.2: 无重算关联的订单不返回额外字段**

```bash
# 假设无重算关联的订单 id=1
curl -X GET http://localhost:8080/api/v1/admin/orders/1 \
  -H "Authorization: Bearer <token>"
```

预期：返回 JSON 中 `recalcSourceOrderId` 为 `null`，`recalcSourceOrderNo` 为 `null`。

- [ ] **验证 2.3: 前端功能走查**

1. 打开运营端订单管理页
2. 点击一条有重算关联的订单 → 详情弹窗显示"重算关联"行，含可点击的原订单号链接
3. 点击链接 → 弹窗刷新显示原订单详情
4. 点击无重算关联的订单 → 详情弹窗显示"重算关联: —"

### Fix 3 验证

- [ ] **验证 3.1: 文档内容审查**

由团队成员比对文档中的签名算法描述与 `WebhookVerificationFilter.java` 源码（第 215–226 行 `computeHmacSha256` 方法、第 54–58 行常量定义），确认完全一致：
- 时间容差 ±5 分钟：`TIMESTAMP_TOLERANCE = Duration.ofMinutes(5)`
- Nonce TTL 10 分钟：`NONCE_TTL = Duration.ofMinutes(10)`
- 签名算法 `HMAC-SHA256(secret, timestamp + nonce + rawBody)`，Base64 编码
- 错误响应 401 `{"code":401,"message":"Unauthorized"}`
```

