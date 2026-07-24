package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.system.dto.FeePreviewRequest;
^import com.jushan.platform.modules.parking.entity.BillingRuleVersion;
^import com.jushan.platform.modules.parking.entity.ParkingLot;
^import com.jushan.platform.modules.parking.entity.ParkingOrder;
^import com.jushan.platform.modules.parking.entity.ParkingRecord;
^import com.jushan.platform.modules.vehicle.entity.Vehicle;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.mapper.ParkingRecordMapper;
import com.jushan.system.mapper.PlateBindingMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.jushan.system.vo.ParkingFeeVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * ParkingFeeService 单元测试（P007）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class ParkingFeeServiceTest {

    @Mock
    private ParkingRecordMapper parkingRecordMapper;
    @Mock
    private ParkingOrderMapper parkingOrderMapper;
    @Mock
    private VehicleMapper vehicleMapper;
    @Mock
    private PlateBindingMapper plateBindingMapper;
    @Mock
    private ParkingLotMapper parkingLotMapper;
    @Mock
    private BillingEngine billingEngine;
    @Mock
    private ParkingLotScopeResolver parkingLotScopeResolver;
    @Mock
    private WxUserService wxUserService;

    private ParkingFeeService parkingFeeService;

    @BeforeEach
    void setUp() {
        parkingFeeService = new ParkingFeeService(
                parkingRecordMapper, parkingOrderMapper, vehicleMapper,
                plateBindingMapper, parkingLotMapper, billingEngine,
                parkingLotScopeResolver, wxUserService);
    }

    // ==================== 小程序端 ====================

    @Test
    @DisplayName("小程序按车牌查询成功")
    void shouldQueryFeeByPlateForWx() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));
        Vehicle vehicle = vehicle(plate);
        BillingRuleVersion version = ruleVersion(30, 500);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);
        when(parkingRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(billingEngine.calculateFee(eq(1L), eq(record.getEntryTime()), any(LocalDateTime.class))).thenReturn(500);
        when(billingEngine.findActiveVersion(1L)).thenReturn(version);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot(1L, "测试停车场"));

        ParkingFeeVO vo = parkingFeeService.queryByPlateForWx(plate);

        assertThat(vo.getRecordId()).isEqualTo(1L);
        assertThat(vo.getFeeCents()).isEqualTo(500);
        assertThat(vo.getPlate()).isEqualTo(plate);
        assertThat(vo.getRuleVersion()).isEqualTo("v3");
        assertThat(vo.getPayable()).isTrue();
    }

    @Test
    @DisplayName("小程序按车牌查询，车牌未绑定 -> FORBIDDEN")
    void shouldRejectUnboundPlateForWx() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        Vehicle vehicle = vehicle(plate);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> parkingFeeService.queryByPlateForWx(plate))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
                });
    }

    @Test
    @DisplayName("小程序按记录 ID 查询成功")
    void shouldQueryFeeByRecordIdForWx() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));
        Vehicle vehicle = vehicle(plate);
        BillingRuleVersion version = ruleVersion(30, 500);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);
        when(billingEngine.calculateFee(eq(1L), eq(record.getEntryTime()), any(LocalDateTime.class))).thenReturn(500);
        when(billingEngine.findActiveVersion(1L)).thenReturn(version);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot(1L, "测试停车场"));

        ParkingFeeVO vo = parkingFeeService.queryByRecordIdForWx(1L);

        assertThat(vo.getFeeCents()).isEqualTo(500);
        assertThat(vo.getPayable()).isTrue();
    }

    @Test
    @DisplayName("小程序按记录 ID 查询，车牌不属于当前用户 -> FORBIDDEN")
    void shouldRejectCrossUserRecordForWx() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));
        Vehicle vehicle = vehicle(plate);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(0L);

        assertThatThrownBy(() -> parkingFeeService.queryByRecordIdForWx(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
                });
    }

    // ==================== 岗亭端 ====================

    @Test
    @DisplayName("岗亭按车牌查询成功")
    void shouldQueryFeeByPlateForBooth() {
        String plate = "京A12345";
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));
        BillingRuleVersion version = ruleVersion(30, 500);

        when(parkingRecordMapper.selectList(any())).thenReturn(List.of(record));
        when(billingEngine.calculateFee(eq(1L), eq(record.getEntryTime()), any(LocalDateTime.class))).thenReturn(500);
        when(billingEngine.findActiveVersion(1L)).thenReturn(version);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot(1L, "测试停车场"));

        ParkingFeeVO vo = parkingFeeService.queryByPlateForBooth(plate, 1L);

        assertThat(vo.getFeeCents()).isEqualTo(500);
        assertThat(vo.getParkingLotId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("岗亭按车牌查询，停车场未授权 -> FORBIDDEN")
    void shouldRejectUnauthorizedParkingLotForBooth() {
        String plate = "京A12345";
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该停车场"))
                .when(parkingLotScopeResolver).validateAccess(1L);

        assertThatThrownBy(() -> parkingFeeService.queryByPlateForBooth(plate, 1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
                });
    }

    @Test
    @DisplayName("岗亭按记录 ID 查询，跨停车场 -> FORBIDDEN")
    void shouldRejectCrossParkingLotRecordForBooth() {
        ParkingRecord record = parkingRecord(1L, 1L, "京A12345", ParkingRecord.STATUS_PARKING,
                LocalDateTime.now().minusMinutes(90));

        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        doThrow(new BusinessException(CommonErrorCode.FORBIDDEN, "无权访问该停车场"))
                .when(parkingLotScopeResolver).validateAccess(1L);

        assertThatThrownBy(() -> parkingFeeService.queryByRecordIdForBooth(1L))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(CommonErrorCode.FORBIDDEN.getCode());
                });
    }

    // ==================== 状态分支 ====================

    @Test
    @DisplayName("已离场记录查询返回订单金额")
    void shouldReturnOrderAmountForCompletedRecord() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        LocalDateTime entryTime = LocalDateTime.now().minusHours(3);
        LocalDateTime exitTime = LocalDateTime.now().minusMinutes(30);
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_COMPLETED, entryTime);
        record.setExitTime(exitTime);
        Vehicle vehicle = vehicle(plate);
        ParkingOrder order = new ParkingOrder();
        order.setId(10L);
        order.setAmountCents(1200);
        order.setStatus(ParkingOrder.STATUS_COMPLETED);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);
        when(parkingOrderMapper.selectOne(any())).thenReturn(order);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot(1L, "测试停车场"));

        ParkingFeeVO vo = parkingFeeService.queryByRecordIdForWx(1L);

        assertThat(vo.getFeeCents()).isEqualTo(1200);
        assertThat(vo.getPayable()).isFalse();
        assertThat(vo.getDurationMinutes()).isEqualTo(150L);
    }

    @Test
    @DisplayName("已取消记录查询费用为 0")
    void shouldReturnZeroForCancelledRecord() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_CANCELLED,
                LocalDateTime.now().minusHours(1));
        Vehicle vehicle = vehicle(plate);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot(1L, "测试停车场"));

        ParkingFeeVO vo = parkingFeeService.queryByRecordIdForWx(1L);

        assertThat(vo.getFeeCents()).isZero();
        assertThat(vo.getPayable()).isFalse();
        assertThat(vo.getDurationMinutes()).isZero();
    }

    // ==================== 结算预览 ====================

    @Test
    @DisplayName("结算预览费用与计费引擎一致")
    void shouldPreviewFeeConsistentWithBillingEngine() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        LocalDateTime entryTime = LocalDateTime.now().minusMinutes(90);
        LocalDateTime previewExitTime = LocalDateTime.now();
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_PARKING, entryTime);
        Vehicle vehicle = vehicle(plate);
        BillingRuleVersion version = ruleVersion(30, 500);
        FeePreviewRequest request = new FeePreviewRequest();
        request.setRecordId(1L);
        request.setPreviewExitTime(previewExitTime);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);
        when(billingEngine.calculateFee(1L, entryTime, previewExitTime)).thenReturn(500);
        when(billingEngine.findActiveVersion(1L)).thenReturn(version);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot(1L, "测试停车场"));

        ParkingFeeVO vo = parkingFeeService.previewForWx(request);

        assertThat(vo.getFeeCents()).isEqualTo(500);
        assertThat(vo.getPreviewExitTime()).isEqualTo(previewExitTime);
    }

    @Test
    @DisplayName("已离场记录不允许预览")
    void shouldRejectPreviewForCompletedRecord() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_COMPLETED,
                LocalDateTime.now().minusHours(1));
        record.setExitTime(LocalDateTime.now());
        Vehicle vehicle = vehicle(plate);
        FeePreviewRequest request = new FeePreviewRequest();
        request.setRecordId(1L);

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> parkingFeeService.previewForWx(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已离场");
    }

    @Test
    @DisplayName("预览出场时间早于入场时间 -> PARAM_ERROR")
    void shouldRejectPreviewExitTimeBeforeEntry() {
        Long wxUserId = 100L;
        String plate = "京A12345";
        LocalDateTime entryTime = LocalDateTime.now();
        ParkingRecord record = parkingRecord(1L, 1L, plate, ParkingRecord.STATUS_PARKING, entryTime);
        Vehicle vehicle = vehicle(plate);
        FeePreviewRequest request = new FeePreviewRequest();
        request.setRecordId(1L);
        request.setPreviewExitTime(entryTime.minusMinutes(10));

        when(wxUserService.getCurrentWxUserId()).thenReturn(wxUserId);
        when(parkingRecordMapper.selectById(1L)).thenReturn(record);
        when(vehicleMapper.selectOne(any())).thenReturn(vehicle);
        when(plateBindingMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> parkingFeeService.previewForWx(request))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> {
                    BusinessException be = (BusinessException) e;
                    assertThat(be.getErrorCode().getCode()).isEqualTo(CommonErrorCode.PARAM_ERROR.getCode());
                });
    }

    // ==================== 辅助方法 ====================

    private ParkingRecord parkingRecord(Long id, Long parkingLotId, String plate, String status,
                                         LocalDateTime entryTime) {
        ParkingRecord record = new ParkingRecord();
        record.setId(id);
        record.setParkingLotId(parkingLotId);
        record.setStandardizedPlate(plate);
        record.setStatus(status);
        record.setEntryTime(entryTime);
        return record;
    }

    private Vehicle vehicle(String plate) {
        Vehicle vehicle = new Vehicle();
        vehicle.setId(1L);
        vehicle.setVehiclePlate(plate);
        vehicle.setStatus(Vehicle.STATUS_ACTIVE);
        return vehicle;
    }

    private BillingRuleVersion ruleVersion(int freeMinutes, int firstAmount) {
        BillingRuleVersion version = new BillingRuleVersion();
        version.setId(1L);
        version.setRuleId(1L);
        version.setParkingLotId(1L);
        version.setVersion(3);
        version.setIsActive(1);
        version.setFreeMinutes(freeMinutes);
        version.setFirstAmount(firstAmount);
        return version;
    }

    private ParkingLot parkingLot(Long id, String name) {
        ParkingLot lot = new ParkingLot();
        lot.setId(id);
        lot.setName(name);
        return lot;
    }
}
