package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 车牌绑定实体。
 * <p>
 * 建立微信用户与车辆的绑定关系，支持多绑定策略。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("plate_binding")
public class PlateBinding implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 绑定类型：车主本人绑定 */
    public static final String BINDING_TYPE_OWNER = "OWNER";
    /** 绑定类型：授权绑定 */
    public static final String BINDING_TYPE_AUTHORIZED = "AUTHORIZED";

    /** 验证方式：仅车牌 */
    public static final String VERIFY_METHOD_PLATE_ONLY = "PLATE_ONLY";
    /** 验证方式：手机号验证 */
    public static final String VERIFY_METHOD_PHONE_VERIFY = "PHONE_VERIFY";
    /** 验证方式：上传行驶证 */
    public static final String VERIFY_METHOD_UPLOAD_CERT = "UPLOAD_CERT";
    /** 验证方式：人工审核 */
    public static final String VERIFY_METHOD_MANUAL_AUDIT = "MANUAL_AUDIT";

    /** 验证状态：待验证 */
    public static final String VERIFY_STATUS_PENDING = "PENDING";
    /** 验证状态：已通过 */
    public static final String VERIFY_STATUS_APPROVED = "APPROVED";
    /** 验证状态：已拒绝 */
    public static final String VERIFY_STATUS_REJECTED = "REJECTED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 微信用户 ID */
    private Long wxUserId;

    /** 车辆 ID */
    private Long vehicleId;

    /** 绑定类型：OWNER-车主本人绑定, AUTHORIZED-授权绑定 */
    private String bindingType;

    /** 验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核 */
    private String verifyMethod;

    /** 验证状态：PENDING-待验证, APPROVED-已通过, REJECTED-已拒绝 */
    private String verifyStatus;

    /** 是否默认车牌：0-否, 1-是 */
    private Boolean isDefault;

    /** 备注/审核说明 */
    private String remark;

    /** 验证通过时间 */
    private LocalDateTime verifiedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删除时间（null 表示未删除） */
    private LocalDateTime deletedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getWxUserId() { return wxUserId; }
    public void setWxUserId(Long wxUserId) { this.wxUserId = wxUserId; }

    public Long getVehicleId() { return vehicleId; }
    public void setVehicleId(Long vehicleId) { this.vehicleId = vehicleId; }

    public String getBindingType() { return bindingType; }
    public void setBindingType(String bindingType) { this.bindingType = bindingType; }

    public String getVerifyMethod() { return verifyMethod; }
    public void setVerifyMethod(String verifyMethod) { this.verifyMethod = verifyMethod; }

    public String getVerifyStatus() { return verifyStatus; }
    public void setVerifyStatus(String verifyStatus) { this.verifyStatus = verifyStatus; }

    public Boolean getIsDefault() { return isDefault; }
    public void setIsDefault(Boolean isDefault) { this.isDefault = isDefault; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }

    public LocalDateTime getVerifiedAt() { return verifiedAt; }
    public void setVerifiedAt(LocalDateTime verifiedAt) { this.verifiedAt = verifiedAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 判断是否已通过验证。
     */
    public boolean isApproved() {
        return VERIFY_STATUS_APPROVED.equals(verifyStatus);
    }

    /**
     * 判断是否为默认车牌。
     */
    public boolean isDefaultPlate() {
        return Boolean.TRUE.equals(isDefault);
    }
}