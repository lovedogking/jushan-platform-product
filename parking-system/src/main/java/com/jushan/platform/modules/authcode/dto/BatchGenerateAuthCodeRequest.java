package com.jushan.platform.modules.authcode.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * 批量生成授权码请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BatchGenerateAuthCodeRequest {

    /** 生成数量 */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "生成数量至少为 1")
    @Max(value = 100, message = "单次最多生成 100 条")
    private Integer count;

    /** 可开通车场数量 */
    @NotNull(message = "可开通车场数量不能为空")
    @Min(value = 1, message = "可开通车场数量至少为 1")
    private Integer maxParkingCount;

    /** 有效期开始 */
    @NotNull(message = "有效期开始不能为空")
    private LocalDate validStart;

    /** 有效期结束 */
    @NotNull(message = "有效期结束不能为空")
    private LocalDate validEnd;

    /** 最大使用次数 */
    @NotNull(message = "最大使用次数不能为空")
    @Min(value = 1, message = "最大使用次数至少为 1")
    private Integer maxUseCount;

    /** 功能版本类型：BASIC / STANDARD / PREMIUM */
    @NotBlank(message = "版本类型不能为空")
    private String versionType;

    // ==================== getter / setter ====================

    public Integer getCount() { return count; }
    public void setCount(Integer count) { this.count = count; }

    public Integer getMaxParkingCount() { return maxParkingCount; }
    public void setMaxParkingCount(Integer maxParkingCount) { this.maxParkingCount = maxParkingCount; }

    public LocalDate getValidStart() { return validStart; }
    public void setValidStart(LocalDate validStart) { this.validStart = validStart; }

    public LocalDate getValidEnd() { return validEnd; }
    public void setValidEnd(LocalDate validEnd) { this.validEnd = validEnd; }

    public Integer getMaxUseCount() { return maxUseCount; }
    public void setMaxUseCount(Integer maxUseCount) { this.maxUseCount = maxUseCount; }

    public String getVersionType() { return versionType; }
    public void setVersionType(String versionType) { this.versionType = versionType; }
}
