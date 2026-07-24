package com.jushan.platform.modules.miniapp.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.TableField;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 微信用户实体（车主端）。
 * <p>
 * 存储通过微信授权登录的车主账户信息。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("wx_user")
public class WxUser implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    private Long tenantId;

    /** 微信 OpenId（唯一标识） */
    private String openid;

    /** 微信 UnionId（跨应用唯一，可为空） */
    private String unionid;

    /** 微信昵称（脱敏展示） */
    private String nickname;

    /** 微信头像 URL */
    private String avatarUrl;

    /** 绑定手机号（脱敏展示） */
    private String phone;

    /** 微信会话密钥（用于旧版 getPhoneNumber 解密，可选） */
    private String sessionKey;

    /** 手机号是否已验证：0-未验证, 1-已验证 */
    private Boolean phoneVerified;

    /** 状态：ACTIVE-正常, DISABLED-禁用 */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    /** 软删除时间（null 表示未删除） */
    private LocalDateTime deletedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public String getOpenid() { return openid; }
    public void setOpenid(String openid) { this.openid = openid; }

    public String getUnionid() { return unionid; }
    public void setUnionid(String unionid) { this.unionid = unionid; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getSessionKey() { return sessionKey; }
    public void setSessionKey(String sessionKey) { this.sessionKey = sessionKey; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }

    /**
     * 获取脱敏手机号（显示前三位和后四位，中间用 * 替代）。
     */
    public String getMaskedPhone() {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * 获取脱敏昵称（只显示首尾字符，中间用 * 替代）。
     */
    public String getMaskedNickname() {
        if (nickname == null || nickname.isEmpty()) {
            return "";
        }
        if (nickname.length() <= 2) {
            return "**";
        }
        return nickname.charAt(0) + "**" + nickname.charAt(nickname.length() - 1);
    }
}