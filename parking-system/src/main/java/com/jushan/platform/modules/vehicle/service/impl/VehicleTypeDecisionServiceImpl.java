package com.jushan.platform.modules.vehicle.service.impl;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.entity.SysVehicleMultiPlate;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWallet;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMultiPlateMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletMapper;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 车辆类型判定服务实现。
 * <p>
 * 优先级链式判定引擎：
 * <ol>
 *   <li>BLACKLIST - 黑名单（禁止入场）</li>
 *   <li>SUPER - 超级车牌（最高权限）</li>
 *   <li>VIP - 贵宾车</li>
 *   <li>MONTHLY - 月租/固定车（检查有效期）</li>
 *   <li>PREPAID - 储值车</li>
 *   <li>FREE - 免费车</li>
 *   <li>TEMP - 临时车（默认）</li>
 * </ol>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class VehicleTypeDecisionServiceImpl implements VehicleTypeDecisionService {

    private final SysVehicleMapper vehicleMapper;
    private final SysVehicleMultiPlateMapper multiPlateMapper;
    private final SysVehicleWalletMapper walletMapper;

    public VehicleTypeDecisionServiceImpl(SysVehicleMapper vehicleMapper,
                                          SysVehicleMultiPlateMapper multiPlateMapper,
                                          SysVehicleWalletMapper walletMapper) {
        this.vehicleMapper = vehicleMapper;
        this.multiPlateMapper = multiPlateMapper;
        this.walletMapper = walletMapper;
    }

    @Override
    public VehicleTypeDecisionVO decide(String plateNumber) {
        String standardizedPlate = plateNumber.toUpperCase();
        Long tenantId = TenantContext.getTenantId();

        VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
        result.setPlateNumber(standardizedPlate);

        // 1. 查询主车牌
        SysVehicle vehicle = vehicleMapper.selectByPlateNumber(standardizedPlate, tenantId);

        // 2. 如果主车牌未找到，查询一位多车绑定
        if (vehicle == null) {
            vehicle = findByMultiPlate(standardizedPlate, tenantId);
        }

        // 3. 未找到任何记录 → 临时车
        if (vehicle == null) {
            result.setVehicleType("TEMP");
            result.setTypeDescription("临时车");
            result.setAllowEntry(true);
            result.setAllowExit(true);
            result.setNeedCharge(true);
            result.setDecisionReason("未找到车辆登记记录，按临时车处理");
            return result;
        }

        // 4. 填充基础信息
        result.setVehicleId(vehicle.getId());
        result.setVehicleType(vehicle.getVehicleType());
        result.setValidStartDate(vehicle.getValidStartDate());
        result.setValidEndDate(vehicle.getValidEndDate());

        // 加载一位多车绑定
        List<SysVehicleMultiPlate> binds = multiPlateMapper.selectByVehicleId(vehicle.getId());
        result.setMultiPlates(binds.stream().map(SysVehicleMultiPlate::getPlateNumber).collect(Collectors.toList()));

        // 5. 优先级链式判定
        return applyPriorityChain(vehicle, result, tenantId);
    }

    @Override
    public boolean allowEntry(String plateNumber) {
        VehicleTypeDecisionVO decision = decide(plateNumber);
        return Boolean.TRUE.equals(decision.getAllowEntry());
    }

    @Override
    public boolean allowExit(String plateNumber) {
        VehicleTypeDecisionVO decision = decide(plateNumber);
        return Boolean.TRUE.equals(decision.getAllowExit());
    }

    /**
     * 通过一位多车绑定查找主车辆。
     */
    private SysVehicle findByMultiPlate(String plateNumber, Long tenantId) {
        // 查询绑定表
        List<SysVehicleMultiPlate> binds = multiPlateMapper.selectByPlateNumber(plateNumber, tenantId);
        if (binds == null || binds.isEmpty()) {
            return null;
        }
        // 取第一个有效绑定
        for (SysVehicleMultiPlate bind : binds) {
            SysVehicle vehicle = vehicleMapper.selectById(bind.getVehicleId());
            if (vehicle != null && tenantId.equals(vehicle.getTenantId())) {
                return vehicle;
            }
        }
        return null;
    }

    /**
     * 应用优先级链式判定逻辑。
     */
    private VehicleTypeDecisionVO applyPriorityChain(SysVehicle vehicle, VehicleTypeDecisionVO result, Long tenantId) {
        String type = vehicle.getVehicleType();

        switch (type) {
            case SysVehicle.TYPE_BLACKLIST -> {
                result.setTypeDescription("黑名单");
                result.setAllowEntry(false);
                result.setAllowExit(false);
                result.setNeedCharge(false);
                result.setDecisionReason("黑名单车辆，禁止入出场");
            }
            case SysVehicle.TYPE_SUPER -> {
                result.setTypeDescription("超级车牌");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(false);
                result.setDecisionReason("超级车牌，享有最高权限");
            }
            case SysVehicle.TYPE_VIP -> {
                result.setTypeDescription("贵宾车");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(false);
                result.setDecisionReason("贵宾车，免费通行");
            }
            case SysVehicle.TYPE_MONTHLY -> {
                // 检查月卡有效期
                boolean expired = isMonthlyExpired(vehicle);
                result.setExpired(expired);
                if (expired) {
                    result.setTypeDescription("月租车（已过期）");
                    result.setAllowEntry(true);
                    result.setAllowExit(true);
                    result.setNeedCharge(true);
                    result.setDecisionReason("月卡已过期，按临时车收费处理");
                } else {
                    result.setTypeDescription("月租车");
                    result.setAllowEntry(true);
                    result.setAllowExit(true);
                    result.setNeedCharge(false);
                    result.setDecisionReason("月卡在有效期内，免费通行");
                }
            }
            case SysVehicle.TYPE_PREPAID -> {
                // 查询储值车余额
                SysVehicleWallet wallet = walletMapper.selectByVehicleId(vehicle.getId(), tenantId);
                BigDecimal balance = wallet != null ? wallet.getBalance() : BigDecimal.ZERO;
                result.setBalance(balance);
                result.setTypeDescription("储值车");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(true);
                result.setDecisionReason("储值车，余额 " + balance + " 元，出场时扣费");
            }
            case SysVehicle.TYPE_FREE -> {
                result.setTypeDescription("免费车");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(false);
                result.setDecisionReason("免费车，无需缴费");
            }
            default -> {
                // 未知类型按临时车处理
                result.setVehicleType("TEMP");
                result.setTypeDescription("临时车");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(true);
                result.setDecisionReason("未知车辆类型，按临时车处理");
            }
        }

        return result;
    }

    /**
     * 检查月卡是否过期。
     */
    private boolean isMonthlyExpired(SysVehicle vehicle) {
        LocalDate now = LocalDate.now();
        LocalDate validEnd = vehicle.getValidEndDate();

        // 没有有效期限制 = 永不过期
        if (validEnd == null) {
            return false;
        }

        // 当前日期 > 有效期结束日期 = 已过期
        return now.isAfter(validEnd);
    }
}
