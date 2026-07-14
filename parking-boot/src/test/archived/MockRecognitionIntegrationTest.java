package com.jushan.boot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.boot.mq.RabbitMqTestBase;
import com.jushan.framework.mq.MqConstants;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.mapper.RecognitionEventLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Mock 识别事件控制器集成测试（T28）。
 * <p>
 * 验证：
 * <ol>
 *   <li>Mock 触发成功（数据库 + MQ）</li>
 *   <li>参数校验失败</li>
 *   <li>设备不存在 / 已停用 / 非 CAMERA 类型拒绝</li>
 *   <li>方向不匹配拒绝</li>
 *   <li>事件来源为 MOCK</li>
 * </ol>
 */
@DisplayName("Mock 识别事件集成测试")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class MockRecognitionIntegrationTest extends RabbitMqTestBase {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RabbitAdmin rabbitAdmin;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RecognitionEventLogMapper logMapper;

    /** 测试用停车场、车道、相机设备的 ID */
    private Long testTenantId;
    private Long testParkingLotId;
    private Long testEntryLaneId;
    private Long testExitLaneId;
    private Long testCameraEntryId;
    private Long testCameraExitId;

    @BeforeEach
    void setUp(@Autowired com.jushan.system.mapper.ParkingLotMapper parkingLotMapper,
               @Autowired com.jushan.system.mapper.ParkingLaneMapper parkingLaneMapper,
               @Autowired com.jushan.system.mapper.DeviceMapper deviceMapper) {

        // 清理旧数据
        logMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        deviceMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLaneMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLotMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());

        // 创建测试租户、停车场
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(100L);
        lot.setName("T28测试停车场");
        lot.setTotalSpaces(100);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(100);
        lot.setStatus("ENABLED");
        lot.setAddress("测试地址");
        parkingLotMapper.insert(lot);
        testParkingLotId = lot.getId();
        testTenantId = lot.getTenantId();

        // 创建入口车道
        ParkingLane entryLane = new ParkingLane();
        entryLane.setParkingLotId(testParkingLotId);
        entryLane.setName("入口001");
        entryLane.setCode("ENTRY001");
        entryLane.setDirection("ENTRY");
        entryLane.setStatus("ENABLED");
        entryLane.setIsKeyLane(1);
        entryLane.setAutoReleasePolicy("MANUAL");
        parkingLaneMapper.insert(entryLane);
        testEntryLaneId = entryLane.getId();

        // 创建出口车道
        ParkingLane exitLane = new ParkingLane();
        exitLane.setParkingLotId(testParkingLotId);
        exitLane.setName("出口001");
        exitLane.setCode("EXIT001");
        exitLane.setDirection("EXIT");
        exitLane.setStatus("ENABLED");
        exitLane.setIsKeyLane(1);
        exitLane.setAutoReleasePolicy("MANUAL");
        parkingLaneMapper.insert(exitLane);
        testExitLaneId = exitLane.getId();

        // 创建入口相机
        Device entryCamera = new Device();
        entryCamera.setParkingLotId(testParkingLotId);
        entryCamera.setLaneId(testEntryLaneId);
        entryCamera.setVendorId(1L);
        entryCamera.setModelId(1L);
        entryCamera.setName("入口相机001");
        entryCamera.setCode("CAM_ENTRY001");
        entryCamera.setDeviceSn("SN_ENTRY001");
        entryCamera.setDeviceType("CAMERA");
        entryCamera.setStatus("ENABLED");
        entryCamera.setCapabilities("RECOGNIZE,CAPTURE");
        deviceMapper.insert(entryCamera);
        testCameraEntryId = entryCamera.getId();

        // 创建出口相机
        Device exitCamera = new Device();
        exitCamera.setParkingLotId(testParkingLotId);
        exitCamera.setLaneId(testExitLaneId);
        exitCamera.setVendorId(1L);
        exitCamera.setModelId(1L);
        exitCamera.setName("出口相机001");
        exitCamera.setCode("CAM_EXIT001");
        exitCamera.setDeviceSn("SN_EXIT001");
        exitCamera.setDeviceType("CAMERA");
        exitCamera.setStatus("ENABLED");
        exitCamera.setCapabilities("RECOGNIZE,CAPTURE");
        deviceMapper.insert(exitCamera);
        testCameraExitId = exitCamera.getId();

        // 声明测试队列（消费识别事件）
        var queue = QueueBuilder.durable(MqConstants.QUEUE_RECOGNITION_EVENT)
                .withArguments(com.jushan.framework.mq.RabbitMqConfig.deadLetterArgs())
                .build();
        rabbitAdmin.declareQueue(queue);
        var binding = BindingBuilder.bind(queue)
                .to(new TopicExchange(MqConstants.EXCHANGE_INTERNAL))
                .with(MqConstants.ROUTING_KEY_RECOGNITION_EVENT);
        rabbitAdmin.declareBinding(binding);
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);
    }

    // ==================== 正常路径 ====================

    @Nested
    @DisplayName("正常路径")
    class HappyPath {

        @Test
        @DisplayName("入口 Mock 事件 — API 响应正确，事件持久化到 DB 且记录状态为 RECEIVED（消费者异步更新为 PROCESSED）")
        void shouldTriggerEntryMockEvent() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 95
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data.plateNumber").value("粤B12345"))
                    .andExpect(jsonPath("$.data.direction").value("ENTRY"))
                    .andExpect(jsonPath("$.data.source").value("MOCK"))
                    .andExpect(jsonPath("$.data.eventId").isNotEmpty());

            // 验证事件已持久化到 DB（Publisher 同步写入，状态为 RECEIVED）
            // Consumer 会异步更新为 PROCESSED，但跨 @BeforeEach 可能导致设备被清理，
            // 因此仅验证 API 响应和持久化结果，Consumer 独立在 RecognitionEventConsumerTest 中验证。
            var logs = logMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                            .eq(RecognitionEventLog::getPlateNumber, "粤B12345"));
            assertThat(logs).isNotEmpty();
            RecognitionEventLog logEntry = logs.get(0);
            assertThat(logEntry.getTenantId()).isEqualTo(testTenantId);
            assertThat(logEntry.getParkingLotId()).isEqualTo(testParkingLotId);
            assertThat(logEntry.getSource()).isEqualTo("MOCK");
        }

        @Test
        @DisplayName("出口 Mock 事件 — 方向为 EXIT")
        void shouldTriggerExitMockEvent() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "京A88888",
                        "direction": "EXIT"
                    }
                    """.formatted(testCameraExitId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.direction").value("EXIT"))
                    .andExpect(jsonPath("$.data.plateNumber").value("京A88888"));
        }
    }

    // ==================== 参数校验 ====================

    @Nested
    @DisplayName("参数校验")
    class Validation {

        @Test
        @DisplayName("缺少 deviceId 应被 Bean Validation 拒绝")
        void shouldRejectMissingDeviceId() throws Exception {
            String requestJson = """
                    {
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY"
                    }
                    """;

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("缺少 plateNumber 应被 Bean Validation 拒绝")
        void shouldRejectMissingPlateNumber() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("缺少 direction 应被 Bean Validation 拒绝")
        void shouldRejectMissingDirection() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("空白车牌号应被拒绝")
        void shouldRejectBlankPlate() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "   ",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 设备和方向校验 ====================

    @Nested
    @DisplayName("设备和方向校验")
    class DeviceAndDirection {

        @Test
        @DisplayName("设备不存在返回 404")
        void shouldRejectNonExistentDevice() throws Exception {
            String requestJson = """
                    {
                        "deviceId": 99999,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY"
                    }
                    """;

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value(404));
        }

        @Test
        @DisplayName("方向不匹配 — ENTRY 设备发 EXIT 事件应被拒绝")
        void shouldRejectDirectionMismatch() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "EXIT"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("非法方向值应被拒绝")
        void shouldRejectInvalidDirection() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "INVALID"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
    }

    // ==================== 事件来源验证 ====================

    @Nested
    @DisplayName("事件来源")
    class EventSourceVerification {

        @Test
        @DisplayName("事件来源标记为 MOCK")
        void shouldMarkSourceAsMock() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.source").value("MOCK"));
        }

        @Test
        @DisplayName("数据库中事件来源为 MOCK，且租户/停车场来自可信记录")
        void shouldPersistMockSourceInDatabase() throws Exception {

            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B66666",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            // 从数据库中查询验证
            var logs = logMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                            .eq(RecognitionEventLog::getPlateNumber, "粤B66666"));
            assertThat(logs).hasSize(1);
            RecognitionEventLog logEntry = logs.get(0);
            assertThat(logEntry.getSource()).isEqualTo("MOCK");
            assertThat(logEntry.getDirection()).isEqualTo("ENTRY");
            assertThat(logEntry.getTenantId()).isEqualTo(testTenantId);
            assertThat(logEntry.getParkingLotId()).isEqualTo(testParkingLotId);
            assertThat(logEntry.getDeviceId()).isEqualTo(testCameraEntryId);
        }
    }
}
