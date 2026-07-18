package com.jushan.platform.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建管理员账号请求参数。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class AdminAccountCreateCmd {

    /** 登录账号 */
    @NotBlank(message = "登录账号不能为空")
    @Size(max = 64, message = "登录账号长度不能超过64个字符")
    private String username;

    /** 初始密码（为空时系统随机生成） */
    @Size(min = 6, max = 64, message = "密码长度须在6-64个字符之间")
    private String password;

    /** 真实姓名 */
    @NotBlank(message = "真实姓名不能为空")
    @Size(max = 64, message = "真实姓名长度不能超过64个字符")
    private String realName;

    /** 手机号 */
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String phone;

    /** 邮箱 */
    @Pattern(regexp = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$", message = "请输入正确的邮箱")
    @Size(max = 128, message = "邮箱长度不能超过128个字符")
    private String email;

    /** 管理员级别：1平台 2公司 3停车场 */
    @NotNull(message = "管理员级别不能为空")
    private Integer level;

    /** 所属公司 ID（二级、三级必填） */
    private Long companyId;

    /** 所属停车场 ID（三级必填） */
    private Long lotId;

    /** 账号状态：0正常 1禁用 2锁定 */
    private Integer status;

    /** 绑定角色 ID 列表 */
    private List<Long> roleIds;

    /** 租户 ID（平台用户创建二级/三级账号时必填） */
    private Long tenantId;

    /** 首次登录是否强制修改密码：0否 1是 */
    private Integer mustChangePassword;

    /** 是否允许费用减免：0否 1是 */
    private Integer allowFeeReduction;

    /** 分配停车场 ID 列表 */
    private List<Long> parkingLotIds;

    // ==================== getter / setter ====================

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRealName() { return realName; }
    public void setRealName(String realName) { this.realName = realName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Long getCompanyId() { return companyId; }
    public void setCompanyId(Long companyId) { this.companyId = companyId; }

    public Long getLotId() { return lotId; }
    public void setLotId(Long lotId) { this.lotId = lotId; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public List<Long> getRoleIds() { return roleIds; }
    public void setRoleIds(List<Long> roleIds) { this.roleIds = roleIds; }

    public Long getTenantId() { return tenantId; }
    public void setTenantId(Long tenantId) { this.tenantId = tenantId; }

    public Integer getMustChangePassword() { return mustChangePassword; }
    public void setMustChangePassword(Integer mustChangePassword) { this.mustChangePassword = mustChangePassword; }

    public Integer getAllowFeeReduction() { return allowFeeReduction; }
    public void setAllowFeeReduction(Integer allowFeeReduction) { this.allowFeeReduction = allowFeeReduction; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }
}
