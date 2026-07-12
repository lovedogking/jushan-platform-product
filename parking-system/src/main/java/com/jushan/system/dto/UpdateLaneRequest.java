package com.jushan.system.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新车道请求。
 * <p>
 * 所有字段均为可选，仅非 null 字段被更新。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UpdateLaneRequest {

    /** 车道名称 */
    @Size(max = 128, message = "车道名称最长128个字符")
    private String name;

    /** 车道编码（停车场内唯一） */
    @Size(max = 64, message = "车道编码最长64个字符")
    private String code;

    /** 车道方向 */
    private String direction;

    /** 是否为关键车道 */
    private Integer isKeyLane;

    /** 自动放行策略 */
    private String autoReleasePolicy;

    /** 备注 */
    @Size(max = 255, message = "备注最长255个字符")
    private String description;

    // ==================== getter / setter ====================

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
