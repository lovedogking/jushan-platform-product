package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 车辆审核提交命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleAuditSubmitCmd {

    /** 车辆ID */
    @NotNull(message = "车辆ID不能为空")
    private Long vehicleId;

    /** 申请类型：NEW-新登记, UPDATE-信息变更, RENEW-续期 */
    @NotBlank(message = "申请类型不能为空")
    @Pattern(regexp = "NEW|UPDATE|RENEW", message = "申请类型必须是 NEW、UPDATE 或 RENEW")
    private String applyType;

    /** 申请原因/备注 */
    @Size(max = 500, message = "申请原因最多500个字符")
    private String applyReason;

    /** 申请人姓名 */
    @Size(max = 30, message = "申请人姓名最多30个字符")
    private String applicantName;

    /** 申请人电话 */
    @Size(max = 20, message = "申请人电话最多20个字符")
    private String applicantPhone;
}
