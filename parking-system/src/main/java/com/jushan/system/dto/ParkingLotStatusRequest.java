package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 停车场状态变更请求（启用/停用）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLotStatusRequest {

    /** 操作类型：ENABLED / DISABLED */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /** 操作原因 */
    @Size(max = 255, message = "原因最长255个字符")
    private String reason;

    // --- 停用时的保留范围（仅在 action=DISABLED 时有效） ---

    /** 是否允许新车入场：1-允许, 0-禁止 */
    private Integer disableNewEntries;

    /** 是否允许缴费：1-允许, 0-禁止 */
    private Integer disablePayment;

    /** 是否允许出场：1-允许, 0-禁止 */
    private Integer disableExit;

    /** 是否保留自动开闸：1-保留, 0-关闭 */
    private Integer disableAutoGate;

    /** 是否仅限制后台配置：1-是, 0-否 */
    private Integer disableOnlyConfig;

    // ==================== getter / setter ====================

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public Integer getDisableNewEntries() { return disableNewEntries; }
    public void setDisableNewEntries(Integer disableNewEntries) { this.disableNewEntries = disableNewEntries; }

    public Integer getDisablePayment() { return disablePayment; }
    public void setDisablePayment(Integer disablePayment) { this.disablePayment = disablePayment; }

    public Integer getDisableExit() { return disableExit; }
    public void setDisableExit(Integer disableExit) { this.disableExit = disableExit; }

    public Integer getDisableAutoGate() { return disableAutoGate; }
    public void setDisableAutoGate(Integer disableAutoGate) { this.disableAutoGate = disableAutoGate; }

    public Integer getDisableOnlyConfig() { return disableOnlyConfig; }
    public void setDisableOnlyConfig(Integer disableOnlyConfig) { this.disableOnlyConfig = disableOnlyConfig; }
}
