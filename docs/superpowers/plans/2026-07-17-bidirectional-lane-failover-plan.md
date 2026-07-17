# 双向通道/主备相机业务逻辑 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers-subagent-driven-development (recommended) or superpowers-executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the full-chain integration of bidirectional lane direction derivation and primary/backup camera failover source tracking from Consumer layer to UI, with zero behavior change for unidirectional lanes.

**Architecture:** Three database tables gain a `camera_source` VARCHAR column (NULL for unidirectional lanes). The RecognitionEventConsumer derives direction from camera `recognitionDirection` for bidirectional lanes and stamps `cameraSource` via `CameraFailoverService.getActiveSource()`. EntryService/ExitService persist it from the payload. Admin lane management gets filtered camera dropdown selectors for bidirectional lanes. Booth monitor LaneCard renders multi-camera tags with primary-offline visual highlighting.

**Tech Stack:** Java 21, Spring Boot 3.x, MyBatis-Plus, MySQL 8, Flyway, Vue 3, Ant Design Vue, TypeScript, Pinia

---

### Task 1: Flyway migration — Add camera_source column to three tables

**Files:**
- Create: `parking-boot/src/main/resources/db/migration/V20260901001__add_camera_source.sql`

- [x] **Step 1: Create Flyway migration file**

```sql
-- =============================================================================
-- 任务包 3-5：识别事件/停车记录/出场记录新增相机来源标记
-- =============================================================================

ALTER TABLE recognition_event_log
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE parking_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '入场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';

ALTER TABLE exit_record
    ADD COLUMN camera_source VARCHAR(20) DEFAULT NULL COMMENT '出场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置';
```

- [x] **Step 2: Verify migration filename ordering**

Run:
```bash
ls parking-boot/src/main/resources/db/migration/ | sort | tail -5
```
Expected: The new file `V20260901001__add_camera_source.sql` appears after `V20260828001__create_vehicle_list.sql` (the current latest migration).

- [x] **Step 3: Commit**

```bash
git add parking-boot/src/main/resources/db/migration/V20260901001__add_camera_source.sql
git commit -m "[任务包3-5] feat: 三表新增 camera_source 字段 Flyway 迁移"
```

---

### Task 2: Add cameraSource field to three entity classes

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java`
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ParkingRecord.java`
- Modify: `parking-system/src/main/java/com/jushan/system/entity/ExitRecord.java`

- [ ] **Step 1: Add cameraSource to RecognitionEventLog**

In `RecognitionEventLog.java`, after the `vendorEventId` field (line 84), add:

```java
    /** 相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置 */
    private String cameraSource;
```

Then after the `getVendorEventId/setVendorEventId` methods (after line 143), add:

```java
    public String getCameraSource() { return cameraSource; }
    public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
```

- [ ] **Step 2: Add cameraSource to ParkingRecord**

In `ParkingRecord.java`, after the `deletedAt` field (line 84), add:

```java
    /** 入场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置 */
    private String cameraSource;
```

Then after the `getDeletedAt/setDeletedAt` methods (after line 137), add:

```java
    public String getCameraSource() { return cameraSource; }
    public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
```

- [ ] **Step 3: Add cameraSource to ExitRecord**

In `ExitRecord.java`, after the `deletedAt` field (line 149), add:

```java
    /** 出场相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=单相机或无主备配置 */
    private String cameraSource;
```

Then after the `getDeletedAt/setDeletedAt` methods (after line 152), add:

```java
    public String getCameraSource() { return cameraSource; }
    public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
```

- [ ] **Step 4: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/entity/RecognitionEventLog.java parking-system/src/main/java/com/jushan/system/entity/ParkingRecord.java parking-system/src/main/java/com/jushan/system/entity/ExitRecord.java
git commit -m "[任务包3-5] feat: RecognitionEventLog/ParkingRecord/ExitRecord 新增 cameraSource 字段"
```

---

### Task 3: Add cameraSource to RecognitionEventPayload

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/event/RecognitionEventPayload.java`

- [ ] **Step 1: Add cameraSource field and fluent method**

In `RecognitionEventPayload.java`, after the `logId` field (line 91), add:

```java
    /** 相机来源：PRIMARY=主相机, BACKUP=备相机, NULL=不适用 */
    private String cameraSource;
```

After the `logId` fluent method (after line 163), add:

```java
    public RecognitionEventPayload cameraSource(String cameraSource) {
        this.cameraSource = cameraSource;
        return this;
    }
```

After the `getLogId/setLogId` methods (after line 216), add:

```java
    public String getCameraSource() { return cameraSource; }
    public void setCameraSource(String cameraSource) { this.cameraSource = cameraSource; }
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/event/RecognitionEventPayload.java
git commit -m "[任务包3-5] feat: RecognitionEventPayload 新增 cameraSource 字段和 Fluent 方法"
```

---

### Task 4: Transform RecognitionEventConsumer — Inject failover, derive direction, stamp cameraSource

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/event/RecognitionEventConsumer.java`

- [ ] **Step 1: Add imports**

In the imports section, after `import com.jushan.system.service.EntryService;` (line 17), add:

```java
import com.jushan.system.service.CameraFailoverService;
```

- [ ] **Step 2: Add CameraFailoverService as a final field and constructor parameter**

Replace the existing field declarations block (lines 66-72):
```java
    private final MessageIdempotency messageIdempotency;
    private final DeviceMapper deviceMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final EntryService entryService;
    private final ExitService exitService;
