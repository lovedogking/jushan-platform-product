package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 区域更新请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingZoneUpdateCmd {

    private Long lotId;

    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    @Size(max = 64, message = "标签长度不能超过64")
    private String tag;

    private Integer level;

    private Long feeRuleId;

    private Integer totalSpaces;

    private Integer fixedSpaces;

    private Integer status;

    private Long managerId;

    @Size(max = 512, message = "备注长度不能超过512")
    private String remark;
}
