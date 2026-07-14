package com.jushan.platform.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 编辑管理员账号请求参数。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class AdminAccountUpdateCmd {

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
    @NotNull(message = "账号状态不能为空")
    private Integer status;

    /** 绑定角色 ID 列表 */
    private List<Long> roleIds;

    // ==================== getter / setter ====================

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
}
