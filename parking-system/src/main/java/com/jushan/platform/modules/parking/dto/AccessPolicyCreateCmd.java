package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 车辆进出策略创建命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class AccessPolicyCreateCmd {

    /** 停车场ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 策略类型：ENTRY-入场策略, EXIT-出场策略, BLACKLIST-黑名单策略, VIP-VIP策略 */
    @NotBlank(message = "策略类型不能为空")
    @Pattern(regexp = "ENTRY|EXIT|BLACKLIST|VIP", message = "策略类型必须是 ENTRY、EXIT、BLACKLIST 或 VIP")
    private String policyType;

    /** 策略键 */
    @NotBlank(message = "策略键不能为空")
    @Size(max = 50, message = "策略键最多50个字符")
    private String policyKey;

    /** 策略值 */
    @NotBlank(message = "策略值不能为空")
    @Size(max = 500, message = "策略值最多500个字符")
    private String policyValue;

    /** 策略说明 */
    @Size(max = 200, message = "策略说明最多200个字符")
    private String description;

    /** 排序 */
    private Integer sortOrder = 0;
}
