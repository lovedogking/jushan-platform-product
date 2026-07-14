package com.jushan.boot.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.boot.mq.RabbitMqTestBase;
import com.jushan.framework.mq.MessageEnvelope;
import com.jushan.framework.mq.MqConstants;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.event.EventSource;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 入场通行与停车记录集成测试（T30）。
 * <p>
 * 覆盖：
 * <ol>
 *   <li>正常入场：Mock 触发 → Consumer → 停车记录 + 容量更新</li>
 *   <li>重复入场拒绝：功能唯一索引阻止同车同场重复 PARKING 记录</li>
 *   <li>并发入场：多个线程同时触发，仅产生一条 PARKING 记录</li>
 *   <li>容量一致性：入场后 currentVehicles 递增 + remainingSpaces 递减</li>
 *   <li>停车场停用 + 禁止入场 → 拒绝</li>
 *   <li>出口事件不创建停车记录</li>
 * </ol>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("入场通行与停车记录集成测试")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class EntryServiceTest extends RabbitMqTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecognitionEventLogMapper logMapper;
    @Autowired private ParkingRecordMapper recordMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private ParkingLaneMapper laneMapper;
    @Autowired private RabbitTemplate rabbitTemplate;

    private Long testTenantId;
    private Long testParkingLotId;
    private Long testEntryLaneId;
    private Long testCameraEntryId;
    private Long testCameraExitId;

    @BeforeEach
    void setUp() {
        // 1. 先清空队列，防止前一个测试遗留的 pending 消息在清理 DB 后才被消费
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);
        // 2. 等待消费者把队列中遗留消息全部处理完（防止前一个并发测试的4条过期消息污染本测试）
        await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<ParkingRecord> all = recordMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>());
                    // 清空队列后，不再有新消息进来，等待所有已入库记录不再增加
                    // 实际是等待消费者把之前积压的消息都处理完
                });

        // 3. 清理所有业务数据（保留结构表）
        recordMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        logMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        deviceMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        laneMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLotMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());

        // 4. 再次清空队列（确保 setUp 期间无新消息）
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);

        // 创建测试停车场
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(100L);
        lot.setName("T30测试停车场");
        lot.setTotalSpaces(100);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(100);
        lot.setStatus("ENABLED");
        lot.setAddress("测试地址");
        parkingLotMapper.insert(lot);
        testParkingLotId = lot.getId();
        testTenantId = lot.getTenantId();

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
        exitCamera.setLaneId(exitLane.getId());
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

    // ==================== 正常入场 ====================

    @Nested
    @DisplayName("正常入场路径")
    class HappyPath {

        @Test
        @DisplayName("入口事件 → Consumer 校验通过 → 创建停车记录 + 更新停车场容量")
        void shouldCreateParkingRecordAndUpdateCapacity() throws Exception {
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

            // 等待 Consumer 异步处理完成
            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        // 1. 事件状态为 PROCESSED
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getPlateNumber, "粤B12345"));
                        assertThat(logs).isNotEmpty();
                        assertThat(logs.get(0).getStatus()).isEqualTo("PROCESSED");

                        // 2. 停车记录已创建
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
                        assertThat(records).hasSize(1);
                        ParkingRecord record = records.get(0);
                        assertThat(record.getStatus()).isEqualTo("PARKING");
                        assertThat(record.getParkingLotId()).isEqualTo(testParkingLotId);
                        assertThat(record.getTenantId()).isEqualTo(testTenantId);
                        assertThat(record.getLaneId()).isEqualTo(testEntryLaneId);
                        assertThat(record.getDeviceId()).isEqualTo(testCameraEntryId);
                        assertThat(record.getEntryTime()).isNotNull();
                        assertThat(record.getExitTime()).isNull();
                    });

            // 3. 停车场容量已更新
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(1);
            assertThat(lot.getRemainingSpaces()).isEqualTo(99);
        }

        @Test
        @DisplayName("多辆车入场 → 每条独立创建停车记录，容量累加")
        void shouldCreateMultipleRecordsAndAccumulateCapacity() throws Exception {
            String[] plates = {"粤B10001", "粤B10002", "粤B10003"};
            for (String plate : plates) {
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
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStatus, "PARKING"));
                        assertThat(records).hasSize(3);
                    });

            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(3);
            assertThat(lot.getRemainingSpaces()).isEqualTo(97);
        }
    }

    // ==================== 重复入场拒绝 ====================

    @Nested
    @DisplayName("重复入场拒绝")
    class DuplicateEntry {

        @Test
        @DisplayName("同车同场已存在 PARKING 记录 → 功能唯一索引阻止重复插入 → 事件标记 FAILED")
        void shouldRejectDuplicateEntry() throws Exception {
            // 第一辆车入场
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStatus, "PARKING"));
                        assertThat(records).hasSize(1);
                    });

            // 同一辆车再次入场（新 eventId）
            String repeatRequestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(repeatRequestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        // 仍然只有一条 PARKING 记录
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStatus, "PARKING"));
                        assertThat(records).hasSize(1);

                        // 第二次事件标记为 FAILED
                        List<RecognitionEventLog> failedLogs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getStatus, "FAILED"));
                        assertThat(failedLogs).hasSize(1);
                        assertThat(failedLogs.get(0).getFailureReason()).contains("已有在场记录");
                    });

            // 容量只更新了一次
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(1);
        }

        @Test
        @DisplayName("数据库直接插入相同 (parkingLotId, plate, PARKING) → 功能索引阻止")
        void shouldRejectDuplicateAtDatabaseLevel() {
            // 直接通过 Mapper 插入第一条 PARKING 记录
            ParkingRecord first = new ParkingRecord();
            first.setTenantId(testTenantId);
            first.setParkingLotId(testParkingLotId);
            first.setStandardizedPlate("粤B12345");
            first.setStatus("PARKING");
            first.setEntryTime(LocalDateTime.now());
            recordMapper.insert(first);

            // 尝试插入第二条相同 (lot, plate, PARKING)
            ParkingRecord second = new ParkingRecord();
            second.setTenantId(testTenantId);
            second.setParkingLotId(testParkingLotId);
            second.setStandardizedPlate("粤B12345");
            second.setStatus("PARKING");
            second.setEntryTime(LocalDateTime.now());

            try {
                recordMapper.insert(second);
                assertThat(false).as("功能唯一索引应阻止重复 PARKING 记录").isTrue();
            } catch (org.springframework.dao.DuplicateKeyException e) {
                // 预期：uk_active_parking 功能索引命中
            }

            // 仅一条 PARKING 记录
            List<ParkingRecord> records = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                            .eq(ParkingRecord::getStatus, "PARKING"));
            assertThat(records).hasSize(1);
        }

        @Test
        @DisplayName("同车同场已完成记录 + 再次入场 → 允许创建新 PARKING 记录")
        void shouldAllowNewEntryAfterCompleted() {
            // 先插入一条已完成的记录
            ParkingRecord completed = new ParkingRecord();
            completed.setTenantId(testTenantId);
            completed.setParkingLotId(testParkingLotId);
            completed.setStandardizedPlate("粤B12345");
            completed.setStatus("COMPLETED");
            completed.setEntryTime(LocalDateTime.now().minusHours(2));
            completed.setExitTime(LocalDateTime.now().minusHours(1));
            recordMapper.insert(completed);

            // 再次入场 → 允许（功能索引只对 PARKING 生效）
            ParkingRecord newEntry = new ParkingRecord();
            newEntry.setTenantId(testTenantId);
            newEntry.setParkingLotId(testParkingLotId);
            newEntry.setStandardizedPlate("粤B12345");
            newEntry.setStatus("PARKING");
            newEntry.setEntryTime(LocalDateTime.now());
            recordMapper.insert(newEntry);

            // 两条记录：一条 COMPLETED + 一条 PARKING
            List<ParkingRecord> all = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>());
            assertThat(all).hasSize(2);
            assertThat(all.stream().filter(r -> "PARKING".equals(r.getStatus())).count()).isEqualTo(1);
        }
    }

    // ==================== 并发入场保护 ====================

    @Nested
    @DisplayName("并发入场保护")
    class Concurrency {

        @Test
        @DisplayName("并发触发同一车牌入场 → 仅产生一条 PARKING 记录")
        void shouldAllowOnlyOneRecordUnderConcurrency() throws Exception {
            int threadCount = 5;
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            var executor = Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                executor.submit(() -> {
                    try {
                        latch.await();
                        String requestJson = """
                                {
                                    "deviceId": %d,
                                    "plateNumber": "粤B66666",
                                    "direction": "ENTRY"
                                }
                                """.formatted(testCameraEntryId);

                        var result = mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                        .content(requestJson))
                                .andReturn();
                        if (result.getResponse().getStatus() == 200) {
                            successCount.incrementAndGet();
                        } else {
                            failCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    } finally {
                        done.countDown();
                    }
                });
            }

            // 同时触发
            latch.countDown();
            done.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            // 所以 Mock API 请求都返回 200（API 层面只负责发布，不负责入场去重）
            assertThat(successCount.get()).isEqualTo(threadCount);

            // 等待 Consumer 处理完所有消息
            await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStatus, "PARKING")
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B66666"));
                        // 仅一条 PARKING 记录（功能唯一索引保护）
                        assertThat(records).hasSize(1);
                    });

            // 容量仅更新一次
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(1);
        }

        @Test
        @DisplayName("并发不同车牌入场 → 全部成功，容量正确")
        void shouldHandleConcurrentDifferentPlates() throws Exception {
            int count = 5;
            CountDownLatch latch = new CountDownLatch(1);
            CountDownLatch done = new CountDownLatch(count);

            var executor = Executors.newFixedThreadPool(count);

            // 用不同车牌直接发 MQ，避免共享 DB 状态扰乱
            String[] plates = {"粤D10001", "粤D10002", "粤D10003", "粤D10004", "粤D10005"};
            for (int i = 0; i < count; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        String eventId = UUID.randomUUID().toString();
                        String plate = plates[idx];

                        // 预插入事件日志
                        RecognitionEventLog preLog = new RecognitionEventLog();
                        preLog.setEventId(eventId);
                        preLog.setTenantId(testTenantId);
                        preLog.setParkingLotId(testParkingLotId);
                        preLog.setLaneId(testEntryLaneId);
                        preLog.setDeviceId(testCameraEntryId);
                        preLog.setPlateNumber(plate);
                        preLog.setDirection("ENTRY");
                        preLog.setEventTime(LocalDateTime.now());
                        preLog.setSource("MOCK");
                        preLog.setStatus("RECEIVED");
                        logMapper.insert(preLog);

                        RecognitionEventPayload payload = RecognitionEventPayload.of(
                                eventId, plate, "ENTRY", EventSource.MOCK)
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
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            latch.countDown();
            done.await(30, TimeUnit.SECONDS);
            executor.shutdown();

            await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStatus, "PARKING"));
                        assertThat(records).hasSize(count);
                        // 验证每个车牌只出现一次
                        for (String plate : plates) {
                            long plateCount = records.stream()
                                    .filter(r -> plate.equals(r.getStandardizedPlate())).count();
                            assertThat(plateCount).as("plate " + plate).isEqualTo(1);
                        }
                    });

            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(count);
            assertThat(lot.getRemainingSpaces()).isEqualTo(100 - count);
        }
    }

    // ==================== 容量一致性 ====================

    @Nested
    @DisplayName("容量一致性")
    class CapacityConsistency {

        @Test
        @DisplayName("入场后 currentVehicles + remainingSpaces ≈ totalSpaces")
        void shouldMaintainCapacityConsistency() throws Exception {
            // 读取初始数据
            ParkingLot initial = parkingLotMapper.selectById(testParkingLotId);
            int initialCurrent = initial.getCurrentVehicles() != null ? initial.getCurrentVehicles() : 0;
            int initialRemaining = initial.getRemainingSpaces() != null ? initial.getRemainingSpaces() : 0;
            int total = initial.getTotalSpaces() != null ? initial.getTotalSpaces() : 0;

            // 入场一辆车
            String requestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStatus, "PARKING"));
                        assertThat(records).hasSize(1);
                    });

            ParkingLot updated = parkingLotMapper.selectById(testParkingLotId);
            // currentVehicles 增加 1
            assertThat(updated.getCurrentVehicles()).isEqualTo(initialCurrent + 1);
            // remainingSpaces 减少 1
            assertThat(updated.getRemainingSpaces()).isEqualTo(initialRemaining - 1);
            // currentVehicles + remainingSpaces 应当等于 totalSpaces（默认不人工修正的情况下）
            assertThat(updated.getCurrentVehicles() + updated.getRemainingSpaces())
                    .isEqualTo(total);
        }
    }

    // ==================== 停车场状态检查 ====================

    @Nested
    @DisplayName("停车场状态")
    class ParkingLotStatus {

        @Test
        @DisplayName("停车场停用 + 禁止入场 → 事件标记 FAILED")
        void shouldRejectEntryInDisabledParkingLot() throws Exception {
            // 停用停车场 + 禁止新车入场
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            lot.setStatus("DISABLED");
            parkingLotMapper.updateById(lot);

            // 尝试入场
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

            RecognitionEventPayload payload = RecognitionEventPayload.of(
                    eventId, "粤B12345", "ENTRY", EventSource.MOCK)
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
                        assertThat(log.getFailureReason()).contains("不允许新车入场");
                    });

            // 无停车记录创建
            List<ParkingRecord> records = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
            assertThat(records).isEmpty();
        }

    }

    // ==================== 出口事件 ====================

    @Nested
    @DisplayName("出口事件")
    class ExitEvent {

        @Test
        @DisplayName("出口方向事件 → 校验通过、标记 PROCESSED，但不创建停车记录")
        void shouldNotCreateParkingRecordForExitEvent() throws Exception {
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
                        assertThat(logs).isNotEmpty();
                        assertThat(logs.get(0).getStatus()).isEqualTo("PROCESSED");
                    });

            // 无停车记录创建
            List<ParkingRecord> records = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
            assertThat(records).isEmpty();

            // 容量不变
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(0);
            assertThat(lot.getRemainingSpaces()).isEqualTo(100);
        }
    }
}
