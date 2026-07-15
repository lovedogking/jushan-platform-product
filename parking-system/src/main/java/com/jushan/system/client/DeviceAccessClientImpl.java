package com.jushan.system.client;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.config.DeviceAccessProperties;
import com.jushan.system.client.dto.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Device Access HTTP 客户端实现（T23，P002 增强，v0.4 扩展）。
 * <p>
 * 使用 {@link RestTemplate} 调用 Device Access v0.2/v0.4 接口，
 * <strong>不包含任何自动重试</strong>，网络超时统一标记为 UNCERTAIN。
 * <p>
 * <strong>P002 新增能力</strong>：
 * <ul>
 *   <li>Micrometer 指标：调用次数、失败率、延迟分布（Prometheus 可观测）</li>
 *   <li>降级策略：Device Access 连续失败时快速失败，避免级联阻塞</li>
 *   <li>完整异常路径：响应解析失败、跨停车场调用、批量查询部分失败</li>
 * </ul>
 * <p>
 * <strong>v0.4 新增</strong>：
 * <ul>
 *   <li>开闸/关闸 — {@code POST /api/v1/devices/{deviceSn}/gate/open|close}</li>
 *   <li>显示屏文字/保存/配置 — {@code POST /api/v1/devices/{deviceSn}/display/text|save|config}</li>
 *   <li>语音播报 — {@code POST /api/v1/devices/{deviceSn}/voice/control}</li>
 * </ul>
 * <p>
 * <strong>错误分类</strong>：
 * <ul>
 *   <li>DA 返回非 200 → {@link BusinessException}（携带 DA 错误码和消息）</li>
 *   <li>网络超时/连接失败 → {@link BusinessException}（INTERNAL_ERROR，标记 UNCERTAIN）</li>
 *   <li>响应解析失败 → {@link BusinessException}（INTERNAL_ERROR）</li>
 *   <li>降级触发 → {@link BusinessException}（INTERNAL_ERROR）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class DeviceAccessClientImpl implements DeviceAccessClient {

    private static final Logger log = LoggerFactory.getLogger(DeviceAccessClientImpl.class);

    // ==================== v0.2 路径 ====================

    private static final String STATUS_PATH = "/api/v1/devices/{deviceSn}/status";
    private static final String SYNC_TIME_PATH = "/api/v1/devices/{deviceSn}/time/sync";

    // ==================== v0.4 路径 ====================

    private static final String OPEN_GATE_PATH = "/api/v1/devices/{deviceSn}/gate/open";
    private static final String CLOSE_GATE_PATH = "/api/v1/devices/{deviceSn}/gate/close";
    private static final String DISPLAY_TEXT_PATH = "/api/v1/devices/{deviceSn}/display/text";
    private static final String SAVE_DISPLAY_PATH = "/api/v1/devices/{deviceSn}/display/save";
    private static final String DISPLAY_CONFIG_PATH = "/api/v1/devices/{deviceSn}/display/config";
    private static final String VOICE_CONTROL_PATH = "/api/v1/devices/{deviceSn}/voice/control";

    /** Micrometer 指标前缀 */
    private static final String METRIC_PREFIX = "device.access";

    /** 降级：连续失败阈值（超过此值触发降级） */
    private static final int CIRCUIT_BREAKER_THRESHOLD = 5;

    /** 降级：恢复时间窗口（秒），超过此时间后尝试恢复 */
    private static final long CIRCUIT_BREAKER_RECOVERY_SECONDS = 30;

    private final RestTemplate restTemplate;
    private final DeviceAccessProperties props;
    private final MeterRegistry meterRegistry;

    // ==================== 降级状态（内存级，非分布式） ====================

    /** 连续失败计数器 */
    private final AtomicInteger consecutiveFailures = new AtomicInteger(0);

    /** 降级状态标志 */
    private final AtomicBoolean circuitOpen = new AtomicBoolean(false);

    /** 上次失败时间（用于计算恢复窗口） */
    private volatile LocalDateTime lastFailureTime = null;

    public DeviceAccessClientImpl(RestTemplate restTemplate, DeviceAccessProperties props,
                                   MeterRegistry meterRegistry) {
        this.restTemplate = restTemplate;
        this.props = props;
        this.meterRegistry = meterRegistry;
        initMetrics();
    }

    /**
     * 初始化 Micrometer 指标。
     */
    private void initMetrics() {
        meterRegistry.gauge(METRIC_PREFIX + ".circuit_breaker.state", circuitOpen,
                cb -> cb.get() ? 1.0 : 0.0);
    }

    // ==================== v0.2 方法 ====================

    @Override
    public DeviceStatusDTO getStatus(String deviceSn) {
        log.debug("查询设备状态: deviceSn={}", deviceSn);

        checkCircuitBreaker("getStatus");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<DeviceStatusDTO> response;

        try {
            response = execute(
                    STATUS_PATH, HttpMethod.GET, deviceSn,
                    new ParameterizedTypeReference<DeviceAccessResponse<DeviceStatusDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "getStatus")
                    .register(meterRegistry));
        }

        recordSuccess("getStatus");
        log.info("设备状态查询成功: deviceSn={}, online={}", deviceSn,
                response.getData() != null ? response.getData().getOnline() : null);
        return response.getData();
    }

    @Override
    public TimeSyncResultDTO syncTime(String deviceSn) {
        log.debug("设备校时: deviceSn={}", deviceSn);

        checkCircuitBreaker("syncTime");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<TimeSyncResultDTO> response;

        try {
            response = execute(
                    SYNC_TIME_PATH, HttpMethod.POST, deviceSn,
                    new ParameterizedTypeReference<DeviceAccessResponse<TimeSyncResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "syncTime")
                    .register(meterRegistry));
        }

        recordSuccess("syncTime");
        log.info("设备校时成功: deviceSn={}, success={}, deviceCode={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null,
                response.getData() != null ? response.getData().getDeviceCode() : null);
        return response.getData();
    }

    // ==================== v0.4 设备控制 ====================

    @Override
    public CommandResultDTO openGate(String deviceSn) {
        log.debug("开闸: deviceSn={}", deviceSn);

        checkCircuitBreaker("openGate");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<CommandResultDTO> response;

        try {
            response = execute(
                    OPEN_GATE_PATH, HttpMethod.POST, deviceSn,
                    new ParameterizedTypeReference<DeviceAccessResponse<CommandResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "openGate")
                    .register(meterRegistry));
        }

        recordSuccess("openGate");
        log.info("开闸成功: deviceSn={}, success={}, deviceCode={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null,
                response.getData() != null ? response.getData().getDeviceCode() : null);
        return response.getData();
    }

    @Override
    public CommandResultDTO closeGate(String deviceSn) {
        log.debug("关闸: deviceSn={}", deviceSn);

        checkCircuitBreaker("closeGate");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<CommandResultDTO> response;

        try {
            response = execute(
                    CLOSE_GATE_PATH, HttpMethod.POST, deviceSn,
                    new ParameterizedTypeReference<DeviceAccessResponse<CommandResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "closeGate")
                    .register(meterRegistry));
        }

        recordSuccess("closeGate");
        log.info("关闸成功: deviceSn={}, success={}, deviceCode={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null,
                response.getData() != null ? response.getData().getDeviceCode() : null);
        return response.getData();
    }

    @Override
    public DisplayResultDTO displayText(String deviceSn, DisplayTextRequest request) {
        log.debug("显示屏文字: deviceSn={}, content={}", deviceSn,
                request != null ? request.getContent() : null);

        checkCircuitBreaker("displayText");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<DisplayResultDTO> response;

        try {
            response = execute(
                    DISPLAY_TEXT_PATH, HttpMethod.POST, deviceSn, request,
                    new ParameterizedTypeReference<DeviceAccessResponse<DisplayResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "displayText")
                    .register(meterRegistry));
        }

        recordSuccess("displayText");
        log.info("显示屏文字设置成功: deviceSn={}, success={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null);
        return response.getData();
    }

    @Override
    public DisplayResultDTO saveDisplay(String deviceSn, DisplaySaveRequest request) {
        log.debug("保存显示内容: deviceSn={}, lineNumber={}", deviceSn,
                request != null ? request.getLineNumber() : null);

        checkCircuitBreaker("saveDisplay");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<DisplayResultDTO> response;

        try {
            response = execute(
                    SAVE_DISPLAY_PATH, HttpMethod.POST, deviceSn, request,
                    new ParameterizedTypeReference<DeviceAccessResponse<DisplayResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "saveDisplay")
                    .register(meterRegistry));
        }

        recordSuccess("saveDisplay");
        log.info("显示内容保存成功: deviceSn={}, success={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null);
        return response.getData();
    }

    @Override
    public DisplayResultDTO displayConfig(String deviceSn, DisplayConfigRequest request) {
        log.debug("显示屏配置: deviceSn={}, configType={}", deviceSn,
                request != null ? request.getConfigType() : null);

        checkCircuitBreaker("displayConfig");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<DisplayResultDTO> response;

        try {
            response = execute(
                    DISPLAY_CONFIG_PATH, HttpMethod.POST, deviceSn, request,
                    new ParameterizedTypeReference<DeviceAccessResponse<DisplayResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "displayConfig")
                    .register(meterRegistry));
        }

        recordSuccess("displayConfig");
        log.info("显示屏配置成功: deviceSn={}, configType={}, success={}", deviceSn,
                request != null ? request.getConfigType() : null,
                response.getData() != null ? response.getData().getSuccess() : null);
        return response.getData();
    }

    @Override
    public VoiceResultDTO voiceControl(String deviceSn, VoiceControlRequest request) {
        log.debug("语音播报: deviceSn={}, action={}, voiceId={}", deviceSn,
                request != null ? request.getAction() : null,
                request != null ? request.getVoiceId() : null);

        checkCircuitBreaker("voiceControl");

        Timer.Sample sample = Timer.start(meterRegistry);
        DeviceAccessResponse<VoiceResultDTO> response;

        try {
            response = execute(
                    VOICE_CONTROL_PATH, HttpMethod.POST, deviceSn, request,
                    new ParameterizedTypeReference<DeviceAccessResponse<VoiceResultDTO>>() {});
        } finally {
            sample.stop(Timer.builder(METRIC_PREFIX + ".latency")
                    .tag("method", "voiceControl")
                    .register(meterRegistry));
        }

        recordSuccess("voiceControl");
        log.info("语音播报成功: deviceSn={}, success={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null);
        return response.getData();
    }

    // ==================== 降级策略 ====================

    /**
     * 检查降级状态。
     */
    private void checkCircuitBreaker(String method) {
        if (!circuitOpen.get()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        if (lastFailureTime != null) {
            long secondsSinceLastFailure = Duration.between(lastFailureTime, now).getSeconds();
            if (secondsSinceLastFailure >= CIRCUIT_BREAKER_RECOVERY_SECONDS) {
                circuitOpen.set(false);
                consecutiveFailures.set(0);
                log.info("Device Access 降级恢复: 超过 {} 秒恢复窗口，尝试恢复调用 (method={})",
                        CIRCUIT_BREAKER_RECOVERY_SECONDS, method);
                return;
            }
        }

        Counter.builder(METRIC_PREFIX + ".calls.errors")
                .tag("method", method)
                .tag("error_type", "circuit_breaker")
                .register(meterRegistry)
                .increment();

        throw new BusinessException(CommonErrorCode.INTERNAL_ERROR,
                String.format("Device Access 服务不可用（降级中），已跳过 %d 秒前连续 %d 次失败后的调用。"
                        + "method=%s, 恢复窗口=%ds",
                        CIRCUIT_BREAKER_RECOVERY_SECONDS, CIRCUIT_BREAKER_THRESHOLD,
                        method, CIRCUIT_BREAKER_RECOVERY_SECONDS));
    }

    /**
     * 记录成功，重置连续失败计数器。
     */
    private void recordSuccess(String method) {
        consecutiveFailures.set(0);

        Counter.builder(METRIC_PREFIX + ".calls.total")
                .tag("method", method)
                .tag("status", "success")
                .register(meterRegistry)
                .increment();
    }

    /**
     * 记录失败，增加连续失败计数器，可能触发降级。
     */
    private void recordFailure(String method, String errorType) {
        int failures = consecutiveFailures.incrementAndGet();
        lastFailureTime = LocalDateTime.now();

        Counter.builder(METRIC_PREFIX + ".calls.total")
                .tag("method", method)
                .tag("status", "error")
                .register(meterRegistry)
                .increment();

        Counter.builder(METRIC_PREFIX + ".calls.errors")
                .tag("method", method)
                .tag("error_type", errorType)
                .register(meterRegistry)
                .increment();

        if (failures >= CIRCUIT_BREAKER_THRESHOLD) {
            circuitOpen.set(true);
            log.error("Device Access 降级触发: 连续 {} 次失败，打开降级 (method={}, error_type={})",
                    failures, method, errorType);
        } else {
            log.warn("Device Access 调用失败: 连续 {} 次 (method={}, error_type={})",
                    failures, method, errorType);
        }
    }

    // ==================== 内部执行方法 ====================

    /**
     * 执行一次 Device Access HTTP 调用并解析统一响应（无请求体）。
     * <p>
     * 委托至 {@link #execute(String, HttpMethod, String, Object, ParameterizedTypeReference)}。
     *
     * @param pathTemplate URL 路径模板（含 {deviceSn} 占位符）
     * @param method       HTTP 方法
     * @param deviceSn     设备 SN
     * @param typeRef      响应类型引用（用于泛型反序列化）
     * @param <T>          业务 data 类型
     * @return 解析后的 DA 响应
     * @throws BusinessException DA 错误、网络异常或解析失败
     */
    private <T> DeviceAccessResponse<T> execute(
            String pathTemplate,
            HttpMethod method,
            String deviceSn,
            ParameterizedTypeReference<DeviceAccessResponse<T>> typeRef) {
        return execute(pathTemplate, method, deviceSn, null, typeRef);
    }

    /**
     * 执行一次 Device Access HTTP 调用并解析统一响应（支持请求体）。
     * <p>
     * 不使用自动重试；写请求的透明重试由本方法显式禁止。
     *
     * @param pathTemplate URL 路径模板（含 {deviceSn} 占位符）
     * @param method       HTTP 方法
     * @param deviceSn     设备 SN
     * @param requestBody  请求体（可为 null）
     * @param typeRef      响应类型引用（用于泛型反序列化）
     * @param <T>          业务 data 类型
     * @return 解析后的 DA 响应
     * @throws BusinessException DA 错误、网络异常或解析失败
     */
    private <T> DeviceAccessResponse<T> execute(
            String pathTemplate,
            HttpMethod method,
            String deviceSn,
            Object requestBody,
            ParameterizedTypeReference<DeviceAccessResponse<T>> typeRef) {

        String url = props.getBaseUrl() + pathTemplate;
        String methodName = method.name().toLowerCase() + "_" + pathTemplate.replace("/", "_");

        try {
            HttpEntity<Object> httpEntity = requestBody != null
                    ? new HttpEntity<>(requestBody)
                    : null;

            ResponseEntity<DeviceAccessResponse<T>> entity =
                    restTemplate.exchange(url, method, httpEntity, typeRef, deviceSn);

            HttpStatusCode statusCode = entity.getStatusCode();
            DeviceAccessResponse<T> body = entity.getBody();

            // 响应体为空
            if (body == null) {
                String errMsg = String.format("Device Access 返回空响应: HTTP %s, deviceSn=%s",
                        statusCode.value(), deviceSn);
                log.warn(errMsg);
                recordFailure(methodName, "empty_response");
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);
            }

            // 检查 DA 业务 code
            if (!body.isSuccess()) {
                String errMsg = String.format("Device Access 返回错误: HTTP %s, DA code=%d, message=%s, deviceSn=%s",
                        statusCode.value(), body.getCode(), body.getMessage(), deviceSn);
                log.warn(errMsg);
                recordFailure(methodName, "da_error_" + body.getCode());
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);
            }

            // 成功：data 字段在 isSuccess() 中已验证非 null
            return body;

        } catch (ResourceAccessException e) {
            // 网络层异常：连接超时、读取超时、连接被拒等 → UNCERTAIN
            String errMsg = String.format(
                    "Device Access 网络异常（UNCERTAIN）: deviceSn=%s, error=%s", deviceSn, e.getMessage());
            log.error(errMsg, e);
            recordFailure(methodName, "resource_access");
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);

        } catch (RestClientException e) {
            // 其他 RestTemplate 异常：序列化/反序列化失败、未知 Content-Type 等
            String errMsg = String.format(
                    "Device Access 客户端异常: deviceSn=%s, error=%s", deviceSn, e.getMessage());
            log.error(errMsg, e);
            recordFailure(methodName, "rest_client");
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);
        }
    }

    /**
     * 获取当前降级状态（用于监控和测试）。
     *
     * @return true 表示降级已打开
     */
    public boolean isCircuitOpen() {
        return circuitOpen.get();
    }

    /**
     * 获取当前连续失败次数（用于监控和测试）。
     *
     * @return 连续失败次数
     */
    public int getConsecutiveFailures() {
        return consecutiveFailures.get();
    }

    /**
     * 手动重置降级状态（用于测试和运维恢复）。
     */
    public void resetCircuitBreaker() {
        circuitOpen.set(false);
        consecutiveFailures.set(0);
        lastFailureTime = null;
        log.info("Device Access 降级状态已手动重置");
    }
}
