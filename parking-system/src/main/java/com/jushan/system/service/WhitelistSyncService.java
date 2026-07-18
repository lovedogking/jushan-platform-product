package com.jushan.system.service;

import com.jushan.system.dto.WhitelistEntry;
import com.jushan.system.dto.WhitelistSyncResponse;
import com.jushan.system.entity.FixedSpaceBinding;
import com.jushan.system.entity.MonthlyPass;
import com.jushan.system.entity.Vehicle;
import com.jushan.system.entity.VehicleList;
import com.jushan.system.mapper.FixedSpaceBindingMapper;
import com.jushan.system.mapper.MonthlyPassMapper;
import com.jushan.system.mapper.VehicleListMapper;
import com.jushan.system.mapper.VehicleMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 白名单同步服务（任务包 7-1）。
 * <p>
 * 查询指定停车场下所有"自动放行且不计费"的有效车辆，
 * 从月卡、固定车位、白名单三张表合并去重后全量返回。
 * <p>
 * 优先级：MONTHLY_PASS > FIXED_SPACE > WHITELIST
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@Service
public class WhitelistSyncService {

    private static final Logger log = LoggerFactory.getLogger(WhitelistSyncService.class);

    private static final String TYPE_MONTHLY_PASS = "MONTHLY_PASS";
    private static final String TYPE_FIXED_SPACE = "FIXED_SPACE";
    private static final String TYPE_WHITELIST = "WHITELIST";

    private final MonthlyPassMapper monthlyPassMapper;
    private final FixedSpaceBindingMapper fixedSpaceBindingMapper;
    private final VehicleListMapper vehicleListMapper;
    private final VehicleMapper vehicleMapper;

    public WhitelistSyncService(MonthlyPassMapper monthlyPassMapper,
                                 FixedSpaceBindingMapper fixedSpaceBindingMapper,
                                 VehicleListMapper vehicleListMapper,
                                 VehicleMapper vehicleMapper) {
        this.monthlyPassMapper = monthlyPassMapper;
        this.fixedSpaceBindingMapper = fixedSpaceBindingMapper;
        this.vehicleListMapper = vehicleListMapper;
        this.vehicleMapper = vehicleMapper;
    }

    /**
     * 生成指定停车场的全量白名单快照。
     *
     * @param parkingLotId 停车场 ID
     * @return 白名单同步响应
     */
    public WhitelistSyncResponse generateSyncData(Long parkingLotId) {
        Map<String, WhitelistEntry> merged = new LinkedHashMap<>();

        LocalDate today = LocalDate.now();

        // 1. 生效中的月卡（最低优先级）
        List<MonthlyPass> monthlyPasses = monthlyPassMapper.selectList(
                new LambdaQueryWrapper<MonthlyPass>()
                        .eq(MonthlyPass::getParkingLotId, parkingLotId)
                        .eq(MonthlyPass::getPassStatus, MonthlyPass.STATUS_ACTIVE));
        for (MonthlyPass mp : monthlyPasses) {
            String plate = mp.getPlateNumber();
            if (plate == null || plate.isBlank()) continue;
            plate = plate.toUpperCase();
            // 检查是否在有效期内
            if (mp.getValidEndDate() != null && mp.getValidEndDate().isBefore(today)) continue;
            WhitelistEntry entry = new WhitelistEntry();
            entry.setPlateNumber(plate);
            entry.setType(TYPE_MONTHLY_PASS);
            entry.setExpireAt(mp.getValidEndDate());
            merged.put(plate, entry);
        }

        // 2. 生效中的固定车位绑定（中优先级，覆盖月卡）
        List<FixedSpaceBinding> bindings = fixedSpaceBindingMapper.selectList(
                new LambdaQueryWrapper<FixedSpaceBinding>()
                        .eq(FixedSpaceBinding::getParkingLotId, parkingLotId)
                        .eq(FixedSpaceBinding::getStatus, FixedSpaceBinding.STATUS_ACTIVE));
        for (FixedSpaceBinding fb : bindings) {
            // 通过 vehicle 表获取车牌号 — 这里使用空间编号作为 fallback
            // 实际业务中 fixed_space_binding.vehicle_id 关联 vehicle 表获取 plate_number
            // 简化实现：查询 vehicle 表
            String plate = getPlateByVehicleId(fb.getVehicleId());
            if (plate == null || plate.isBlank()) continue;
            plate = plate.toUpperCase();
            if (fb.getValidEnd() != null && fb.getValidEnd().isBefore(today)) continue;
            WhitelistEntry entry = new WhitelistEntry();
            entry.setPlateNumber(plate);
            entry.setType(TYPE_FIXED_SPACE);
            entry.setSpotCode(fb.getSpaceNo());
            entry.setExpireAt(fb.getValidEnd());
            merged.put(plate, entry); // 覆盖月卡同名条目
        }

        // 3. 生效中的白名单（最高优先级，覆盖月卡和固定车位）
        List<VehicleList> whitelists = vehicleListMapper.selectList(
                new LambdaQueryWrapper<VehicleList>()
                        .eq(VehicleList::getParkingLotId, parkingLotId)
                        .eq(VehicleList::getListType, VehicleList.TYPE_WHITE)
                        .eq(VehicleList::getStatus, VehicleList.STATUS_ACTIVE));
        for (VehicleList vl : whitelists) {
            String plate = vl.getPlateNumber();
            if (plate == null || plate.isBlank()) continue;
            plate = plate.toUpperCase();
            if (vl.getEndDate() != null && vl.getEndDate().isBefore(today)) continue;
            WhitelistEntry entry = new WhitelistEntry();
            entry.setPlateNumber(plate);
            entry.setType(TYPE_WHITELIST);
            entry.setExpireAt(vl.getEndDate());
            merged.put(plate, entry); // 覆盖同名条目
        }

        List<WhitelistEntry> entries = new ArrayList<>(merged.values());

        WhitelistSyncResponse response = new WhitelistSyncResponse();
        response.setParkingLotId(parkingLotId);
        response.setGeneratedAt(LocalDateTime.now());
        response.setTotalCount(entries.size());
        response.setEntries(entries);

        log.info("白名单同步快照生成完成: parkingLotId={}, totalCount={}", parkingLotId, entries.size());
        return response;
    }

    /**
     * 通过 vehicle_id 查询车牌号。
     * <p>
     * 固定车位绑定中的 vehicle_id 关联 vehicle 表，取出 vehicle_plate 字段。
     */
    private String getPlateByVehicleId(Long vehicleId) {
        if (vehicleId == null) return null;
        Vehicle vehicle = vehicleMapper.selectById(vehicleId);
        return vehicle != null ? vehicle.getVehiclePlate() : null;
    }
}
