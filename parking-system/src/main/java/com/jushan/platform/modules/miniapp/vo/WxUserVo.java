package com.jushan.platform.modules.miniapp.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 微信用户信息 VO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class WxUserVo {

    /** 用户 ID */
    private Long id;

    /** 微信昵称（脱敏） */
    private String nickname;

    /** 微信头像 URL */
    private String avatarUrl;

    /** 手机号（脱敏） */
    private String maskedPhone;

    /** 手机号是否已验证 */
    private Boolean phoneVerified;

    /** 状态 */
    private String status;

    /** 绑定车牌数量 */
    private Integer plateCount;

    /** 默认车牌 */
    private PlateBindingVo defaultPlate;

    /** 绑定车牌列表 */
    private List<PlateBindingVo> plates;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }

    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }

    public String getMaskedPhone() { return maskedPhone; }
    public void setMaskedPhone(String maskedPhone) { this.maskedPhone = maskedPhone; }

    public Boolean getPhoneVerified() { return phoneVerified; }
    public void setPhoneVerified(Boolean phoneVerified) { this.phoneVerified = phoneVerified; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getPlateCount() { return plateCount; }
    public void setPlateCount(Integer plateCount) { this.plateCount = plateCount; }

    public PlateBindingVo getDefaultPlate() { return defaultPlate; }
    public void setDefaultPlate(PlateBindingVo defaultPlate) { this.defaultPlate = defaultPlate; }

    public List<PlateBindingVo> getPlates() { return plates; }
    public void setPlates(List<PlateBindingVo> plates) { this.plates = plates; }

    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(LocalDateTime lastLoginAt) { this.lastLoginAt = lastLoginAt; }
}