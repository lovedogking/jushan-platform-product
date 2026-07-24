package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 绑定车牌请求 DTO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class BindPlateRequest {

    /** 车牌号（标准格式，如：京A12345） */
    @NotBlank(message = "车牌号不能为空")
    private String plate;

    /** 车辆类型：SMALL-小型车, LARGE-大型车, NEW_ENERGY-新能源车, OTHER-其他 */
    private String vehicleType = "SMALL";

    /** 车辆品牌 */
    private String brand;

    /** 车辆颜色 */
    private String color;

    /** 车主姓名（可选） */
    private String ownerName;

    /** 验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核 */
    private String verifyMethod = "PLATE_ONLY";

    // ==================== getter / setter ====================

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getVerifyMethod() { return verifyMethod; }
    public void setVerifyMethod(String verifyMethod) { this.verifyMethod = verifyMethod; }
}