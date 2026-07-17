package com.jushan.system.vo;

/**
 * 岗亭监控车道相机视图（任务包 3-5）。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public class BoothLaneCameraVO {

    private Long deviceId;
    private String name;
    /** PRIMARY / BACKUP */
    private String role;
    /** ENTRY / EXIT */
    private String direction;
    private Boolean online;
    private Boolean isActive;

    // ==================== getter / setter ====================

    public Long getDeviceId() { return deviceId; }
    public void setDeviceId(Long deviceId) { this.deviceId = deviceId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }

    public String getDirection() { return direction; }
    public void setDirection(String direction) { this.direction = direction; }

    public Boolean getOnline() { return online; }
    public void setOnline(Boolean online) { this.online = online; }

    public Boolean getIsActive() { return isActive; }
    public void setIsActive(Boolean isActive) { this.isActive = isActive; }
}
