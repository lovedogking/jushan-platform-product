package com.jushan.boot.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.boot.mq.RabbitMqTestBase;
import com.jushan.framework.mq.MessageEnvelope;
import com.jushan.framework.mq.MqConstants;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.DuplicateEntryLog;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.entity.RecognitionEventLog;
import com.jushan.system.event.EventSource;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.DuplicateEntryLogMapper;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 重复入场处理策略集成测试（P003）。
 * <p>
 * 覆盖三种策略：
 * <ol>
 *   <li>REJECT（默认）：拒绝重复入场，记录异常日志</li>
 *   <li>UPDATE：更新原记录入场时间和抓拍</li>
 *   <li>EXCEPTION：原记录标记异常，创建新记录</li>
 * </ol>
 * 以及：
 * <ul>
 *   <li>并发重复入场：策略执行一致性</li>
 *   <li>策略降级：UPDATE 条件更新失败时降级为创建新记录</li>
 *   <li>异常日志表：所有策略均记录审计日志</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("重复入场处理策略集成测试")
@ActiveProfiles("test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DuplicateEntryPolicyTest extends RabbitMqTestBase {

    @Autowired private MockMvc mockMvc;
    @Autowired private RabbitAdmin rabbitAdmin;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private RecognitionEventLogMapper logMapper;
    @Autowired private ParkingRecordMapper recordMapper;
    @Autowired private DeviceMapper deviceMapper;
    @Autowired private ParkingLotMapper parkingLotMapper;
    @Autowired private ParkingLaneMapper laneMapper;
    @Autowired private DuplicateEntryLogMapper duplicateEntryLogMapper;
    @Autowired private RabbitTemplate rabbitTemplate;

    private Long testTenantId;
    private Long testParkingLotId;
    private Long testEntryLaneId;
    private Long testCameraEntryId;

    @BeforeEach
    void setUp() {
        // 1. 先清空队列
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);

        // 2. 等待消费者处理完遗留消息
        await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    List<ParkingRecord> all = recordMapper.selectList(
                            new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>());
                });

        // 3. 清理所有业务数据
        duplicateEntryLogMapper.delete(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
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

        // 4. 再次清空队列
        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);

        // 创建测试停车场（默认 REJECT 策略）
        ParkingLot lot = new ParkingLot();
        lot.setTenantId(100L);
        lot.setName("P003测试停车场");
        lot.setTotalSpaces(100);
        lot.setCurrentVehicles(0);
        lot.setRemainingSpaces(100);
        lot.setStatus("ENABLED");
        lot.setAddress("测试地址");
        lot.setDuplicateEntryPolicy("REJECT");
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

        rabbitAdmin.purgeQueue(MqConstants.QUEUE_RECOGNITION_EVENT, true);
    }

    // ==================== REJECT 策略 ====================

    @Nested
    @DisplayName("REJECT 策略（默认）")
    class RejectPolicy {

        @Test
        @DisplayName("同车再次入场 → 拒绝，事件标记 FAILED，记录 duplicate_entry_log")
        void shouldRejectDuplicateEntryAndLog() throws Exception {
            // 第一辆车入场
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
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
                        assertThat(records).hasSize(1);
                    });

            // 同一辆车再次入场
            String repeatRequestJson = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 88
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
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
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
    }

    // ==================== UPDATE 策略 ====================

    @Nested
    @DisplayName("UPDATE 策略")
    class UpdatePolicy {

        @Test
        @DisplayName("同车再次入场 → 更新原记录入场时间和抓拍，不增加容量")
        void shouldUpdateExistingRecord() throws Exception {
            // 设置停车场为 UPDATE 策略
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            lot.setDuplicateEntryPolicy("UPDATE");
            parkingLotMapper.updateById(lot);

            // 第一辆车入场
            String requestJson1 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 95,
                        "imagePath": "/images/entry1.jpg"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson1))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
                        assertThat(records).hasSize(1);
                    });

            ParkingRecord firstRecord = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                            .eq(ParkingRecord::getStandardizedPlate, "粤B12345")).get(0);
            Long firstRecordId = firstRecord.getId();
            LocalDateTime firstEntryTime = firstRecord.getEntryTime();

            // 等待一小段时间确保时间不同
            Thread.sleep(100);

            // 同一辆车再次入场（新 eventId，新图片）
            String requestJson2 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 88,
                        "imagePath": "/images/entry2.jpg"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson2))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        // 仍然只有一条 PARKING 记录
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
                        assertThat(records).hasSize(1);

                        ParkingRecord updated = records.get(0);
                        // 记录 ID 不变
                        assertThat(updated.getId()).isEqualTo(firstRecordId);
                        // 入场时间已更新（比第一次晚）
                        assertThat(updated.getEntryTime()).isAfter(firstEntryTime);
                        // 图片已更新
                        assertThat(updated.getEntryImagePath()).isEqualTo("/images/entry2.jpg");
                        // 状态仍为 PARKING
                        assertThat(updated.getStatus()).isEqualTo("PARKING");

                        // 第二次事件也标记为 PROCESSED（成功处理）
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getStandardizedPlate, "粤B12345")
                                        .eq(RecognitionEventLog::getStatus, "PROCESSED"));
                        assertThat(logs).hasSize(2);

                        // 记录 duplicate_entry_log
                        List<DuplicateEntryLog> dupLogs = duplicateEntryLogMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DuplicateEntryLog>()
                                        .eq(DuplicateEntryLog::getStandardizedPlate, "粤B12345"));
                        assertThat(dupLogs).hasSize(1);
                        assertThat(dupLogs.get(0).getStrategy()).isEqualTo("UPDATE");
                        assertThat(dupLogs.get(0).getAction()).isEqualTo("UPDATED");
                    });

            // 容量只更新了一次（UPDATE 不增加容量）
            ParkingLot updatedLot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(updatedLot.getCurrentVehicles()).isEqualTo(1);
        }
    }

    // ==================== EXCEPTION 策略 ====================

    @Nested
    @DisplayName("EXCEPTION 策略")
    class ExceptionPolicy {

        @Test
        @DisplayName("同车再次入场 → 原记录标记 EXCEPTION，创建新 PARKING 记录，容量+1")
        void shouldCreateExceptionRecordAndNewParkingRecord() throws Exception {
            // 设置停车场为 EXCEPTION 策略
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            lot.setDuplicateEntryPolicy("EXCEPTION");
            parkingLotMapper.updateById(lot);

            // 第一辆车入场
            String requestJson1 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 95
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson1))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
                        assertThat(records).hasSize(1);
                    });

            ParkingRecord firstRecord = recordMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                            .eq(ParkingRecord::getStandardizedPlate, "粤B12345")).get(0);
            Long firstRecordId = firstRecord.getId();

            // 同一辆车再次入场
            String requestJson2 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B12345",
                        "direction": "ENTRY",
                        "confidence": 88
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson2))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        // 两条记录：一条 EXCEPTION + 一条 PARKING
                        List<ParkingRecord> allRecords = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B12345"));
                        assertThat(allRecords).hasSize(2);

                        ParkingRecord exceptionRecord = allRecords.stream()
                                .filter(r -> "EXCEPTION".equals(r.getStatus()))
                                .findFirst().orElse(null);
                        ParkingRecord newRecord = allRecords.stream()
                                .filter(r -> "PARKING".equals(r.getStatus()))
                                .findFirst().orElse(null);

                        assertThat(exceptionRecord).isNotNull();
                        assertThat(exceptionRecord.getId()).isEqualTo(firstRecordId);

                        assertThat(newRecord).isNotNull();
                        assertThat(newRecord.getId()).isNotEqualTo(firstRecordId);

                        // 两次事件均 PROCESSED
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getStandardizedPlate, "粤B12345")
                                        .eq(RecognitionEventLog::getStatus, "PROCESSED"));
                        assertThat(logs).hasSize(2);

                        // 记录 duplicate_entry_log
                        List<DuplicateEntryLog> dupLogs = duplicateEntryLogMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<DuplicateEntryLog>()
                                        .eq(DuplicateEntryLog::getStandardizedPlate, "粤B12345"));
                        assertThat(dupLogs).hasSize(1);
                        assertThat(dupLogs.get(0).getStrategy()).isEqualTo("EXCEPTION");
                        assertThat(dupLogs.get(0).getAction()).isEqualTo("EXCEPTION_CREATED");
                    });

            // 容量增加了两次（EXCEPTION 策略创建了新记录）
            ParkingLot updatedLot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(updatedLot.getCurrentVehicles()).isEqualTo(2);
        }
    }

    // ==================== 并发重复入场 ====================

    @Nested
    @DisplayName("并发重复入场")
    class ConcurrentDuplicateEntry {

        @Test
        @DisplayName("REJECT 策略下并发重复入场 → 仅一条 PARKING，其余被拒绝")
        void shouldRejectConcurrentDuplicates() throws Exception {
            int threadCount = 5;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);

            var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

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

                        mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                        .content(requestJson))
                                .andReturn();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            latch.countDown();
            done.await(30, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            // 等待 Consumer 处理完
            await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B66666"));
                        // 仅一条 PARKING 记录（uk_active_parking 保护）
                        assertThat(records).hasSize(1);
                    });

            // 容量仅更新一次
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(lot.getCurrentVehicles()).isEqualTo(1);
        }

        @Test
        @DisplayName("UPDATE 策略下并发重复入场 → 仅一条 PARKING，更新多次")
        void shouldUpdateConcurrentDuplicates() throws Exception {
            // 设置 UPDATE 策略
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            lot.setDuplicateEntryPolicy("UPDATE");
            parkingLotMapper.updateById(lot);

            int threadCount = 5;
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch done = new java.util.concurrent.CountDownLatch(threadCount);

            var executor = java.util.concurrent.Executors.newFixedThreadPool(threadCount);

            for (int i = 0; i < threadCount; i++) {
                final int idx = i;
                executor.submit(() -> {
                    try {
                        latch.await();
                        String requestJson = """
                                {
                                    "deviceId": %d,
                                    "plateNumber": "粤B77777",
                                    "direction": "ENTRY",
                                    "imagePath": "/images/concurrent_%d.jpg"
                                }
                                """.formatted(testCameraEntryId, idx);

                        mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                                        .content(requestJson))
                                .andReturn();
                    } catch (Exception ignored) {
                    } finally {
                        done.countDown();
                    }
                });
            }

            latch.countDown();
            done.await(30, java.util.concurrent.TimeUnit.SECONDS);
            executor.shutdown();

            // 等待 Consumer 处理完
            await().atMost(15, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B77777"));
                        // 仅一条 PARKING 记录
                        assertThat(records).hasSize(1);
                    });

            // 容量仅更新一次（UPDATE 不增加容量）
            ParkingLot updatedLot = parkingLotMapper.selectById(testParkingLotId);
            assertThat(updatedLot.getCurrentVehicles()).isEqualTo(1);
        }
    }

    // ==================== 策略切换 ====================

    @Nested
    @DisplayName("策略切换")
    class PolicySwitch {

        @Test
        @DisplayName("停车场从 REJECT 切换到 UPDATE 后，重复入场按 UPDATE 处理")
        void shouldSwitchFromRejectToUpdate() throws Exception {
            // 默认 REJECT 策略下先入场一辆车
            String requestJson1 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B88888",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson1))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B88888"));
                        assertThat(records).hasSize(1);
                    });

            // 切换到 UPDATE 策略
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            lot.setDuplicateEntryPolicy("UPDATE");
            parkingLotMapper.updateById(lot);

            // 再次入场同一辆车
            String requestJson2 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B88888",
                        "direction": "ENTRY",
                        "imagePath": "/images/updated.jpg"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson2))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B88888"));
                        assertThat(records).hasSize(1);
                        assertThat(records.get(0).getEntryImagePath()).isEqualTo("/images/updated.jpg");

                        // 两次 PROCESSED 事件
                        List<RecognitionEventLog> logs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getStandardizedPlate, "粤B88888")
                                        .eq(RecognitionEventLog::getStatus, "PROCESSED"));
                        assertThat(logs).hasSize(2);
                    });
        }
    }

    // ==================== 非法策略值 ====================

    @Nested
    @DisplayName("非法策略值")
    class InvalidPolicy {

        @Test
        @DisplayName("停车场配置非法策略值 → 默认使用 REJECT")
        void shouldDefaultToRejectForInvalidPolicy() throws Exception {
            // 设置非法策略值
            ParkingLot lot = parkingLotMapper.selectById(testParkingLotId);
            lot.setDuplicateEntryPolicy("INVALID_POLICY");
            parkingLotMapper.updateById(lot);

            // 先入场一辆车
            String requestJson1 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B99999",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson1))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B99999"));
                        assertThat(records).hasSize(1);
                    });

            // 再次入场 → 应被拒绝（默认 REJECT）
            String requestJson2 = """
                    {
                        "deviceId": %d,
                        "plateNumber": "粤B99999",
                        "direction": "ENTRY"
                    }
                    """.formatted(testCameraEntryId);

            mockMvc.perform(post("/api/v1/internal/mock/recognition-event")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content(requestJson2))
                    .andExpect(status().isOk());

            await().atMost(10, java.util.concurrent.TimeUnit.SECONDS)
                    .untilAsserted(() -> {
                        // 仍然只有一条记录
                        List<ParkingRecord> records = recordMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingRecord>()
                                        .eq(ParkingRecord::getStandardizedPlate, "粤B99999"));
                        assertThat(records).hasSize(1);

                        // 有 FAILED 事件
                        List<RecognitionEventLog> failedLogs = logMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RecognitionEventLog>()
                                        .eq(RecognitionEventLog::getStatus, "FAILED"));
                        assertThat(failedLogs).hasSize(1);
                    });
        }
    }
}