```

With:
```java
    private final MessageIdempotency messageIdempotency;
    private final DeviceMapper deviceMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final ParkingLaneMapper laneMapper;
    private final RecognitionEventLogMapper eventLogMapper;
    private final EntryService entryService;
    private final ExitService exitService;
    private final CameraFailoverService cameraFailoverService;
```

Replace the existing constructor (lines 74-88):
```java
    public RecognitionEventConsumer(MessageIdempotency messageIdempotency,
                                     DeviceMapper deviceMapper,
                                     ParkingLotMapper parkingLotMapper,
                                     ParkingLaneMapper laneMapper,
                                     RecognitionEventLogMapper eventLogMapper,
                                     EntryService entryService,
                                     ExitService exitService) {
        this.messageIdempotency = messageIdempotency;
        this.deviceMapper = deviceMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.eventLogMapper = eventLogMapper;
        this.entryService = entryService;
        this.exitService = exitService;
    }
```

With:
```java
    public RecognitionEventConsumer(MessageIdempotency messageIdempotency,
                                     DeviceMapper deviceMapper,
                                     ParkingLotMapper parkingLotMapper,
                                     ParkingLaneMapper laneMapper,
                                     RecognitionEventLogMapper eventLogMapper,
                                     EntryService entryService,
                                     ExitService exitService,
                                     CameraFailoverService cameraFailoverService) {
        this.messageIdempotency = messageIdempotency;
        this.deviceMapper = deviceMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.eventLogMapper = eventLogMapper;
        this.entryService = entryService;
        this.exitService = exitService;
        this.cameraFailoverService = cameraFailoverService;
    }
```

- [ ] **Step 3: Add bidirectional lane direction derivation + cameraSource stamping in validateAndStandardize**

**IMPORTANT: Variable scope awareness.** The `lane` variable is defined inside `if (device.getLaneId() != null)` (line 253 of the original), so the direction derivation must go INSIDE that block before it closes at line 272. The `device` variable is defined at line 209 and is accessible throughout. The cameraSource stamping (which only needs `device`) can go after the block closes.

**Step 3a: Insert direction derivation inside the `if (device.getLaneId() != null)` block.**

In the original code, lines 262-271 contain the existing direction validation. Line 272 is `}` closing the `if (device.getLaneId() != null)` block. Insert the new code IMMEDIATELY BEFORE line 272. The exact insertion point is between line 271 (`}` closing the direction mismatch check) and line 272 (`}` closing the outer if block).

Replace lines 262-272:
```java
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
```

With:
```java
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

            // 3.5b 双向车道：相机有识别方向时，覆盖事件方向
            //      确保入场/出场路由基于相机实际安装位置，而非事件发布端填写值
            if (lane.getType() != null && lane.getType() == 3
                    && device.getRecognitionDirection() != null) {
                String cameraDirection = device.getRecognitionDirection() == 1 ? "ENTRY" : "EXIT";
                if (!cameraDirection.equals(payload.getDirection())) {
                    log.info("双向车道方向由相机推导覆盖: lane={} device={} cameraDirection={} originalDirection={}",
                            lane.getName(), device.getId(), cameraDirection, payload.getDirection());
                    payload.setDirection(cameraDirection);
                }
            }
        }
```

**Step 3b: Insert cameraSource stamping after the `if (device.getLaneId() != null)` block closes.**

In the original code, lines 272-279 are:
```java
        }

        // 3.6 基本格式校验（非阻塞性，仅记录）
        if (!PlateStandardizer.isValidFormat(standardized)) {
            log.info("车牌格式可能异常（非阻塞）: plate={} eventId={}", standardized, payload.getEventId());
        }

        result.success = true;
        return result;
```

Replace with:
```java
        }

        // 3.6 标记相机来源（仅当设备绑定了车道且有识别方向）
        if (device.getLaneId() != null && device.getRecognitionDirection() != null) {
            String source = cameraFailoverService.getActiveSource(
                    device.getLaneId(), device.getRecognitionDirection());
            payload.setCameraSource(source);
        }

        // 3.7 基本格式校验（非阻塞性，仅记录）
        if (!PlateStandardizer.isValidFormat(standardized)) {
            log.info("车牌格式可能异常（非阻塞）: plate={} eventId={}", standardized, payload.getEventId());
        }

        result.success = true;
        return result;
```

- [ ] **Step 5: Update updateEventLog to persist cameraSource**

In `updateEventLog()`, after line 295 (`update.setStatus("PROCESSED");` / `update.setStatus("FAILED");` logic), before the LambdaUpdateWrapper, add cameraSource setting.

Replace the entire `updateEventLog` method body (lines 291-307):

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
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            update.setCameraSource(payload.getCameraSource());
        }

        int rows = eventLogMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));

        if (rows == 0) {
            log.warn("事件日志不存在，无法更新状态（可能 DB 事务未提交）: eventId={}", payload.getEventId());
        }
    }
```

- [ ] **Step 6: Update updateEventLogFailed to persist cameraSource**

Replace the entire `updateEventLogFailed` method body (lines 312-331):

