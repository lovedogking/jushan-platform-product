package com.jushan.platform.modules.vehicle.service;

import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.vo.VehicleTypeDecisionVO;

/**
 * 车辆类型判定服务接口。
 * <p>
 * 优先级链式判定引擎：黑名单 → 超级车牌 → VIP → 月租（含过期检查） → 储值车 → 免费车 → 临时车。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface VehicleTypeDecisionService {

    /**
     * 根据车牌号判定车辆类型。
     * <p>
     * 判定优先级（从高到低）：
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
     * @param plateNumber 车牌号
     * @return 判定结果
     */
    VehicleTypeDecisionVO decide(String plateNumber);

    /**
     * 检查车辆是否允许入场。
     *
     * @param plateNumber 车牌号
     * @return true-允许入场
     */
    boolean allowEntry(String plateNumber);

    /**
     * 检查车辆是否允许出场。
     *
     * @param plateNumber 车牌号
     * @return true-允许出场
     */
    boolean allowExit(String plateNumber);
}
