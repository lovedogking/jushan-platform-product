package com.jushan.platform.modules.parking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.parking.entity.ParkingRecordSyncLog;
import com.jushan.platform.modules.parking.entity.PayMerchantConfig;
import com.jushan.platform.modules.parking.mapper.ParkingRecordMapper;
import com.jushan.platform.modules.parking.mapper.ParkingRecordSyncLogMapper;
import com.jushan.platform.modules.parking.mapper.PayMerchantConfigMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * P云停车记录同步服务（Sprint 8）。
 * <p>
 * 负责将停车场入场/离场/更新记录同步到 P云开放平台：
 * <ul>
 *   <li>推送车辆入场 /gate/1.0/parking/internal/enter</li>
 *   <li>推送车辆离场 /gate/1.0/parking/internal/leave</li>
 *   <li>推送停车信息更新 /gate/1.0/parking/internal/update</li>
 * </ul>
 * <p>
 * 同步策略：异步发送 + 失败重试 + 幂等（parking_serial 唯一）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class PyunParkingSyncService {

    private static final Logger log = LoggerFactory.getLogger(PyunParkingSyncService.class);

    private static final String BASE_URL = "https://api.4pyun.com";
    private static final String ENTER_URL = "/gate/1.0/parking/internal/enter";
    private static final String LEAVE_URL = "/gate/1.0/parking/internal/leave";
    private static final String UPDATE_URL = "/gate/1.0/parking/internal/update";

    private final PyunPaymentClient pyunClient;
    private final ParkingRecordMapper parkingRecordMapper;
    private final ParkingRecordSyncLogMapper syncLogMapper;
    private final PayMerchantConfigMapper merchantConfigMapper;
    private final ObjectMapper objectMapper;

    public PyunParkingSyncService(PyunPaymentClient pyunClient,
                                   ParkingRecordMapper parkingRecordMapper,
                                   ParkingRecordSyncLogMapper syncLogMapper,
                                   PayMerchantConfigMapper merchantConfigMapper,
                                   ObjectMapper objectMapper) {
        this.pyunClient = pyunClient;
        this.parkingRecordMapper = parkingRecordMapper;
        this.syncLogMapper = syncLogMapper;
        this.merchantConfigMapper = merchantConfigMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步推送车辆入场记录到 P云。
     *
     * @param record 停车记录
     */
    @Async("taskExecutor")
    public void syncEnter(ParkingRecord record) {
        if (record == null) return;

        PayMerchantConfig config = getMerchantConfig(record.getParkingLotId());
        if (config == null) {
            log.debug("停车场未配置P云商户，跳过入场同步: parkingLotId={}", record.getParkingLotId());
            return;
        }

        String parkingSerial = String.valueOf(record.getId());

        // 幂等检查
        if (isAlreadySynced(record.getId(), "ENTER")) {
            log.debug("入场记录已同步，跳过: recordId={}", record.getId());
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("park_uuid", config.getParkUuid());
        params.put("parking_serial", parkingSerial);
        params.put("plate", record.getStandardizedPlate());
        params.put("plate_color", "-1");
        params.put("enter_time", String.valueOf(record.getEntryTime().toEpochSecond(java.time.ZoneOffset.ofHours(8)) * 1000));
        params.put("enter_gate", record.getLaneId() != null ? String.valueOf(record.getLaneId()) : "001");
        params.put("car_type", "1");
        params.put("car_desc", "临停车辆");
        params.put("charge_type", "1");

        // 记录同步日志
        ParkingRecordSyncLog syncLog = createSyncLog(record, "ENTER", config.getParkUuid(), parkingSerial, params);

        try {
            JsonNode response = pyunClient.postForm(BASE_URL + ENTER_URL, config, params);
            String code = response.path("code").asText("");

            if ("200".equals(code) || "1000".equals(code) || "1001".equals(code)) {
                syncLog.setStatus(ParkingRecordSyncLog.STATUS_SUCCESS);
                syncLog.setSyncedAt(LocalDateTime.now());
                log.info("P云入场同步成功: recordId={} parkingSerial={}", record.getId(), parkingSerial);
            } else {
                syncLog.setStatus(ParkingRecordSyncLog.STATUS_FAILED);
                syncLog.setResponseCode(code);
                syncLog.setResponseMsg(response.path("message").asText(""));
                log.warn("P云入场同步失败: recordId={} code={} message={}",
                        record.getId(), code, response.path("message").asText());
            }
            syncLog.setResponseRaw(response.toString());
        } catch (Exception e) {
            syncLog.setStatus(ParkingRecordSyncLog.STATUS_FAILED);
            syncLog.setResponseMsg(e.getMessage());
            log.error("P云入场同步异常: recordId={}", record.getId(), e);
        }

        syncLogMapper.insert(syncLog);
    }

    /**
     * 异步推送车辆离场记录到 P云。
     *
     * @param record 停车记录
     * @param feeCents 费用（分）
     * @param payValue 实付金额（分）
     */
    @Async("taskExecutor")
    public void syncLeave(ParkingRecord record, int feeCents, int payValue) {
        if (record == null) return;

        PayMerchantConfig config = getMerchantConfig(record.getParkingLotId());
        if (config == null) {
            log.debug("停车场未配置P云商户，跳过离场同步: parkingLotId={}", record.getParkingLotId());
            return;
        }

        String parkingSerial = String.valueOf(record.getId());

        // 幂等检查
        if (isAlreadySynced(record.getId(), "LEAVE")) {
            log.debug("离场记录已同步，跳过: recordId={}", record.getId());
            return;
        }

        Map<String, String> params = new HashMap<>();
        params.put("park_uuid", config.getParkUuid());
        params.put("parking_serial", parkingSerial);
        params.put("plate", record.getStandardizedPlate());
        params.put("plate_color", "-1");
        params.put("enter_time", String.valueOf(record.getEntryTime().toEpochSecond(java.time.ZoneOffset.ofHours(8)) * 1000));
        params.put("leave_time", String.valueOf(record.getExitTime() != null ? record.getExitTime().toEpochSecond(java.time.ZoneOffset.ofHours(8)) * 1000 : System.currentTimeMillis()));
        params.put("leave_gate", record.getLaneId() != null ? String.valueOf(record.getLaneId()) : "001");
        params.put("car_type", "1");
        params.put("car_desc", "临停车辆");
        params.put("charge_type", "1");
        params.put("total_value", String.valueOf(feeCents));
        params.put("free_value", "0");
        params.put("pay_value", String.valueOf(payValue));

        ParkingRecordSyncLog syncLog = createSyncLog(record, "LEAVE", config.getParkUuid(), parkingSerial, params);

        try {
            JsonNode response = pyunClient.postForm(BASE_URL + LEAVE_URL, config, params);
            String code = response.path("code").asText("");

            if ("200".equals(code) || "1000".equals(code) || "1001".equals(code)) {
                syncLog.setStatus(ParkingRecordSyncLog.STATUS_SUCCESS);
                syncLog.setSyncedAt(LocalDateTime.now());
                log.info("P云离场同步成功: recordId={} parkingSerial={}", record.getId(), parkingSerial);
            } else {
                syncLog.setStatus(ParkingRecordSyncLog.STATUS_FAILED);
                syncLog.setResponseCode(code);
                syncLog.setResponseMsg(response.path("message").asText(""));
                log.warn("P云离场同步失败: recordId={} code={}", record.getId(), code);
            }
            syncLog.setResponseRaw(response.toString());
        } catch (Exception e) {
            syncLog.setStatus(ParkingRecordSyncLog.STATUS_FAILED);
            syncLog.setResponseMsg(e.getMessage());
            log.error("P云离场同步异常: recordId={}", record.getId(), e);
        }

        syncLogMapper.insert(syncLog);
    }

    /**
     * 推送停车信息更新到 P云。
     *
     * @param record 停车记录
     * @param reason 更新原因
     */
    @Async("taskExecutor")
    public void syncUpdate(ParkingRecord record, String reason) {
        if (record == null) return;

        PayMerchantConfig config = getMerchantConfig(record.getParkingLotId());
        if (config == null) return;

        String parkingSerial = String.valueOf(record.getId());
        String flowNo = "U" + System.currentTimeMillis();

        Map<String, String> params = new HashMap<>();
        params.put("park_uuid", config.getParkUuid());
        params.put("flow_no", flowNo);
        params.put("parking_serial", parkingSerial);
        params.put("plate", record.getStandardizedPlate());
        params.put("operate_time", LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
        params.put("operator", "system");
        params.put("reason", reason);

        ParkingRecordSyncLog syncLog = createSyncLog(record, "UPDATE", config.getParkUuid(), parkingSerial, params);
        syncLog.setRequestRaw(params.toString());

        try {
            JsonNode response = pyunClient.postForm(BASE_URL + UPDATE_URL, config, params);
            String code = response.path("code").asText("");

            if ("1001".equals(code)) {
                syncLog.setStatus(ParkingRecordSyncLog.STATUS_SUCCESS);
                syncLog.setSyncedAt(LocalDateTime.now());
            } else {
                syncLog.setStatus(ParkingRecordSyncLog.STATUS_FAILED);
                syncLog.setResponseCode(code);
            }
            syncLog.setResponseRaw(response.toString());
        } catch (Exception e) {
            syncLog.setStatus(ParkingRecordSyncLog.STATUS_FAILED);
            syncLog.setResponseMsg(e.getMessage());
        }

        syncLogMapper.insert(syncLog);
    }

    // ==================== 内部方法 ====================

    private PayMerchantConfig getMerchantConfig(Long parkingLotId) {
        // 简化为查询第一个 ACTIVE 配置
        return merchantConfigMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<PayMerchantConfig>()
                        .eq("parking_lot_id", parkingLotId)
                        .eq("status", "ACTIVE")
                        .isNull("deleted_at")
                        .last("LIMIT 1")).stream().findFirst().orElse(null);
    }

    private boolean isAlreadySynced(Long recordId, String syncType) {
        Long count = syncLogMapper.selectCount(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ParkingRecordSyncLog>()
                        .eq("parking_record_id", recordId)
                        .eq("sync_type", syncType)
                        .eq("status", ParkingRecordSyncLog.STATUS_SUCCESS));
        return count != null && count > 0;
    }

    private ParkingRecordSyncLog createSyncLog(ParkingRecord record, String syncType,
                                                String parkUuid, String parkingSerial,
                                                Map<String, String> params) {
        ParkingRecordSyncLog log = new ParkingRecordSyncLog();
        log.setTenantId(record.getTenantId());
        log.setParkingLotId(record.getParkingLotId());
        log.setParkingRecordId(record.getId());
        log.setSyncType(syncType);
        log.setParkUuid(parkUuid);
        log.setParkingSerial(parkingSerial);
        log.setPlate(record.getStandardizedPlate());
        log.setStatus(ParkingRecordSyncLog.STATUS_PENDING);
        log.setRetryCount(0);
        try {
            log.setRequestRaw(objectMapper.writeValueAsString(params));
        } catch (Exception e) {
            log.setRequestRaw(params.toString());
        }
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        return log;
    }
}
