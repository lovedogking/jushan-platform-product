package com.jushan.system.service;

import com.jushan.common.BusinessException;
import com.jushan.system.cache.VehicleListCacheStore;
import com.jushan.system.dto.VehicleListCreateCmd;
import com.jushan.platform.modules.parking.entity.ParkingLot;
import com.jushan.platform.modules.vehicle.entity.VehicleList;
import com.jushan.system.mapper.ParkingLotMapper;
import com.jushan.system.mapper.VehicleListMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VehicleListServiceTest {

    @Mock
    private VehicleListMapper vehicleListMapper;

    @Mock
    private VehicleListCacheStore cacheStore;

    @Mock
    private ParkingLotMapper parkingLotMapper;

    private VehicleListService service;

    @BeforeEach
    void setUp() {
        service = new VehicleListService(vehicleListMapper, cacheStore, parkingLotMapper);
    }

    @Test
    @DisplayName("互斥校验：已存在白名单，再添加黑名单应被拦截")
    void shouldRejectBlacklistWhenWhitelistExists() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京A12345");
        cmd.setListType(VehicleList.TYPE_BLACK);
        cmd.setParkingLotId(1L);
        cmd.setTriggerType(VehicleList.TRIGGER_OTHER);

        VehicleList existingWhite = new VehicleList();
        existingWhite.setPlateNumber("京A12345");
        existingWhite.setListType(VehicleList.TYPE_WHITE);
        existingWhite.setParkingLotId(1L);

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(1L), eq("京A12345"), eq(VehicleList.TYPE_WHITE)))
                .thenReturn(existingWhite);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在于白名单中");
    }

    @Test
    @DisplayName("互斥校验：已存在黑名单，再添加白名单应被拦截")
    void shouldRejectWhitelistWhenBlacklistExists() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京B67890");
        cmd.setListType(VehicleList.TYPE_WHITE);
        cmd.setParkingLotId(2L);

        VehicleList existingBlack = new VehicleList();
        existingBlack.setPlateNumber("京B67890");
        existingBlack.setListType(VehicleList.TYPE_BLACK);
        existingBlack.setParkingLotId(2L);

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(2L), eq("京B67890"), eq(VehicleList.TYPE_BLACK)))
                .thenReturn(existingBlack);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在于黑名单中");
    }

    @Test
    @DisplayName("黑名单创建时 triggerType 为空应抛异常")
    void shouldRejectBlacklistWithoutTriggerType() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京C11111");
        cmd.setListType(VehicleList.TYPE_BLACK);
        cmd.setParkingLotId(3L);

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(3L), eq("京C11111"), eq(VehicleList.TYPE_WHITE)))
                .thenReturn(null);

        assertThatThrownBy(() -> service.create(cmd))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("触发类型不能为空");
    }

    @Test
    @DisplayName("正常创建黑名单后缓存被失效")
    void shouldCreateBlacklistAndEvictCache() {
        VehicleListCreateCmd cmd = new VehicleListCreateCmd();
        cmd.setPlateNumber("京D22222");
        cmd.setListType(VehicleList.TYPE_BLACK);
        cmd.setParkingLotId(4L);
        cmd.setTriggerType(VehicleList.TRIGGER_ARREARS);
        cmd.setRemark("测试欠费拉黑");

        when(vehicleListMapper.selectByLotAndPlateAndType(eq(4L), eq("京D22222"), eq(VehicleList.TYPE_WHITE)))
                .thenReturn(null);
        when(vehicleListMapper.insert(any(VehicleList.class))).thenReturn(1);
        
        ParkingLot mockLot = new ParkingLot();
        mockLot.setId(4L);
        mockLot.setTenantId(1L);
        when(parkingLotMapper.selectByIdIgnoreTenant(4L)).thenReturn(mockLot);

        VehicleList result = service.create(cmd);

        assertThat(result.getPlateNumber()).isEqualTo("京D22222");
        assertThat(result.getListType()).isEqualTo(VehicleList.TYPE_BLACK);
        assertThat(result.getTriggerType()).isEqualTo(VehicleList.TRIGGER_ARREARS);
        assertThat(result.getStatus()).isEqualTo(VehicleList.STATUS_ACTIVE);
        verify(cacheStore).evict(eq(4L), eq("京D22222"));
    }
}
