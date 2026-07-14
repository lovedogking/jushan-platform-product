package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 访客预约审核处理命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class VisitorApplyAuditCmd {

    /** 审核结果：APPROVED-通过, REJECTED-拒绝 */
    @NotBlank(message = "审核结果不能为空")
    @Pattern(regexp = "APPROVED|REJECTED", message = "审核结果必须是 APPROVED 或 REJECTED")
    private String applyStatus;

    /** 审核结果说明 */
    @Size(max = 200, message = "审核说明最多200个字符")
    private String auditResult;
}
