package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建停车场请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateParkingLotRequest {

    /** 所属公司 ID（可选，一期不强制绑定公司） */
    private Long companyId;

    /** 目标租户 ID（可选，仅平台用户创建时生效；租户用户一律从登录会话推导，忽略该字段） */
    private Long tenantId;

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
    private String longitude;

    /** 纬度（预留） */
    private String latitude;

    /** 总车位数（默认 0，由区域汇总计算） */
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

    /** 重复入场策略：REJECT-拒绝, UPDATE-更新原记录, EXCEPTION-创建异常记录 */
    private String duplicateEntryPolicy;

    // ==================== getter / setter ====================

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getLongitude() { return longitude; }
    public void setLongitude(String longitude) { this.longitude = longitude; }

    public String getLatitude() { return latitude; }
    public void setLatitude(String latitude) { this.latitude = latitude; }

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

    public String getDuplicateEntryPolicy() { return duplicateEntryPolicy; }
    public void setDuplicateEntryPolicy(String duplicateEntryPolicy) { this.duplicateEntryPolicy = duplicateEntryPolicy; }
}
