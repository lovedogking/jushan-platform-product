package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * P云商户配置实体。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("pay_merchant_config")
public class PayMerchantConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long parkingLotId;

    /** P云应用ID */
    private String appId;

    /** P云应用密钥（加密存储） */
    private String appSecret;

    /** P云商户号 */
    private String merchantNo;

    /** P云停车场UUID */
    private String parkUuid;

    /** 支付回调地址 */
    private String notifyUrl;

    /** 支付成功前端回调地址 */
    private String callbackUrl;

    /** 状态：ACTIVE-生效, DISABLED-禁用 */
    private String status;

    /** 环境：PROD-生产, SANDBOX-沙箱 */
    private String env;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
    public static final String ENV_PROD = "PROD";
    public static final String ENV_SANDBOX = "SANDBOX";

    // getters/setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }
    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }
    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }
    public String getAppSecret() { return appSecret; }
    public void setAppSecret(String appSecret) { this.appSecret = appSecret; }
    public String getMerchantNo() { return merchantNo; }
    public void setMerchantNo(String merchantNo) { this.merchantNo = merchantNo; }
    public String getParkUuid() { return parkUuid; }
    public void setParkUuid(String parkUuid) { this.parkUuid = parkUuid; }
    public String getNotifyUrl() { return notifyUrl; }
    public void setNotifyUrl(String notifyUrl) { this.notifyUrl = notifyUrl; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getEnv() { return env; }
    public void setEnv(String env) { this.env = env; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
