package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.auth.DataScope;
import com.jushan.system.entity.MonitorAlert;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.mapper.MonitorAlertMapper;
import com.jushan.system.vo.MonitorAlertVO;
import com.jushan.system.ws.BoothWebSocketPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗亭监控异常提醒服务（P005）。
 * <p>
 * 负责异常规则的检测、持久化、确认和推送。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class MonitorAlertService {

    private static final Logger log = LoggerFactory.getLogger(MonitorAlertService.class);

    /** 未确认的异常提醒查询上限。 */
    private static final int MAX_UNACKNOWLEDGED_ALERTS = 50;

    private final MonitorAlertMapper alertMapper;
    private final ParkingLotScopeResolver scopeResolver;
    private final BoothWebSocketPublisher boothWebSocketPublisher;

    public MonitorAlertService(MonitorAlertMapper alertMapper,
                                ParkingLotScopeResolver scopeResolver,
                                BoothWebSocketPublisher boothWebSocketPublisher) {
        this.alertMapper = alertMapper;
        this.scopeResolver = scopeResolver;
        this.boothWebSocketPublisher = boothWebSocketPublisher;
    }

    /**
     * 查询指定停车场的未确认异常提醒。
     *
     * @param parkingLotId 停车场 ID
     * @return 未确认异常提醒列表
     */
    public List<MonitorAlert> findUnacknowledged(Long parkingLotId) {
        scopeResolver.validateAccess(parkingLotId);

        return alertMapper.selectList(
                new LambdaQueryWrapper<MonitorAlert>()
                        .eq(MonitorAlert::getParkingLotId, parkingLotId)
                        .eq(MonitorAlert::getAcknowledged, 0)
                        .orderByDesc(MonitorAlert::getCreatedAt)
                        .last("LIMIT " + MAX_UNACKNOWLEDGED_ALERTS));
    }

    /**
     * 确认异常提醒。
     *
     * @param alertId 提醒 ID
     */
    @Transactional
    public void acknowledge(Long alertId) {
        MonitorAlert alert = alertMapper.selectById(alertId);
        if (alert == null) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "异常提醒不存在");
        }

        scopeResolver.validateAccess(alert.getParkingLotId());
        DataScope.validateTenantMatch(alert.getTenantId(), "异常提醒");

        alert.setAcknowledged(1);
        alert.setAcknowledgedAt(LocalDateTime.now());
        alertMapper.updateById(alert);

        log.info("异常提醒已确认: alertId={}, parkingLotId={}", alertId, alert.getParkingLotId());
    }

    /**
     * 创建设备离线提醒。
     *
     * @param lot        停车场
     * @param deviceId   设备 ID
     * @param deviceName 设备名称
     * @param reason     离线原因
     */
    @Transactional
    public void createDeviceOfflineAlert(ParkingLot lot, Long deviceId, String deviceName, String reason) {
        if (existsUnacknowledged(lot.getId(), MonitorAlert.TYPE_DEVICE_OFFLINE, String.valueOf(deviceId))) {
            return;
        }
        MonitorAlert alert = createAlert(lot, MonitorAlert.TYPE_DEVICE_OFFLINE,
                MonitorAlert.SEVERITY_CRITICAL, String.valueOf(deviceId),
                String.format("设备离线: %s (%s)", deviceName, reason));
        saveAndPush(alert);
    }

    /**
     * 创建车位已满提醒。
     *
     * @param lot 停车场
     */
    @Transactional
    public void createLotFullAlert(ParkingLot lot) {
        if (existsUnacknowledged(lot.getId(), MonitorAlert.TYPE_LOT_FULL, null)) {
            return;
        }
        MonitorAlert alert = createAlert(lot, MonitorAlert.TYPE_LOT_FULL,
                MonitorAlert.SEVERITY_WARNING, null,
                String.format("停车场车位已满: %s", lot.getName()));
        saveAndPush(alert);
    }

    /**
     * 创建停车场停用提醒。
     *
     * @param lot 停车场
     */
    @Transactional
    public void createLotDisabledAlert(ParkingLot lot) {
        if (existsUnacknowledged(lot.getId(), MonitorAlert.TYPE_LOT_DISABLED, null)) {
            return;
        }
        MonitorAlert alert = createAlert(lot, MonitorAlert.TYPE_LOT_DISABLED,
                MonitorAlert.SEVERITY_CRITICAL, null,
                String.format("停车场已停用: %s", lot.getName()));
        saveAndPush(alert);
    }

    /**
     * 创建识别失败提醒。
     *
     * @param lot   停车场
     * @param event 识别事件日志
     */
    @Transactional
    public void createRecognitionFailAlert(ParkingLot lot, RecognitionEventLog event) {
        MonitorAlert alert = createAlert(lot, MonitorAlert.TYPE_RECOGNITION_FAIL,
                MonitorAlert.SEVERITY_WARNING,
                event.getId() != null ? String.valueOf(event.getId()) : event.getEventId(),
                String.format("识别事件处理失败: %s, 原因: %s",
                        event.getPlateNumber() != null ? event.getPlateNumber() : "未知车牌",
                        event.getFailureReason() != null ? event.getFailureReason() : "未知原因"));
        saveAndPush(alert);
    }

    /**
     * 创建超时停放告警（系统自动拉黑前触发）。
     *
     * @param tenantId     租户 ID
     * @param parkingLotId 停车场 ID
     * @param plateNumber  车牌号
     * @param message      告警描述
     */
    @Transactional
    public void createOverstayAlert(Long tenantId, Long parkingLotId, String plateNumber, String message) {
        if (existsUnacknowledged(parkingLotId, MonitorAlert.TYPE_OVERSTAY, plateNumber)) {
            return;
        }
        MonitorAlert alert = new MonitorAlert();
        alert.setTenantId(tenantId);
        alert.setParkingLotId(parkingLotId);
        alert.setAlertType(MonitorAlert.TYPE_OVERSTAY);
        alert.setSeverity(MonitorAlert.SEVERITY_WARNING);
        alert.setSourceId(plateNumber);
        alert.setMessage(message);
        alert.setAcknowledged(0);
        alert.setCreatedAt(LocalDateTime.now());
        saveAndPush(alert);
    }

    /**
     * 创建开闸/设备异常告警。
     * <p>
     * 用于开闸失败、设备配置缺失、UNCERTAIN 等场景。
     * 一期仅持久化并推送，不触发自动修复。
     *
     * @param tenantId     租户 ID
     * @param parkingLotId 停车场 ID
     * @param laneId       车道 ID
     * @param deviceSn     设备 SN（可为 null）
     * @param alertType    告警类型（如 DEVICE_CONFIG_MISSING, DEVICE_FAULT, DEVICE_UNCERTAIN）
     * @param message      告警描述
     */
    @Transactional
    public void createGateAlert(Long tenantId, Long parkingLotId, Long laneId, String deviceSn,
                                 String alertType, String message) {
        MonitorAlert alert = new MonitorAlert();
        alert.setTenantId(tenantId);
        alert.setParkingLotId(parkingLotId);
        alert.setAlertType(alertType);
        alert.setSeverity(MonitorAlert.SEVERITY_WARNING);
        alert.setSourceId(deviceSn != null ? deviceSn : String.valueOf(laneId));
        alert.setMessage(message);
        alert.setAcknowledged(0);
        alert.setCreatedAt(LocalDateTime.now());
        saveAndPush(alert);
    }

    /**
     * 根据设备状态快照检查是否需要生成离线提醒。
     *
     * @param lot      停车场
     * @param deviceId 设备 ID
     * @param vo       设备状态视图
     */
    public void checkDeviceOffline(ParkingLot lot, Long deviceId, com.jushan.system.vo.DeviceStatusVO vo) {
        if (lot == null || vo == null) {
            return;
        }
        boolean offline = Boolean.FALSE.equals(vo.getOnline())
                || Boolean.FALSE.equals(vo.getLastQuerySuccess())
                || Boolean.TRUE.equals(vo.getStale());
        if (offline) {
            String reason = Boolean.TRUE.equals(vo.getStale())
                    ? "快照已过期"
                    : (Boolean.FALSE.equals(vo.getLastQuerySuccess())
                            ? "状态查询失败: " + vo.getLastErrorCode()
                            : "设备离线");
            createDeviceOfflineAlert(lot, deviceId, vo.getDeviceName(), reason);
        }
    }

    // ==================== 私有方法 ====================

    private boolean existsUnacknowledged(Long parkingLotId, String alertType, String sourceId) {
        LambdaQueryWrapper<MonitorAlert> wrapper = new LambdaQueryWrapper<MonitorAlert>()
                .eq(MonitorAlert::getParkingLotId, parkingLotId)
                .eq(MonitorAlert::getAlertType, alertType)
                .eq(MonitorAlert::getAcknowledged, 0);
        if (sourceId != null) {
            wrapper.eq(MonitorAlert::getSourceId, sourceId);
        }
        return alertMapper.selectCount(wrapper) > 0;
    }

    private MonitorAlert createAlert(ParkingLot lot, String alertType, String severity,
                                      String sourceId, String message) {
        MonitorAlert alert = new MonitorAlert();
        alert.setTenantId(lot.getTenantId());
        alert.setParkingLotId(lot.getId());
        alert.setAlertType(alertType);
        alert.setSeverity(severity);
        alert.setSourceId(sourceId);
        alert.setMessage(message);
        alert.setAcknowledged(0);
        alert.setCreatedAt(LocalDateTime.now());
        return alert;
    }

    private void saveAndPush(MonitorAlert alert) {
        alertMapper.insert(alert);
        if (boothWebSocketPublisher != null) {
            boothWebSocketPublisher.sendAlert(alert.getParkingLotId(), alert);
        }
        log.info("异常提醒已生成并推送: alertType={}, parkingLotId={}, message={}",
                alert.getAlertType(), alert.getParkingLotId(), alert.getMessage());
    }

    // ==================== VO 转换 ====================

    /**
     * 转换为视图对象。
     */
    public static MonitorAlertVO toVO(MonitorAlert alert) {
        MonitorAlertVO vo = new MonitorAlertVO();
        vo.setId(alert.getId());
        vo.setAlertType(alert.getAlertType());
        vo.setSeverity(alert.getSeverity());
        vo.setSourceId(alert.getSourceId());
        vo.setMessage(alert.getMessage());
        vo.setCreatedAt(alert.getCreatedAt());
        return vo;
    }
}
