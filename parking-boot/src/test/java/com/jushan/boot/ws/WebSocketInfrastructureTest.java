package com.jushan.boot.ws;

import com.jushan.boot.ParkingApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.springframework.web.socket.sockjs.client.SockJsClient;
import org.springframework.web.socket.sockjs.client.Transport;
import org.springframework.web.socket.sockjs.client.WebSocketTransport;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * WebSocket 基础设施集成测试。
 * <p>
 * 通过 STOMP over WebSocket 端到端验证连接、鉴权框架和订阅。
 * 附带 MySQL + RabbitMQ 容器，满足完整基础设施依赖。
 */
@SpringBootTest(
    classes = ParkingApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
    // 移除 test profile 中 RabbitMQ 的排除，由 Testcontainers 提供
    "spring.autoconfigure.exclude=",
    // 启用平台内部 MQ 配置
    "jushan.mq.rabbit.enabled=true"
})
@DisplayName("WebSocket 基础设施集成测试")
class WebSocketInfrastructureTest {

    @LocalServerPort
    private int port;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("jushan_platform_ws_test");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

    // ==================== ① STOMP 连接 ====================

    @Test
    @DisplayName("STOMP 连接 — WebSocket 端点可访问并建立连接")
    void shouldConnectToStompEndpoint() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws";
        CountDownLatch connectedLatch = new CountDownLatch(1);

        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connectedLatch.countDown();
            }
        };

        StompSession session = stompClient.connectAsync(url, handler).get(5, TimeUnit.SECONDS);
        assertThat(connectedLatch.await(3, TimeUnit.SECONDS))
                .as("STOMP 应在 3s 内建立连接").isTrue();
        assertThat(session.isConnected()).as("Session 应为已连接").isTrue();
        session.disconnect();
    }

    // ==================== ② STOMP 未认证连接（当前阶段放行） ====================

    @Test
    @DisplayName("未认证连接 — 当前阶段不应拒绝（T12 后改为拒绝）")
    void shouldAllowUnauthenticatedConnectionInCurrentPhase() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws";

        CountDownLatch connectedLatch = new CountDownLatch(1);
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connectedLatch.countDown();
            }
        };

        StompSession session = stompClient.connectAsync(url, handler).get(5, TimeUnit.SECONDS);
        assertThat(session.isConnected()).as("当前阶段未认证也应可连接").isTrue();
        session.disconnect();
    }

    // ==================== ③ STOMP 订阅 ====================

    @Test
    @DisplayName("STOMP 订阅 — 允许订阅公共 topic")
    void shouldAllowSubscriptionToPublicTopic() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws";

        CountDownLatch connectedLatch = new CountDownLatch(1);
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connectedLatch.countDown();
            }
        };

        StompSession session = stompClient.connectAsync(url, handler).get(5, TimeUnit.SECONDS);
        assertThat(connectedLatch.await(3, TimeUnit.SECONDS)).isTrue();

        StompSession.Subscription subscription = session.subscribe(
                "/topic/health",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // no-op
                    }
                }
        );

        assertThat(subscription).as("订阅 topic/health 应成功").isNotNull();
        subscription.unsubscribe();
        session.disconnect();
    }

    // ==================== ④ SockJS 降级支持 ====================

    @Test
    @DisplayName("SockJS — info 端点可用")
    void shouldProvideSockJsEndpoint() throws Exception {
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                .uri(java.net.URI.create("http://localhost:" + port + "/ws/info"))
                .GET()
                .build();

        java.net.http.HttpResponse<String> response = client.send(
                request, java.net.http.HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
    }

    // ==================== 辅助方法 ====================

    private WebSocketStompClient createStompClient() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new StringMessageConverter());
        return stompClient;
    }
}