```java
    private void updateEventLogFailed(RecognitionEventPayload payload, String reason) {
        RecognitionEventLog update = new RecognitionEventLog();
        update.setStatus("FAILED");
        update.setFailureReason(reason.length() > 500 ? reason.substring(0, 500) : reason);

        if (payload.getPlateNumber() != null) {
            String standardized = PlateStandardizer.normalize(payload.getPlateNumber());
            if (standardized != null) {
                update.setStandardizedPlate(standardized);
            }
        }
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            update.setCameraSource(payload.getCameraSource());
        }

        int rows = eventLogMapper.update(update,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RecognitionEventLog>()
                        .eq(RecognitionEventLog::getEventId, payload.getEventId()));

        if (rows == 0) {
            log.warn("事件日志不存在，无法更新失败状态: eventId={}", payload.getEventId());
        }
    }
```

- [ ] **Step 7: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/event/RecognitionEventConsumer.java
git commit -m "[任务包3-5] feat: Consumer 注入 CameraFailoverService，双向车道方向推导 + cameraSource 标记"
```

---

### Task 5: EntryService — persist cameraSource on ParkingRecord

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/EntryService.java`

- [ ] **Step 1: Add cameraSource persistence in createParkingRecord**

In `createParkingRecord()`, after line 321 (`record.setEntryImagePath(payload.getImagePath());`) and before line 323 (`try {`), insert:

```java
        // 记录相机来源（主备切换场景标记）
        if (payload.getCameraSource() != null) {
            record.setCameraSource(payload.getCameraSource());
        }
```

- [ ] **Step 2: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/service/EntryService.java
git commit -m "[任务包3-5] feat: EntryService.createParkingRecord 持久化 cameraSource"
```

---

### Task 6: ExitService — persist cameraSource on ExitRecord (all three creation paths)

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/ExitService.java`

- [ ] **Step 1: Add cameraSource in createExitRecord**

In `createExitRecord()`, after line 538 (`exitRecord.setReleaseDecision(decisionCode);`) and before line 539 (`exitRecord.setOrderId(...)`), insert:

```java
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
```

- [ ] **Step 2: Add cameraSource in createNoRecordExit**

In `createNoRecordExit()`, after line 557 (`exitRecord.setReleaseDecision(ExitRecord.DECISION_NO_RECORD);`) and before line 558 (`exitRecord.setLaneId(...)`), insert:

```java
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
```

- [ ] **Step 3: Add cameraSource in createExitRecordForArrears**

In `createExitRecordForArrears()`, after line 704 (`exitRecord.setReleaseDecision(decisionCode);`) and before line 705 (`exitRecord.setOrderId(0L);`), insert:

```java
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
```

- [ ] **Step 4: Also add cameraSource in handleArrearsReentry for the inline exitRecord**

In `handleArrearsReentry()`, the inline ExitRecord creation inside the `MUST_PAY` block. After line 640 (`exitRecord.setReleaseDecision(ExitRecord.DECISION_ARREARS_MUST_PAY);`) and before line 641 (`exitRecord.setOrderId(mergedOrder.getId());`), insert:

```java
        // 记录相机来源
        if (payload.getCameraSource() != null) {
            exitRecord.setCameraSource(payload.getCameraSource());
        }
```

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/service/ExitService.java
git commit -m "[任务包3-5] feat: ExitService 四个出场记录创建点持久化 cameraSource"
```

---

### Task 7: DeviceController — Add available-for-lane endpoint for admin lane form

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/controller/DeviceController.java`

- [ ] **Step 1: Add the available-for-lane endpoint**

In `DeviceController.java`, after the `getStatusSnapshots` method (after line 421, before the closing `}` of the class), add:

```java
    // ==================== 车道相机配置辅助 ====================

    /**
     * 查询指定停车场下可绑定到车道的相机列表（含识别方向）。
     * <p>
     * 返回所有已启用且设备类型为 CAMERA 的设备，
     * 每项包含 deviceId、deviceName、recognitionDirection。
     * <p>
     * 权限：device:read
     *
     * @param parkingLotId 停车场 ID（必填）
     */
    @GetMapping("/available-for-lane")
    @RequirePermission("device:read")
    public R<List<Map<String, Object>>> availableForLane(@RequestParam Long parkingLotId) {
        List<Map<String, Object>> cameras = deviceService.listAvailableForLane(parkingLotId);
        return R.ok(cameras);
    }
```

Add required imports at the top of the file:
```java
import java.util.HashMap;
```
is not needed since we use `Map<String, Object>` and the method internally builds them in `DeviceService`.

- [ ] **Step 2: Add listAvailableForLane method to DeviceService**

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/service/DeviceService.java`

In `DeviceService.java`, add the new method. Find the `DeviceMapper` injection and existing `list` method patterns for reference.

Add this method after the existing list/query methods:

```java
    /**
     * 查询指定停车场下可绑定到车道的相机列表。
     * <p>
     * 仅返回已启用 + CAMERA 类型的设备，包含识别方向信息。
     *
     * @param parkingLotId 停车场 ID
     * @return 相机列表，每项含 deviceId、deviceName、recognitionDirection
     */
    public List<Map<String, Object>> listAvailableForLane(Long parkingLotId) {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getParkingLotId, parkingLotId)
                        .eq(Device::getDeviceType, "CAMERA")
                        .eq(Device::getStatus, STATUS_ENABLED));

        return devices.stream().map(d -> {
            Map<String, Object> item = new java.util.LinkedHashMap<>();
            item.put("deviceId", d.getId());
            item.put("deviceName", d.getName());
            item.put("recognitionDirection", d.getRecognitionDirection());
            return item;
        }).collect(Collectors.toList());
    }
