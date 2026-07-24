package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 车辆实体。
 * <p>
 * 存储车主名下的车辆信息。一辆车可被多个微信用户绑定（需配置策略）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("vehicle")
public class Vehicle implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 车辆类型：小型车 */
    public static final String TYPE_SMALL = "SMALL";
    /** 车辆类型：大型车 */
    public static final String TYPE_LARGE = "LARGE";
    /** 车辆类型：新能源车 */
    public static final String TYPE_NEW_ENERGY = "NEW_ENERGY";
    /** 车辆类型：其他 */
    public static final String TYPE_OTHER = "OTHER";

    /** 状态：正常 */
    public static final String STATUS_ACTIVE = "ACTIVE";
    /** 状态：禁用 */
    public static final String STATUS_DISABLED = "DISABLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 车牌号（标准格式，如：京A12345） */
    private String vehiclePlate;

    /** 车辆类型：SMALL-小型车, LARGE-大型车, NEW_ENERGY-新能源车, OTHER-其他 */
    private String vehicleType;

    /** 车辆品牌 */
    private String brand;

    /** 车辆颜色 */
    private String color;

    /** 车主姓名（可选，用于月卡等场景） */
    private String ownerName;

    /** 状态：ACTIVE-正常, DISABLED-禁用 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删除时间（null 表示未删除） */
    private LocalDateTime deletedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getVehiclePlate() { return vehiclePlate; }
    public void setVehiclePlate(String vehiclePlate) { this.vehiclePlate = vehiclePlate; }

    public String getVehicleType() { return vehicleType; }
    public void setVehicleType(String vehicleType) { this.vehicleType = vehicleType; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 判断是否为小型车。
     */
    public boolean isSmallCar() {
        return TYPE_SMALL.equals(vehicleType);
    }

    /**
     * 判断是否为大型车。
     */
    public boolean isLargeCar() {
        return TYPE_LARGE.equals(vehicleType);
    }

    /**
     * 判断是否为新能源车。
     */
    public boolean isNewEnergy() {
        return TYPE_NEW_ENERGY.equals(vehicleType);
    }
}