package com.jushan.platform.modules.account.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员账号视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class AdminAccountVO {

    /** 账号 ID（序列化为字符串，防止前端 JS Number 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 租户 ID */
    private Long tenantId;

    /** 公司 ID */
    private Long companyId;

    /** 停车场 ID */
    private Long lotId;

    /** 登录账号 */
    private String username;

    /** 真实姓名 */
    private String realName;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 管理员级别：1平台 2公司 3停车场 */
    private Integer level;

    /** 账号状态：0正常 1禁用 2锁定 */
    private Integer status;

    /** 连续登录失败次数 */
    private Integer loginFailCount;

    /** 锁定截止时间 */
    private LocalDateTime lockUntil;

    /** 最后登录时间 */
    private LocalDateTime lastLoginTime;

    /** 首次登录是否强制修改密码：0否 1是 */
    private Integer mustChangePassword;

    /** 是否允许费用减免：0否 1是 */
    private Integer allowFeeReduction;

    /** 分配停车场 ID 列表 */
    private List<Long> parkingLotIds;

    /** 绑定角色 ID 列表 */
    private List<Long> roleIds;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getLoginFailCount() { return loginFailCount; }
    public void setLoginFailCount(Integer loginFailCount) { this.loginFailCount = loginFailCount; }

    public LocalDateTime getLockUntil() { return lockUntil; }
    public void setLockUntil(LocalDateTime lockUntil) { this.lockUntil = lockUntil; }

    public LocalDateTime getLastLoginTime() { return lastLoginTime; }
    public void setLastLoginTime(LocalDateTime lastLoginTime) { this.lastLoginTime = lastLoginTime; }

    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Integer getAllowFeeReduction() { return allowFeeReduction; }
    public void setAllowFeeReduction(Integer allowFeeReduction) { this.allowFeeReduction = allowFeeReduction; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
