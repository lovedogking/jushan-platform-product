package com.jushan.boot.service;

import com.jushan.boot.test.TestcontainersBaseTest;
import com.jushan.system.entity.BillingRule;
import com.jushan.system.entity.BillingRuleVersion;
import com.jushan.system.entity.Device;
import com.jushan.system.entity.ExitRecord;
import com.jushan.system.entity.ParkingLane;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.entity.ParkingRecord;
import com.jushan.system.event.EventSource;
import com.jushan.system.event.RecognitionEventPayload;
import com.jushan.system.mapper.BillingRuleMapper;
import com.jushan.system.mapper.BillingRuleVersionMapper;
import com.jushan.system.mapper.DeviceMapper;
import com.jushan.system.mapper.ExitRecordMapper;
import com.jushan.system.mapper.ParkingLaneMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.service.ExitResult;
import com.jushan.system.service.ExitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 出场流程集成测试（P004）。
 * <p>
 * 基于 Testcontainers MySQL，验证出口识别事件 → 匹配停车记录 → 计费 →
 * 生成订单 → 出场记录的完整数据链路。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@DisplayName("出场流程集成测试")
class ExitFlowIntegrationTest extends TestcontainersBaseTest {

    @Autowired
    private ExitService exitService;

    @Autowired
    private ParkingLotMapper parkingLotMapper;

    @Autowired
    private ParkingLaneMapper laneMapper;

    @Autowired
    private DeviceMapper deviceMapper;

    @Autowired
    private ParkingRecordMapper recordMapper;

    @Autowired
    private ExitRecordMapper exitRecordMapper;

    @Autowired
    private ParkingOrderMapper orderMapper;

    @Autowired
    private BillingRuleMapper billingRuleMapper;

    @Autowired
    private BillingRuleVersionMapper versionMapper;

    private Long tenantId;
    private Long parkingLotId;
    private Long exitLaneId;
    private Long exitDeviceId;
    private Long entryDeviceId;
    private static final String PLATE = "粤B12345";

