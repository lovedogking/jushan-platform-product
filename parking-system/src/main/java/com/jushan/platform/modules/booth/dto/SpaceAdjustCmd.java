package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 岗亭余位调整命令。
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>SET — 直接设定目标剩余车位数</li>
 *   <li>ADJUST — 加减调整（正数加、负数减）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.5.3
 */
public class SpaceAdjustCmd {

    /** 操作模式：SET（直接设定）/ ADJUST（加减调整） */
    @NotBlank(message = "操作模式不能为空")
    private String mode;

    /** 数值：SET 模式为目标值，ADJUST 模式为增量（正加负减） */
    @NotNull(message = "数值不能为空")
    private Integer value;

    /** 停车场 ID */
    @NotNull(message = "停车场ID不能为空")
    private Long parkingLotId;

    /** 修改原因 */
    @NotBlank(message = "修改原因不能为空")
    @Size(max = 255, message = "原因最长255个字符")
    private String reason;

    // ==================== 常量 ====================

    public static final String MODE_SET = "SET";
    public static final String MODE_ADJUST = "ADJUST";

    // ==================== getter / setter ====================

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public Integer getValue() { return value; }
    public void setValue(Integer value) { this.value = value; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