```

If `Collectors` is not already imported, add:
```java
import java.util.stream.Collectors;
```

If `LambdaQueryWrapper` is not already imported, add (but it should already be present in DeviceService based on the codebase pattern).

- [ ] **Step 3: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/controller/DeviceController.java parking-system/src/main/java/com/jushan/system/service/DeviceService.java
git commit -m "[任务包3-5] feat: 新增 /api/admin/devices/available-for-lane 端点，返回可绑定相机列表含识别方向"
```

---

### Task 8: BoothMonitorService — Extend Lane snapshot with cameras array

**Files:**
- Modify: `parking-system/src/main/java/com/jushan/system/vo/BoothLaneVO.java`
- Modify: `parking-system/src/main/java/com/jushan/system/service/BoothMonitorService.java`

- [ ] **Step 1: Create BoothLaneCameraVO**

Create new file `parking-system/src/main/java/com/jushan/system/vo/BoothLaneCameraVO.java`:

```java
package com.jushan.system.vo;

/**
 * 岗亭监控车道相机视图（任务包 3-5）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public class BoothLaneCameraVO {

    private Long deviceId;
    private String name;
    /** PRIMARY / BACKUP */
    private String role;
    /** ENTRY / EXIT */
    private String direction;
    private Boolean online;
    private Boolean isActive;

    // ==================== getter / setter ====================

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
```

- [ ] **Step 2: Add cameras field to BoothLaneVO**

In `BoothLaneVO.java`, after the `deviceName` field (line 18), add:

```java
    /** 车道绑定的相机列表（任务包 3-5：支持多相机/主备场景） */
    private List<BoothLaneCameraVO> cameras;
```

Add import at top:
```java
import java.util.List;
```

After the `getDeviceName/setDeviceName` methods (after line 44), add:

```java
    public List<BoothLaneCameraVO> getCameras() { return cameras; }
    public void setCameras(List<BoothLaneCameraVO> cameras) { this.cameras = cameras; }
```

- [ ] **Step 3: Inject CameraFailoverService into BoothMonitorService**

In `BoothMonitorService.java`, add import:
```java
import com.jushan.system.service.CameraFailoverService;
import com.jushan.system.vo.BoothLaneCameraVO;
import java.util.ArrayList;
```

Add field:
```java
    private final CameraFailoverService cameraFailoverService;
```

Update constructor to include `CameraFailoverService`:

Replace the constructor signature (lines 55-69):
```java
    public BoothMonitorService(ParkingLotMapper parkingLotMapper,
                                ParkingLaneMapper laneMapper,
                                DeviceMapper deviceMapper,
                                RecognitionEventLogMapper eventLogMapper,
                                DeviceService deviceService,
                                MonitorAlertService alertService,
                                ParkingLotScopeResolver scopeResolver) {
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.deviceMapper = deviceMapper;
        this.eventLogMapper = eventLogMapper;
        this.deviceService = deviceService;
        this.alertService = alertService;
        this.scopeResolver = scopeResolver;
    }
```

With:
```java
    public BoothMonitorService(ParkingLotMapper parkingLotMapper,
                                ParkingLaneMapper laneMapper,
                                DeviceMapper deviceMapper,
                                RecognitionEventLogMapper eventLogMapper,
                                DeviceService deviceService,
                                MonitorAlertService alertService,
                                ParkingLotScopeResolver scopeResolver,
                                CameraFailoverService cameraFailoverService) {
        this.parkingLotMapper = parkingLotMapper;
        this.laneMapper = laneMapper;
        this.deviceMapper = deviceMapper;
        this.eventLogMapper = eventLogMapper;
        this.deviceService = deviceService;
        this.alertService = alertService;
        this.scopeResolver = scopeResolver;
        this.cameraFailoverService = cameraFailoverService;
    }
```

- [ ] **Step 4: Rewrite loadLanes to populate cameras array**

Replace the entire `loadLanes` method (lines 173-209):

