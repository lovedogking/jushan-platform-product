package com.jushan.boot.ws;

import com.jushan.boot.ParkingApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.containers.RabbitMQContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * WebSocket 基础设施集成测试（FIX-03）。
 * <p>
 * 通过 STOMP over WebSocket 端到端验证<b>认证后</b>连接、鉴权和订阅。
 * T12 登录已实现，匿名 WebSocket 连接不再放行。
 * <p>
 * 附带 MySQL + RabbitMQ 容器，满足完整基础设施依赖。
 */
@SpringBootTest(
    classes = ParkingApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
    "spring.autoconfigure.exclude=",
    "jushan.mq.rabbit.enabled=true"
})
@DisplayName("WebSocket 基础设施集成测试（FIX-03：认证后连接）")
class WebSocketInfrastructureTest {

    @LocalServerPort
    private int port;

    private String adminToken;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("jushan_platform_ws_test");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @BeforeEach
    void setUp() throws Exception {
        // 登录获取 token 用于 WebSocket 认证
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"username\":\"super_admin\",\"password\":\"admin123\"}"))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("登录应成功").isEqualTo(200);
        String body = response.body();
        int start = body.indexOf("\"token\":\"") + 9;
        int end = body.indexOf("\"", start);
        assertThat(start).as("响应中应包含 token").isGreaterThan(8);
        adminToken = body.substring(start, end);
    }

    // ==================== ① 认证 STOMP 连接 ====================

    @Test
    @DisplayName("认证 STOMP 连接 — 有效 token 可建立 WebSocket 连接")
    void shouldConnectToStompEndpoint() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws?token=" + adminToken;
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

    // ==================== ② 未认证连接拒绝（FIX-03） ====================

    @Test
    @DisplayName("未认证连接拒绝 — T12 后匿名 WebSocket 连接应被拒绝")
    void shouldRejectUnauthenticatedConnection() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws";

        CountDownLatch connectedLatch = new CountDownLatch(1);
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void afterConnected(StompSession session, StompHeaders connectedHeaders) {
                connectedLatch.countDown();
            }
        };

        // FIX-03：匿名连接应被拒绝
        assertThatThrownBy(() -> stompClient.connectAsync(url, handler).get(5, TimeUnit.SECONDS))
                .as("匿名 WebSocket 连接应被拒绝")
                .isInstanceOf(ExecutionException.class);
    }

    // ==================== ③ 认证后订阅公共 topic ====================

    @Test
    @DisplayName("认证后 STOMP 订阅 — 允许订阅公共 topic")
    void shouldAllowSubscriptionToPublicTopic() throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws?token=" + adminToken;

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
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/ws/info"))
                .GET()
                .build();

        HttpResponse<String> response = client.send(
                request, HttpResponse.BodyHandlers.ofString());

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
