package com.jushan.platform.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.entity.ParkingSession;
import com.jushan.platform.modules.parking.mapper.AccessPolicyMapper;
import com.jushan.platform.modules.parking.mapper.ParkingSessionMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.system.entity.SysAuditLog;
import com.jushan.system.mapper.SysAuditLogMapper;
import com.jushan.system.service.MonitorAlertService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 超时停放自动拉黑服务（PRD §5.1）。
 * <p>
 * 扫描在场（status='IN'）且连续停放超过配置时长的非固定车，自动在 {@code sys_vehicle}
 * 中创建一条 {@code TYPE_BLACKLIST} 记录，并在拉黑前通过 {@link MonitorAlertService} 生成告警、
 * 向 {@code sys_audit_log} 写入系统操作审计。
 * <p>
 * <strong>多租户约束</strong>：定时任务运行在系统上下文（无租户会话），因此扫描在场车辆时
 * 通过 {@code @InterceptorIgnore(tenantLine=true)} 跨租户读取；处理每条命中记录前再显式设置
 * {@link TenantContext} 为对应租户，确保命中黑名单的写入与去重查询落在正确的租户范围内。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class OverstayBlacklistService {

    /** 默认超时阈值（小时）：168 小时 = 7 天 */
    public static final int DEFAULT_TIMEOUT_HOURS = 168;

    /** access_policy 策略类型：黑名单 */
    public static final String POLICY_TYPE_BLACKLIST = "BLACKLIST";

    /** access_policy 策略键：超时阈值（小时） */
    public static final String POLICY_KEY_TIMEOUT_HOURS = "timeout_hours";

    /** 拉黑原因 */
    public static final String BLACKLIST_REASON = "超时停放";

    /** 拉黑来源（系统自动） */
    public static final String BLACKLIST_SOURCE = "系统自动";

    /** 系统操作人 ID（审计日志占位，无对应 sys_user 记录） */
    public static final long SYSTEM_OPERATOR_ID = -1L;

    /** 系统操作人名称 */
    public static final String SYSTEM_OPERATOR_NAME = "SYSTEM";

    /** 固定车类型集合：这些类型不参与自动拉黑 */
    private static final Set<String> FIXED_VEHICLE_TYPES = Set.of(
            SysVehicle.TYPE_PREPAID,
            SysVehicle.TYPE_FREE,
            SysVehicle.TYPE_VIP,
            SysVehicle.TYPE_SUPER
    );

    private final ParkingSessionMapper sessionMapper;
    private final SysVehicleMapper vehicleMapper;
    private final AccessPolicyMapper policyMapper;
    private final MonitorAlertService monitorAlertService;
    private final SysAuditLogMapper auditLogMapper;

    public OverstayBlacklistService(ParkingSessionMapper sessionMapper,
                                    SysVehicleMapper vehicleMapper,
                                    AccessPolicyMapper policyMapper,
                                    MonitorAlertService monitorAlertService,
                                    SysAuditLogMapper auditLogMapper) {
        this.sessionMapper = sessionMapper;
        this.vehicleMapper = vehicleMapper;
        this.policyMapper = policyMapper;
        this.monitorAlertService = monitorAlertService;
        this.auditLogMapper = auditLogMapper;
    }

    /**
     * 执行一次超时停放自动拉黑检测。
     *
     * @param now 当前时间（由调用方传入，便于单元测试 Mock）
     * @return 本次新拉黑（含由非黑名单记录转换）的车辆数量
     */
    @Transactional(rollbackFor = Exception.class)
    public int detectAndBlacklist(LocalDateTime now) {
        List<ParkingSession> inSessions = sessionMapper.selectAllInSessions();
        if (inSessions == null || inSessions.isEmpty()) {
            return 0;
        }

        int blacklisted = 0;
        // 阈值按 (租户, 停车场) 缓存，避免重复读取策略表
        Map<String, Integer> timeoutCache = new HashMap<>();

        for (ParkingSession session : inSessions) {
            if (!isQualified(session)) {
                continue;
            }

            int timeoutHours = timeoutCache.computeIfAbsent(
                    cacheKey(session.getTenantId(), session.getParkingLotId()),
                    k -> resolveTimeoutHours(session.getTenantId(), session.getParkingLotId()));

            // 未超过阈值：跳过
            if (!session.getEntryTime().isBefore(now.minusHours(timeoutHours))) {
                continue;
            }

            // 固定车不参与自动拉黑
            if (isFixedVehicle(session.getVehicleType())) {
                continue;
            }

            // 逐条设置租户上下文，确保写入与去重落在正确的租户范围
            TenantContext.set(new TenantContext.Snapshot(
                    session.getTenantId(), SYSTEM_OPERATOR_ID, "system", null, null));
            try {
                if (processSession(session, now, timeoutHours)) {
                    blacklisted++;
                }
            } finally {
                TenantContext.clear();
            }
        }

        log.info("超时停放自动拉黑任务完成: now={}, 扫描在场={}, 拉黑={}",
                now, inSessions.size(), blacklisted);
        return blacklisted;
    }

    // ==================== 私有方法 ====================

    /**
     * 处理单条命中记录：去重 → 告警 → 拉黑 → 审计。
     *
     * @return 是否产生了黑名单写入（新增或转换）
     */
    private boolean processSession(ParkingSession session, LocalDateTime now, int timeoutHours) {
        String plate = session.getPlateNumber().toUpperCase();

        // 防重：同车同租户已有黑名单记录（唯一键约束层面即每条车牌至多一条）时跳过，
        // 尊重人工解除黑名单的操作，不被定时任务覆盖。
        SysVehicle existing = vehicleMapper.selectByPlateNumber(plate, session.getTenantId());
        if (existing != null && SysVehicle.TYPE_BLACKLIST.equals(existing.getVehicleType())) {
            log.debug("车辆已有黑名单记录，跳过: plate={}, lotId={}", plate, session.getParkingLotId());
            return false;
        }

        // 拉黑前创建告警
        String alertMessage = String.format(
                "车辆 %s 在停车场 %d 连续停放超过 %d 小时，系统已自动拉黑",
                plate, session.getParkingLotId(), timeoutHours);
        monitorAlertService.createOverstayAlert(session.getTenantId(), session.getParkingLotId(), plate, alertMessage);

        SysVehicle blacklist;
        if (existing != null) {
            // 已有非黑名单记录（如临时车）：转换为黑名单，避免唯一键冲突
            existing.setVehicleType(SysVehicle.TYPE_BLACKLIST);
            existing.setStatus(SysVehicle.STATUS_ACTIVE);
            existing.setParkingLotId(session.getParkingLotId());
            existing.setRemark(buildRemark());
            existing.setUpdatedAt(now);
            vehicleMapper.updateById(existing);
            blacklist = existing;
        } else {
            blacklist = new SysVehicle();
            blacklist.setPlateNumber(plate);
            blacklist.setPlateColor(session.getPlateColor());
            blacklist.setVehicleType(SysVehicle.TYPE_BLACKLIST);
            blacklist.setParkingLotId(session.getParkingLotId());
            blacklist.setStatus(SysVehicle.STATUS_ACTIVE);
            blacklist.setTenantId(session.getTenantId());
            blacklist.setRemark(buildRemark());
            blacklist.setCreatedAt(now);
            blacklist.setUpdatedAt(now);
            vehicleMapper.insert(blacklist);
        }

        writeAuditLog(session, blacklist, now);
        return true;
    }

    /**
     * 从 access_policy 读取超时阈值，未配置或非法时回退默认值。
     */
    private int resolveTimeoutHours(Long tenantId, Long parkingLotId) {
        try {
            String value = policyMapper.selectTimeoutHoursValue(parkingLotId, tenantId);
            if (value != null && !value.isBlank()) {
                int hours = Integer.parseInt(value.trim());
                if (hours > 0) {
                    return hours;
                }
            }
        } catch (NumberFormatException e) {
            log.warn("超时阈值配置非法，回退默认值: lotId={}, tenantId={}", parkingLotId, tenantId, e);
        }
        return DEFAULT_TIMEOUT_HOURS;
    }

    /**
     * 记录自动拉黑审计日志（操作人为 SYSTEM）。
     */
    private void writeAuditLog(ParkingSession session, SysVehicle blacklist, LocalDateTime now) {
        SysAuditLog logEntry = new SysAuditLog();
        logEntry.setTenantId(session.getTenantId());
        logEntry.setTargetType("sys_vehicle");
        logEntry.setTargetId(String.valueOf(blacklist.getId()));
        logEntry.setAction("vehicle_blacklist_auto");
        logEntry.setOperatorId(SYSTEM_OPERATOR_ID);
        logEntry.setOperatorName(SYSTEM_OPERATOR_NAME);
        logEntry.setIsProxy(0);
        logEntry.setBeforeValue(null);
        logEntry.setAfterValue(String.format(
                "{\"plateNumber\":\"%s\",\"vehicleType\":\"BLACKLIST\",\"parkingLotId\":%d,"
                        + "\"reason\":\"%s\",\"source\":\"%s\"}",
                blacklist.getPlateNumber(), session.getParkingLotId(),
                BLACKLIST_REASON, BLACKLIST_SOURCE));
        logEntry.setResult("SUCCESS");
        logEntry.setReason(BLACKLIST_REASON);
        logEntry.setClientIp(null);
        logEntry.setCreatedAt(now);
        auditLogMapper.insert(logEntry);
    }

    private boolean isQualified(ParkingSession session) {
        return session.getEntryTime() != null
                && session.getParkingLotId() != null
                && session.getTenantId() != null
                && session.getPlateNumber() != null;
    }

    private boolean isFixedVehicle(String vehicleType) {
        return vehicleType != null && FIXED_VEHICLE_TYPES.contains(vehicleType);
    }

    private String buildRemark() {
        return "拉黑来源：" + BLACKLIST_SOURCE + "；拉黑原因：" + BLACKLIST_REASON;
    }

    private String cacheKey(Long tenantId, Long parkingLotId) {
        return tenantId + ":" + parkingLotId;
    }
}
