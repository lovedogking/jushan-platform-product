package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 车辆入场记录创建命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingSessionEntryCmd {

    /** 停车场ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 租户ID（内部调用时从可信记录推导） */
    private Long tenantId;

    /** 入场通道ID */
    private Long laneId;

    /** 车牌号 */
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型判定结果 */
    private String vehicleType;

    /** 入场抓拍图片URL */
    @Size(max = 255, message = "图片URL最多255个字符")
    private String entryImage;

    /** 入场时间（为 null 时取当前时间，人工补录场景传入指定时间） */
    private LocalDateTime entryTime;

    /** 入场操作人ID（人工放行时） */
    private Long entryOperator;

    /** 入场触发方式：whitelist_auto/manual_open/always_open_period/manual_entry */
    private String entryTrigger;

    /** 备注 */
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
