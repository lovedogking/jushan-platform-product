package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 区域创建请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class ParkingZoneCreateCmd {

    @NotNull(message = "所属车场不能为空")
    private Long lotId;

    @NotBlank(message = "区域名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    @Size(max = 64, message = "标签长度不能超过64")
    private String tag;

    private Integer level;

    private Long feeRuleId;

    @NotNull(message = "车位总数不能为空")
    private Integer totalSpaces;

    private Integer fixedSpaces;

    private Integer status;

    private Long managerId;

    @Size(max = 512, message = "备注长度不能超过512")
    private String remark;
}
