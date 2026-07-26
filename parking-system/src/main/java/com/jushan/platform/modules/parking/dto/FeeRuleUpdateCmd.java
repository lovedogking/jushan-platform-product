package com.jushan.platform.modules.parking.dto;

import com.jushan.platform.modules.parking.entity.FeeRuleSegment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 收费规则更新请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeRuleUpdateCmd {

    @NotBlank(message = "规则名称不能为空")
    @Size(max = 128, message = "名称长度不能超过128")
    private String name;

    @Size(max = 256, message = "描述长度不能超过256")
    private String description;

    private Integer billingMode;

    /** 适用车辆类型（逗号分隔，如 TEMP,MONTHLY），不传则适用所有 */
    private String vehicleType;

    /** 适用车牌颜色（逗号分隔，如 BLUE,GREEN），不传则适用所有 */
    private String plateColor;

    private Integer freeMinutes;

    private Integer unitMinutes;

    /** 首时段时长（分钟），0 表示无首时段 */
    private Integer firstPeriodMinutes;

    private BigDecimal firstPeriodPrice;

    private BigDecimal subsequentPrice;

    private BigDecimal dailyCap;

    /** 最大封顶金额（整单封顶，NULL 表示不封顶） */
    private BigDecimal maxAmount;

    private BigDecimal nightCap;

    /** 跨天计费规则：1按自然日分段 2连续计费 */
    private Integer crossDayMode;

    /** 生效方式：1立即生效 2仅新入场生效 3定时生效 */
    private Integer effectMode;

    private Integer priority;

    private Integer status;

    private LocalDateTime effectiveStart;

    private LocalDateTime effectiveEnd;

    private String holidayRules;

    /** 时段配置（分时段模式必填） */
    private List<FeeRuleSegment> timeSegments;
}
