package com.jushan.platform.modules.vehicle.service.impl;

import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.miniapp.entity.MonthlyPass;
import com.jushan.platform.modules.miniapp.mapper.MonthlyPassMapper;
import com.jushan.platform.modules.vehicle.entity.SysVehicleMultiPlate;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWallet;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMultiPlateMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletMapper;
import com.jushan.platform.modules.vehicle.service.VehicleTypeDecisionService;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;
import com.jushan.platform.modules.vehicle.entity.VehicleList;
import com.jushan.platform.modules.miniapp.service.FixedSpaceService;
import com.jushan.platform.modules.vehicle.service.VehicleListService;
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
 *   <li>FIXED_SPACE - 固定车位车辆（检查固定车位绑定有效期）</li>
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
    private final FixedSpaceService fixedSpaceService;
    private final MonthlyPassMapper monthlyPassMapper;
    private final VehicleListService vehicleListService;

    public VehicleTypeDecisionServiceImpl(SysVehicleMapper vehicleMapper,
                                          SysVehicleMultiPlateMapper multiPlateMapper,
                                          SysVehicleWalletMapper walletMapper,
                                          FixedSpaceService fixedSpaceService,
                                          MonthlyPassMapper monthlyPassMapper,
                                          VehicleListService vehicleListService) {
        this.vehicleMapper = vehicleMapper;
        this.multiPlateMapper = multiPlateMapper;
        this.walletMapper = walletMapper;
        this.fixedSpaceService = fixedSpaceService;
        this.monthlyPassMapper = monthlyPassMapper;
        this.vehicleListService = vehicleListService;
    }

    @Override
    @Deprecated
    public VehicleTypeDecisionVO decide(String plateNumber) {
        return decide(plateNumber, null, TenantContext.getTenantId());
    }

    @Override
    public VehicleTypeDecisionVO decide(String plateNumber, Long tenantId) {
        return decide(plateNumber, null, tenantId);
    }

    @Override
    public VehicleTypeDecisionVO decide(String plateNumber, Long parkingLotId, Long tenantId) {
        String standardizedPlate = plateNumber.toUpperCase();

        // ====== 步骤 -1: 白名单检查（任务包 3-3，最高优先级） ======
        if (parkingLotId != null && vehicleListService != null
                && vehicleListService.isWhitelisted(parkingLotId, standardizedPlate)) {
            VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
            result.setPlateNumber(standardizedPlate);
            result.setVehicleType("WHITE");
            result.setTypeDescription("白名单车辆");
            result.setAllowEntry(true);
            result.setAllowExit(true);
            result.setNeedCharge(false);
            result.setDecisionReason("白名单车辆，自动放行不计费");
            return result;
        }

        // ====== 步骤 0': 黑名单检查（任务包 3-3） ======
        if (parkingLotId != null && vehicleListService != null) {
            VehicleList black = vehicleListService.resolveBlacklist(parkingLotId, standardizedPlate);
            if (black != null) {
                VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
                result.setPlateNumber(standardizedPlate);
                result.setVehicleType("BLACK");
                result.setTypeDescription("黑名单车辆");
                result.setTriggerType(black.getTriggerType());
                result.setTriggerTypeLabel(resolveTriggerTypeLabel(black.getTriggerType()));

                // TODO: triggerMode 应从车场配置读取，当前硬编码为 DENY_ENTRY
                String triggerMode = resolveTriggerMode(parkingLotId);
                switch (triggerMode) {
                    case "DENY_ENTRY":
                        result.setAllowEntry(false);
                        result.setAllowExit(false);
                        result.setNeedCharge(false);
                        result.setDecisionReason("黑名单车辆，禁止入场");
                        break;
                    case "ALLOW_WITH_ALERT":
                        result.setAllowEntry(true);
                        result.setAllowExit(true);
                        result.setNeedCharge(true);
                        result.setDecisionReason("黑名单车辆，允许入场但已触发告警");
                        break;
                    case "BY_TYPE":
                        if (VehicleList.TRIGGER_ARREARS.equals(black.getTriggerType())) {
                            result.setAllowEntry(false);
                            result.setAllowExit(false);
                            result.setNeedCharge(false);
                            result.setDecisionReason("欠费类黑名单车辆，禁止入场");
                        } else if (VehicleList.TRIGGER_MANAGEMENT.equals(black.getTriggerType())) {
                            result.setAllowEntry(true);
                            result.setAllowExit(true);
                            result.setNeedCharge(true);
                            result.setDecisionReason("管理类黑名单车辆，允许入场但已触发告警");
                        } else {
                            result.setAllowEntry(false);
                            result.setAllowExit(false);
                            result.setNeedCharge(false);
                            result.setDecisionReason("其他类黑名单车辆，禁止入场");
                        }
                        break;
                    default:
                        result.setAllowEntry(false);
                        result.setAllowExit(false);
                        result.setNeedCharge(false);
                        result.setDecisionReason("黑名单车辆，禁止入场（默认策略）");
                }
                return result;
            }
        }

        // 0. 优先查询月卡（新体系：任务包 3-1）
        MonthlyPass monthlyPass = monthlyPassMapper.selectActiveByPlate(
                tenantId, standardizedPlate, LocalDate.now());
        if (monthlyPass != null) {
            VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
            result.setPlateNumber(standardizedPlate);
            result.setVehicleType("MONTHLY");
            result.setTypeDescription("月租车");
            result.setAllowEntry(true);
            result.setAllowExit(true);
            result.setNeedCharge(false);
            result.setValidStartDate(monthlyPass.getValidStartDate());
            result.setValidEndDate(monthlyPass.getValidEndDate());
            result.setExpired(false);
            result.setDecisionReason("月卡在有效期内，免费通行");
            return result;
        }

        VehicleTypeDecisionVO result = new VehicleTypeDecisionVO();
        result.setPlateNumber(standardizedPlate);

        // 1. 查询主车牌
        SysVehicle vehicle = vehicleMapper.selectByPlateNumber(standardizedPlate, tenantId);

        // 2. 如果主车牌未找到，查询一位多车绑定
        if (vehicle == null) {
            vehicle = findByMultiPlate(standardizedPlate, tenantId);
        }

        // 3. 未找到任何记录 → 临时车（一期：非白名单车辆需岗亭人工放行）
        if (vehicle == null) {
            result.setVehicleType("TEMP");
            result.setTypeDescription("临时车");
            result.setAllowEntry(false); // 一期：非白名单车辆默认不允许自动入场，需岗亭人工放行
            result.setAllowExit(true);
            result.setNeedCharge(true);
            result.setDecisionReason("非白名单车辆，需岗亭人工放行");
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

        // FIXED_SPACE 检查：对于非黑名单/超级车牌/贵宾车/月卡/储值车车辆，
        // 检查是否绑定了生效中的固定车位
        if (!SysVehicle.TYPE_BLACKLIST.equals(type)
                && !SysVehicle.TYPE_SUPER.equals(type)
                && !SysVehicle.TYPE_VIP.equals(type)
                && !SysVehicle.TYPE_PREPAID.equals(type)) {
            if (fixedSpaceService != null && fixedSpaceService.hasActiveBindingByVehicleId(
                    vehicle.getId(), vehicle.getParkingLotId(), tenantId)) {
                result.setVehicleType("FIXED_SPACE");
                result.setTypeDescription("固定车位车辆");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(false);
                result.setDecisionReason("固定车位在有效期内，免费通行");
                return result;
            }
        }

        switch (type) {
            case SysVehicle.TYPE_BLACKLIST -> {
                log.warn("deprecated: sys_vehicle BLACKLIST判定，应走vehicle_list新体系: plate={}", vehicle.getPlateNumber());
                result.setTypeDescription("黑名单（旧口径）");
                result.setAllowEntry(false);
                result.setAllowExit(false);
                result.setNeedCharge(false);
                result.setDecisionReason("黑名单车辆（旧口径），禁止入出场");
            }
            case SysVehicle.TYPE_SUPER -> {
                result.setTypeDescription("超级车牌");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(false);
                result.setDecisionReason("超级车牌，享有最高权限");
            }
            case SysVehicle.TYPE_VIP -> {
                log.warn("deprecated: sys_vehicle VIP判定，应走vehicle_list白名单: plate={}", vehicle.getPlateNumber());
                result.setTypeDescription("贵宾车（旧口径）");
                result.setAllowEntry(true);
                result.setAllowExit(true);
                result.setNeedCharge(false);
                result.setDecisionReason("贵宾车（旧口径），免费通行");
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

    private String resolveTriggerMode(Long parkingLotId) {
        return "DENY_ENTRY";
    }

    private String resolveTriggerTypeLabel(String triggerType) {
        if (triggerType == null) return "";
        return switch (triggerType) {
            case "ARREARS" -> "欠费类";
            case "MANAGEMENT" -> "管理类";
            case "OTHER" -> "其他类";
            default -> triggerType;
        };
    }
}
