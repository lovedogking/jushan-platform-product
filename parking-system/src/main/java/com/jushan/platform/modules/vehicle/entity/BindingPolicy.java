package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 车辆绑定策略配置实体。
 * <p>
 * 控制同一车牌是否允许多账号绑定、默认验证方式等。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("binding_policy")
public class BindingPolicy implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 多账号模式：单一微信账号绑定 */
    public static final String MULTI_ACCOUNT_SINGLE = "SINGLE_ACCOUNT";
    /** 多账号模式：主车主授权其他账号 */
    public static final String MULTI_ACCOUNT_AUTHORIZED = "AUTHORIZED_MULTI";
    /** 多账号模式：允许多账号直接绑定 */
    public static final String MULTI_ACCOUNT_ALLOW = "ALLOW_MULTI";

    /** 默认验证方式：仅车牌 */
    public static final String VERIFY_METHOD_PLATE_ONLY = "PLATE_ONLY";
    /** 默认验证方式：手机号验证 */
    public static final String VERIFY_METHOD_PHONE_VERIFY = "PHONE_VERIFY";
    /** 默认验证方式：上传行驶证 */
    public static final String VERIFY_METHOD_UPLOAD_CERT = "UPLOAD_CERT";
    /** 默认验证方式：人工审核 */
    public static final String VERIFY_METHOD_MANUAL_AUDIT = "MANUAL_AUDIT";

    /** 状态：启用 */
    public static final String STATUS_ENABLED = "ENABLED";
    /** 状态：禁用 */
    public static final String STATUS_DISABLED = "DISABLED";

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 租户 ID（NULL 表示平台默认策略） */
    private Long tenantId;

    /** 停车场 ID（NULL 表示租户默认策略） */
    private Long parkingLotId;

    /** 多账号模式：SINGLE_ACCOUNT-单一微信账号绑定, AUTHORIZED_MULTI-主车主授权其他账号, ALLOW_MULTI-允许多账号直接绑定 */
    private String multiAccountMode;

    /** 默认验证方式：PLATE_ONLY-仅车牌, PHONE_VERIFY-手机号验证, UPLOAD_CERT-上传行驶证, MANUAL_AUDIT-人工审核 */
    private String defaultVerifyMethod;

    /** 单个用户最大绑定车辆数 */
    private Integer maxBindingsPerUser;

    /** 单个车牌最大绑定用户数 */
    private Integer maxUsersPerPlate;

    /** 是否允许更换车牌：0-否, 1-是 */
    private Boolean allowChangePlate;

    /** 状态：ENABLED-启用, DISABLED-禁用 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public String getMultiAccountMode() { return multiAccountMode; }
    public void setMultiAccountMode(String multiAccountMode) { this.multiAccountMode = multiAccountMode; }

    public String getDefaultVerifyMethod() { return defaultVerifyMethod; }
    public void setDefaultVerifyMethod(String defaultVerifyMethod) { this.defaultVerifyMethod = defaultVerifyMethod; }

    public Integer getMaxBindingsPerUser() { return maxBindingsPerUser; }
    public void setMaxBindingsPerUser(Integer maxBindingsPerUser) { this.maxBindingsPerUser = maxBindingsPerUser; }

    public Integer getMaxUsersPerPlate() { return maxUsersPerPlate; }
    public void setMaxUsersPerPlate(Integer maxUsersPerPlate) { this.maxUsersPerPlate = maxUsersPerPlate; }

    public Boolean getAllowChangePlate() { return allowChangePlate; }
    public void setAllowChangePlate(Boolean allowChangePlate) { this.allowChangePlate = allowChangePlate; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    /**
     * 判断是否为平台级默认策略。
     */
    public boolean isPlatformDefault() {
        return tenantId == null && parkingLotId == null;
    }

    /**
     * 判断是否为租户级策略。
     */
    public boolean isTenantLevel() {
        return tenantId != null && parkingLotId == null;
    }

    /**
     * 判断是否为停车场级策略。
     */
    public boolean isParkingLotLevel() {
        return tenantId != null && parkingLotId != null;
    }

    /**
     * 判断是否允许单一账号绑定。
     */
    public boolean isSingleAccountMode() {
        return MULTI_ACCOUNT_SINGLE.equals(multiAccountMode);
    }
}