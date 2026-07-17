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
     * 根据车牌号 + 可信租户 ID 判定车辆类型（任务包 1-2）。
     * <p>
     * 供无 {@link com.jushan.common.auth.TenantContext} 的链路（如 MQ 识别事件入场）使用，
     * 租户 ID 由调用方从可信设备/停车记录推导，不信任外部传入。
     *
     * @param plateNumber 车牌号
     * @param tenantId    可信租户 ID
     * @return 判定结果
     */
    VehicleTypeDecisionVO decide(String plateNumber, Long tenantId);

    /**
     * 根据车牌号 + 车场ID + 租户 ID 判定车辆类型（任务包 3-3）。
     * <p>
     * 新增车场维度，用于 vehicle_list 黑白名单判定。
     *
     * @param plateNumber  车牌号
     * @param parkingLotId 停车场ID（null=跳过名单查询，走旧逻辑降级）
     * @param tenantId     租户 ID
     * @return 判定结果
     */
    VehicleTypeDecisionVO decide(String plateNumber, Long parkingLotId, Long tenantId);

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
