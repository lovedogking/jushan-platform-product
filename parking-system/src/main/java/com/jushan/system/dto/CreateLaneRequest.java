package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 创建车道请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateLaneRequest {

    /** 所属停车场 ID */
    @NotNull(message = "所属停车场不能为空")
    private Long parkingLotId;

    /** 车道名称 */
    @NotBlank(message = "车道名称不能为空")
    @Size(max = 128, message = "车道名称最长128个字符")
    private String name;

    /** 车道编码（停车场内唯一） */
    @NotBlank(message = "车道编码不能为空")
    @Size(max = 64, message = "车道编码最长64个字符")
    private String code;

    /** 车道方向：ENTRY-入口, EXIT-出口, MIXED-混合 */
    @NotBlank(message = "车道方向不能为空")
    private String direction;

    /** 是否为关键车道 */
    private Integer isKeyLane;

    /** 自动放行策略 */
    private String autoReleasePolicy;

    /** 备注 */
    @Size(max = 255, message = "备注最长255个字符")
    private String description;

    // ==================== getter / setter ====================

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Integer getIsKeyLane() { return isKeyLane; }
    public void setIsKeyLane(Integer isKeyLane) { this.isKeyLane = isKeyLane; }

    public String getAutoReleasePolicy() { return autoReleasePolicy; }
    public void setAutoReleasePolicy(String autoReleasePolicy) { this.autoReleasePolicy = autoReleasePolicy; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