```java
    private List<BoothLaneVO> loadLanes(Long parkingLotId) {
        List<ParkingLane> lanes = laneMapper.selectByLotIdIgnoreTenant(parkingLotId);

        if (lanes.isEmpty()) {
            return Collections.emptyList();
        }

        // 加载该停车场所有已启用相机
        List<Device> allCameras = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getParkingLotId, parkingLotId)
                        .eq(Device::getDeviceType, "CAMERA")
                        .eq(Device::getStatus, DeviceService.STATUS_ENABLED));

        // laneId -> cameras
        Map<Long, List<Device>> camerasByLane = allCameras.stream()
                .filter(d -> d.getLaneId() != null)
                .collect(Collectors.groupingBy(Device::getLaneId));

        // 加载设备状态快照
        List<Long> allDeviceIds = allCameras.stream().map(Device::getId).collect(Collectors.toList());
        Map<Long, DeviceStatusVO> statusMap;
        if (allDeviceIds.isEmpty()) {
            statusMap = Collections.emptyMap();
        } else {
            statusMap = deviceService.getLatestSnapshots(allDeviceIds).stream()
                    .collect(Collectors.toMap(DeviceStatusVO::getDeviceId, s -> s, (a, b) -> a));
        }

        return lanes.stream()
                .map(lane -> {
                    List<Device> laneCameras = camerasByLane.getOrDefault(lane.getId(), Collections.emptyList());

                    BoothLaneVO vo = new BoothLaneVO();
                    vo.setId(lane.getId());
                    vo.setParkingLotId(lane.getLotId());
                    vo.setName(lane.getName());
                    vo.setCode(lane.getLaneNo());
                    vo.setDirection(ParkingLaneService.intToDirectionStr(lane.getType()));
                    vo.setStatus(ParkingLaneService.intToStatusStr(lane.getStatus()));

                    // 构建 cameras 数组
                    List<BoothLaneCameraVO> cameraVOs = new ArrayList<>();
                    for (Device camera : laneCameras) {
                        BoothLaneCameraVO cvo = new BoothLaneCameraVO();
                        cvo.setDeviceId(camera.getId());
                        cvo.setName(camera.getName());
                        cvo.setRole(camera.getCameraRole() != null
                                ? (camera.getCameraRole() == DeviceService.CAMERA_ROLE_PRIMARY ? "PRIMARY" : "BACKUP")
                                : null);
                        cvo.setDirection(camera.getRecognitionDirection() != null
                                ? (camera.getRecognitionDirection() == 1 ? "ENTRY" : "EXIT")
                                : null);

                        DeviceStatusVO status = statusMap.get(camera.getId());
                        cvo.setOnline(status != null && Boolean.TRUE.equals(status.getOnline()) && !Boolean.TRUE.equals(status.getStale()));

                        // 计算 isActive：对比 CameraFailoverService 的活跃来源
                        boolean isActive = false;
                        if (camera.getRecognitionDirection() != null && camera.getCameraRole() != null) {
                            String activeSource = cameraFailoverService.getActiveSource(
                                    lane.getId(), camera.getRecognitionDirection());
                            String roleStr = camera.getCameraRole() == DeviceService.CAMERA_ROLE_PRIMARY ? "PRIMARY" : "BACKUP";
                            isActive = roleStr.equals(activeSource);
                        } else if (laneCameras.size() == 1) {
                            // 单相机车道：该相机即为活跃
                            isActive = true;
                        }
                        cvo.setIsActive(isActive);

                        cameraVOs.add(cvo);
                    }

                    // 向后兼容：单相机场景仍填充 deviceId/deviceName
                    if (!cameraVOs.isEmpty()) {
                        // 优先选主相机，否则取第一个
                        BoothLaneCameraVO primary = cameraVOs.stream()
                                .filter(c -> "PRIMARY".equals(c.getRole()))
                                .findFirst()
                                .orElse(cameraVOs.get(0));
                        vo.setDeviceId(primary.getDeviceId());
                        vo.setDeviceName(primary.getName());
                    }

                    vo.setCameras(cameraVOs);
                    return vo;
                })
                .collect(Collectors.toList());
    }
```

- [ ] **Step 5: Commit**

```bash
git add parking-system/src/main/java/com/jushan/system/vo/BoothLaneCameraVO.java parking-system/src/main/java/com/jushan/system/vo/BoothLaneVO.java parking-system/src/main/java/com/jushan/system/service/BoothMonitorService.java
git commit -m "[任务包3-5] feat: 岗亭监控快照 Lane 扩展 cameras 数组（含主备角色/在线/isActive）"
```

---

### Task 9: Admin Web — Camera dropdown selectors and guidance hints for bidirectional lanes

**Files:**
- Modify: `admin-web/src/views/parking/ParkingLaneManage.vue`

- [ ] **Step 1: Add camera dropdown state and API call**

In the `<script setup lang="ts">` section of `ParkingLaneManage.vue`, add imports and reactive state after existing imports. After line 157 (`import { getParkingZonesByLotId, type ParkingZoneVO } from '@/api/parking-zone'`):

```typescript
import { getAvailableCameras, type AvailableCamera } from '@/api/parking-lane'
```

After line 206 (`const formZoneOptions = ref<ParkingZoneVO[]>([])`):

```typescript
const availableCameras = ref<AvailableCamera[]>([])

const hasEntryCamera = computed(() => formData.entryCameraId != null && formData.entryCameraId > 0)
const hasExitCamera = computed(() => formData.exitCameraId != null && formData.exitCameraId > 0)

const entryCameraOptions = computed(() =>
  availableCameras.value.filter(
    c => c.recognitionDirection === 1 || c.recognitionDirection == null
  )
)

const exitCameraOptions = computed(() =>
  availableCameras.value.filter(
    c => c.recognitionDirection === 2 || c.recognitionDirection == null
  )
)
```

- [ ] **Step 2: Add availableCameras loading function**

After line 264 (`handleFormLotChange` method), add:

```typescript
async function loadAvailableCameras(lotId: number) {
  try {
    const cameras = await getAvailableCameras(lotId)
    availableCameras.value = cameras
  } catch {
    availableCameras.value = []
  }
}
```

- [ ] **Step 3: Call loadAvailableCameras on lot change**

Modify `handleFormLotChange` (lines 257-264), add `loadAvailableCameras` call:

