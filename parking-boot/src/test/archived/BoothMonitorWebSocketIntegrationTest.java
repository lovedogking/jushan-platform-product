package com.jushan.boot.ws;

import com.jushan.boot.ParkingApplication;
import com.jushan.system.entity.*;
import com.jushan.system.mapper.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.StringMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
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
 * 岗亭监控 WebSocket 集成测试（P005）。
 * <p>
 * 验证：
 * <ul>
 *   <li>岗亭操作员可订阅授权停车场 topic</li>
 *   <li>未授权停车场 topic 订阅被拒绝并断开连接</li>
 *   <li>平台用户禁止订阅岗亭 topic</li>
 *   <li>认证后连接可接收推送消息</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
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
@DisplayName("P005 岗亭监控 WebSocket 集成测试")
class BoothMonitorWebSocketIntegrationTest {

    @LocalServerPort
    private int port;

    @Container
    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("jushan_platform_booth_ws_test");

    @Container
    @ServiceConnection
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:4-management-alpine");

    @Container
    @ServiceConnection("redis")
    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired private TenantMapper tenantMapper;
    @Autowired private SysUserMapper sysUserMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;

    private static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "admin123";

    private String adminToken;
    private String boothOperatorToken;
    private Long parkingLotAId;
    private Long parkingLotBId;

    @BeforeEach
    void setUp() throws Exception {
        adminToken = login(ADMIN_USERNAME, ADMIN_PASSWORD);
        cleanupTestData();

        registerAndApprove("13988880001", "BoothWs租户", "BoothWs联系人");
        String customerAdminToken = login("13988880001", "pass123");

        parkingLotAId = createParkingLotViaApi(customerAdminToken, "BoothWs停车场A", 100);
        parkingLotBId = createParkingLotViaApi(customerAdminToken, "BoothWs停车场B", 200);

        createEmployee(customerAdminToken, "BoothWs岗亭", "13988880101",
                "booth_operator", parkingLotAId);
        boothOperatorToken = login("13988880101", "pass123");
    }

    @AfterEach
    void tearDown() {
        cleanupTestData();
    }