    @BeforeEach
    void setUp() {
        exitRecordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        orderMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        recordMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        deviceMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        laneMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        versionMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        billingRuleMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());
        parkingLotMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<>());

        ParkingLot lot = new ParkingLot();
        lot.setTenantId(100L);
        lot.setName("P004测试停车场");
        lot.setTotalSpaces(100);
        lot.setCurrentVehicles(1);
        lot.setRemainingSpaces(99);
        lot.setStatus("ENABLED");
        parkingLotMapper.insert(lot);
        parkingLotId = lot.getId();
        tenantId = lot.getTenantId();

        ParkingLane exitLane = new ParkingLane();
        exitLane.setParkingLotId(parkingLotId);
        exitLane.setName("出口001");
        exitLane.setCode("EXIT001");
        exitLane.setDirection("EXIT");
        exitLane.setStatus("ENABLED");
        exitLane.setIsKeyLane(1);
        exitLane.setAutoReleasePolicy("MANUAL");
        laneMapper.insert(exitLane);
        exitLaneId = exitLane.getId();

        Device entryDevice = new Device();
        entryDevice.setParkingLotId(parkingLotId);
        entryDevice.setVendorId(1L);
        entryDevice.setModelId(1L);
        entryDevice.setName("入口相机");
        entryDevice.setCode("CAM_ENTRY");
        entryDevice.setDeviceSn("SN_ENTRY");
        entryDevice.setDeviceType("CAMERA");
        entryDevice.setStatus("ENABLED");
        entryDevice.setCapabilities("RECOGNIZE,CAPTURE");
        deviceMapper.insert(entryDevice);
        entryDeviceId = entryDevice.getId();

        Device exitDevice = new Device();
        exitDevice.setParkingLotId(parkingLotId);
        exitDevice.setLaneId(exitLaneId);
        exitDevice.setVendorId(1L);
        exitDevice.setModelId(1L);
        exitDevice.setName("出口相机");
        exitDevice.setCode("CAM_EXIT");
        exitDevice.setDeviceSn("SN_EXIT");
        exitDevice.setDeviceType("CAMERA");
        exitDevice.setStatus("ENABLED");
        exitDevice.setCapabilities("RECOGNIZE,CAPTURE");
        deviceMapper.insert(exitDevice);
        exitDeviceId = exitDevice.getId();
    }

    @Test
    @DisplayName("有在场记录 + NO_FEE 规则 → 零元放行，停车记录完成")
    void shouldReleaseForNoFeeRule() {
        createNoFeeRule();
        ParkingRecord record = createParkingRecord();

        LocalDateTime exitTime = record.getEntryTime().plusHours(2);
        ExitResult result = exitService.handleExit(
                exitPayload(exitDeviceId, exitLaneId, PLATE, exitTime), PLATE);

        assertThat(result.isAllowExit()).isTrue();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);
        assertThat(result.getFeeCents()).isZero();

        ParkingRecord updated = recordMapper.selectById(record.getId());
        assertThat(updated.getStatus()).isEqualTo("COMPLETED");
        assertThat(updated.getExitTime()).isEqualToIgnoringNanos(exitTime);

        List<ParkingOrder> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getParkingRecordId, record.getId()));
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getAmountCents()).isZero();
        assertThat(orders.get(0).getStatus()).isEqualTo(ParkingOrder.STATUS_COMPLETED);

        List<ExitRecord> exits = exitRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExitRecord>()
                        .eq(ExitRecord::getParkingRecordId, record.getId()));
        assertThat(exits).hasSize(1);
        assertThat(exits.get(0).getReleaseDecision()).isEqualTo(ExitRecord.DECISION_ZERO_FEE);
        assertThat(exits.get(0).getFeeCents()).isZero();

        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        assertThat(lot.getCurrentVehicles()).isZero();
        assertThat(lot.getRemainingSpaces()).isEqualTo(100);
    }

    @Test
    @DisplayName("有在场记录 + HOURLY 规则 → 生成待支付订单，不放行")
    void shouldHoldForPendingPayment() {
        createHourlyRule();
        ParkingRecord record = createParkingRecord();

        LocalDateTime exitTime = record.getEntryTime().plusHours(2);
        ExitResult result = exitService.handleExit(
                exitPayload(exitDeviceId, exitLaneId, PLATE, exitTime), PLATE);

        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_PENDING_PAYMENT);
        assertThat(result.getFeeCents()).isPositive();

        ParkingRecord updated = recordMapper.selectById(record.getId());
        assertThat(updated.getStatus()).isEqualTo("PARKING");
        assertThat(updated.getExitTime()).isNull();

        List<ParkingOrder> orders = orderMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ParkingOrder>()
                        .eq(ParkingOrder::getParkingRecordId, record.getId()));
        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getAmountCents()).isEqualTo(result.getFeeCents());
        assertThat(orders.get(0).getStatus()).isEqualTo(ParkingOrder.STATUS_PENDING);

        ParkingLot lot = parkingLotMapper.selectById(parkingLotId);
        assertThat(lot.getCurrentVehicles()).isEqualTo(1);
        assertThat(lot.getRemainingSpaces()).isEqualTo(99);
    }

    @Test
    @DisplayName("无在场记录 → NO_RECORD 出场记录")
    void shouldCreateNoRecordExit() {
        LocalDateTime exitTime = LocalDateTime.now();
        ExitResult result = exitService.handleExit(
                exitPayload(exitDeviceId, exitLaneId, "粤B99999", exitTime), "粤B99999");

        assertThat(result.isAllowExit()).isFalse();
        assertThat(result.getDecisionCode()).isEqualTo(ExitRecord.DECISION_NO_RECORD);

        List<ExitRecord> exits = exitRecordMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ExitRecord>()
                        .eq(ExitRecord::getStandardizedPlate, "粤B99999"));
        assertThat(exits).hasSize(1);
        assertThat(exits.get(0).getReleaseDecision()).isEqualTo(ExitRecord.DECISION_NO_RECORD);
    }

    private void createNoFeeRule() {
        BillingRule rule = new BillingRule();
        rule.setTenantId(tenantId);
        rule.setParkingLotId(parkingLotId);
        rule.setName("免费规则");
        rule.setRuleType(BillingRule.RULE_TYPE_NO_FEE);
        rule.setStatus(BillingRule.STATUS_ENABLED);
        rule.setIsDefault(1);
        rule.setCreatedBy(1L);
        billingRuleMapper.insert(rule);

        BillingRuleVersion version = new BillingRuleVersion();
        version.setRuleId(rule.getId());
        version.setTenantId(tenantId);
        version.setParkingLotId(parkingLotId);
        version.setVersion(1);
        version.setIsActive(1);
        version.setConfig("{\"type\":\"NO_FEE\"}");
        version.setCreatedBy(1L);
        versionMapper.insert(version);
    }

    private void createHourlyRule() {
        BillingRule rule = new BillingRule();
        rule.setTenantId(tenantId);
        rule.setParkingLotId(parkingLotId);
        rule.setName("按时长规则");
        rule.setRuleType(BillingRule.RULE_TYPE_HOURLY);
        rule.setStatus(BillingRule.STATUS_ENABLED);
        rule.setIsDefault(1);
        rule.setCreatedBy(1L);
        billingRuleMapper.insert(rule);

        BillingRuleVersion version = new BillingRuleVersion();
        version.setRuleId(rule.getId());
        version.setTenantId(tenantId);
        version.setParkingLotId(parkingLotId);
        version.setVersion(1);
        version.setIsActive(1);
        version.setConfig("{\"type\":\"HOURLY\",\"freeMinutes\":0,\"firstPeriod\":60,\"firstAmount\":500,\"unitPeriod\":30,\"unitAmount\":200}");
        version.setFreeMinutes(0);
        version.setFirstPeriod(60);
        version.setFirstAmount(500);
        version.setUnitPeriod(30);
        version.setUnitAmount(200);
        version.setCreatedBy(1L);
        versionMapper.insert(version);
    }

    private ParkingRecord createParkingRecord() {
        ParkingRecord record = new ParkingRecord();
        record.setTenantId(tenantId);
        record.setParkingLotId(parkingLotId);
        record.setDeviceId(entryDeviceId);
        record.setStandardizedPlate(PLATE);
        record.setStatus("PARKING");
        record.setEntryTime(LocalDateTime.of(2026, 7, 12, 9, 0, 0));
        recordMapper.insert(record);
        return record;
    }

    private RecognitionEventPayload exitPayload(Long deviceId, Long laneId, String plate,
                                                 LocalDateTime exitTime) {
        return RecognitionEventPayload.of(UUID.randomUUID().toString(), plate, "EXIT", EventSource.MOCK)
                .tenantId(tenantId)
                .parkingLotId(parkingLotId)
                .deviceId(deviceId)
                .laneId(laneId)
                .eventTime(exitTime)
                .logId(999L); // 测试用 logId，真实场景由 Publisher 设置
    }
}
