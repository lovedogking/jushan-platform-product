package com.jushan.system.vo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车场视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ParkingLotVO {

    private Long id;
    private Long tenantId;
    private String name;
    private String address;
    private String contactPhone;
    private BigDecimal longitude;
    private BigDecimal latitude;
    private Integer totalSpaces;
    private Integer currentVehicles;
    private Integer remainingSpaces;
    private String status;
    private String paymentMode;
    private Integer imageRetentionDays;
    private Integer dataRetentionDays;
    private Integer freeExitMinutes;
    private String manualReleasePolicy;
    private String offlinePolicy;
    private Integer disableNewEntries;
    private Integer disablePayment;
    private Integer disableExit;
    private Integer disableAutoGate;
    private Integer disableOnlyConfig;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

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

    public Integer getCurrentVehicles() { return currentVehicles; }
    public void setCurrentVehicles(Integer currentVehicles) { this.currentVehicles = currentVehicles; }

    public Integer getRemainingSpaces() { return remainingSpaces; }
    public void setRemainingSpaces(Integer remainingSpaces) { this.remainingSpaces = remainingSpaces; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

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

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
