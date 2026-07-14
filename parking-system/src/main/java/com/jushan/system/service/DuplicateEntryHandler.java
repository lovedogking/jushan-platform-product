package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.entity.DuplicateEntryLog;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.DuplicateEntryLogMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 重复入场策略处理器（P003）。
 * <p>
 * 根据停车场配置的 {@link DuplicateEntryPolicy} 处理同一车辆
     * 在已有未结停车记录时的再次入场识别。
 * <p>
 * <strong>并发安全</strong>：
 * <ul>
 *   <li>REJECT 策略依赖 {@code uk_active_parking} 功能唯一索引作为最终保障</li>
 *   <li>UPDATE 策略使用条件更新（WHERE status = 'PARKING'）防止并发覆盖</li>
 *   <li>EXCEPTION 策略使用条件更新将原记录改为 EXCEPTION，再插入新记录</li>
 * </ul>
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>所有操作在事务内完成</li>
 *   <li>不直接信任前端传入的任何 ID</li>
 *   <li>tenantId 和 parkingLotId 从可信记录推导</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class DuplicateEntryHandler {

    private static final Logger log = LoggerFactory.getLogger(DuplicateEntryHandler.class);

    private final ParkingRecordMapper recordMapper;
    private final DuplicateEntryLogMapper duplicateEntryLogMapper;

    public DuplicateEntryHandler(ParkingRecordMapper recordMapper,
                                  DuplicateEntryLogMapper duplicateEntryLogMapper) {
        this.recordMapper = recordMapper;
        this.duplicateEntryLogMapper = duplicateEntryLogMapper;
    }

    /**
     * 记录重复入场审计日志（独立事务，不受外层事务回滚影响）。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeAuditLog(DuplicateEntryLog logEntry) {
        duplicateEntryLogMapper.insert(logEntry);
    }

    /**
     * 执行重复入场策略。
     * <p>
     * 调用方（EntryService）已确认：
     * <ol>
     *   <li>停车场存在且已校验通过</li>
     *   <li>该车牌在当前停车场已存在 PARKING 记录</li>
     *   <li>payload 和 standardizedPlate 已校验</li>
     * </ol>
     *
     * @param lot               停车场（含策略配置）
     * @param payload           识别事件载荷
     * @param standardizedPlate 标准化车牌号
     * @param existingRecord    已存在的 PARKING 记录（查询结果，可能为 null 在并发下）
     * @return 处理结果（可能为原记录更新后、新记录、或 null 表示拒绝）
     * @throws BusinessException 策略执行失败
     */
    @Transactional(rollbackFor = Exception.class)
    public ParkingRecord handle(ParkingLot lot, RecognitionEventPayload payload,
                                 String standardizedPlate, ParkingRecord existingRecord) {
        DuplicateEntryPolicy policy = resolvePolicy(lot);

        log.info("重复入场处理: plate={} parkingLotId={} policy={} existingRecordId={}",
                standardizedPlate, lot.getId(), policy,
                existingRecord != null ? existingRecord.getId() : "null");

        return switch (policy) {
            case REJECT -> handleReject(lot, payload, standardizedPlate, existingRecord);
            case UPDATE -> handleUpdate(lot, payload, standardizedPlate, existingRecord);
            case EXCEPTION -> handleException(lot, payload, standardizedPlate, existingRecord);
        };
    }

    // ==================== REJECT 策略 ====================

    /**
     * 拒绝策略：记录异常日志，抛出业务异常。
     * <p>
     * 保留现有行为，事件日志已由 Consumer 记录，此处记录到 duplicate_entry_log
     * 供运营人员查看。
     */
    private ParkingRecord handleReject(ParkingLot lot, RecognitionEventPayload payload,
                                        String standardizedPlate, ParkingRecord existingRecord) {
        // 记录到异常日志表
        DuplicateEntryLog logEntry = new DuplicateEntryLog();
        logEntry.setTenantId(payload.getTenantId());
        logEntry.setParkingLotId(lot.getId());
        logEntry.setLaneId(payload.getLaneId());
        logEntry.setDeviceId(payload.getDeviceId());
        logEntry.setStandardizedPlate(standardizedPlate);
        logEntry.setExistingRecordId(existingRecord != null ? existingRecord.getId() : 0L);
        logEntry.setStrategy("REJECT");
        logEntry.setAction("REJECTED");
        logEntry.setReason("该车辆已有在场记录，按 REJECT 策略拒绝");
        // entryEventId 关联 recognition_event_log.id（自增 Long），
        // payload.eventId 是 UUID 字符串，不设置此字段
        logEntry.setImagePath(payload.getImagePath());
        logEntry.setConfidence(payload.getConfidence());
        logEntry.setCreatedAt(LocalDateTime.now());
        writeAuditLog(logEntry);

        log.warn("重复入场已拒绝(REJECT): plate={} parkingLotId={} existingRecordId={}",
                standardizedPlate, lot.getId(),
                existingRecord != null ? existingRecord.getId() : "null");

        throw new BusinessException(CommonErrorCode.PARAM_ERROR,
                String.format("该车辆已有在场记录: plate=%s parkingLotId=%d", standardizedPlate, lot.getId()));
    }

    // ==================== UPDATE 策略 ====================

    /**
     * 更新策略：更新原记录的入场时间、车道、设备、事件 ID、抓拍图片。
     * <p>
     * 使用条件更新防止并发竞态：WHERE id = ? AND status = 'PARKING'。
     * 如果条件更新影响 0 行（说明原记录已被并发处理），则降级为创建新记录。
     */
    private ParkingRecord handleUpdate(ParkingLot lot, RecognitionEventPayload payload,
                                        String standardizedPlate, ParkingRecord existingRecord) {
        if (existingRecord == null) {
            // 并发下原记录可能已不存在（被其他线程处理），降级为创建新记录
            log.warn("UPDATE策略: 原记录不存在（并发），降级为创建新记录: plate={}", standardizedPlate);
            return createNewRecord(payload, standardizedPlate);
        }

        // 条件更新：仅当记录仍为 PARKING 时才更新
        // entryEventId 关联 recognition_event_log.id（自增 Long），
        // payload.eventId 是 UUID 字符串，不更新此字段
        LambdaUpdateWrapper<ParkingRecord> wrapper = new LambdaUpdateWrapper<ParkingRecord>()
                .set(ParkingRecord::getEntryTime, payload.getEventTime() != null
                        ? payload.getEventTime() : LocalDateTime.now())
                .set(ParkingRecord::getLaneId, payload.getLaneId())
                .set(ParkingRecord::getDeviceId, payload.getDeviceId())
                .set(ParkingRecord::getEntryImagePath, payload.getImagePath())
                .set(ParkingRecord::getUpdatedAt, LocalDateTime.now())
                .eq(ParkingRecord::getId, existingRecord.getId())
                .eq(ParkingRecord::getStatus, "PARKING");

        int updated = recordMapper.update(null, wrapper);

        if (updated == 0) {
            // 并发下原记录状态已变更，降级为创建新记录
            log.warn("UPDATE策略: 条件更新失败（记录状态已变更），降级为创建新记录: plate={} recordId={}",
                    standardizedPlate, existingRecord.getId());
            return createNewRecord(payload, standardizedPlate);
        }

        // 记录审计日志
        DuplicateEntryLog logEntry = new DuplicateEntryLog();
        logEntry.setTenantId(payload.getTenantId());
        logEntry.setParkingLotId(lot.getId());
        logEntry.setLaneId(payload.getLaneId());
        logEntry.setDeviceId(payload.getDeviceId());
        logEntry.setStandardizedPlate(standardizedPlate);
        logEntry.setExistingRecordId(existingRecord.getId());
        logEntry.setStrategy("UPDATE");
        logEntry.setAction("UPDATED");
        logEntry.setReason("重复入场，按 UPDATE 策略更新原记录入场信息");
        // entryEventId 关联 recognition_event_log.id（自增 Long），
        // payload.eventId 是 UUID 字符串，不设置此字段
        logEntry.setImagePath(payload.getImagePath());
        logEntry.setConfidence(payload.getConfidence());
        logEntry.setCreatedAt(LocalDateTime.now());
        writeAuditLog(logEntry);

        log.info("重复入场已更新(UPDATE): plate={} recordId={} newEntryTime={}",
                standardizedPlate, existingRecord.getId(), payload.getEventTime());

        // 返回更新后的记录（重新查询）
        return recordMapper.selectById(existingRecord.getId());
    }

    // ==================== EXCEPTION 策略 ====================

    /**
     * 异常策略：将原记录标记为 EXCEPTION，创建新的 PARKING 记录。
     * <p>
     * 原记录保留供运营人员核查，新记录正常入场。
     * 使用条件更新将原记录改为 EXCEPTION，防止并发覆盖。
     */
    private ParkingRecord handleException(ParkingLot lot, RecognitionEventPayload payload,
                                           String standardizedPlate, ParkingRecord existingRecord) {
        if (existingRecord != null) {
            // 条件更新：将原记录标记为 EXCEPTION
            LambdaUpdateWrapper<ParkingRecord> wrapper = new LambdaUpdateWrapper<ParkingRecord>()
                    .set(ParkingRecord::getStatus, "EXCEPTION")
                    .set(ParkingRecord::getUpdatedAt, LocalDateTime.now())
                    .eq(ParkingRecord::getId, existingRecord.getId())
                    .eq(ParkingRecord::getStatus, "PARKING");

            int updated = recordMapper.update(null, wrapper);

            if (updated == 0) {
                log.warn("EXCEPTION策略: 原记录状态已变更，跳过标记: plate={} recordId={}",
                        standardizedPlate, existingRecord.getId());
            } else {
                log.info("EXCEPTION策略: 原记录已标记为 EXCEPTION: plate={} recordId={}",
                        standardizedPlate, existingRecord.getId());
            }
        }

        // 创建新的 PARKING 记录
        ParkingRecord newRecord = createNewRecord(payload, standardizedPlate);

        // 记录审计日志
        DuplicateEntryLog logEntry = new DuplicateEntryLog();
        logEntry.setTenantId(payload.getTenantId());
        logEntry.setParkingLotId(lot.getId());
        logEntry.setLaneId(payload.getLaneId());
        logEntry.setDeviceId(payload.getDeviceId());
        logEntry.setStandardizedPlate(standardizedPlate);
        logEntry.setExistingRecordId(existingRecord != null ? existingRecord.getId() : 0L);
        logEntry.setStrategy("EXCEPTION");
        logEntry.setAction("EXCEPTION_CREATED");
        logEntry.setReason("重复入场，按 EXCEPTION 策略创建新记录，原记录标记异常待处理");
        // entryEventId 关联 recognition_event_log.id（自增 Long），
        // payload.eventId 是 UUID 字符串，不设置此字段
        logEntry.setImagePath(payload.getImagePath());
        logEntry.setConfidence(payload.getConfidence());
        logEntry.setCreatedAt(LocalDateTime.now());
        writeAuditLog(logEntry);

        log.info("重复入场已处理(EXCEPTION): plate={} newRecordId={} oldRecordId={}",
                standardizedPlate, newRecord.getId(),
                existingRecord != null ? existingRecord.getId() : "null");

        return newRecord;
    }

    // ==================== 私有工具方法 ====================

    /**
     * 解析停车场的重复入场策略。
     * <p>
     * 未配置或非法值时默认使用 REJECT（安全默认）。
     */
    private DuplicateEntryPolicy resolvePolicy(ParkingLot lot) {
        String policyStr = lot.getDuplicateEntryPolicy();
        if (policyStr == null || policyStr.isBlank()) {
            return DuplicateEntryPolicy.REJECT;
        }
        try {
            return DuplicateEntryPolicy.valueOf(policyStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("停车场配置了非法的重复入场策略: parkingLotId={} policy={}，使用默认 REJECT",
                    lot.getId(), policyStr);
            return DuplicateEntryPolicy.REJECT;
        }
    }

    /**
     * 创建新的 PARKING 记录。
     * <p>
     * 调用方负责在事务内执行。可能抛出 DuplicateKeyException（uk_active_parking）。
     */
    private ParkingRecord createNewRecord(RecognitionEventPayload payload, String standardizedPlate) {
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
        // payload.eventId 是 UUID 字符串，此处不设置
        record.setEntryImagePath(payload.getImagePath());

        recordMapper.insert(record);
        return record;
    }

    /**
     * 查询指定车牌在指定停车场的活跃（PARKING）记录。
     * <p>
     * 正常情况下应返回 0 或 1 条记录（由 uk_active_parking 保证）。
     *
     * @param parkingLotId      停车场 ID
     * @param standardizedPlate 标准化车牌号
     * @return 存在的 PARKING 记录，或 null
     */
    public ParkingRecord findActiveRecord(Long parkingLotId, String standardizedPlate) {
        List<ParkingRecord> records = recordMapper.selectList(
                new LambdaQueryWrapper<ParkingRecord>()
                        .eq(ParkingRecord::getParkingLotId, parkingLotId)
                        .eq(ParkingRecord::getStandardizedPlate, standardizedPlate)
                        .eq(ParkingRecord::getStatus, "PARKING")
                        .orderByDesc(ParkingRecord::getEntryTime)
                        .last("LIMIT 1"));
        return records.isEmpty() ? null : records.get(0);
    }
}
