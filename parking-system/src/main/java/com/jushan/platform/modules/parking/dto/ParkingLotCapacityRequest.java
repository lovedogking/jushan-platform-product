package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 停车场容量变更请求（总车位修改或剩余车位人工修正）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLotCapacityRequest {

    /** 变更字段：total_spaces / remaining_spaces */
    @NotBlank(message = "变更字段不能为空")
    private String fieldName;

    /** 修改后数值 */
    @NotNull(message = "数值不能为空")
    @Min(value = 0, message = "数值不能为负数")
    private Integer value;

    /** 修改原因 */
    @NotBlank(message = "修改原因不能为空")
    @Size(max = 255, message = "原因最长255个字符")
    private String reason;

    // ==================== getter / setter ====================

    public String getFieldName() { return fieldName; }
    public void setFieldName(String fieldName) { this.fieldName = fieldName; }

    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
