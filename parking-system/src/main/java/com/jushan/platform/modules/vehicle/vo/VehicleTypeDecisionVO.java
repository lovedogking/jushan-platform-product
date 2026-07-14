package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车辆类型判定结果视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleTypeDecisionVO {

    /** 车牌号 */
    private String plateNumber;

    /** 判定结果类型：BLACKLIST, SUPER, VIP, MONTHLY, PREPAID, FREE, TEMP */
    private String vehicleType;

    /** 车辆ID（如找到） */
    private Long vehicleId;

    /** 车辆类型描述 */
    private String typeDescription;

    /** 是否允许入场 */
    private Boolean allowEntry;

    /** 是否允许出场 */
    private Boolean allowExit;

    /** 是否需要收费 */
    private Boolean needCharge;

    /** 月卡有效期开始（仅月租车） */
    private LocalDate validStartDate;

    /** 月卡有效期结束（仅月租车） */
    private LocalDate validEndDate;

    /** 月卡是否过期（仅月租车） */
    private Boolean expired;

    /** 储值车余额（仅储值车） */
    private BigDecimal balance;

    /** 一位多车绑定车牌列表 */
    private java.util.List<String> multiPlates;

    /** 判定说明 */
    private String decisionReason;
}
