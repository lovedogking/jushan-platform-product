package com.jushan.system.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.BusinessException;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.parking.entity.ParkingSpacePolicy;
import com.jushan.platform.modules.parking.entity.ParkingZone;
import com.jushan.platform.modules.parking.mapper.ParkingSpacePolicyMapper;
import com.jushan.platform.modules.parking.mapper.ParkingZoneMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.system.dto.FixedSpaceCreateRequest;
import com.jushan.system.dto.FixedSpaceRenewRequest;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.entity.ParkingLot;
import com.jushan.system.entity.ParkingOrder;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.ParkingOrderMapper;
import com.jushan.system.vo.FixedSpaceVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * {@link FixedSpaceService} 单元测试（Phase 1 A2）。
 * <p>
 * 覆盖：列表查询、到期预警、绑定、续期、注销、重复校验。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class FixedSpaceServiceTest {

    @Mock
    private FixedSpaceBindingMapper bindingMapper;
    @Mock
    private SysVehicleMapper vehicleMapper;
    @Mock
    private ParkingLotMapper parkingLotMapper;
    @Mock
    private ParkingZoneMapper zoneMapper;
    @Mock
    private ParamResolver paramResolver;
    @Mock
    private ParkingSpacePolicyMapper spacePolicyMapper;
    @Mock
    private ParkingOrderMapper parkingOrderMapper;

    private FixedSpaceService fixedSpaceService;

    private FixedSpaceBinding activeBinding;
    private SysVehicle vehicle;
    private ParkingLot parkingLot;

    @BeforeEach
    void setUp() {
        fixedSpaceService = new FixedSpaceService(
                bindingMapper, vehicleMapper, parkingLotMapper, zoneMapper,
                paramResolver, spacePolicyMapper, parkingOrderMapper);

        // 设置测试租户上下文
        TenantContext.set(new TenantContext.Snapshot(1L, 100L, "TENANT", "", ""));

        vehicle = new SysVehicle();
        vehicle.setId(10L);
        vehicle.setTenantId(1L);
        vehicle.setParkingLotId(1L);
        vehicle.setPlateNumber("京A12345");
        vehicle.setVehicleType(SysVehicle.TYPE_FREE);
        vehicle.setStatus(SysVehicle.STATUS_ACTIVE);
        vehicle.setCreatedAt(LocalDateTime.now());

        parkingLot = new ParkingLot();
        parkingLot.setId(1L);
        parkingLot.setName("测试车场");

        activeBinding = new FixedSpaceBinding();
        activeBinding.setId(100L);
        activeBinding.setTenantId(1L);
        activeBinding.setParkingLotId(1L);
        activeBinding.setSpaceNo("A-001");
        activeBinding.setVehicleId(10L);
        activeBinding.setValidStart(LocalDate.of(2026, 1, 1));
        activeBinding.setValidEnd(LocalDate.of(2026, 12, 31));
        activeBinding.setStatus(FixedSpaceBinding.STATUS_ACTIVE);
        activeBinding.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);
        activeBinding.setPaidAmountCents(30000);
        activeBinding.setReviewStatus(FixedSpaceBinding.REVIEW_APPROVED);
        activeBinding.setSource(FixedSpaceBinding.SOURCE_ADMIN);
        activeBinding.setCreatedAt(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ==================== 列表查询 ====================

    @Test
    @DisplayName("分页查询固定车位列表")
    void shouldPageListFixedSpaces() {
        when(bindingMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<FixedSpaceBinding> page = invocation.getArgument(0);
                    page.setRecords(List.of(activeBinding));
                    page.setTotal(1);
                    return page;
                });
        when(vehicleMapper.selectBatchIds(List.of(10L))).thenReturn(List.of(vehicle));
        when(parkingLotMapper.selectBatchIds(List.of(1L))).thenReturn(List.of(parkingLot));

        IPage<FixedSpaceVO> result = fixedSpaceService.pageList(null, null, null, null, null, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        assertThat(result.getRecords().get(0).getSpaceNo()).isEqualTo("A-001");
        assertThat(result.getRecords().get(0).getPlateNumber()).isEqualTo("京A12345");
        assertThat(result.getRecords().get(0).getParkingLotName()).isEqualTo("测试车场");
        assertThat(result.getRecords().get(0).getStatus()).isEqualTo(1);
    }

    @Test
    @DisplayName("分页查询返回空列表")
    void shouldReturnEmptyPageWhenNoData() {
        when(bindingMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<FixedSpaceBinding> page = invocation.getArgument(0);
                    page.setRecords(List.of());
                    page.setTotal(0);
                    return page;
                });

        IPage<FixedSpaceVO> result = fixedSpaceService.pageList(null, null, null, null, null, 1, 10);

        assertThat(result.getRecords()).isEmpty();
        assertThat(result.getTotal()).isZero();
    }

    // ==================== 到期预警 ====================

    @Test
    @DisplayName("到期预警列表展示即将到期的绑定")
    void shouldReturnExpiringList() {
        activeBinding.setValidEnd(LocalDate.now().plusDays(3));
        when(bindingMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<FixedSpaceBinding> page = invocation.getArgument(0);
                    page.setRecords(List.of(activeBinding));
                    page.setTotal(1);
                    return page;
                });
        when(vehicleMapper.selectBatchIds(any())).thenReturn(List.of(vehicle));
        when(parkingLotMapper.selectBatchIds(any())).thenReturn(List.of(parkingLot));

        IPage<FixedSpaceVO> result = fixedSpaceService.expiringList(7, 1, 10);

        assertThat(result.getRecords()).hasSize(1);
        verify(bindingMapper).selectPage(any(Page.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("没有即将到期的绑定记录时返回空列表")
    void shouldReturnEmptyIfNoExpiring() {
        when(bindingMapper.selectPage(any(Page.class), any(QueryWrapper.class)))
                .thenAnswer(invocation -> {
                    Page<FixedSpaceBinding> page = invocation.getArgument(0);
                    page.setRecords(List.of());
                    page.setTotal(0);
                    return page;
                });

        IPage<FixedSpaceVO> result = fixedSpaceService.expiringList(7, 1, 10);

        assertThat(result.getRecords()).isEmpty();
    }

    // ==================== 绑定 ====================

    @Test
    @DisplayName("绑定固定车位成功（车辆已存在）")
    void shouldCreateFixedSpaceWithExistingVehicle() {
        when(vehicleMapper.selectByPlateNumber("京B67890", 1L)).thenReturn(vehicle);
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);
        when(paramResolver.getString(anyString(), eq(1L))).thenReturn("AUTO");

        vehicle.setPlateNumber("京B67890");

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setSpaceNo("B-002");
        request.setPlateNumber("京B67890");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        FixedSpaceVO result = fixedSpaceService.create(request);

        assertThat(result.getSpaceNo()).isEqualTo("B-002");
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getParkingLotName()).isEqualTo("测试车场");
        assertThat(result.getReviewStatus()).isEqualTo(FixedSpaceBinding.REVIEW_APPROVED);
        verify(bindingMapper).insert(any(FixedSpaceBinding.class));
        verify(parkingOrderMapper).insert(any(ParkingOrder.class));
    }

    @Test
    @DisplayName("绑定固定车位成功（车辆不存在，自动创建）")
    void shouldCreateFixedSpaceWithNewVehicle() {
        when(vehicleMapper.selectByPlateNumber("京C12345", 1L)).thenReturn(null);
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);
        when(paramResolver.getString(anyString(), eq(1L))).thenReturn("AUTO");

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setSpaceNo("C-003");
        request.setPlateNumber("京C12345");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        FixedSpaceVO result = fixedSpaceService.create(request);

        assertThat(result.getSpaceNo()).isEqualTo("C-003");
        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getReviewStatus()).isEqualTo(FixedSpaceBinding.REVIEW_APPROVED);
        verify(vehicleMapper).insert(any(SysVehicle.class));
        verify(bindingMapper).insert(any(FixedSpaceBinding.class));
        verify(parkingOrderMapper).insert(any(ParkingOrder.class));
    }

    @Test
    @DisplayName("同一车场同一车位号重复绑定时抛出异常")
    void shouldRejectDuplicateSpace() {
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        // 第一次 selectCount：checkDuplicateSpace 检查车位占用
        // 第二次 selectCount：checkDuplicateVehicle 检查车辆重复
        when(bindingMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(1L) // 车位已占用
                .thenReturn(0L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setSpaceNo("A-001");
        request.setPlateNumber("京A12345");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        assertThatThrownBy(() -> fixedSpaceService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已被绑定");
    }

    @Test
    @DisplayName("同一车辆在同一车场重复绑定时抛出异常")
    void shouldRejectDuplicateVehicle() {
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        // 第一次 selectCount：checkDuplicateSpace → 0（车位空闲）
        // 第二次 selectCount：checkDuplicateVehicle → 1（车辆已绑定）
        when(bindingMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(1L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setSpaceNo("Z-999");
        request.setPlateNumber("京A12345");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        assertThatThrownBy(() -> fixedSpaceService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已在当前车场绑定固定车位");
    }

    // ==================== 续期 ====================

    @Test
    @DisplayName("固定车位续期成功")
    void shouldRenewFixedSpace() {
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);

        FixedSpaceRenewRequest request = new FixedSpaceRenewRequest();
        request.setNewValidEnd(LocalDate.of(2027, 12, 31));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        FixedSpaceVO result = fixedSpaceService.renew(100L, request);

        assertThat(result).isNotNull();
        assertThat(result.getValidEnd()).isEqualTo(LocalDate.of(2027, 12, 31));
        verify(bindingMapper).updateById(any(FixedSpaceBinding.class));
    }

    @Test
    @DisplayName("已注销的固定车位续期抛出异常")
    void shouldRejectRenewForDisabled() {
        activeBinding.setStatus(FixedSpaceBinding.STATUS_DISABLED);
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);

        FixedSpaceRenewRequest request = new FixedSpaceRenewRequest();
        request.setNewValidEnd(LocalDate.of(2027, 12, 31));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        assertThatThrownBy(() -> fixedSpaceService.renew(100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅生效中");
    }

    @Test
    @DisplayName("跨租户固定车位续期抛出异常")
    void shouldRejectRenewForDifferentTenant() {
        activeBinding.setTenantId(999L);
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);

        FixedSpaceRenewRequest request = new FixedSpaceRenewRequest();
        request.setNewValidEnd(LocalDate.of(2027, 12, 31));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        assertThatThrownBy(() -> fixedSpaceService.renew(100L, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    // ==================== 注销 ====================

    @Test
    @DisplayName("固定车位注销成功")
    void shouldCancelFixedSpace() {
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);

        FixedSpaceVO result = fixedSpaceService.cancel(100L);

        assertThat(result.getStatus()).isEqualTo(3);
        verify(bindingMapper).updateById(any(FixedSpaceBinding.class));
    }

    @Test
    @DisplayName("不存在的固定车位注销抛出异常")
    void shouldRejectCancelForNonExistent() {
        when(bindingMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> fixedSpaceService.cancel(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不存在");
    }

    // ==================== hasActiveBinding ====================

    @Test
    @DisplayName("有生效中的固定车位绑定返回 true")
    void shouldReturnTrueWhenActiveBindingExists() {
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        boolean result = fixedSpaceService.hasActiveBinding("京A12345", 1L, 1L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("无固定车位绑定返回 false")
    void shouldReturnFalseWhenNoActiveBinding() {
        when(vehicleMapper.selectByPlateNumber("京A12345", 1L)).thenReturn(vehicle);
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        boolean result = fixedSpaceService.hasActiveBinding("京A12345", 1L, 1L);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("车辆不存在时返回 false")
    void shouldReturnFalseWhenVehicleNotFound() {
        when(vehicleMapper.selectByPlateNumber("京X99999", 1L)).thenReturn(null);

        boolean result = fixedSpaceService.hasActiveBinding("京X99999", 1L, 1L);

        assertThat(result).isFalse();
    }

    // ==================== 审核 ====================

    @Test
    @DisplayName("审核通过待审核的固定车位")
    void shouldApproveFixedSpace() {
        activeBinding.setReviewStatus(FixedSpaceBinding.REVIEW_PENDING);
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);
        when(vehicleMapper.selectById(10L)).thenReturn(vehicle);

        FixedSpaceVO result = fixedSpaceService.approve(100L);

        assertThat(result.getReviewStatus()).isEqualTo(FixedSpaceBinding.REVIEW_APPROVED);
        verify(bindingMapper).updateById(any(FixedSpaceBinding.class));
        verify(parkingOrderMapper).insert(any(ParkingOrder.class));
    }

    @Test
    @DisplayName("已通过的固定车位再次审核抛出异常")
    void shouldRejectApproveForNonPending() {
        activeBinding.setReviewStatus(FixedSpaceBinding.REVIEW_APPROVED);
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);

        assertThatThrownBy(() -> fixedSpaceService.approve(100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("仅待审核状态");
    }

    @Test
    @DisplayName("驳回待审核的固定车位")
    void shouldRejectFixedSpace() {
        activeBinding.setReviewStatus(FixedSpaceBinding.REVIEW_PENDING);
        when(bindingMapper.selectById(100L)).thenReturn(activeBinding);

        FixedSpaceVO result = fixedSpaceService.reject(100L);

        assertThat(result.getReviewStatus()).isEqualTo(FixedSpaceBinding.REVIEW_REJECTED);
        verify(bindingMapper).updateById(any(FixedSpaceBinding.class));
        verify(parkingOrderMapper, never()).insert(any(ParkingOrder.class));
    }

    // ==================== 审核模式 ====================

    @Test
    @DisplayName("审核模式 AUTO 时自动设置为已通过")
    void shouldAutoApproveWhenReviewModeAuto() {
        when(vehicleMapper.selectByPlateNumber("京AUTO01", 1L)).thenReturn(vehicle);
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);
        when(paramResolver.getString(anyString(), eq(1L))).thenReturn("AUTO");

        vehicle.setPlateNumber("京AUTO01");

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setSpaceNo("AUTO-01");
        request.setPlateNumber("京AUTO01");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        FixedSpaceVO result = fixedSpaceService.create(request);

        assertThat(result.getReviewStatus()).isEqualTo(FixedSpaceBinding.REVIEW_APPROVED);
        verify(parkingOrderMapper).insert(any(ParkingOrder.class));
    }

    @Test
    @DisplayName("审核模式 MANUAL 时设置为待审核")
    void shouldSetPendingWhenReviewModeManual() {
        when(vehicleMapper.selectByPlateNumber("京MAN01", 1L)).thenReturn(vehicle);
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);
        when(parkingLotMapper.selectById(1L)).thenReturn(parkingLot);
        when(paramResolver.getString(anyString(), eq(1L))).thenReturn("MANUAL");

        vehicle.setPlateNumber("京MAN01");

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setSpaceNo("MAN-01");
        request.setPlateNumber("京MAN01");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        FixedSpaceVO result = fixedSpaceService.create(request);

        assertThat(result.getReviewStatus()).isEqualTo(FixedSpaceBinding.REVIEW_PENDING);
        verify(parkingOrderMapper, never()).insert(any(ParkingOrder.class));
    }

    // ==================== 配额检查 ====================

    @Test
    @DisplayName("配额检查：区域固定车位已达上限抛出异常")
    void shouldRejectWhenZoneQuotaExceeded() {
        when(vehicleMapper.selectByPlateNumber("京QUOTA01", 1L)).thenReturn(vehicle);
        // 第一次 selectCount：checkDuplicateSpace → 0
        // 第二次 selectCount：checkDuplicateVehicle → 0
        // 第三次 selectCount：checkZoneQuota → 3（已占满）
        when(bindingMapper.selectCount(any(QueryWrapper.class)))
                .thenReturn(0L)
                .thenReturn(0L)
                .thenReturn(3L);

        ParkingSpacePolicy policy = new ParkingSpacePolicy();
        policy.setFixedSpaces(3);
        when(spacePolicyMapper.selectByZoneId(1L, 1L)).thenReturn(policy);

        vehicle.setPlateNumber("京QUOTA01");

        FixedSpaceCreateRequest request = new FixedSpaceCreateRequest();
        request.setParkingLotId(1L);
        request.setZoneId(1L);
        request.setSpaceNo("Q-001");
        request.setPlateNumber("京QUOTA01");
        request.setValidStart(LocalDate.of(2026, 7, 1));
        request.setValidEnd(LocalDate.of(2027, 6, 30));
        request.setPaidAmountCents(50000);
        request.setPayMethod(FixedSpaceBinding.PAY_METHOD_CASH);

        assertThatThrownBy(() -> fixedSpaceService.create(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已达上限");
    }

    // ==================== countActiveByZone ====================

    @Test
    @DisplayName("统计区域生效中的固定车位绑定数")
    void shouldCountActiveByZone() {
        when(bindingMapper.selectCount(any(QueryWrapper.class))).thenReturn(5L);

        long count = fixedSpaceService.countActiveByZone(1L, 1L, 1L);

        assertThat(count).isEqualTo(5L);
    }
}
