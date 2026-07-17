package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.BillingRule;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.BillingRuleMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.service.MonitorAlertService;
import com.jushan.system.service.VehicleListService;
import com.jushan.system.vo.VehicleListDecisionVO;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.jushan.platform.modules.parking.dto.ParkingSessionEntryCmd;
import com.jushan.platform.modules.parking.service.ParkingSessionService;
import com.jushan.platform.modules.parking.vo.ParkingSessionVO;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;

import java.time.LocalDateTime;

/**
 * 入场服务（T30 + P003 重复入场策略）。
 * <p>
 * 在识别事件校验通过后，根据停车场配置的重复入场策略处理车辆入场：
 * <ol>
 *   <li>REJECT（默认）：同车同场已有 PARKING 记录 → 拒绝并记录异常</li>
 *   <li>UPDATE：同车同场已有 PARKING 记录 → 更新原记录入场信息</li>
 *   <li>EXCEPTION：同车同场已有 PARKING 记录 → 原记录标记异常，创建新记录</li>
 * </ol>
 * <p>
 * <strong>同车同场仅允许一条 PARKING 记录</strong>，
 * 由数据库功能唯一索引 {@code uk_active_parking} 提供最终保障。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>tenantId 和 parkingLotId 已由 Consumer 从可信设备记录推导</li>
 *   <li>车牌为 PlateStandardizer 标准化后的值</li>
 *   <li>禁用停车场检查 disableNewEntries 标志</li>
 *   <li>重复入场策略由停车场配置决定，未配置时默认 REJECT</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class EntryService {

    private static final Logger log = LoggerFactory.getLogger(EntryService.class);

    private final ParkingRecordMapper recordMapper;
    private final ParkingLotMapper parkingLotMapper;
    private final BillingRuleMapper ruleMapper;
    private final DuplicateEntryHandler duplicateEntryHandler;
    private final BoothWebSocketPublisher boothWebSocketPublisher;
    private final ParkingSessionService parkingSessionService;
    private final FixedSpaceService fixedSpaceService;
    private final ParkingOrderService parkingOrderService;
    private final VehicleTypeDecisionService vehicleTypeDecisionService;
    private final BillingEngine billingEngine;
    private final VehicleListService vehicleListService;
    private final MonitorAlertService monitorAlertService;

    public EntryService(ParkingRecordMapper recordMapper,
                        ParkingLotMapper parkingLotMapper,
                        BillingRuleMapper ruleMapper,
                        DuplicateEntryHandler duplicateEntryHandler,
                        BoothWebSocketPublisher boothWebSocketPublisher,
                        ParkingSessionService parkingSessionService,
                        FixedSpaceService fixedSpaceService,
                        ParkingOrderService parkingOrderService,
                        VehicleTypeDecisionService vehicleTypeDecisionService,
                        BillingEngine billingEngine,
                        VehicleListService vehicleListService,
                        MonitorAlertService monitorAlertService) {
        this.recordMapper = recordMapper;
        this.parkingLotMapper = parkingLotMapper;
        this.ruleMapper = ruleMapper;
        this.duplicateEntryHandler = duplicateEntryHandler;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
        this.parkingSessionService = parkingSessionService;
        this.fixedSpaceService = fixedSpaceService;
        this.parkingOrderService = parkingOrderService;
        this.vehicleTypeDecisionService = vehicleTypeDecisionService;
        this.billingEngine = billingEngine;
        this.vehicleListService = vehicleListService;
        this.monitorAlertService = monitorAlertService;
    }

    /**
     * 处理车辆入场。
     * <p>
     * 在事务内完成：检查重复入场 → 按策略处理 → 更新停车场容量。
     * 任意一步失败则整体回滚。
     *
     * @param payload           识别事件载荷（校验通过，tenantId/parkingLotId 已从可信记录推导）
     * @param standardizedPlate 标准化车牌号
     * @return 创建的或更新后的停车记录
     * @throws BusinessException 停车场检查不通过或 REJECT 策略拒绝
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingRecord handleEntry(RecognitionEventPayload payload, String standardizedPlate) {
        Long parkingLotId = payload.getParkingLotId();

        // 1. 停车场状态检查
        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        if (lot == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND,
                    "停车场不存在: parkingLotId=" + parkingLotId);
        }

        // 1a. 已停用的停车场禁止新车入场
        if ("DISABLED".equals(lot.getStatus())) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    "停车场已停用，不允许新车入场: " + lot.getName());
        }

        // 2. 检查是否已有活跃（PARKING）记录
        ParkingRecord existingRecord = duplicateEntryHandler.findActiveRecord(parkingLotId, standardizedPlate);

        ParkingRecord record;
        if (existingRecord != null) {
            // 2a. 存在活跃记录 → 按策略处理
            log.info("检测到重复入场: plate={} parkingLotId={} existingRecordId={} policy={}",
                    standardizedPlate, parkingLotId, existingRecord.getId(),
                    lot.getDuplicateEntryPolicy());
            record = duplicateEntryHandler.handle(lot, payload, standardizedPlate, existingRecord);
        } else {
            // 2b. 无活跃记录 → 正常创建
            record = createParkingRecord(payload, standardizedPlate);
        }

        // 2d. 新记录入场时快照当前生效的计费规则（NEW_ENTRY_ONLY 使用）
        if (isNewRecordCreated(record, existingRecord)) {
            snapshotRuleOnEntry(record);
        }

        // 2c. 检查固定车位绑定（MQ 消费者路径无车辆类型判定服务）
        String vehicleType = null;
        if (record != null && payload.getTenantId() != null) {
            boolean hasFixedSpace = fixedSpaceService.hasActiveBinding(
                    standardizedPlate, parkingLotId, payload.getTenantId());
            if (hasFixedSpace) {
                vehicleType = "FIXED_SPACE";
                log.info("固定车位车辆入场: plate={} parkingLotId={}", standardizedPlate, parkingLotId);
            }
        }

        // 2e. 黑白名单入场判定（任务包 3-3 新增）
        if (vehicleListService != null) {
            VehicleListDecisionVO listDecision = vehicleListService.checkEntry(
                    parkingLotId, standardizedPlate);

            if (listDecision.isDenyEntry()) {
                log.warn("黑名单车辆禁止入场: plate={} lotId={} listType={} triggerType={}",
                        standardizedPlate, parkingLotId, listDecision.getListType(),
                        listDecision.getTriggerType());
                throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                        listDecision.getReason());
            }

            if (listDecision.isAlert()) {
                log.warn("黑名单车辆允许入场但触发告警: plate={} lotId={} triggerType={}",
                        standardizedPlate, parkingLotId, listDecision.getTriggerType());
                if (monitorAlertService != null) {
                    monitorAlertService.createBlacklistEntryAlert(
                            payload.getTenantId(), parkingLotId, standardizedPlate,
                            listDecision.getTriggerType());
                }
            }
        }

        // 3. 同步创建 ParkingSession（在场记录），避免双写分裂
        if (isNewRecordCreated(record, existingRecord)) {
            createParkingSession(record, payload, vehicleType);
        }

        // 4. 更新停车场容量（仅当成功创建了新记录时）
        //    UPDATE 策略不增加容量（车辆数不变）
        //    EXCEPTION 策略增加容量（新记录）
        //    REJECT 策略不会到达此处（已抛异常）
        if (isNewRecordCreated(record, existingRecord)) {
            int updated = parkingLotMapper.update(null,
                    new LambdaUpdateWrapper<ParkingLot>()
                            .setSql("current_vehicles = current_vehicles + 1")
                            .setSql("remaining_spaces = remaining_spaces - 1")
                            .eq(ParkingLot::getId, parkingLotId));

            if (updated == 0) {
                log.warn("停车场容量更新影响0行（可能记录不存在）: parkingLotId={}", parkingLotId);
            } else {
                log.info("停车场容量已更新: parkingLotId={} entry={}",
                        parkingLotId, standardizedPlate);
                pushSpaceUpdate(parkingLotId);
            }
        } else {
            log.info("停车场容量未更新（重复入场策略处理，非新记录）: parkingLotId={} plate={}",
                    parkingLotId, standardizedPlate);
        }

        // 5. 临停车辆生成预订单（任务包 1-2）；
        //    月卡/固定车位/白名单等无需计费车辆仅生成通行记录，不建订单
        if (isNewRecordCreated(record, existingRecord)) {
            createPreOrderIfChargeable(record);
        }

        return record;
    }

    /**
     * 为需计费的临停车辆生成预订单（任务包 1-2）。
     * <p>
     * 口径与全链路一致：根据车辆类型判定，月卡（有效）/固定车位/白名单（VIP/SUPER/FREE）
     * 等 {@code needCharge=false} 的车辆不生成订单；临停/储值/过期月卡等需计费车辆生成 PRE_ORDER。
     * <p>
     * 车辆类型判定失败时采取 fail-safe：跳过预订单创建（避免错建），出场链路对无预订单有兼容建单逻辑。
     * 预订单插入与入场处于同一事务，保证一致性。
     */
    private void createPreOrderIfChargeable(ParkingRecord record) {
        VehicleTypeDecisionVO decision;
        try {
            decision = vehicleTypeDecisionService.decide(
                    record.getStandardizedPlate(),
                    record.getParkingLotId(),
                    record.getTenantId());
        } catch (Exception e) {
            // 判定失败不阻塞入场，也不错建预订单（出场走兼容建单）
            log.warn("入场车辆类型判定失败，跳过预订单创建: plate={} lotId={} error={}",
                    record.getStandardizedPlate(), record.getParkingLotId(), e.getMessage());
            return;
        }
        if (decision == null || !Boolean.TRUE.equals(decision.getNeedCharge())) {
            log.info("入场免建预订单（无需计费车辆 type={}）: plate={} lotId={}",
                    decision != null ? decision.getVehicleType() : "UNKNOWN",
                    record.getStandardizedPlate(), record.getParkingLotId());
            return;
        }
        ParkingOrder preOrder = parkingOrderService.createPreOrder(record);
        log.info("临停入场生成预订单: orderId={} plate={} lotId={}",
                preOrder.getId(), record.getStandardizedPlate(), record.getParkingLotId());
    }

    /**
     * 同步创建 ParkingSession，与 ParkingRecord 保持一致。
     *
     * @param vehicleType 车辆类型（可为 null，由后续服务判定；FIXED_SPACE 表示固定车位车辆）
     */
    private void createParkingSession(ParkingRecord record, RecognitionEventPayload payload, String vehicleType) {
        if (parkingSessionService == null) {
            return;
        }
        try {
            ParkingSessionEntryCmd cmd = new ParkingSessionEntryCmd();
            cmd.setTenantId(record.getTenantId());
            cmd.setParkingLotId(record.getParkingLotId());
            cmd.setLaneId(record.getLaneId());
            cmd.setPlateNumber(record.getStandardizedPlate());
            cmd.setPlateColor(null); // 识别事件暂未携带颜色
            cmd.setVehicleType(vehicleType);
            cmd.setEntryImage(record.getEntryImagePath());

            ParkingSessionVO session = parkingSessionService.entry(cmd);
            log.info("ParkingSession 已同步创建: sessionId={} recordId={} plate={}",
                    session.getId(), record.getId(), record.getStandardizedPlate());
        } catch (Exception e) {
            log.warn("ParkingSession 同步创建失败（不影响 ParkingRecord 主业务）: recordId={} error={}",
                    record.getId(), e.getMessage());
        }
    }

    /**
     * 推送车位变化到岗亭前端。
     */
    private void pushSpaceUpdate(Long parkingLotId) {
        if (boothWebSocketPublisher == null) {
            return;
        }
        try {
            ParkingLot updated = parkingLotMapper.selectById(parkingLotId);
            if (updated != null) {
                boothWebSocketPublisher.sendSpaceUpdate(
                        parkingLotId,
                        updated.getRemainingSpaces(),
                        updated.getCurrentVehicles(),
                        updated.getTotalSpaces());
            }
        } catch (Exception e) {
            log.warn("入场车位变化 WebSocket 推送失败（不影响主业务）: parkingLotId={}, error={}",
                    parkingLotId, e.getMessage());
        }
    }

    /**
     * 创建新的停车记录。
     * <p>
     * 功能唯一索引 uk_active_parking 防止并发重复插入。
     */
    private ParkingRecord createParkingRecord(RecognitionEventPayload payload, String standardizedPlate) {
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(payload.getTenantId());
        record.setParkingLotId(payload.getParkingLotId());
        record.setLaneId(payload.getLaneId());
        record.setDeviceId(payload.getDeviceId());
        record.setStandardizedPlate(standardizedPlate);
        record.setStatus("PARKING");
        record.setEntryTime(payload.getEventTime() != null
                ? payload.getEventTime() : LocalDateTime.now());
        // entryEventId 关联 recognition_event_log.id（自增 Long），
        // payload.eventId 是 UUID 字符串，两者不同，此处不设置
        record.setEntryImagePath(payload.getImagePath());

        try {
            recordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            // 并发下 uk_active_parking 命中：降级为 REJECT
            log.warn("并发重复入场，唯一索引保护生效: plate={} parkingLotId={}",
                    standardizedPlate, payload.getParkingLotId());
            throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                    String.format("该车辆已有在场记录: plate=%s parkingLotId=%d",
                            standardizedPlate, payload.getParkingLotId()));
        }

        log.info("停车记录已创建: recordId={} plate={} parkingLotId={}",
                record.getId(), standardizedPlate, payload.getParkingLotId());

        return record;
    }

    /**
     * 判断是否创建了新记录（需要更新容量）。
     * <p>
     * - REJECT：抛异常，不会到达此处
     * - UPDATE：未创建新记录（更新原记录），不更新容量
     * - EXCEPTION：创建了新记录，更新容量
     * - 正常入场：创建了新记录，更新容量
     */
    private boolean isNewRecordCreated(ParkingRecord result, ParkingRecord existingRecord) {
        if (result == null) {
            return false;
        }
        if (existingRecord == null) {
            // 正常入场，一定创建了新记录
            return true;
        }
        // 有 existingRecord 时：
        // - UPDATE 策略返回的是原记录（ID 相同）
        // - EXCEPTION 策略返回的是新记录（ID 不同）
        return !result.getId().equals(existingRecord.getId());
    }

    /**
     * 入场时快照当前生效的计费规则。
     * <p>
     * 若当前激活规则为 NEW_ENTRY_ONLY，则保存规则快照到 parking_record，
     * 供出场计费时使用（已在场车辆按入场时的规则计算）。
     */
    private void snapshotRuleOnEntry(ParkingRecord record) {
        try {
            BillingRuleVersion activeVersion = billingEngine.findActiveVersion(record.getParkingLotId());
            if (activeVersion == null) {
                log.info("入场无生效计费规则，跳过快照: recordId={}", record.getId());
                return;
            }
            BillingRule rule = ruleMapper.selectById(activeVersion.getRuleId());
            if (rule == null) {
                return;
            }
            String snapshot = billingEngine.buildSnapshot(activeVersion, rule.getRuleType());
            record.setRuleSnapshot(snapshot);
            recordMapper.updateById(record);
            log.info("入场规则快照已保存: recordId={} effectType={}", record.getId(), rule.getEffectType());
        } catch (Exception e) {
            log.warn("入场规则快照失败（不影响入场）: recordId={} error={}", record.getId(), e.getMessage());
        }
    }
}
