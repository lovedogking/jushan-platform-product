package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 车辆视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleVO {

    /** 车辆ID */
    private Long id;

    /** 车牌号 */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型 */
    private String vehicleType;

    /** 车主姓名 */
    private String ownerName;

    /** 车主电话 */
    private String ownerPhone;

    /** 所属部门ID */
    private Long departmentId;

    /** 所属停车场ID */
    private Long parkingLotId;

    /** 月卡/固定车有效期开始 */
    private LocalDate validStartDate;

    /** 月卡/固定车有效期结束 */
    private LocalDate validEndDate;

    /** 储值车余额（分） */
    private BigDecimal prepaidBalance;

    /** 月卡/固定车收费标准ID */
    private Long feeRuleId;

    /** 备注 */
    private String remark;

    /** 状态 */
    private String status;

    /** 绑定车牌列表（一位多车） */
    private List<String> multiPlates;

    /** 生效车道ID列表 */
    private List<Long> laneIds;

    /** 生效车道名称列表 */
    private List<String> laneNames;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
