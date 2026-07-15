package com.jushan.platform.modules.device.webhook;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.platform.modules.device.dto.DeviceWebhookEvent;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Device Access Webhook 事件处理服务。
 * <p>
 * 负责 Webhook 事件的幂等校验、设备身份验证、方向匹配、
 * 租户上下文推导与注入，最终委托 {@link DeviceWebhookEventHandler}
 * 将事件适配到平台识别处理管线。
 * <p>
 * <b>安全约束（P0 红线）</b>：
 * <ul>
 *   <li>禁止信任 Device Access 推送中的 tenantId/parkingLotId/laneId</li>
 *   <li>必须从平台设备台账推导可信的租户/停车场/车道信息</li>
 *   <li>设备未找到时记录错误但不抛异常（始终返回 200 给 Device Access）</li>
 *   <li>方向不匹配时拒绝处理并记录审计日志</li>
 *   <li>同一 eventId 幂等去重</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class DeviceWebhookService {

    /**
     * 已处理事件 ID 缓存（内存去重，24 小时 TTL）。
     * <p>
     * TODO: 多实例部署时升级为 Redis Set + TTL。
     */
    private final Map<String, LocalDateTime> processedEvents = new ConcurrentHashMap<>();

    /** 事件去重保留时间（小时） */
    private static final int EVENT_RETENTION_HOURS = 24;

    private final DeviceMapper deviceMapper;
    private final ParkingLaneMapper laneMapper;
    private final DeviceWebhookEventHandler eventHandler;

    public DeviceWebhookService(DeviceMapper deviceMapper,
                                ParkingLaneMapper laneMapper,
                                DeviceWebhookEventHandler eventHandler) {
        this.deviceMapper = deviceMapper;
        this.laneMapper = laneMapper;
        this.eventHandler = eventHandler;
    }

    /**
     * 处理 Device Access Webhook 事件。
     * <p>
     * 本方法<b>不抛出异常</b>——所有业务异常内部消化，
     * 确保 Device Access 始终收到 HTTP 200，避免其重试。
     *
     * @param event Webhook 推送事件
     */
    public void processEvent(DeviceWebhookEvent event) {
        // 0. 清理过期事件缓存
        cleanupExpiredEvents();

        // 1. 幂等校验
        if (event.getEventId() != null && processedEvents.containsKey(event.getEventId())) {
            log.info("Webhook 事件重复，已跳过: eventId={}, deviceSn={}, plate={}",
                    event.getEventId(), event.getDeviceSn(), event.getPlateNumber());
            return;
        }

        // 2. 设备身份校验：从 deviceSn 查询平台设备记录
        if (event.getDeviceSn() == null || event.getDeviceSn().isBlank()) {
            log.error("Webhook 事件缺少 deviceSn，无法处理: eventId={}", event.getEventId());
            return;
        }

        Device device = deviceMapper.selectByDeviceSn(event.getDeviceSn());
        if (device == null) {
            log.error("Webhook 事件 deviceSn 无匹配设备，已跳过: eventId={}, deviceSn={}",
                    event.getEventId(), event.getDeviceSn());
            return;
        }

        // 提取可信的 tenantId / parkingLotId / laneId（来自平台台账）
        Long trustedTenantId = device.getTenantId();
        Long trustedParkingLotId = device.getParkingLotId();
        Long trustedLaneId = device.getLaneId();

        log.info("Webhook 设备身份校验通过: eventId={}, deviceSn={}, deviceId={}, tenantId={}, lotId={}, laneId={}",
                event.getEventId(), event.getDeviceSn(), device.getId(),
                trustedTenantId, trustedParkingLotId, trustedLaneId);

        // 记录不可信字段（仅日志，不参与业务逻辑）
        if (event.getTenantId() != null && !event.getTenantId().equals(trustedTenantId)) {
            log.warn("Webhook tenantId 与设备台账不一致（已忽略推送值）: eventId={}, pushed={}, trusted={}",
                    event.getEventId(), event.getTenantId(), trustedTenantId);
        }
        if (event.getParkingLotId() != null && !event.getParkingLotId().equals(trustedParkingLotId)) {
            log.warn("Webhook parkingLotId 与设备台账不一致（已忽略推送值）: eventId={}, pushed={}, trusted={}",
                    event.getEventId(), event.getParkingLotId(), trustedParkingLotId);
        }

        // 3. 方向校验：对比事件方向与车道绑定方向
        if (trustedLaneId != null && event.getDirection() != null) {
            ParkingLane lane = laneMapper.selectByIdIgnoreTenant(trustedLaneId);
            if (lane != null && lane.getDirection() != null && !"MIXED".equals(lane.getDirection())) {
                String laneDirection = lane.getDirection();
                if (!laneDirection.equals(event.getDirection())) {
                    log.error("Webhook 事件方向与车道方向不匹配，拒绝处理: eventId={}, deviceSn={}, laneId={}, " +
                                    "eventDirection={}, laneDirection={}",
                            event.getEventId(), event.getDeviceSn(), trustedLaneId,
                            event.getDirection(), laneDirection);
                    // 记录审计（不可信方向事件）
                    return;
                }
            }
        }

        // 4. 车牌标准化
        String normalizedPlate = event.getPlateNumber() != null
                ? event.getPlateNumber().toUpperCase().trim() : null;
        if (normalizedPlate == null || normalizedPlate.isEmpty()) {
            log.error("Webhook 事件缺少车牌号，已跳过: eventId={}, deviceSn={}",
                    event.getEventId(), event.getDeviceSn());
            return;
        }

        // 5. 置信度转换（0.0~1.0 → 0~100 整数）
        Integer confidence = null;
        if (event.getConfidence() != null) {
            if (event.getConfidence() <= 1.0) {
                confidence = (int) Math.round(event.getConfidence() * 100);
            } else {
                confidence = event.getConfidence().intValue();
            }
        }

        // 6. 设置租户上下文（Webhook 无 JWT Token，需手动注入）
        TenantContext.Snapshot previousContext = TenantContext.get();
        try {
            TenantContext.set(new TenantContext.Snapshot(
                    trustedTenantId,
                    0L,           // 系统用户 ID
                    "platform",   // 用户类型
                    null,         // roles
                    null          // permissions
            ));

            // 7. 委托事件处理器调用 RecognitionEventService
            RecognitionResultVO result = eventHandler.handlePlateRecognized(
                    normalizedPlate,
                    event.getPlateColor(),
                    event.getDirection(),
                    trustedParkingLotId,
                    trustedLaneId,
                    event.getImageUrl(),
                    confidence,
                    event.getDeviceSn(),
                    event.getEventId(),
                    event.getCaptureTime()
            );

            // 8. 记录处理成功
            processedEvents.put(event.getEventId(), LocalDateTime.now());

            log.info("Webhook 事件处理完成: eventId={}, plate={}, allowPass={}, sessionId={}",
                    event.getEventId(), normalizedPlate, result.getAllowPass(), result.getSessionId());

        } catch (Exception e) {
            // 业务处理异常内部消化，不影响 HTTP 响应
            log.error("Webhook 事件处理异常: eventId={}, deviceSn={}, plate={}, error={}",
                    event.getEventId(), event.getDeviceSn(), normalizedPlate, e.getMessage(), e);
        } finally {
            // 恢复或清除租户上下文
            if (previousContext != null) {
                TenantContext.set(previousContext);
            } else {
                TenantContext.clear();
            }
        }
    }

    /**
     * 清理超过保留时间的已处理事件记录。
     */
    private void cleanupExpiredEvents() {
        LocalDateTime cutoff = LocalDateTime.now().minusHours(EVENT_RETENTION_HOURS);
        processedEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(cutoff));
    }
}
