package com.jushan.framework.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import java.time.Duration;

/**
 * Device Access HTTP Client 配置（T23）。
 * <p>
 * 创建专用于 Device Access 调用的 {@link RestTemplate} Bean。
 * <strong>不配置任何自动重试拦截器</strong>——写请求（如校时）不得透明重试；
 * 网络超时和读取超时统一标记为 UNCERTAIN，由上层业务决策。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Configuration
@EnableConfigurationProperties(DeviceAccessProperties.class)
public class DeviceAccessConfig {

    private static final Logger log = LoggerFactory.getLogger(DeviceAccessConfig.class);

    /**
     * Device Access 专用 RestTemplate。
     * <p>
     * 使用 {@link SimpleClientHttpRequestFactory} 配置连接和读取超时，
     * 不添加任何 RetryInterceptor、错误重试或熔断逻辑。
     *
     * @param props Device Access 配置属性
     * @return 配置好的 RestTemplate
     */
    @Bean
    @ConditionalOnMissingBean(name = "deviceAccessRestTemplate")
    public RestTemplate deviceAccessRestTemplate(DeviceAccessProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(props.getConnectTimeout()));
        factory.setReadTimeout(Duration.ofMillis(props.getReadTimeout()));

        RestTemplate restTemplate = new RestTemplateBuilder()
                .requestFactory(() -> factory)
                .build();

        // 添加 API Key 认证拦截器
        if (props.getApiKey() != null && !props.getApiKey().isBlank()) {
            restTemplate.setInterceptors(List.of((ClientHttpRequestInterceptor) (request, body, execution) -> {
                request.getHeaders().set("X-API-Key", props.getApiKey());
                return execution.execute(request, body);
            }));
        }

        // 设置 NoOp 错误处理器：HTTP 4xx/5xx 不抛异常，由客户端自行解析响应体中的错误码
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // 不做任何处理，由调用方检查 HTTP 状态码和响应体
            }
        });

        log.info("Device Access RestTemplate 已配置: baseUrl={}, connectTimeout={}ms, readTimeout={}ms",
                props.getBaseUrl(), props.getConnectTimeout(), props.getReadTimeout());
        return restTemplate;
    }
}
