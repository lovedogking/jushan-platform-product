package com.jushan.platform.modules.miniapp.controller;

import com.jushan.common.R;
import com.jushan.platform.modules.booth.dto.MockRecognitionRequest;
import com.jushan.platform.modules.booth.entity.RecognitionEventLog;
import com.jushan.platform.modules.parking.service.MockRecognitionService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Mock/人工识别事件触发控制器（T28）。
 * <p>
 * 在正式车牌识别事件契约未冻结前，提供仅 local/test 环境可用的
 * Mock 和人工触发入口，用于验证平台内部事件模型和后续业务链路。
 * <p>
 * <strong>P0 安全红线</strong>：
 * <ul>
 *   <li>此控制器仅在 {@code local} 和 {@code test} Profile 下注册</li>
 *   <li>{@code prod} Profile 下 Bean 不创建，接口不可访问</li>
 *   <li>不得通过配置或环境变量在生产环境激活此控制器</li>
 *   <li>事件来源标注为 {@code MOCK}，不得声称是真机 Device Access 事件</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Profile({"local", "test"})
@RestController
@RequestMapping("/api/v1/internal/mock")
public class MockRecognitionController {

    private static final Logger log = LoggerFactory.getLogger(MockRecognitionController.class);

    private final MockRecognitionService mockRecognitionService;

    public MockRecognitionController(MockRecognitionService mockRecognitionService) {
        this.mockRecognitionService = mockRecognitionService;
    }

    /**
     * 触发 Mock 识别事件。
     * <p>
     * 接收平台设备 ID、车牌号和方向，由后端从可信记录推导
     * tenantId、parkingLotId、laneId，构造标准事件并发布。
     *
     * @param request Mock 触发请求（deviceId + plateNumber + direction 必填）
     * @return 包含 eventId 和日志 ID 的响应
     */
    @PostMapping("/recognition-event")
    public R<Map<String, Object>> triggerRecognitionEvent(
            @Valid @RequestBody MockRecognitionRequest request) {

        log.info("收到 Mock 识别事件触发请求: deviceId={} plate={} direction={}",
                request.getDeviceId(), request.getPlateNumber(), request.getDirection());

        RecognitionEventLog eventLog = mockRecognitionService.processMockEvent(request);

        Map<String, Object> result = Map.of(
                "eventId", eventLog.getEventId(),
                "logId", eventLog.getId(),
                "plateNumber", eventLog.getPlateNumber(),
                "direction", eventLog.getDirection(),
                "source", eventLog.getSource(),
                "eventTime", eventLog.getEventTime().toString()
        );

        return R.ok(result);
    }
}