```typescript
async function handleFormLotChange() {
  formData.zoneId = undefined
  if (formData.lotId) {
    formZoneOptions.value = await loadZonesByLotId(formData.lotId)
    await loadAvailableCameras(formData.lotId)
  } else {
    formZoneOptions.value = []
    availableCameras.value = []
  }
}
```

Also modify `handleCreate` (line 287-305) to load cameras:

```typescript
function handleCreate() {
  // ... existing code ...
  if (formData.lotId) {
    handleFormLotChange()
  }
  formModalOpen.value = true
}
```

And `handleEdit` (line 307-325) to also load cameras:

```typescript
function handleEdit(record: any) {
  // ... existing code ...
  if (formData.lotId) {
    handleFormLotChange()
  }
  formModalOpen.value = true
}
```

Both already call `handleFormLotChange()`.

- [ ] **Step 4: Replace a-input-number with a-select for bidirectional lanes**

Replace the entry camera form item (lines 114-116):
```html
        <a-form-item v-if="showEntryCamera" label="入口相机ID">
          <a-input-number v-model:value="formData.entryCameraId" placeholder="入口相机ID" style="width: 100%" />
        </a-form-item>
```

With:
```html
        <a-form-item v-if="showEntryCamera" :label="formData.type === 3 ? '入口相机' : '入口相机ID'">
          <a-select
            v-if="formData.type === 3 && entryCameraOptions.length > 0"
            v-model:value="formData.entryCameraId"
            placeholder="选择入口方向相机"
            allow-clear
            style="width: 100%"
            :options="entryCameraOptions.map(c => ({ value: c.deviceId, label: c.deviceName }))"
          />
          <a-input-number
            v-else
            v-model:value="formData.entryCameraId"
            placeholder="入口相机ID"
            style="width: 100%"
          />
        </a-form-item>
```

Replace the exit camera form item (lines 117-119):
```html
        <a-form-item v-if="showExitCamera" label="出口相机ID">
          <a-input-number v-model:value="formData.exitCameraId" placeholder="出口相机ID" style="width: 100%" />
        </a-form-item>
```

With:
```html
        <a-form-item v-if="showExitCamera" :label="formData.type === 3 ? '出口相机' : '出口相机ID'">
          <a-select
            v-if="formData.type === 3 && exitCameraOptions.length > 0"
            v-model:value="formData.exitCameraId"
            placeholder="选择出口方向相机"
            allow-clear
            style="width: 100%"
            :options="exitCameraOptions.map(c => ({ value: c.deviceId, label: c.deviceName }))"
          />
          <a-input-number
            v-else
            v-model:value="formData.exitCameraId"
            placeholder="出口相机ID"
            style="width: 100%"
          />
        </a-form-item>
```

- [ ] **Step 5: Add camera configuration guidance hints**

After the `</a-form>` closing tag (line 135) and before the `</a-modal>` closing tag (line 136), add:

```html
        <div v-if="formData.type === 3" class="camera-guide" style="margin-top: 12px; padding: 0 24px;">
          <a-alert v-if="hasEntryCamera && hasExitCamera" type="success" message="相机配置完整：双方向均已绑定" show-icon />
          <a-alert v-else-if="!hasEntryCamera && !hasExitCamera" type="warning" message="建议配置入场方向和出场方向相机" show-icon />
          <a-alert v-else-if="!hasEntryCamera" type="warning" message="建议添加入场方向相机" show-icon />
          <a-alert v-else-if="!hasExitCamera" type="warning" message="建议添加出场方向相机" show-icon />
        </div>
```

Note: These alerts should go inside the `a-modal` but outside `a-form`. The modal's content ends at the existing `</a-form>` + `</a-modal>`. Place the guidance div between `</a-form>` and `</a-modal>`.

- [ ] **Step 6: Add camera binding summary column to table**

In the `columns` array (line 159-169), add a new column after the `cameraMode` column:

```typescript
  { title: '相机模式', key: 'cameraMode', width: 110 },
  { title: '相机绑定', key: 'cameraBinding', width: 160 },
  { title: '潮汐模式', key: 'tideMode', width: 120 },
```

Add the column's bodyCell template in the `<template #bodyCell>` section. After the `cameraMode` template (line 63-65), add:

```html
        <template v-if="column.key === 'cameraBinding'">
          <template v-if="record.type === 3">
            <span>
              <span v-if="record.entryCameraId" style="color: #10b981">入场: ✅</span>
              <span v-else style="color: #f59e0b">入场: ❌</span>
              <span style="margin: 0 4px">|</span>
              <span v-if="record.exitCameraId" style="color: #10b981">出场: ✅</span>
              <span v-else style="color: #f59e0b">出场: ❌</span>
            </span>
          </template>
          <template v-else>-</template>
        </template>
```

- [ ] **Step 7: Add AvailableCamera type and getAvailableCameras to API**

In `admin-web/src/api/parking-lane.ts`, add after the `CAMERA_MODE_OPTIONS` constant (after line 49):

```typescript
/** 可绑定的相机设备 */
export interface AvailableCamera {
  deviceId: number
  deviceName: string
  recognitionDirection: number | null
}

/** 查询指定停车场可绑定到车道的相机列表 */
export function getAvailableCameras(parkingLotId: number) {
  return request.get<AvailableCamera[]>('/admin/devices/available-for-lane', { parkingLotId })
}
```

- [ ] **Step 8: Commit**

