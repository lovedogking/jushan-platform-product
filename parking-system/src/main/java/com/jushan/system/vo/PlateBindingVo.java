package com.jushan.system.vo;

import java.time.LocalDateTime;

/**
 * 车牌绑定信息 VO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class PlateBindingVo {

    /** 绑定记录 ID */
    private Long id;

    /** 车辆 ID */
    private Long vehicleId;

    /** 车牌号 */
    private String plate;

    /** 车辆类型 */
    private String vehicleType;

    /** 车辆品牌 */
    private String brand;

    /** 车辆颜色 */
    private String color;

    /** 绑定类型：OWNER-车主本人绑定, AUTHORIZED-授权绑定 */
    private String bindingType;

    /** 验证方式 */
    private String verifyMethod;

    /** 验证状态：PENDING-待验证, APPROVED-已通过, REJECTED-已拒绝 */
    private String verifyStatus;

    /** 是否默认车牌 */
    private Boolean isDefault;

    /** 绑定时间 */
    private LocalDateTime createdAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getBindingType() { return bindingType; }
    public void setBindingType(String bindingType) { this.bindingType = bindingType; }

    public String getVerifyMethod() { return verifyMethod; }
    public void setVerifyMethod(String verifyMethod) { this.verifyMethod = verifyMethod; }

    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}