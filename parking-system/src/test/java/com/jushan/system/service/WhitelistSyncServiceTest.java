package com.jushan.system.service;

import com.jushan.system.dto.WhitelistSyncResponse;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.VehicleListMapper;
import com.jushan.system.mapper.VehicleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * WhitelistSyncService 单元测试（任务包 7-1）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WhitelistSyncService 白名单同步测试")
class WhitelistSyncServiceTest {

    @Mock
    private MonthlyPassMapper monthlyPassMapper;

    @Mock
    private FixedSpaceBindingMapper fixedSpaceBindingMapper;

    @Mock
    private VehicleListMapper vehicleListMapper;

    @Mock
    private VehicleMapper vehicleMapper;

    private WhitelistSyncService service;

    private static final Long PARKING_LOT_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new WhitelistSyncService(monthlyPassMapper, fixedSpaceBindingMapper, vehicleListMapper, vehicleMapper);
    }

    @Test
    @DisplayName("三表合并 → 返回合并后的条目，同车牌优先级 WHITELIST > FIXED_SPACE > MONTHLY_PASS")
    void shouldMergeAndDedupByPriority() {
        // 同一车牌 "京A12345" 出现在月卡和固定车位 → 固定车位覆盖月卡
        // 同一车牌 "京B67890" 出现在三张表 → 白名单覆盖
        MonthlyPass mp1 = new MonthlyPass();
        mp1.setPlateNumber("京A12345");
        mp1.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        mp1.setValidEndDate(LocalDate.now().plusDays(30));

        MonthlyPass mp2 = new MonthlyPass();
        mp2.setPlateNumber("京B67890");
        mp2.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        mp2.setValidEndDate(LocalDate.now().plusDays(10));

        Vehicle vehicleA = new Vehicle();
        vehicleA.setVehiclePlate("京A12345");
        FixedSpaceBinding fb1 = new FixedSpaceBinding();
        fb1.setVehicleId(100L);
        fb1.setSpaceNo("A001");
        fb1.setValidEnd(LocalDate.now().plusDays(60));
        fb1.setStatus(FixedSpaceBinding.STATUS_ACTIVE);

        VehicleList wl1 = new VehicleList();
        wl1.setPlateNumber("京B67890");
        wl1.setListType(VehicleList.TYPE_WHITE);
        wl1.setStatus(VehicleList.STATUS_ACTIVE);

        VehicleList wl2 = new VehicleList();
        wl2.setPlateNumber("京C11111");
        wl2.setListType(VehicleList.TYPE_WHITE);
        wl2.setStatus(VehicleList.STATUS_ACTIVE);

        when(monthlyPassMapper.selectList(any())).thenReturn(List.of(mp1, mp2));
        when(fixedSpaceBindingMapper.selectList(any())).thenReturn(List.of(fb1));
        when(vehicleListMapper.selectList(any())).thenReturn(List.of(wl1, wl2));
        when(vehicleMapper.selectById(100L)).thenReturn(vehicleA);

        WhitelistSyncResponse response = service.generateSyncData(PARKING_LOT_ID);

        assertThat(response.getParkingLotId()).isEqualTo(PARKING_LOT_ID);
        assertThat(response.getTotalCount()).isEqualTo(3); // 京A12345, 京B67890, 京C11111
        assertThat(response.getEntries()).extracting("plateNumber")
                .containsExactly("京A12345", "京B67890", "京C11111");
        // 京A12345: 月卡最先添加，后被固定车位覆盖 → FIXED_SPACE
        // 京B67890: 月卡→固定车位（无 vehicle）→白名单覆盖 → WHITELIST
        // 京C11111: 仅白名单 → WHITELIST
        assertThat(response.getEntries()).extracting("type")
                .containsExactly("FIXED_SPACE", "WHITELIST", "WHITELIST");
    }

    @Test
    @DisplayName("空车场 → 返回空列表，totalCount=0")
    void shouldReturnEmptyForEmptyLot() {
        when(monthlyPassMapper.selectList(any())).thenReturn(List.of());
        when(fixedSpaceBindingMapper.selectList(any())).thenReturn(List.of());
        when(vehicleListMapper.selectList(any())).thenReturn(List.of());

        WhitelistSyncResponse response = service.generateSyncData(PARKING_LOT_ID);

        assertThat(response.getTotalCount()).isEqualTo(0);
        assertThat(response.getEntries()).isEmpty();
    }

    @Test
    @DisplayName("过期月卡不包含 → 有效期内月卡包含")
    void shouldExcludeExpiredMonthlyPass() {
        MonthlyPass active = new MonthlyPass();
        active.setPlateNumber("京D44444");
        active.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        active.setValidEndDate(LocalDate.now().plusDays(7));

        MonthlyPass expired = new MonthlyPass();
        expired.setPlateNumber("京E55555");
        expired.setPassStatus(MonthlyPass.STATUS_ACTIVE);
        expired.setValidEndDate(LocalDate.now().minusDays(1));

        when(monthlyPassMapper.selectList(any())).thenReturn(List.of(active, expired));
        when(fixedSpaceBindingMapper.selectList(any())).thenReturn(List.of());
        when(vehicleListMapper.selectList(any())).thenReturn(List.of());

        WhitelistSyncResponse response = service.generateSyncData(PARKING_LOT_ID);

        assertThat(response.getTotalCount()).isEqualTo(1);
        assertThat(response.getEntries().get(0).getPlateNumber()).isEqualTo("京D44444");
    }
}
