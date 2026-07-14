package com.jushan.boot.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.boot.mq.RabbitMqTestBase;
import com.jushan.framework.mq.MessageEnvelope;
import com.jushan.framework.mq.MqConstants;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.event.EventSource;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.RecognitionEventLogMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 识别事件消费者集成测试（T29）。
 * <p>
 * 覆盖：
 * <ol>
 *   <li>正常消费：Mock 触发 → MQ → Consumer 更新状态为 PROCESSED</li>
 *   <li>车牌标准化：去空格、大写转换</li>
 *   <li>业务级幂等：相同 eventId 不产生重复记录</li>
 *   <li>校验拒绝：设备不存在、跨停车场、方向不匹配</li>
 *   <li>并发：多个不同事件并发消费</li>
 * </ol>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("识别事件消费者集成测试")
@ActiveProfiles("test")
@AutoConfigureMockMvc
class RecognitionEventConsumerTest extends RabbitMqTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecognitionEventLogMapper logMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private ParkingLaneMapper laneMapper;
    @Autowired private RabbitTemplate rabbitTemplate;

    private Long testTenantId;
    private Long testParkingLotId;
    private Long testEntryLaneId;
    private Long testExitLaneId;
    private Long testCameraEntryId;
    private Long testCameraExitId;

    /** 另一个租户的停车场（用于跨停车场测试） */
    private Long otherParkingLotId;

    @BeforeEach
    void setUp() {
        // 1. 先清空队列，防止前一个测试遗留的 pending 消息在清理 DB 后才被消费
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);

        // 2. 清理所有业务数据（保留结构表）
        logMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        deviceMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        laneMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLotMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());

        // 3. 再次清空队列（确保 setUp 期间无新消息）
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);

        // 主停车场（租户100）
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(100L);
        lot.setName("T29主停车场");
        lot.setTotalSpaces(100);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(100);
        lot.setStatus("ENABLED");
        lot.setAddress("测试地址");
        parkingLotMapper.insert(lot);
        testParkingLotId = lot.getId();
        testTenantId = lot.getTenantId();

        // 另一个租户的停车场（租户200）
        ParkingLot otherLot = new ParkingLot();
        otherLot.setTenantId(200L);
        otherLot.setName("T29其他停车场");
        otherLot.setTotalSpaces(50);
        otherLot.setCurrentVehicles(0);
        otherLot.setRemainingSpaces(50);
        otherLot.setStatus("ENABLED");
        otherLot.setAddress("其他地址");
        parkingLotMapper.insert(otherLot);
        otherParkingLotId = otherLot.getId();

        // 入口车道
        ParkingLane entryLane = new ParkingLane();
        entryLane.setParkingLotId(testParkingLotId);
        entryLane.setName("入口001");
        entryLane.setCode("ENTRY001");
        entryLane.setDirection("ENTRY");
        entryLane.setStatus("ENABLED");
        entryLane.setIsKeyLane(1);
        entryLane.setAutoReleasePolicy("MANUAL");
        laneMapper.insert(entryLane);
        testEntryLaneId = entryLane.getId();

        // 出口车道
        ParkingLane exitLane = new ParkingLane();
        exitLane.setParkingLotId(testParkingLotId);
        exitLane.setName("出口001");
        exitLane.setCode("EXIT001");
        exitLane.setDirection("EXIT");
        exitLane.setStatus("ENABLED");
        exitLane.setIsKeyLane(1);
        exitLane.setAutoReleasePolicy("MANUAL");
        laneMapper.insert(exitLane);
        testExitLaneId = exitLane.getId();

        // 入口相机
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

        // 出口相机
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

        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);
    }

    // ==================== 正常消费 ====================

    @Nested
    @DisplayName("正常消费路径")
    class HappyPath {

        @Test
        @DisplayName("Mock 触发 → Consumer 消费 → 状态更新为 PROCESSED + 车牌标准化")
        void shouldProcessEventAndUpdateStatus() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 95
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .orderByDesc(RecognitionEventLog::getId));
                        assertThat(logs).isNotEmpty();
                        RecognitionEventLog last = logs.get(0);
                        assertThat(last.getStatus()).isEqualTo("PROCESSED");
                        assertThat(last.getStandardizedPlate()).isEqualTo("粤B12345");
                    });
        }

        @Test
        @DisplayName("车牌含空格和大小写混合，Consumer 应标准化为统一格式")
        void shouldStandardizePlateNumber() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "  粤b 1 2 3 4 5  ",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .orderByDesc(RecognitionEventLog::getId));
                        assertThat(logs).isNotEmpty();
                        RecognitionEventLog last = logs.get(0);
                        assertThat(last.getStatus()).isEqualTo("PROCESSED");
                        assertThat(last.getStandardizedPlate()).isEqualTo("粤B12345");
                        assertThat(last.getPlateNumber()).isEqualTo("粤B12345");
                    });
        }
    }

    // ==================== 幂等 ====================

    @Nested
    @DisplayName("幂等")
    class Idempotency {

        @Test
        @DisplayName("相同 eventId 重复插入 → 数据库唯一约束阻止（业务幂等最终保障）")
        void shouldRejectDuplicateEventId() throws Exception {
            String eventId = UUID.randomUUID().toString();

            // 第一次插入
            RecognitionEventLog first = new RecognitionEventLog();
            first.setEventId(eventId);
            first.setTenantId(testTenantId);
            first.setParkingLotId(testParkingLotId);
            first.setLaneId(testEntryLaneId);
            first.setDeviceId(testCameraEntryId);
            first.setPlateNumber("粤C_IDEM_001");
            first.setDirection("ENTRY");
            first.setEventTime(LocalDateTime.now());
            first.setSource("MOCK");
            first.setStatus("RECEIVED");
            logMapper.insert(first);

            // 第二次插入相同 eventId → 应抛唯一约束异常
            RecognitionEventLog second = new RecognitionEventLog();
            second.setEventId(eventId);
            second.setTenantId(testTenantId);
            second.setParkingLotId(testParkingLotId);
            second.setLaneId(testEntryLaneId);
            second.setDeviceId(testCameraEntryId);
            second.setPlateNumber("粤A00001");
            second.setDirection("ENTRY");
            second.setEventTime(LocalDateTime.now());
            second.setSource("MOCK");
            second.setStatus("RECEIVED");

            try {
                logMapper.insert(second);
                assertThat(false).as("应抛出唯一约束异常").isTrue();
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 预期：uk_event_id 约束命中
            }
        }

        @Test
        @DisplayName("MQ 重复投递 → Consumer 跳过（消息级幂等 + 业务幂等双重保护）")
        void shouldSkipDuplicateMessage() throws Exception {
            // 直接通过 MQ 发布两次相同 eventId 的消息
            String eventId = UUID.randomUUID().toString();

            // 先插入预记录（模拟 Publisher）
            RecognitionEventLog preLog = new RecognitionEventLog();
            preLog.setEventId(eventId);
            preLog.setTenantId(testTenantId);
            preLog.setParkingLotId(testParkingLotId);
            preLog.setLaneId(testEntryLaneId);
            preLog.setDeviceId(testCameraEntryId);
            preLog.setPlateNumber("粤C_DUP_001");
            preLog.setDirection("ENTRY");
            preLog.setEventTime(LocalDateTime.now());
            preLog.setSource("MOCK");
            preLog.setStatus("RECEIVED");
            logMapper.insert(preLog);

            // 发布第一条消息
            RecognitionEventPayload payload = RecognitionEventPayload.of(
                    eventId, "粤C_DUP_001", "ENTRY", EventSource.MOCK)
                    .deviceId(testCameraEntryId)
                    .laneId(testEntryLaneId)
                    .parkingLotId(testParkingLotId)
                    .tenantId(testTenantId);

            MessageEnvelope<RecognitionEventPayload> envelope1 = MessageEnvelope
                    .of(MqConstants.TYPE_RECOGNITION_EVENT, payload)
                    .tenantId(testTenantId)
                    .parkingLotId(testParkingLotId);

            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_INTERNAL,
                    MqConstants.ROUTING_KEY_RECOGNITION_EVENT,
                    envelope1);

            // 等待第一条消息处理完成（消息级幂等标记完成）
            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        RecognitionEventLog log = logMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getEventId, eventId));
                        assertThat(log).isNotNull();
                        assertThat(log.getStatus()).isEqualTo("PROCESSED");
                    });

            // 发布第二条消息（相同 eventId，不同 messageId → 消息级幂等不命中，
            // 但第一条消息已标记幂等，第二条消息应被 Consumer 的 isProcessed 检查跳过）
            MessageEnvelope<RecognitionEventPayload> envelope2 = MessageEnvelope
                    .of(MqConstants.TYPE_RECOGNITION_EVENT, payload)
                    .tenantId(testTenantId)
                    .parkingLotId(testParkingLotId);

            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_INTERNAL,
                    MqConstants.ROUTING_KEY_RECOGNITION_EVENT,
                    envelope2);

            // 等待第二条消息被幂等跳过
            Thread.sleep(2000);

            // 断言：仍然只有一条记录，且状态未被覆盖
            List<RecognitionEventLog> logs = logMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                            .eq(RecognitionEventLog::getEventId, eventId));
            assertThat(logs).hasSize(1);
            assertThat(logs.get(0).getStatus()).isEqualTo("PROCESSED");
        }
    }

    // ==================== 校验拒绝 ====================

    @Nested
    @DisplayName("校验拒绝")
    class ValidationRejection {

        @Test
        @DisplayName("设备不存在 → Consumer 标记 FAILED + 记录原因")
        void shouldRejectNonexistentDevice() throws Exception {
            String eventId = UUID.randomUUID().toString();

            RecognitionEventLog preLog = new RecognitionEventLog();
            preLog.setEventId(eventId);
            preLog.setTenantId(testTenantId);
            preLog.setParkingLotId(testParkingLotId);
            preLog.setDeviceId(99999L);
            preLog.setPlateNumber("粤B12345");
            preLog.setDirection("ENTRY");
            preLog.setEventTime(LocalDateTime.now());
            preLog.setSource("MOCK");
            preLog.setStatus("RECEIVED");
            logMapper.insert(preLog);

            RecognitionEventPayload payload = RecognitionEventPayload.of(
                    eventId, "粤B12345", "ENTRY", EventSource.MOCK)
                    .deviceId(99999L)
                    .parkingLotId(testParkingLotId)
                    .tenantId(testTenantId);
            MessageEnvelope<RecognitionEventPayload> envelope = MessageEnvelope
                    .of(MqConstants.TYPE_RECOGNITION_EVENT, payload)
                    .tenantId(testTenantId)
                    .parkingLotId(testParkingLotId);
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_INTERNAL,
                    MqConstants.ROUTING_KEY_RECOGNITION_EVENT,
                    envelope);

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        RecognitionEventLog log = logMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getEventId, eventId));
                        assertThat(log).isNotNull();
                        assertThat(log.getStatus()).isEqualTo("FAILED");
                        assertThat(log.getFailureReason()).contains("设备不存在");
                    });
        }

        @Test
        @DisplayName("跨停车场越权 → Consumer 标记 FAILED + 记录原因")
        void shouldRejectCrossParkingLot() throws Exception {
            String eventId = UUID.randomUUID().toString();

            RecognitionEventLog preLog = new RecognitionEventLog();
            preLog.setEventId(eventId);
            preLog.setTenantId(testTenantId);
            preLog.setParkingLotId(testParkingLotId);
            preLog.setDeviceId(testCameraEntryId);
            preLog.setPlateNumber("粤B12345");
            preLog.setDirection("ENTRY");
            preLog.setEventTime(LocalDateTime.now());
            preLog.setSource("MOCK");
            preLog.setStatus("RECEIVED");
            logMapper.insert(preLog);

            // 消息声称属于 otherParkingLotId（越权）
            RecognitionEventPayload payload = RecognitionEventPayload.of(
                    eventId, "粤B12345", "ENTRY", EventSource.MOCK)
                    .deviceId(testCameraEntryId)
                    .parkingLotId(otherParkingLotId)
                    .tenantId(testTenantId);
            MessageEnvelope<RecognitionEventPayload> envelope = MessageEnvelope
                    .of(MqConstants.TYPE_RECOGNITION_EVENT, payload)
                    .tenantId(testTenantId)
                    .parkingLotId(otherParkingLotId);
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_INTERNAL,
                    MqConstants.ROUTING_KEY_RECOGNITION_EVENT,
                    envelope);

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        RecognitionEventLog log = logMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getEventId, eventId));
                        assertThat(log).isNotNull();
                        assertThat(log.getStatus()).isEqualTo("FAILED");
                        assertThat(log.getFailureReason()).contains("停车场不匹配");
                    });
        }

        @Test
        @DisplayName("方向不匹配 → Consumer 标记 FAILED")
        void shouldRejectDirectionMismatch() throws Exception {
            String eventId = UUID.randomUUID().toString();

            RecognitionEventLog preLog = new RecognitionEventLog();
            preLog.setEventId(eventId);
            preLog.setTenantId(testTenantId);
            preLog.setParkingLotId(testParkingLotId);
            preLog.setLaneId(testEntryLaneId);
            preLog.setDeviceId(testCameraEntryId);
            preLog.setPlateNumber("粤B12345");
            preLog.setDirection("EXIT");
            preLog.setEventTime(LocalDateTime.now());
            preLog.setSource("MOCK");
            preLog.setStatus("RECEIVED");
            logMapper.insert(preLog);

            RecognitionEventPayload payload = RecognitionEventPayload.of(
                    eventId, "粤B12345", "EXIT", EventSource.MOCK)
                    .deviceId(testCameraEntryId)
                    .laneId(testEntryLaneId)
                    .parkingLotId(testParkingLotId)
                    .tenantId(testTenantId);
            MessageEnvelope<RecognitionEventPayload> envelope = MessageEnvelope
                    .of(MqConstants.TYPE_RECOGNITION_EVENT, payload)
                    .tenantId(testTenantId)
                    .parkingLotId(testParkingLotId);
            rabbitTemplate.convertAndSend(
                    MqConstants.EXCHANGE_INTERNAL,
                    MqConstants.ROUTING_KEY_RECOGNITION_EVENT,
                    envelope);

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        RecognitionEventLog log = logMapper.selectOne(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getEventId, eventId));
                        assertThat(log).isNotNull();
                        assertThat(log.getStatus()).isEqualTo("FAILED");
                        assertThat(log.getFailureReason()).contains("方向不匹配");
                    });
        }
    }

    // ==================== 并发 ====================

    @Nested
    @DisplayName("并发")
    class Concurrency {

        @Test
        @DisplayName("多个不同事件并发消费，互不干扰，各自处理完成")
        void shouldHandleConcurrentEvents() throws Exception {
            int eventCount = 5;
            for (int i = 0; i < eventCount; i++) {
                String requestJson = """
                        {
                            "deviceId": %d,
                            "plateNumber": "粤A%d",
                            "direction": "ENTRY"
                        }
                        """.formatted(testCameraEntryId, 10000 + i);
                mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(requestJson))
                        .andExpect(status().isOk());
            }

            await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>());
                        assertThat(logs).hasSize(eventCount);
                        for (RecognitionEventLog log : logs) {
                            assertThat(log.getStatus())
                                    .as("事件状态应为 PROCESSED")
                                    .isEqualTo("PROCESSED");
                        }
                    });
        }

        @Test
        @DisplayName("并发下每条事件的标准化车牌均正确")
        void shouldStandardizeAllPlatesUnderConcurrency() throws Exception {
            int count = 3;
            for (int i = 0; i < count; i++) {
                String plate = "粤C" + (10000 + i);
                String requestJson = """
                        {
                            "deviceId": %d,
                            "plateNumber": "%s",
                            "direction": "ENTRY"
                        }
                        """.formatted(testCameraEntryId, plate);
                mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                .content(requestJson))
                        .andExpect(status().isOk());
            }

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .likeRight(RecognitionEventLog::getPlateNumber, "粤C"));
                        assertThat(logs).hasSize(count);
                        for (RecognitionEventLog log : logs) {
                            assertThat(log.getStandardizedPlate()).isEqualTo(log.getPlateNumber());
                        }
                    });
        }
    }

    // ==================== 边界场景 ====================

    @Nested
    @DisplayName("边界场景")
    class EdgeCases {

        @Test
        @DisplayName("空车牌 → Mock API 在标准化阶段拒绝（T29）")
        void shouldRejectEmptyPlateAtEntry() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "   ",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("全角字符车牌 → Consumer 转换为半角后处理")
        void shouldHandleFullWidthCharacters() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤Ｂ１２３４５",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .orderByDesc(RecognitionEventLog::getId));
                        assertThat(logs).isNotEmpty();
                        RecognitionEventLog last = logs.get(0);
                        assertThat(last.getStandardizedPlate()).isEqualTo("粤B12345");
                    });
        }

        @Test
        @DisplayName("出口相机正常处理 EXIT 方向事件")
        void shouldProcessExitEvent() throws Exception {
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "京A88888",
                        "direction": "EXIT"
                    }
                    """.formatted(testCameraExitId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getPlateNumber, "京A88888"));
                        assertThat(logs).hasSize(1);
                        assertThat(logs.get(0).getStatus()).isEqualTo("PROCESSED");
                        assertThat(logs.get(0).getDirection()).isEqualTo("EXIT");
                    });
        }
    }
}
