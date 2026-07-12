package com.jushan.system.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * 创建停车场请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateParkingLotRequest {

    /** 停车场名称 */
    @NotBlank(message = "停车场名称不能为空")
    @Size(max = 128, message = "停车场名称最长128个字符")
    private String name;

    /** 地址 */
    @Size(max = 255, message = "地址最长255个字符")
    private String address;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话最长20个字符")
    private String contactPhone;

    /** 经度（预留） */
    private BigDecimal longitude;

    /** 纬度（预留） */
    private BigDecimal latitude;

    /** 总车位数 */
    @NotNull(message = "总车位数不能为空")
    @Min(value = 0, message = "总车位数不能为负数")
    private Integer totalSpaces;

    /** 支付模式 */
    private String paymentMode;

    /** 抓拍图片保存天数 */
    private Integer imageRetentionDays;

    /** 业务数据保存天数 */
    private Integer dataRetentionDays;

    /** 缴费后免费离场时间（分钟） */
    private Integer freeExitMinutes;

    /** 人工放行策略 */
    private String manualReleasePolicy;

    /** 离线运行策略 */
    private String offlinePolicy;

    // ==================== getter / setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public BigDecimal getLongitude() { return longitude; }
    public void setLongitude(BigDecimal longitude) { this.longitude = longitude; }

    public BigDecimal getLatitude() { return latitude; }
    public void setLatitude(BigDecimal latitude) { this.latitude = latitude; }

    public Integer getTotalSpaces() { return totalSpaces; }
    public void setTotalSpaces(Integer totalSpaces) { this.totalSpaces = totalSpaces; }

    public String getPaymentMode() { return paymentMode; }
    public void setPaymentMode(String paymentMode) { this.paymentMode = paymentMode; }

    public Integer getImageRetentionDays() { return imageRetentionDays; }
    public void setImageRetentionDays(Integer imageRetentionDays) { this.imageRetentionDays = imageRetentionDays; }

    public Integer getDataRetentionDays() { return dataRetentionDays; }
    public void setDataRetentionDays(Integer dataRetentionDays) { this.dataRetentionDays = dataRetentionDays; }

    public Integer getFreeExitMinutes() { return freeExitMinutes; }
    public void setFreeExitMinutes(Integer freeExitMinutes) { this.freeExitMinutes = freeExitMinutes; }

    public String getManualReleasePolicy() { return manualReleasePolicy; }
    public void setManualReleasePolicy(String manualReleasePolicy) { this.manualReleasePolicy = manualReleasePolicy; }

    public String getOfflinePolicy() { return offlinePolicy; }
    public void setOfflinePolicy(String offlinePolicy) { this.offlinePolicy = offlinePolicy; }
}