```bash
git add admin-web/src/views/parking/ParkingLaneManage.vue admin-web/src/api/parking-lane.ts
git commit -m "[任务包3-5] feat: 运营端车道管理页双向通道相机下拉选择器 + 配置引导提示 + 表格绑定摘要列"
```

---

### Task 10: Booth Web — LaneCard multi-camera rendering with primary-offline highlighting

**Files:**
- Modify: `booth-web/src/api/monitor-types.ts`
- Modify: `booth-web/src/stores/monitor.ts`
- Modify: `booth-web/src/views/monitor/index.vue`

- [ ] **Step 1: Extend monitor-types.ts with LaneCamera**

In `booth-web/src/api/monitor-types.ts`, after the `RemoteGateAlertPayload` interface (after line 111), add:

```typescript
/** 车道绑定的单个相机信息（任务包 3-5） */
export interface LaneCamera {
  deviceId: number
  name: string
  role: 'PRIMARY' | 'BACKUP' | null
  direction: 'ENTRY' | 'EXIT' | null
  online: boolean
  isActive: boolean
}
```

Then in the `Lane` interface (lines 11-20), add `cameras` field after `deviceName`:

```typescript
export interface Lane {
  id: number
  parkingLotId: number
  name: string
  code: string
  direction: 'ENTRY' | 'EXIT' | 'MIXED'
  status: string
  deviceId?: number
  deviceName?: string
  /** 车道绑定的相机列表（支持多相机/主备） */
  cameras?: LaneCamera[]
}
```

- [ ] **Step 2: Update LaneCard interface in index.vue**

In `booth-web/src/views/monitor/index.vue`, update the `LaneCard` interface (lines 493-503). Add the `Camera` import from monitor-types.

First, add the import for `LaneCamera`. Change the import line (line 305):
```typescript
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent, RemoteGateAlertPayload } from '@/api/monitor-types'
```
to:
```typescript
import type { DeviceStatus, RecognitionEventPayload, SpaceUpdatePayload, AlertPayload, RecognitionEvent, RemoteGateAlertPayload, LaneCamera } from '@/api/monitor-types'
```

Then replace the `LaneCard` interface (lines 493-503):
```typescript
interface LaneCard {
  laneId: number
  laneName: string
  direction: string
  deviceId?: number
  deviceOnline: boolean
  isOffline: boolean
  /** 是否正在收费中 */
  charging: boolean
  latestEvent?: RecognitionEventPayload
}
```

With:
```typescript
interface LaneCard {
  laneId: number
  laneName: string
  direction: string
  deviceId?: number
  deviceOnline: boolean
  isOffline: boolean
  /** 是否正在收费中 */
  charging: boolean
  latestEvent?: RecognitionEventPayload
  /** 多相机模式下的相机详情 */
  cameras?: LaneCamera[]
  /** 主相机是否离线（用于高亮告警） */
  primaryOffline: boolean
  /** 当前活跃相机描述文本 */
  activeSourceLabel?: string
}
```

- [ ] **Step 3: Rewrite laneCards computed with multi-camera split**

Replace the entire `laneCards` computed (lines 505-528):

```typescript
const laneCards = computed((): LaneCard[] => {
  return store.lanes.map((lane) => {
    const cameras = lane.cameras || []
    const hasMultiCameras = cameras.length > 0

    // 单相机模式（兼容现有逻辑）
    if (!hasMultiCameras) {
      const device = lane.deviceId
        ? store.deviceStatuses.find((d) => d.deviceId === lane.deviceId)
        : undefined
      const latestEvent = store.recentEvents.find((e) => e.laneId === lane.id)
      const charging =
        store.chargePanelVisible &&
        store.currentChargeInfo?.laneId === lane.id
      return {
        laneId: lane.id,
        laneName: lane.name || `车道 ${lane.id}`,
        direction: lane.direction,
        deviceId: lane.deviceId,
        deviceOnline: !!device?.online && !device?.stale,
        isOffline: !device || !device.online || device.stale,
        charging,
        latestEvent: latestEvent as RecognitionEventPayload | undefined,
        cameras: [],
        primaryOffline: false,
        activeSourceLabel: undefined,
      }
    }

    // 多相机模式：cameras 已由后端 BoothMonitorService.loadLanes 填充
    // 在线状态从后端 cameras[].online 直接使用（loadLanes 已查询 DeviceStatusVO 快照）
    const primaryCameras = cameras.filter(c => c.role === 'PRIMARY')
    const primaryOffline = primaryCameras.some(c => !c.online)
    const activeCamera = cameras.find(c => c.isActive)
    const activeSourceLabel = activeCamera
      ? `当前: ${activeCamera.role === 'PRIMARY' ? '主相机' : '备相机'}`
      : undefined

    return {
      laneId: lane.id,
      laneName: lane.name || `车道 ${lane.id}`,
      direction: lane.direction,
      deviceOnline: cameras.some(c => c.isActive && c.online),
      isOffline: cameras.every(c => !c.online),
      charging: store.chargePanelVisible && store.currentChargeInfo?.laneId === lane.id,
      latestEvent: store.recentEvents.find((e) => e.laneId === lane.id) as RecognitionEventPayload | undefined,
      cameras,
      primaryOffline,
      activeSourceLabel,
    }
  })
})
```

- [ ] **Step 4: Update template — Add multi-camera tag rendering**

