package com.jushan.platform.modules.device.webhook;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.booth.vo.RecognitionResultVO;
import com.jushan.platform.modules.device.dto.DeviceWebhookEvent;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.ws.BoothWebSocketPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
     * 基于（车牌+方向）的去重缓存，窗口 5 秒。
     * <p>
     * 臻识 C5H 每次识别会同时发两条 MQTT 消息（quick_ivs_result + ivs_result），
     * DA 会为两者分别生成不同 eventId 并推送 Webhook。
     * 此缓存按（车牌+方向）在 5 秒窗口内去重，避免同一辆车创建多条 ParkingSession。
     */
    private final Map<String, LocalDateTime> recentPlateEvents = new ConcurrentHashMap<>();

    /**
     * 事件去重 Redis key 前缀
     */
    static final String EVENT_KEY_PREFIX = "webhook:event:";

    /**
     * 事件去重 TTL：24 小时
     */
    static final Duration EVENT_TTL = Duration.ofHours(24);

    /** 车牌级去重窗口（秒），BR-08: 同一车道同一车牌 30 秒内去重 */
    private static final int PLATE_DEDUP_WINDOW_SECONDS = 30;

    private final DeviceMapper deviceMapper;
    private final ParkingLaneMapper laneMapper;
    private final DeviceWebhookEventHandler eventHandler;
    private final StringRedisTemplate stringRedisTemplate;
    private final BoothWebSocketPublisher wsPublisher;

    public DeviceWebhookService(DeviceMapper deviceMapper,
                                ParkingLaneMapper laneMapper,
                                DeviceWebhookEventHandler eventHandler,
                                ObjectProvider<StringRedisTemplate> redisTemplateProvider,
                                BoothWebSocketPublisher wsPublisher) {
        this.deviceMapper = deviceMapper;
        this.laneMapper = laneMapper;
        this.eventHandler = eventHandler;
        this.stringRedisTemplate = redisTemplateProvider.getIfAvailable();
        this.wsPublisher = wsPublisher;
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
        // 0. 清理过期车牌去重缓存
        cleanupExpiredPlateEvents();

        // 1. 幂等校验（Redis SETNX，24h TTL，多实例安全）
        if (event.getEventId() != null && !event.getEventId().isBlank()) {
            if (!markEventProcessed(event.getEventId())) {
                log.info("Webhook 事件重复，已跳过: eventId={}, deviceSn={}, plate={}",
                        event.getEventId(), event.getDeviceSn(), event.getPlateNumber());
                return;
            }
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

        // 3. 方向判定：优先按相机 recognition_direction，兜底车道类型推断
        //    臻识 C5H direction=4 等场景由兜底逻辑处理
        determineDirection(event, device, trustedLaneId);

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

        // 5b. 车牌级去重（BR-08）：同一车道同一车牌同一方向在 30 秒窗口内去重
        //     臻识 C5H 每次识别发两条 MQTT（quick_ivs_result + ivs_result），
        //     DA 为两者分别生成不同 eventId 但车牌相同，两事件可能几乎同时到达。
        //     使用 ConcurrentHashMap.compute 原子操作避免竞态条件。
        //     方向在步骤 3 中已从车道绑定推断，此时有效。
        if (normalizedPlate != null && event.getDirection() != null && trustedLaneId != null) {
            String plateKey = normalizedPlate + ":" + event.getDirection() + ":" + trustedLaneId;
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime existing = recentPlateEvents.compute(plateKey, (k, v) -> {
                if (v != null && v.plusSeconds(PLATE_DEDUP_WINDOW_SECONDS).isAfter(now)) {
                    return v; // 窗口内不更新，保留旧值
                }
                return now; // 窗口已过或新记录，更新时间
            });
            // 如果 existing 不等于 now，说明窗口内已有记录（compute 返回了旧值）
            if (!existing.equals(now)) {
                log.info("Webhook 同一车道同一车牌短时重复（BR-08），跳过会话创建但事件照常推送: plate={}, direction={}, laneId={}, eventId={}",
                        normalizedPlate, event.getDirection(), trustedLaneId, event.getEventId());
                // V1.4: BR-08 去重仅跳过会话创建，事件照常推送岗亭
                wsPublisher.sendLightweightRecognitionEvent(
                        trustedParkingLotId,
                        normalizedPlate,
                        event.getDirection(),
                        trustedLaneId,
                        event.getDeviceSn(),
                        event.getImageUrl(),
                        confidence,
                        event.getCaptureTime() != null ? event.getCaptureTime().toString() : null);
                return;
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

            // 8. 记录处理完成
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
     * 判定识别事件方向，优先级：
     * <ol>
     *   <li>相机 recognition_direction（1=ENTRY, 2=EXIT）→ 直接采用</li>
     *   <li>非 MIXED 车道类型反推方向（兼容臻识 direction=4 等场景）</li>
     *   <li>MIXED 车道 + 相机无方向 → 保留事件原始 direction 或 null</li>
     * </ol>
     */
    private void determineDirection(DeviceWebhookEvent event, Device device, Long laneId) {
        // 优先：相机识别方向
        if (device.getRecognitionDirection() != null) {
            String deviceDirection = device.getRecognitionDirection() == 1 ? "ENTRY" : "EXIT";
            log.info("Webhook 方向取自相机识别方向: deviceId={}, recognitionDirection={} → {}",
                    device.getId(), device.getRecognitionDirection(), deviceDirection);
            if (event.getDirection() != null && !deviceDirection.equals(event.getDirection())) {
                log.warn("Webhook 事件方向({})与相机识别方向({})不一致，以相机方向为准: eventId={}, deviceSn={}",
                        event.getDirection(), deviceDirection, event.getEventId(), event.getDeviceSn());
            }
            event.setDirection(deviceDirection);
            return;
        }

        // 兜底：从车道类型推断
        if (laneId != null) {
            ParkingLane lane = laneMapper.selectByIdIgnoreTenant(laneId);
            if (lane != null && lane.getType() != null && lane.getType() != 3) {
                String laneDirection = lane.getType() == 1 ? "ENTRY" : "EXIT";
                if (event.getDirection() != null) {
                    if (!laneDirection.equals(event.getDirection())) {
                        log.warn("Webhook 事件方向与车道方向不匹配（兜底，以车道为准）: eventId={}, deviceSn={}, laneId={}, " +
                                        "eventDirection={}, laneDirection={}",
                                event.getEventId(), event.getDeviceSn(), laneId,
                                event.getDirection(), laneDirection);
                    }
                }
                log.info("Webhook 方向兜底取自车道类型: laneId={}, type={} → {}",
                        laneId, lane.getType(), laneDirection);
                event.setDirection(laneDirection);
                return;
            }
        }

        // MIXED 车道且相机无方向：保留事件方向原值（可能为 null）
        if (event.getDirection() == null || event.getDirection().isBlank()) {
            log.warn("Webhook 无法确定识别方向（相机无识别方向 + 车道为双向或无车道绑定）: eventId={}, deviceSn={}",
                    event.getEventId(), event.getDeviceSn());
        }
    }

    /**
     * 清理车牌去重缓存中超过 1 分钟的旧记录。
     */
    private void cleanupExpiredPlateEvents() {
        LocalDateTime plateCutoff = LocalDateTime.now().minusMinutes(1);
        recentPlateEvents.entrySet().removeIf(entry -> entry.getValue().isBefore(plateCutoff));
    }

    /**
     * 标记事件为已处理（Redis SETNX），返回 true 表示首次处理（即未重复）。
     * <p>
     * 多实例安全：使用 Redis SETNX 原子操作，只有一个节点能成功标记。
     *
     * @param eventId 事件 ID
     * @return true=首次（继续处理），false=重复（跳过）
     */
    private boolean markEventProcessed(String eventId) {
        if (stringRedisTemplate == null) {
            log.warn("Redis 不可用，跳过事件幂等检查: eventId={}", eventId);
            return true;
        }
        String key = EVENT_KEY_PREFIX + eventId;
        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(key, "1", EVENT_TTL);
        return Boolean.TRUE.equals(success);
    }
}
