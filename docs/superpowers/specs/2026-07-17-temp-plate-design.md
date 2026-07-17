# 无牌车临时车牌处理 — 设计规格说明书（任务包 3-4）

## 文档信息

| 项目 | 内容 |
| :--- | :--- |
| **文档名称** | 无牌车临时车牌处理设计规格说明书 |
| **版本号** | V1.0 |
| **编写日期** | 2026-07-17 |
| **对应任务包** | 3-4：无牌车临时车牌——识别失败 → 临时车牌 → 全流程 |
| **需求依据** | 需求规格说明书 V1.2 §3.2.5（BOOTH-005）、§7.4-5、附录无牌车流程 |
| **功能编号** | COMM-013（通信层识别失败路由）、BOOTH-005（岗亭端手动处理） |
| **设计决策** | D5：无牌车识别失败 → 临时车牌全链路 |

---

## 1. 目标

当停车场相机的车牌识别返回空结果（无牌车、遮挡、脏污等）时，系统根据车场级参数 `recognition.fail_strategy` 的配置，将识别事件转换为临时车牌流程处理。AUTO_RELEASE 策略下自动生成临时车牌、创建在场记录、开闸放行；MANUAL 策略下推送 WebSocket 告警到岗亭端，由岗亭操作员手动录入临时车牌完成入场。出场时岗亭操作员通过临时车牌匹配在场记录，经计费引擎计算费用后以现金方式收款并开闸放行。

整个流程确保无牌车辆完整参与停车计费链路（入场 → 在场 → 计费 → 出场），临时车牌生成的临时记录与正式车牌记录统一存储于 `parking_record` 和 `parking_order` 表，通过 `temp_plate_flag` 字段区分。

---

## 2. 非目标（本期不做）

| 项目 | 说明 |
| :--- | :--- |
| 低置信度识别触发临时车牌 | 仅空车牌号（plateNumber 为 null/blank）触发识别失败流程；置信度低于阈值仍按正式车牌流程处理 |
| 小程序端无牌车自助入场 | 无牌车仅通过岗亭端处理，小程序用户无法自助为无牌车创建通行记录 |
| 临时车牌自动同步至相机白名单 | 本期不做设备侧白名单同步 |
| 临时车牌 OCR 纠正/回填 | 事后发现真实车牌后回填到记录的功能不做 |
| 批量无牌车导入 | 仅支持岗亭端逐辆车录入 |
| 临时车牌历史归档单独视图 | 复用现有通行记录查询接口，通过 tempPlateFlag 筛选即可 |

---

## 3. 背景与现状

### 3.1 当前实现

- `RecognitionEventConsumer.validateAndStandardize()`：当车牌号为空时，标记 FAILED 并记录 failureReason="车牌号为空"，不进入入场/出场处理。
- `PlateStandardizer.normalize(null/blank)`：返回 null，上层无后续处理。
- `BoothWebSocketPublisher`：已实现 `/topic/booth/{lotId}/alerts` 和 `/topic/booth/{lotId}/events` 推送通道，推送方法内部捕获异常不阻断主业务。
- `ParamKeys.RECOGNITION_FAIL_STRATEGY`：已定义参数键 `"recognition.fail_strategy"`，含 `MANUAL`（默认）和 `AUTO_RELEASE` 两个枚举值。
- `MonitorAlert.TYPE_RECOGNITION_FAIL`：告警类型常量已存在（`"RECOGNITION_FAIL"`）。
- `DeviceService.openGateByLane(laneId, reason)`：已实现，通过适配器层 HTTP REST 下发开闸指令。
- `BillingEngine.calculateFee(parkingLotId, entryTime, exitTime)`：已实现，按车场当前生效计费规则计算费用。

### 3.2 核心问题

1. **空车牌事件被丢弃**：Consumer 直接把空车牌事件标记 FAILED 结束，缺乏无牌车通行处理能力。
2. **无临时车牌生成机制**：无牌车入场需要一个人工可识别的临时标识，当前系统完全没有。
3. **无牌车出场无法匹配**：即使岗亭手动创建了在场记录，临时车牌格式与标准车牌不同，现有查询逻辑可能产生混淆。
4. **前端缺少无牌车处理入口**：岗亭端没有识别失败告警弹窗、临时车牌录入界面。

---

## 4. 设计决策汇总

| # | 决策点 | 结论 | 理由 |
|---|--------|------|------|
| 1 | 临时车牌生成方式 | Redis INCR，key 格式 `temp_plate:{lotId}:{yyyyMMdd}`，值格式 "临" + yyMMdd + 2位序号（如 "临26071701"） | 按天按车场自增，天然无并发冲突，无需数据库序列 |
| 2 | 识别失败触发条件 | 仅 `plateNumber` 为 null 或 blank 时触发 | 低置信度仍需人工确认，但走正式车牌流程而非临时车牌 |
| 3 | 架构模式 | 在 `RecognitionEventConsumer` 内联路由，不新增独立 MQ 队列 | Consumer 已掌握所有上下文（设备/车道/车场/租户），新增队列引入不必要的复杂度 |
| 4 | 事件日志新状态 | 新增 `RECOGNITION_FAILED` 状态（区别于 FAILED） | FAILED 表示永久失败（设备异常等），RECOGNITION_FAILED 表示识别失败但系统已接管处理（MANUAL 推送告警 / AUTO_RELEASE 已处理） |
| 5 | AUTO_RELEASE 策略 | 自动生成临时车牌 + 创建在场记录 + 开闸，不生成预订单 | 无人值守场景，出场时长需由岗亭操作员确认后计费，预订单无实际意义 |
| 6 | MANUAL 策略 | 仅推送 WebSocket 告警，不生成任何记录 | 保持最小侵入，由岗亭操作员决定是否放行及车牌号 |
| 7 | 岗亭端出场匹配 | 通过 `TempPlateService.handleExitMatch()` 新方法完成：查在场记录 → 计费 → 现金订单 → 完成记录 → 开闸 | 与 `ExitService.handleExit()` 场景不同（后者由相机识别触发），单独方法职责清晰 |
| 8 | 临时车牌格式校验 | 非空 + 长度 ≤20 字符 + 不以空格开头 | 宽松校验，不强制标准车牌 regex，因为临时车牌本就是非标准格式 |
| 9 | 数据保留 | 临时车牌记录与正式车牌记录永久共存，可搜索 | 不区分表，仅通过 tempPlateFlag=1 筛选 |

