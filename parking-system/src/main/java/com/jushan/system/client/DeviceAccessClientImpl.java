package com.jushan.system.client;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.framework.config.DeviceAccessProperties;
import com.jushan.system.client.dto.DeviceAccessResponse;
import com.jushan.system.client.dto.DeviceStatusDTO;
import com.jushan.system.client.dto.TimeSyncResultDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * Device Access HTTP 客户端实现（T23）。
 * <p>
 * 使用 {@link RestTemplate} 调用 Device Access v0.2 接口，
 * <strong>不包含任何自动重试</strong>，网络超时统一标记为 UNCERTAIN。
 * <p>
 * <strong>错误分类</strong>：
 * <ul>
 *   <li>DA 返回非 200 → {@link BusinessException}（携带 DA 错误码和消息）</li>
 *   <li>网络超时/连接失败 → {@link BusinessException}（INTERNAL_ERROR，标记 UNCERTAIN）</li>
 *   <li>响应解析失败 → {@link BusinessException}（INTERNAL_ERROR）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class DeviceAccessClientImpl implements DeviceAccessClient {

    private static final Logger log = LoggerFactory.getLogger(DeviceAccessClientImpl.class);

    private static final String STATUS_PATH = "/api/v1/devices/{deviceSn}/status";
    private static final String SYNC_TIME_PATH = "/api/v1/devices/{deviceSn}/time/sync";

    private final RestTemplate restTemplate;
    private final DeviceAccessProperties props;

    public DeviceAccessClientImpl(RestTemplate restTemplate, DeviceAccessProperties props) {
        this.restTemplate = restTemplate;
        this.props = props;
    }

    @Override
    public DeviceStatusDTO getStatus(String deviceSn) {
        log.debug("查询设备状态: deviceSn={}", deviceSn);
        DeviceAccessResponse<DeviceStatusDTO> response = execute(
                STATUS_PATH, HttpMethod.GET, deviceSn,
                new ParameterizedTypeReference<DeviceAccessResponse<DeviceStatusDTO>>() {});
        log.info("设备状态查询成功: deviceSn={}, online={}", deviceSn,
                response.getData() != null ? response.getData().getOnline() : null);
        return response.getData();
    }

    @Override
    public TimeSyncResultDTO syncTime(String deviceSn) {
        log.debug("设备校时: deviceSn={}", deviceSn);
        DeviceAccessResponse<TimeSyncResultDTO> response = execute(
                SYNC_TIME_PATH, HttpMethod.POST, deviceSn,
                new ParameterizedTypeReference<DeviceAccessResponse<TimeSyncResultDTO>>() {});
        log.info("设备校时成功: deviceSn={}, success={}, deviceCode={}", deviceSn,
                response.getData() != null ? response.getData().getSuccess() : null,
                response.getData() != null ? response.getData().getDeviceCode() : null);
        return response.getData();
    }

    // ==================== 内部执行方法 ====================

    /**
     * 执行一次 Device Access HTTP 调用并解析统一响应。
     * <p>
     * 不使用自动重试；写请求的透明重试由本方法显式禁止。
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

        String url = props.getBaseUrl() + pathTemplate;

        try {
            ResponseEntity<DeviceAccessResponse<T>> entity =
                    restTemplate.exchange(url, method, null, typeRef, deviceSn);

            HttpStatusCode statusCode = entity.getStatusCode();
            DeviceAccessResponse<T> body = entity.getBody();

            // 响应体为空
            if (body == null) {
                String errMsg = String.format("Device Access 返回空响应: HTTP %s, deviceSn=%s",
                        statusCode.value(), deviceSn);
                log.warn(errMsg);
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);
            }

            // 检查 DA 业务 code
            if (!body.isSuccess()) {
                String errMsg = String.format("Device Access 返回错误: HTTP %s, DA code=%d, message=%s, deviceSn=%s",
                        statusCode.value(), body.getCode(), body.getMessage(), deviceSn);
                log.warn(errMsg);
                throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);
            }

            // 成功：data 字段在 isSuccess() 中已验证非 null
            return body;

        } catch (ResourceAccessException e) {
            // 网络层异常：连接超时、读取超时、连接被拒等 → UNCERTAIN
            String errMsg = String.format(
                    "Device Access 网络异常（UNCERTAIN）: deviceSn=%s, error=%s", deviceSn, e.getMessage());
            log.error(errMsg, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);

        } catch (RestClientException e) {
            // 其他 RestTemplate 异常：序列化/反序列化失败、未知 Content-Type 等
            String errMsg = String.format(
                    "Device Access 客户端异常: deviceSn=%s, error=%s", deviceSn, e.getMessage());
            log.error(errMsg, e);
            throw new BusinessException(CommonErrorCode.INTERNAL_ERROR, errMsg);
        }
    }
}