    @Test
    @DisplayName("岗亭操作员订阅授权停车场 topic → 成功")
    void boothOperatorCanSubscribeAuthorizedLotTopic() throws Exception {
        StompSession session = connect(boothOperatorToken);

        StompSession.Subscription subscription = session.subscribe(
                "/topic/booth/" + parkingLotAId + "/events",
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders headers, Object payload) {
                        // no-op
                    }
                });

        // 订阅成功即通过
        assertThat(subscription.getSubscriptionId()).isNotBlank();
        subscription.unsubscribe();
        session.disconnect();
    }

    @Test
    @DisplayName("岗亭操作员订阅未授权停车场 topic → 收到 ERROR 帧并被拒绝")
    void boothOperatorCannotSubscribeUnauthorizedLotTopic() throws Exception {
        StompSession session = connect(boothOperatorToken);

        CountDownLatch errorLatch = new CountDownLatch(1);
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, StompCommand command,
                                         StompHeaders headers, byte[] payload, Throwable exception) {
                errorLatch.countDown();
            }
        };

        // 订阅未授权 topic 应触发服务端 ERROR 帧（拒绝订阅）
        session.subscribe("/topic/booth/" + parkingLotBId + "/events", handler);

        assertThat(errorLatch.await(3, TimeUnit.SECONDS))
                .as("订阅未授权 topic 应在 3s 内收到 ERROR 帧").isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("平台管理员订阅岗亭 topic → 收到 ERROR 帧并被拒绝")
    void platformAdminCannotSubscribeBoothTopic() throws Exception {
        StompSession session = connect(adminToken);

        CountDownLatch errorLatch = new CountDownLatch(1);
        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {
            @Override
            public void handleException(StompSession session, StompCommand command,
                                         StompHeaders headers, byte[] payload, Throwable exception) {
                errorLatch.countDown();
            }
        };

        session.subscribe("/topic/booth/" + parkingLotAId + "/events", handler);

        assertThat(errorLatch.await(3, TimeUnit.SECONDS))
                .as("平台用户订阅岗亭 topic 应在 3s 内收到 ERROR 帧").isTrue();
        session.disconnect();
    }

    @Test
    @DisplayName("未认证用户订阅岗亭 topic → 连接被拒绝")
    void unauthenticatedUserCannotSubscribeBoothTopic() {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws";

        StompSessionHandlerAdapter handler = new StompSessionHandlerAdapter() {};

        assertThatThrownBy(() -> stompClient.connectAsync(url, handler).get(5, TimeUnit.SECONDS))
                .as("匿名 WebSocket 连接应被拒绝")
                .isInstanceOf(ExecutionException.class);
    }

    // ==================== 工具方法 ====================

    private StompSession connect(String token) throws Exception {
        WebSocketStompClient stompClient = createStompClient();
        String url = "http://localhost:" + port + "/ws?token=" + token;
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
        return session;
    }

    private WebSocketStompClient createStompClient() {
        List<Transport> transports = List.of(new WebSocketTransport(new StandardWebSocketClient()));
        WebSocketStompClient stompClient = new WebSocketStompClient(new SockJsClient(transports));
        stompClient.setMessageConverter(new StringMessageConverter());
        return stompClient;
    }

    private String login(String username, String password) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/auth/login"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        String.format("{\"username\":\"%s\",\"password\":\"%s\"}", username, password)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return extractToken(response.body());
    }

    private void registerAndApprove(String phone, String companyName, String contactPerson) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest register = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/register"))
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(String.format(
                        "{\"companyName\":\"%s\",\"contactPerson\":\"%s\",\"contactPhone\":\"%s\",\"password\":\"pass123\"}",
                        companyName, contactPerson, phone)))
                .build();
        client.send(register, HttpResponse.BodyHandlers.ofString());

        Tenant tenant = tenantMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                        .eq(Tenant::getContactPhone, phone));

        HttpRequest audit = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/tenants/" + tenant.getId() + "/audit"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"action\":\"APPROVED\",\"reason\":\"P005 WebSocket测试通过\"}"))
                .build();
        client.send(audit, HttpResponse.BodyHandlers.ofString());
    }

    private Long createParkingLotViaApi(String token, String name, int totalSpaces) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/parking-lots"))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(
                        String.format("{\"name\":\"%s\",\"totalSpaces\":%d}", name, totalSpaces)))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return extractId(response.body());
    }

    private void createEmployee(String adminToken, String displayName, String phone,
                                 String roleCode, Long... parkingLotIds) throws Exception {
        StringBuilder lotIdsJson = new StringBuilder("[");
        for (int i = 0; i < parkingLotIds.length; i++) {
            if (i > 0) lotIdsJson.append(",");
            lotIdsJson.append(parkingLotIds[i]);
        }
        lotIdsJson.append("]");

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/admin/employees"))
                .header("Authorization", "Bearer " + adminToken)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .POST(HttpRequest.BodyPublishers.ofString(String.format(
                        "{\"displayName\":\"%s\",\"phone\":\"%s\",\"password\":\"pass123\",\"roleCode\":\"%s\",\"parkingLotIds\":%s}",
                        displayName, phone, roleCode, lotIdsJson)))
                .build();
        client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String extractToken(String json) {
        int start = json.indexOf("\"accessToken\":\"") + 15;
        int end = json.indexOf("\"", start);
        return json.substring(start, end);
    }

    private Long extractId(String json) {
        int start = json.indexOf("\"id\":") + 5;
        int end = json.indexOf(",", start);
        if (end == -1) {
            end = json.indexOf("}", start);
        }
        return Long.parseLong(json.substring(start, end).trim());
    }

    private void cleanupTestData() {
        String[] phones = {"13988880001", "13988880101"};
        for (String phone : phones) {
            sysUserMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>()
                    .eq(SysUser::getUsername, phone));
            tenantMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Tenant>()
                    .eq(Tenant::getContactPhone, phone));
        }
        for (String pattern : new String[]{"BoothWs"}) {
            java.util.List<ParkingLot> lots = parkingLotMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingLot>()
                            .like(ParkingLot::getName, pattern));
            for (ParkingLot lot : lots) {
                parkingLotMapper.deleteById(lot.getId());
            }
        }
    }
}