---

## 5. 数据库设计

### 5.1 现有表新增字段

三张表各新增 `temp_plate_flag` 字段：

```sql
-- parking_record
ALTER TABLE parking_record
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

-- parking_order
ALTER TABLE parking_order
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

-- recognition_event_log
ALTER TABLE recognition_event_log
    ADD COLUMN temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';
```

### 5.2 字段语义

| 表 | temp_plate_flag=0 | temp_plate_flag=1 |
| :--- | :--- | :--- |
| `parking_record` | 正式车牌在场记录 | 临时车牌在场记录（无牌车入场） |
| `parking_order` | 正式车牌订单 | 临时车牌订单 |
| `recognition_event_log` | 普通识别事件 | 识别失败事件（已转为临时车牌处理） |

### 5.3 RecognitionEventLog 新增状态值

`recognition_event_log.status` 新增合法值 `RECOGNITION_FAILED`：

| 状态 | 含义 |
| :--- | :--- |
| `RECEIVED` | 已接收，待处理 |
| `PROCESSING` | 处理中 |
| `PROCESSED` | 处理成功 |
| `FAILED` | 处理失败（设备/车道/停车场校验失败、系统异常等永久失败） |
| `RECOGNITION_FAILED` | 识别失败，已接管（MANUAL 已推送告警 / AUTO_RELEASE 已处理） |

> **区分**：`FAILED` 是“无法处理的坏事件”，`RECOGNITION_FAILED` 是“识别失败但后续系统已接管”的过渡状态。

### 5.4 Flyway 迁移脚本

文件名：`V20260717__add_temp_plate_flag.sql`

```sql
-- =============================================================================
-- 任务包 3-4：无牌车临时车牌 — 三表新增 temp_plate_flag 字段
-- =============================================================================

ALTER TABLE parking_record
    ADD COLUMN IF NOT EXISTS temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE parking_order
    ADD COLUMN IF NOT EXISTS temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';

ALTER TABLE recognition_event_log
    ADD COLUMN IF NOT EXISTS temp_plate_flag TINYINT NOT NULL DEFAULT 0 COMMENT '临时车牌标记：0=正式车牌, 1=临时车牌';
```

---

## 6. 临时车牌编号生成规则

### 6.1 格式

```
格式: "临" + yyMMdd + 两位序号
示例: 临26071701, 临26071702, ...
```

- **前缀**："临"（中文"临"字，固定）
- **日期**：`yyMMdd`，使用事件发生当日日期（`LocalDate.now()`），与 Redis key 中的日期一致
- **序号**：两位数字，左补零，从 01 开始，上限 99
- **总长度**：6 位（1 中文 + 2 年 + 2 月 + 2 日 + 2 序号）

### 6.2 Redis INCR 实现

```java
// TempPlateNumberGenerator
public String generate(Long parkingLotId) {
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    String key = "temp_plate:" + parkingLotId + ":" + today;
    
    // Redis INCR：原子自增，首次调用返回 1
    Long seq = redisTemplate.opsForValue().increment(key);
    
    // 过期时间：当日 23:59:59 后失效，保留到次日凌晨
    redisTemplate.expireAt(key, 
        LocalDateTime.now().toLocalDate().plusDays(1).atStartOfDay());
    
    return "临" + today + String.format("%02d", seq);
}
```

### 6.3 关键特性

- **原子性**：Redis INCR 是单线程原子操作，天然防并发
- **按天隔离**：每天从 01 重新开始，避免序号增长过大
- **按车场隔离**：不同车场的临时车牌不会冲突
- **当日上限**：单日每个车场最多 99 辆无牌车（序号 01-99）
- **序号耗尽**：超过 99 时 INCR 返回 100+，`String.format("%02d", 100)` 截断为 "00"，此时应返回错误提示"当日临时车牌号已用完"

---

## 7. Consumer 改造

### 7.1 改造 `validateAndStandardize()`

关键变更：空车牌检查从步骤 3.1（车牌标准化）移动到步骤 3.5 之后（设备/车道/停车场校验完成之后），此时 Consumer 已掌握停车场所需的所有上下文信息（parkingLotId、tenantId、laneId、deviceId），可直接进入识别失败接管分支而不丢失必要数据。

改造后的流程：

```
validateAndStandardize(payload):
  3.1 车牌标准化：若非空，执行标准化并赋值 standardizedPlate
  3.2 设备校验（CAMERA 类型/启用/存在）— 必须通过，识别失败也需要设备信息
  3.3 停车场校验（存在性 + 租户一致性）— 必须通过
  3.5 车道方向校验 — 必须通过
  3.6 ★ (新位置) 空车牌分支：若 rawPlate 为空/null
        → result.recognitionFailed = true
        → 保存 parkingLotId/tenantId/laneId/deviceId/eventTime 到 result
        → return result（success=false, recognitionFailed=true）
  3.7 格式校验（非阻塞）
```

