package com.jushan.platform.modules.vehicle.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 车辆审核处理命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VehicleAuditProcessCmd {

    /** 审核结果：APPROVED-通过, REJECTED-驳回, NEED_INFO-待补充 */
    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "APPROVED|REJECTED|NEED_INFO", message = "审核结果必须是 APPROVED、REJECTED 或 NEED_INFO")
    private String auditStatus;

    /** 审核结果说明 */
    @Size(max = 500, message = "审核说明最多500个字符")
    private String auditResult;
}
