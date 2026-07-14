package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 车辆更新命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleUpdateCmd {

    /** 车牌号 */
    @Pattern(regexp = "^[A-Z0-9]{5,10}$", message = "车牌号格式不正确")
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型 */
    private String vehicleType;

    /** 车主姓名 */
    @Size(max = 30, message = "车主姓名最多30个字符")
    private String ownerName;

    /** 车主电话 */
    @Size(max = 20, message = "车主电话最多20个字符")
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
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;

    /** 状态 */
    private String status;
}