即：仅在设备/车道/停车场校验全部通过后，才判定是否属于识别失败场景。如果设备或停车场校验失败，则标记 FAILED（设备配置问题不归入识别失败范畴）。

```java
// 3.1 车牌标准化（调整后：空车牌不立即失败，仅跳过标准化）
String rawPlate = payload.getPlateNumber();
boolean plateIsEmpty = (rawPlate == null || rawPlate.isBlank());
String standardized = null;
if (!plateIsEmpty) {
    standardized = PlateStandardizer.normalize(rawPlate);
    if (standardized == null || standardized.isEmpty()) {
        result.fail("车牌号标准化后为空");
        return result;
    }
    result.standardizedPlate = standardized;
}

// 3.2-3.5 设备/车道/停车场校验（不变，必须执行以获取上下文）
// ... (existing validation code) ...

// 3.6 (新位置) 识别失败接管
if (plateIsEmpty) {
    result.recognitionFailed = true;
    result.parkingLotId = device.getParkingLotId();  // 从已验证的 Device 推导
    result.tenantId = parkingLot.getTenantId();
    result.laneId = payload.getLaneId();
    result.deviceId = payload.getDeviceId();
    result.eventTime = payload.getEventTime();
    return result;  // success=false, recognitionFailed=true
}

// 3.7 格式校验（仅正式车牌执行）
if (!PlateStandardizer.isValidFormat(standardized)) {
    log.info("车牌格式可能异常（非阻塞）: plate={} eventId={}", standardized, payload.getEventId());
}

result.success = true;
return result;
```

返回的 `ProcessingResult` 需要新增字段：

```java
private static class ProcessingResult {
    boolean success = false;
    boolean recognitionFailed = false;   // 新增：识别失败接管标记
    String standardizedPlate;
    String failureReason;
    // 识别失败时需要保留的上下文（从已验证的 Device/ParkingLot 推导）
    Long parkingLotId;
    Long tenantId;
    Long laneId;
    Long deviceId;
    LocalDateTime eventTime;
    // ...
}
```

### 7.2 改造 `onRecognitionEvent()` 主流程

在 `validateAndStandardize()` 返回后、`handleEntry/handleExit` 调用前插入：

```java
// ---- 3.5 识别失败接管（无牌车处理） ----
if (result.recognitionFailed) {
    handleRecognitionFailure(payload, result);
    updateEventLog(payload, result);       // 状态置为 RECOGNITION_FAILED
    messageIdempotency.markProcessed(messageId, IDEMPOTENCY_TTL);
    return;
}
```

### 7.3 新方法 `handleRecognitionFailure()`

```java
private void handleRecognitionFailure(RecognitionEventPayload payload, ProcessingResult result) {
    // 仅入场方向需要无牌车处理（出场方向识别失败无法匹配在场记录，直接标记 FAILED）
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
        handleManualAlert(payload, result);  // MANUAL 是默认值
    }
    // 无论哪种策略，状态都标记为 RECOGNITION_FAILED（系统已接管）
}
```

### 7.4 MANUAL 策略处理

```java
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

    // 2. 创建 MonitorAlert 记录（便于告警历史查询）
    ParkingLot lot = parkingLotMapper.selectById(result.parkingLotId);
    RecognitionEventLog eventLog = eventLogMapper.selectOne(
            new LambdaQueryWrapper<RecognitionEventLog>()
                    .eq(RecognitionEventLog::getEventId, payload.getEventId()));
    if (lot != null && eventLog != null) {
        // 复用已有的 createRecognitionFailAlert(ParkingLot, RecognitionEventLog)
        eventLog.setPlateNumber("无牌车");
        eventLog.setFailureReason("识别失败，等待岗亭处理");
        monitorAlertService.createRecognitionFailAlert(lot, eventLog);
    }
}
```

BoothWebSocketPublisher 新增方法：