Replace the `.lane-tags` section (lines 89-96):
```html
                <div class="lane-tags">
                  <a-tag v-if="lane.charging" color="processing">
                    <SyncOutlined :spin="true" style="margin-right: 2px" />收费中
                  </a-tag>
                  <a-tag :color="lane.deviceOnline ? 'success' : 'error'">
                    {{ lane.deviceOnline ? '在线' : '离线' }}
                  </a-tag>
                </div>
```

With:
```html
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
```

- [ ] **Step 5: Add primary-offline visual highlighting**

Add `ExclamationCircleOutlined` icon import. Change the import line (line 302):
```typescript
import { SyncOutlined, BellOutlined } from '@ant-design/icons-vue'
```
to:
```typescript
import { SyncOutlined, BellOutlined, ExclamationCircleOutlined } from '@ant-design/icons-vue'
```

In the lane-card class binding (lines 82-85), add `lane-primary-offline`:
```html
              :class="{
                'lane-offline': lane.isOffline,
                'lane-charging': lane.charging,
                'lane-primary-offline': lane.primaryOffline,
              }"
```

In the `.lane-header` div (lines 87-89), add a warning icon after the lane name for primary-offline state:
```html
              <div class="lane-header">
                <span class="lane-name">
                  {{ lane.laneName }}
                  <span v-if="lane.primaryOffline" style="color: #f59e0b; margin-left: 6px;">
                    <ExclamationCircleOutlined />
                    <span style="font-size: 12px; margin-left: 2px;">主相机离线</span>
                  </span>
                </span>
```

- [ ] **Step 6: Add lane-primary-offline CSS**

In the `<style>` section, after the `.lane-card.lane-charging` rule (lines 766-769), add:

```css
  &.lane-primary-offline {
    border: 2px solid #f59e0b;
    background-color: rgba(245, 158, 11, 0.04);
  }
```

- [ ] **Step 7: Commit**

```bash
git add booth-web/src/api/monitor-types.ts booth-web/src/stores/monitor.ts booth-web/src/views/monitor/index.vue
git commit -m "[任务包3-5] feat: 岗亭监控 LaneCard 多相机标签 + 主相机离线高亮边框和警告图标"
```

---

### Task 11: Compile, test, and verify

- [ ] **Step 1: Compile backend**

Run:
```bash
mvn clean compile -pl parking-system -am 2>&1 | tail -30
```
Expected: `BUILD SUCCESS` with no compilation errors.

- [ ] **Step 2: Run full test suite**

Run:
```bash
mvn test -pl parking-boot -am 2>&1 | tail -40
```
Expected: `BUILD SUCCESS`, all tests pass. Watch for any test failures related to `RecognitionEventConsumer` (constructor now requires `CameraFailoverService`) or `BoothMonitorService` (constructor now requires `CameraFailoverService`). If existing unit tests break, the test configurations need updating — this is a known risk identified in the spec §12.3.

- [ ] **Step 3: Verify Flyway migration validity**

Run (if local DB available):
```bash
mvn flyway:info -pl parking-boot 2>&1 | grep V20260901001
```
Or manually verify the SQL syntax:
```bash
# Check that the SQL is syntactically valid
cat parking-boot/src/main/resources/db/migration/V20260901001__add_camera_source.sql
```
Expected: Three `ALTER TABLE ... ADD COLUMN` statements with correct column definitions.

- [ ] **Step 4: Verify no regressions in unidirectional lane flow**

Run a targeted test of the Consumer logic:

Run:
```bash
mvn test -pl parking-boot -am -Dtest="RecognitionEventConsumerTest" 2>&1 | tail -20
```
Expected: All existing consumer tests pass. If the test configuration needs updating for the new constructor parameter, update the test's Spring context or mock setup before re-running.

- [ ] **Step 5: Package the application**

Run:
```bash
mvn clean package -pl parking-boot -am -DskipTests 2>&1 | tail -10
```
Expected: `BUILD SUCCESS`.

- [ ] **Step 6: Final commit (if any test fixes were needed)**

If any test configuration fixes were made:
```bash
git add -A
git commit -m "[任务包3-5] fix: 适配 Consumer/BoothMonitorService 新构造函数参数的测试配置"
```

---

### Task 12: Self-Audit Checklist

Run through this checklist before declaring the plan complete:

- [ ] **Spec coverage**: Each requirement from the spec has at least one task:
  - §5 (DB migration) → Task 1
  - §6 (Entity changes) → Tasks 2, 3
  - §7 (Consumer) → Task 4
  - §7.5 (EntryService/ExitService) → Tasks 5, 6
  - §8 (Admin lane page) → Task 9
  - §9 (Booth monitor) → Tasks 8, 10
  - §12 (Testing) → Task 11
- [ ] **No placeholders**: Search for "TBD", "TODO", "implement later", "appropriate error handling" — none present
- [ ] **Type consistency**: `cameraSource` field name consistent across all entity classes, payload, and all service locations
- [ ] **Constructor consistency**: `RecognitionEventConsumer` and `BoothMonitorService` both updated with new `CameraFailoverService` parameter
- [ ] **Flyway version check**: `V20260901001` does not conflict with existing migrations (latest is `V20260828001`)
- [ ] **Unidirectional lane safety**: All new logic gated on `lane.getType() == 3` or `lane.getType() != 3` checks, plus nullable cameraSource defaulting
