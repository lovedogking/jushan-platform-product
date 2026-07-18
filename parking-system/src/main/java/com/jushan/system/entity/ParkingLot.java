package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 停车场实体（T18 完整字段）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("parking_lot")
public class ParkingLot implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 所属公司 ID */
    private Long companyId;

    /** 所属集团 ID（冗余，用于快速按集团查询） */
    private Long groupId;

    /** 停车场名称 */
    private String name;

    /** 地址 */
    private String address;

    /** 联系电话 */
    private String contactPhone;

    /** 经度（预留） */
    private BigDecimal longitude;

    /** 纬度（预留） */
    private BigDecimal latitude;

    /** 总车位数 */
    private Integer totalSpaces;

    /** 当前在场车辆数（只读，由停车记录计算） */
    private Integer currentVehicles;

    /** 剩余车位数（默认 = totalSpaces - currentVehicles，允许人工修正） */
    private Integer remainingSpaces;

    /** 状态：ENABLED-启用, DISABLED-禁用 */
    private String status;

    /** 支付模式：PLATFORM-平台统一商户, CUSTOMER-客户独立商户 */
    private String paymentMode;

    /** 抓拍图片保存天数 */
    private Integer imageRetentionDays;

    /** 业务数据保存天数 */
    private Integer dataRetentionDays;

    /** 缴费后免费离场时间（分钟） */
    private Integer freeExitMinutes;

    /** 人工放行策略：ADMIN_ONLY-仅管理员, BOOTH_ALLOWED-岗亭可放行 */
    private String manualReleasePolicy;

    /** 离线运行策略：ALLOW_ENTRY_EXIT-允许出入, ALLOW_EXIT_ONLY-只出不进, STRICT-禁止通行 */
    private String offlinePolicy;

    /** 重复入场策略：REJECT-拒绝, UPDATE-更新原记录, EXCEPTION-创建异常记录 */
    private String duplicateEntryPolicy;

    /** 停用时是否允许新车入场：1-允许, 0-禁止（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableNewEntries;

    /** 停用时是否允许缴费：1-允许, 0-禁止（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disablePayment;

    /** 停用时是否允许出场：1-允许, 0-禁止（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableExit;

    /** 停用时是否保留自动开闸：1-保留, 0-关闭（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableAutoGate;

    /** 停用时是否仅限制后台配置：1-是, 0-否（数据库暂未添加此列） */
    @TableField(exist = false)
    private Integer disableOnlyConfig;

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

    public String getDuplicateEntryPolicy() { return duplicateEntryPolicy; }
    public void setDuplicateEntryPolicy(String duplicateEntryPolicy) { this.duplicateEntryPolicy = duplicateEntryPolicy; }

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