```java
/**
 * 推送识别失败告警到岗亭端。
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
        String imagePath, LocalDateTime eventTime) {
    if (parkingLotId == null) return;
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

### 7.5 AUTO_RELEASE 策略处理

AUTO_RELEASE 需要在一个独立的事务中完成记录创建和容量更新（不能复用 Consumer 外层事务，因为 Consumer 本身不开启事务）。实现方式：Consumer 注入 `TransactionTemplate`，在 `handleAutoRelease` 中通过 `transactionTemplate.execute()` 包裹数据库操作。

```java
private void handleAutoRelease(RecognitionEventPayload payload, ProcessingResult result) {
    log.info("无牌车识别失败-AUTO_RELEASE策略: eventId={} lotId={} laneId={}",
            payload.getEventId(), result.parkingLotId, result.laneId);

    // 1. 生成临时车牌号
    String tempPlate = tempPlateNumberGenerator.generate(result.parkingLotId);

    // 2. 事务内创建在场记录 + 更新容量（tempPlateFlag=1，无预订单）
    ParkingRecord record = transactionTemplate.execute(status -> {
        ParkingRecord r = new ParkingRecord();
        r.setTenantId(result.tenantId);
        r.setParkingLotId(result.parkingLotId);
        r.setLaneId(result.laneId);
        r.setDeviceId(result.deviceId);
        r.setStandardizedPlate(tempPlate);      // 存临时车牌号
        r.setTempPlateFlag(1);                   // 标记为临时车牌
        r.setStatus(ParkingRecord.STATUS_PARKING);
        r.setEntryTime(result.eventTime != null ? result.eventTime : LocalDateTime.now());
        r.setEntryImagePath(payload.getImagePath());
        recordMapper.insert(r);

        // 更新停车场容量
        parkingLotMapper.update(null,
                new LambdaUpdateWrapper<ParkingLot>()
                        .setSql("current_vehicles = current_vehicles + 1")
                        .setSql("remaining_spaces = remaining_spaces - 1")
                        .eq(ParkingLot::getId, result.parkingLotId));
        return r;
    });

    result.standardizedPlate = tempPlate;   // 回填到 result，用于事件日志更新

    // 3. AUTO_RELEASE 不生成预订单（出场时由岗亭按实际时长计费）

    // 4. 开闸放行（事务外执行，失败不阻塞）
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

> **注意**：AUTO_RELEASE 中开闸失败不阻塞记录创建。若闸机故障导致车辆实际未入场，岗亭端可通过"无牌车处理"列表找到该在场记录（实际未进场），操作员手动补开闸或作废记录。

### 7.6 Consumer 新增依赖

`RecognitionEventConsumer` 构造函数新增以下依赖注入（部分可能已存在）：

```java
// 新增
private final TempPlateNumberGenerator tempPlateNumberGenerator;
private final BoothWebSocketPublisher boothWebSocketPublisher;  
private final MonitorAlertService monitorAlertService;           
private final ParamResolver paramResolver;                       
private final ParkingRecordMapper recordMapper;                  
private final DeviceMapper deviceMapper;  // 已有，用于 device 查询获取 laneId
private final TransactionTemplate transactionTemplate;  // 事务模板

// 已有（确认 Consumer 已注入）
private final MessageIdempotency messageIdempotency;     // 已有
private final ParkingLotMapper parkingLotMapper;          // 已有
private final RecognitionEventLogMapper eventLogMapper;   // 已有
```

### 7.7 `updateEventLog()` 改造

现有 `updateEventLog()` 方法根据 `result.success` 二选一设置状态（PROCESSED/FAILED）。需新增第三个分支处理 `result.recognitionFailed`：

```java
private void updateEventLog(RecognitionEventPayload payload, ProcessingResult result) {
    RecognitionEventLog update = new RecognitionEventLog();
    update.setStandardizedPlate(result.standardizedPlate);
    if (result.recognitionFailed) {
        // 新增：识别失败已接管
        update.setStatus("RECOGNITION_FAILED");
        update.setTempPlateFlag(1);
    } else if (result.success) {
        update.setStatus("PROCESSED");
        update.setTempPlateFlag(0);
    } else {
        update.setStatus("FAILED");
        update.setFailureReason(result.failureReason);
    }
    // ... 其余更新逻辑不变（includes setTempPlateFlag）
}
```

---

## 8. 服务层设计

### 8.1 `TempPlateNumberGenerator`（新建）

文件位置：`parking-system/src/main/java/com/jushan/system/service/TempPlateNumberGenerator.java`

职责：通过 Redis INCR 按车场+日期维度生成唯一临时车牌号。

```java
@Service
public class TempPlateNumberGenerator {

    private static final int MAX_SEQ_PER_DAY = 99;
    private final StringRedisTemplate redisTemplate;

    /**
     * 生成临时车牌号。
     *
     * @param parkingLotId 车场 ID
     * @return 临时车牌号（如 "临26071701"）
     * @throws BusinessException 当日序号超过 99 时抛出
     */
    public String generate(Long parkingLotId) {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        String key = "temp_plate:" + parkingLotId + ":" + today;

        Long seq = redisTemplate.opsForValue().increment(key);
        // 首次使用时设置过期：当日 23:59:59 后自动清除
        if (seq == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(
                ChronoUnit.SECONDS.between(LocalDateTime.now(),
                    LocalDate.now().plusDays(1).atStartOfDay())));
        }

        if (seq > MAX_SEQ_PER_DAY) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "当日临时车牌号已用完（已达" + MAX_SEQ_PER_DAY + "个）");
        }

        return "临" + today + String.format("%02d", seq);
    }
}
```

### 8.2 `TempPlateService`（新建）

文件位置：`parking-system/src/main/java/com/jushan/system/service/TempPlateService.java`

职责：岗亭端手动操作临时车牌的业务逻辑（生成建议号、手动入场、出场匹配）。

#### 8.2.1 方法签名

```java
@Service
public class TempPlateService {

    /** 生成建议临时车牌号（不消耗序号，仅预览）。 */
    public String suggestTempPlate(Long parkingLotId);

    /** 岗亭手动无牌车入场：创建在场记录 + 预订单 + 开闸。 */
    @Transactional
    public ParkingRecord manualEntry(Long parkingLotId, Long laneId,
            String tempPlate, Long boothUserId);

    /** 岗亭手动无牌车出场匹配计费。 */
    @Transactional
    public ExitService.ExitResult handleExitMatch(String tempPlate,
            Long parkingLotId, Long laneId, Long boothUserId);
}
```

#### 8.2.2 `suggestTempPlate()` 实现

```java
public String suggestTempPlate(Long parkingLotId) {
    // 预览：通过 Redis GET 获取当前计数，不 INCR
    String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
    String key = "temp_plate:" + parkingLotId + ":" + today;
    String currentSeq = redisTemplate.opsForValue().get(key);
    long seq = (currentSeq != null ? Long.parseLong(currentSeq) : 0) + 1;
    if (seq > 99) {
        throw new BusinessException(CommonErrorCode.PARAM_ERROR, "当日临时车牌号已用完");
    }
    return "临" + today + String.format("%02d", seq);
}
```

> **注意**：`suggestTempPlate` 不消耗序号（GET 而非 INCR）。`manualEntry` 中可传入自定义车牌号覆盖建议值，此时也不消耗序号；仅 AUTO_RELEASE 和岗亭端使用自动生成时才消耗。

#### 8.2.3 `manualEntry()` 实现

```java
@Transactional(rollbackFor = Exception.class)
public ParkingRecord manualEntry(Long parkingLotId, Long laneId,
        String tempPlate, Long boothUserId) {
    // 1. 校验临时车牌格式
    validateTempPlateFormat(tempPlate);

    // 2. 检查是否已有同临牌的 PARKING 记录（防止重复创建）
    ParkingRecord existing = recordMapper.selectOne(
        new LambdaQueryWrapper<ParkingRecord>()
            .eq(ParkingRecord::getParkingLotId, parkingLotId)
            .eq(ParkingRecord::getStandardizedPlate, tempPlate)
            .eq(ParkingRecord::getStatus, ParkingRecord.STATUS_PARKING));
    if (existing != null) {
        throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                "该临时车牌已有在场记录: " + tempPlate);
    }

    // 3. 获取车场和租户信息
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
    record.setEntryImagePath(null);  // 人工录入无可抓拍图
    recordMapper.insert(record);

    // 5. 创建预订单（PRE_ORDER，供后续计费使用）
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
        // 开闸失败不阻断流程，记录已创建
    }

    log.info("岗亭手动无牌车入场: recordId={} tempPlate={} lotId={} userId={}",
            record.getId(), tempPlate, parkingLotId, boothUserId);
    return record;
}
```

#### 8.2.4 `handleExitMatch()` 实现

```java
@Transactional(rollbackFor = Exception.class)
public ExitService.ExitResult handleExitMatch(String tempPlate,
        Long parkingLotId, Long laneId, Long boothUserId) {
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

    // 4. 创建订单（PENDING_PAY, payScene=AT_EXIT）
    ParkingOrder order = parkingOrderService.createOrderInternal(
            record, feeCents, null,
            ParkingOrder.PAY_SCENE_AT_EXIT, laneId, null,
            "岗亭无牌车出场计费");
    // 现金支付：payChannel 设置为 CASH
    order.setTempPlateFlag(1);
    order.setPayChannel(ParkingOrder.PAY_CHANNEL_CASH);
    orderMapper.updateById(order);

    // 5. 模拟支付：preparePay → confirmPay（模拟现金收款）
    mockPaymentService.preparePay(order);
    boolean paid = mockPaymentService.confirmPay(order.getId(),
            "BOOTH_USER_" + boothUserId);
    if (!paid) {
        throw new BusinessException(CommonErrorCode.SYSTEM_ERROR,
                "模拟现金支付失败，请重试");
    }

    // 6. 完成停车记录
    record.setStatus(ParkingRecord.STATUS_COMPLETED);
    record.setExitTime(exitTime);
    recordMapper.updateById(record);

    // 7. 减停车容量
    parkingLotMapper.update(null,
            new UpdateWrapper<ParkingLot>()
                    .setSql("current_vehicles = current_vehicles - 1")
                    .setSql("remaining_spaces = remaining_spaces + 1")
                    .eq("id", parkingLotId));

    // 8. 开闸放行
    deviceService.openGateByLane(laneId, "岗亭手动出场开闸(" + tempPlate + ")");

    // 9. 创建出场记录
    ExitRecord exitRecord = new ExitRecord();
    exitRecord.setTenantId(record.getTenantId());
    exitRecord.setParkingLotId(parkingLotId);
    exitRecord.setParkingRecordId(record.getId());
    exitRecord.setLaneId(laneId);
    exitRecord.setStandardizedPlate(tempPlate);
    exitRecord.setExitTime(exitTime);
    exitRecord.setFeeCents(feeCents);
    exitRecord.setPaidCents(feeCents);
    exitRecord.setReleaseDecision("ALLOW");  // 允许放行
    exitRecord.setOrderId(order.getId());
    exitRecord.setReason("岗亭手动无牌车出场，现金支付");
    exitRecordMapper.insert(exitRecord);

    log.info("岗亭手动无牌车出场: recordId={} tempPlate={} feeCents={} orderId={} userId={}",
            record.getId(), tempPlate, feeCents, order.getId(), boothUserId);

    return ExitService.ExitResult.of(
            ExitService.ReleaseDecision.zeroFee(), // 已支付按零费放行
            exitRecord.getId(), order.getId(), feeCents);
}
```

#### 8.2.5 `validateTempPlateFormat()`

```java
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
```

---

## 9. API 设计

### 9.1 岗亭端 Controller

文件位置：`parking-system/src/main/java/com/jushan/system/controller/BoothTempPlateController.java`

```java
@RestController
@RequestMapping("/api/booth/temp-plate")
public class BoothTempPlateController {

    private final TempPlateService tempPlateService;
    private final TempPlateNumberGenerator numberGenerator;

    // ==================== 端点 ====================

    /**
     * 生成建议临时车牌号（预览，不消耗序号）。
     */
    @GetMapping("/suggest")
    @RequirePermission("booth:monitor")
    public R<Map<String, String>> suggest(@RequestParam Long parkingLotId) {
        String suggested = tempPlateService.suggestTempPlate(parkingLotId);
        return R.ok(Map.of("tempPlate", suggested));
    }

    /**
     * 岗亭手动无牌车入场。
     * <p>
     * 创建在场记录 + 预订单 + 开闸。若 tempPlate 为空则自动生成。
     */
    @PostMapping("/entry")
    @RequirePermission("booth:monitor")
    @BusinessLog("岗亭手动无牌车入场")
    public R<Map<String, Object>> manualEntry(
            @RequestBody @Valid TempPlateEntryRequest request) {
        Long boothUserId = TenantContext.getUserId();

        String tempPlate = request.getTempPlate();
        if (tempPlate == null || tempPlate.isBlank()) {
            // 未指定时自动生成（消耗序号）
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
    @BusinessLog("岗亭手动无牌车出场")
    public R<Map<String, Object>> exitMatch(
            @RequestBody @Valid TempPlateExitRequest request) {
        Long boothUserId = TenantContext.getUserId();

        ExitService.ExitResult result = tempPlateService.handleExitMatch(
                request.getTempPlate(), request.getParkingLotId(),
                request.getLaneId(), boothUserId);

        return R.ok(Map.of(
                "exitRecordId", result.exitRecordId(),
                "orderId", result.orderId(),
                "feeCents", result.feeCents(),
                "message", result.decision().getMessage()));
    }
}
```

### 9.2 请求体

```java
// TempPlateEntryRequest.java
public class TempPlateEntryRequest {
    @NotNull(message = "车场ID不能为空")
    private Long parkingLotId;

    @NotNull(message = "车道ID不能为空")
    private Long laneId;

    // 可选：岗亭端传入固定车牌号；为空时自动生成
    private String tempPlate;
}

// TempPlateExitRequest.java
public class TempPlateExitRequest {
    @NotBlank(message = "临时车牌号不能为空")
    private String tempPlate;

    @NotNull(message = "车场ID不能为空")
    private Long parkingLotId;

    @NotNull(message = "车道ID不能为空")
    private Long laneId;
}
```

### 9.3 权限要求

- 所有岗亭端临时车牌接口使用 `@RequirePermission("booth:monitor")`，岗亭操作员默认拥有该权限。
- 敏感操作用 `@BusinessLog` 注解记录审计日志。

---

## 10. 运营端改造

### 10.1 通行记录列表

`ParkingRecordAdminController` 查询返回 VO 中加入 `tempPlateFlag` 字段：

```java
// ParkingRecordAdminVO 新增
private Integer tempPlateFlag;  // 0=正式, 1=临时
```

运营端前端（admin-web）在通行记录列表中，`tempPlateFlag=1` 的行显示 "临" 标签（Badge/Tag 组件），颜色建议为橙色，Hover 提示 "临时车牌记录"。

### 10.2 订单列表

`OrderAdminController` 查询返回 VO 中加入 `tempPlateFlag` 字段：
```java
// 同理在订单 VO 中加入，前端显示逻辑同上
```

### 10.3 查询筛选

- 通行记录/订单列表查询接口支持 `tempPlateFlag` 作为可选筛选条件（Integer，null=全部，0=正式，1=临时）。
- 车牌号搜索框支持临时车牌号搜索（如输入"临26071701"）。

---

## 11. 岗亭端前端改造（Booth UI）

### 11.1 WebSocket 告警监听

岗亭端（booth-web）需要监听 `/topic/booth/{lotId}/alerts` 通道，识别 `type: "RECOGNITION_FAILED"` 的消息，触发告警弹窗。

### 11.2 告警弹窗

- **位置**：右下角小弹窗（非模态），自动弹出后 10 秒未操作自动收起到任务栏图标。
- **内容**：
  - 标题："入口识别失败 —— 无牌车辆"
  - 方向、车道名称、事件时间
  - 抓拍全景图（如有 imagePath）
  - 临时车牌输入框（预填建议值，调用 `GET /api/booth/temp-plate/suggest`）
  - "确认入场"和"忽略"按钮
- **确认入场**：调用 `POST /api/booth/temp-plate/entry` 完成入场，成功后弹窗关闭
- **忽略**：弹窗关闭，不创建记录（操作员认为不需要入场）

### 11.3 通行作业区

在岗亭端"通行作业区"增加"无牌车处理"入口（按钮/选项卡）：
- 展示当天所有 `tempPlateFlag=1` 的在场记录列表
- 每条记录显示：临时车牌号、入场时间、已停时长
- 操作：点击某条记录弹出出场计费确认框（显示费用、现金支付确认）
- 确认后调用 `POST /api/booth/temp-plate/exit-match` 完成出场

---

## 12. 业务流程完整示意

### 12.1 AUTO_RELEASE 策略：无牌车入场

```
相机识别失败(空车牌)
  → RecognitionEventConsumer 检测到 plateNumber blank
  → 查询 param: recognition.fail_strategy = AUTO_RELEASE
  → TempPlateNumberGenerator.generate(lotId) → 临26071701
  → 创建 parking_record (tempPlateFlag=1, plate="临26071701", status=PARKING)
  → DeviceService.openGateByLane(laneId)
  → 事件日志: status=RECOGNITION_FAILED, tempPlateFlag=1
  → 车辆入场完成
```

### 12.2 MANUAL 策略：无牌车入场

```
相机识别失败(空车牌)
  → RecognitionEventConsumer 检测到 plateNumber blank
  → 查询 param: recognition.fail_strategy = MANUAL
  → BoothWebSocketPublisher.sendRecognitionFailedAlert()
  → MonitorAlert 创建记录(alertType=RECOGNITION_FAIL)
  → 事件日志: status=RECOGNITION_FAILED
  → （岗亭端弹窗）
  → 岗亭操作员确认入场 → POST /api/booth/temp-plate/entry
  → TempPlateService.manualEntry() 创建记录 + 预订单 + 开闸
  → 车辆入场完成
```

### 12.3 无牌车出场（MANUAL 和 AUTO_RELEASE 通用）

```
岗亭操作员搜索/选择在场临时车牌
  → POST /api/booth/temp-plate/exit-match
  → TempPlateService.handleExitMatch()
      → 查询 parking_record WHERE standardizedPlate=tempPlate AND status=PARKING
      → BillingEngine.calculateFee(lotId, entryTime, now)
      → 创建 parking_order (type=PARKING, tempPlateFlag=1, payChannel=CASH, status=PAID)
      → 完成 parking_record (status=COMPLETED)
      → 减停车场容量
      → 创建 exit_record
      → DeviceService.openGateByLane(laneId)
  → 出场完成
```

---

## 13. 文件变更清单

### 13.1 新建文件

| 文件 | 模块 | 说明 |
| :--- | :--- | :--- |
| `service/TempPlateNumberGenerator.java` | system | Redis INCR 临时车牌号生成器 |
| `service/TempPlateService.java` | system | 岗亭端临时车牌入场/出场业务逻辑 |
| `controller/BoothTempPlateController.java` | system | 岗亭端临时车牌 REST API |
| `dto/TempPlateEntryRequest.java` | system | 入场请求体 |
| `dto/TempPlateExitRequest.java` | system | 出场匹配请求体 |
| `db/migration/V20260717__add_temp_plate_flag.sql` | boot | Flyway 迁移：三表新增 tempPlateFlag |

### 13.2 修改文件

| 文件 | 改动内容 |
| :--- | :--- |
| `entity/ParkingRecord.java` | 新增 `tempPlateFlag` 字段 |
| `entity/ParkingOrder.java` | 新增 `tempPlateFlag` 字段 |
| `entity/RecognitionEventLog.java` | 新增 `tempPlateFlag` 字段 |
| `event/RecognitionEventConsumer.java` | 注入新依赖；空车牌识别改为识别失败接管；新增 `handleRecognitionFailure()`、`handleManualAlert()`、`handleAutoRelease()` 方法 |
| `ws/BoothWebSocketPublisher.java` | 新增 `sendRecognitionFailedAlert()` 方法 |
| `vo/ParkingRecordAdminVO.java` | 新增 `tempPlateFlag` 字段 |
| `controller/ParkingRecordAdminController.java` | 查询接口支持 tempPlateFlag 筛选和返回 |
| 运营端/岗亭端 Vue 前端 | 见 §10、§11 |

---

## 14. 业务规则

### 14.1 临时车牌唯一性

- 同车场 + 同临时车牌号 + `status=PARKING` 时，不允许重复创建入场记录。
- 临时车牌号由 `TempPlateNumberGenerator` 保证按天按车场唯一，Redis INCR 原子递增。

### 14.2 临时车牌与正式车牌的隔离

- `standardized_plate` 字段同时存储正式车牌和临时车牌，不新建独立字段。
- `temp_plate_flag=1` 区分两类记录。
- 正式车牌查询（如出场缴费小程序搜索）不会将临时车牌纳入结果（查询条件应加 `temp_plate_flag=0`）。
- 异常数据保护：正式车牌不会与临时车牌号冲突（正式车牌不可能以"临"开头），即使冲突也有 `temp_plate_flag` 兜底区分。

### 14.3 出场缴费

- 岗亭端临时车牌出场默认走**现金支付**（`payChannel=CASH`）。
- 不支持小程序端/运营端远程支付临时车牌订单（临时车牌无手机号绑定）。
- 支持现金支付后开具收费凭证。

### 14.4 预订单差异

| 策略 | 是否生成预订单 | 原因 |
| :--- | :---: | :--- |
| AUTO_RELEASE | 否 | 无人值守自动放行，出场由岗亭按实际时长计费，预订单无实际作用 |
| MANUAL（岗亭入场） | 是 | 岗亭操作员确认入场后立即生成 PRE_ORDER，与正式车辆流程一致 |

### 14.5 白名单/月卡/固定车位

- 无牌车辆的 `standardized_plate` 为“临26071701”等临时车牌号，无法匹配白名单/月卡/固定车位。
- 即使某临时车牌号偶然与白名单车牌号相同（极低概率），`temp_plate_flag=1` 可确保不走免费通行路径。

---

## 15. 测试策略

### 15.1 单元测试

| 测试类 | 测试点 |
| :--- | :--- |
| `TempPlateNumberGeneratorTest` | Redis INCR 原子递增；跨天重新从 01 开始；序号超过 99 抛异常；不同车场独立计数 |
| `TempPlateServiceTest` | 入场：正常创建记录+预订单+开闸；重复入场拦截；出场：匹配到在场记录→计费→完成；未找到在场记录抛异常 |
| `RecognitionEventConsumerTest` | 空车牌+MANUAL 策略→推送 WS 告警+标记 RECOGNITION_FAILED；空车牌+AUTO_RELEASE→自动生成临时车牌+开闸；空车牌+EXIT 方向→FAILED；非空车牌→不变 |

### 15.2 接口测试

| 端点 | 测试场景 |
| :--- | :--- |
| `GET /api/booth/temp-plate/suggest` | 返回当天建议临时车牌号；序号不递增 |
| `POST /api/booth/temp-plate/entry` | 指定车牌入场成功；空车牌自动生成入场；重复车牌拦截 |
| `POST /api/booth/temp-plate/exit-match` | 匹配到在场记录→计费→出入场完成；无在场记录返回 404 |

### 15.3 集成测试

- 完整无牌车入场+出场链路（MANUAL 策略）：
  1. 模拟相机发送空车牌事件
  2. 验证 WS 告警推送到岗亭端
  3. 岗亭端调用手动入场接口
  4. 验证 parking_record 和 parking_order 写入
  5. 岗亭端调用出场匹配接口
  6. 验证计费正确、订单完成、开闸指令下发
  7. 验证停车场容量恢复

- AUTO_RELEASE 策略集成测试：
  1. 模拟相机发送空车牌事件
  2. 验证自动创建在场记录
  3. 验证开闸指令下发
  4. 验证无预订单生成

### 15.4 已有测试适配

- `RecognitionEventConsumer` 的现有测试用例中，如果测试“空车牌→FAILED”的预期行为，需要更新为：
  - MANUAL 策略：状态变为 `RECOGNITION_FAILED`（非 `FAILED`）
  - AUTO_RELEASE 策略：状态变为 `RECOGNITION_FAILED` 且记录创建成功

---

## 16. 风险与缓解

### 风险 1：Redis 不可用导致临时车牌号生成失败

- **严重程度**：中
- **影响**：AUTO_RELEASE 策略下无法生成临时车牌号，无牌车被阻塞在入口。
- **缓解措施**：
  - `TempPlateNumberGenerator.generate()` 中捕获 Redis 连接异常，降级为使用时间戳后 4 位 + 随机 2 位数字作为后备方案（如 "临2607170100R3"，加后缀避免冲突）。
  - 降级时打印 ERROR 日志并触发监控告警。
  - Redis 集群部署保证高可用。

### 风险 2：当日临时车牌序号超过 99

- **严重程度**：低
- **影响**：极端情况下（单个车场单日超过 99 辆无牌车），生成失败。
- **缓解措施**：
  - 超过 99 时抛出明确的业务异常，前端提示“当日临时车牌号已用完，请联系管理员”。
  - 如需扩容，将数字位数从 2 位扩展为 3 位（`%03d`），上限提升至 999。

### 风险 3：AUTO_RELEASE 策略下开闸失败

- **严重程度**：中
- **影响**：记录已创建、容量已更新，但闸机未打开，车辆无法入场。
- **缓解措施**：
  - 开闸放在记录创建后再执行，失败不影响记录创建（非事务内，已用 try-catch 包裹）。
  - 开闸失败时打印 ERROR 日志，岗亭端可通过"无牌车处理"列表看到该临时车牌在场记录（虽然实际车辆未入场），操作员手动补开闸。
  - 需在监控中增加“AUTO_RELEASE 开闸失败”的告警指标。

### 风险 4：临时车牌被误作正式车牌查询

- **严重程度**：低
- **影响**：小程序用户搜索临时车牌号时可能看到误匹配结果。
- **缓解措施**：
  - 所有对外查询接口（小程序端/运营端）默认加 `temp_plate_flag=0` 过滤条件，仅岗亭端无牌车处理视图可展示临时车牌记录。
  - 正式车牌不会以"临"字开头，天然不容易混淆。

---

## 17. 决策总结

- 临时车牌通过 Redis INCR 按车场+日期维度生成，格式为"临"+yyMMdd+两位序号（如"临26071701"）。
- 仅 `plateNumber` 为空时触发识别失败流程，置信度低不触发。
- AUTO_RELEASE 策略：自动生成临时车牌、创建在场记录、开闸放行，不生成预订单。
- MANUAL 策略：推送 WebSocket 告警 + 创建 MonitorAlert 记录，由岗亭操作员决定处理。
- Consumer 改造为内联路由模式（`handleRecognitionFailure`），不新增独立 MQ 队列。
- RecognitionEventLog 新增 `RECOGNITION_FAILED` 状态（区别于 FAILED）。
- 三张表（parking_record、parking_order、recognition_event_log）新增 `temp_plate_flag TINYINT` 字段。
- 岗亭端提供三个 REST 接口：建议临时车牌（GET /suggest）、手动入场（POST /entry）、出场匹配（POST /exit-match）。
- 岗亭端出场默认走现金支付（payChannel=CASH），不支持小程序远程支付。
- 运营端通行记录列表展示临时车牌标签（"临" Badge）。
- 出场流程通过 `TempPlateService.handleExitMatch()` 完成完整链路（匹配 → 计费 → 现金订单 → 完成记录 → 开闸 → 出场记录）。

---

## 附录 A：RecognitionEventLog status 状态汇总

| status | 含义 | 触发条件 |
| :--- | :--- | :--- |
| `RECEIVED` | 已接收 | 事件落库时初始状态 |
| `PROCESSING` | 处理中 | Consumer 开始处理前更新 |
| `PROCESSED` | 处理成功 | 入场/出场处理完成 |
| `FAILED` | 处理失败（永久） | 设备/车道/停车场校验失败、DB 异常等 |
| `RECOGNITION_FAILED` | 识别失败已接管 | 空车牌事件 + MANUAL/AUTO_RELEASE 策略已执行 |

## 附录 B：Redis Key 设计

| Key 模式 | 示例值 | TTL |
| :--- | :--- | :--- |
| `temp_plate:{lotId}:{yyyyMMdd}` | `temp_plate:1001:20260717` | 至当日 23:59:59 |

## 附录 C：WebSocket 告警载荷格式

```json
{
  "type": "RECOGNITION_FAILED",
  "eventId": "uuid-string",
  "logId": 12345,
  "parkingLotId": 1001,
  "laneId": 2001,
  "direction": "ENTRY",
  "imagePath": "/captures/xxx.jpg",
  "eventTime": "2026-07-17 10:30:00",
  "message": "入口识别失败，请手动处理无牌车辆"
}
```
