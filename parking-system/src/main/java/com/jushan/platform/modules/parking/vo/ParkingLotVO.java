package com.jushan.platform.modules.parking.vo;

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
    private Long companyId;
    private Long groupId;
    private String companyName;
    private String name;
    private String address;
    private String contactPhone;
    private String contactName;
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
    private String duplicateEntryPolicy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getGroupId() { return groupId; }
    public void setGroupId(Long groupId) { this.groupId = groupId; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

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

    public String getDuplicateEntryPolicy() { return duplicateEntryPolicy; }
    public void setDuplicateEntryPolicy(String duplicateEntryPolicy) { this.duplicateEntryPolicy = duplicateEntryPolicy; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
