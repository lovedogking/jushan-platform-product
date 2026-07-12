package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 客户注册请求参数。
 * <p>
 * 客户在运营端注册页面填写企业信息和管理员账号后提交，
 * 系统创建待审核租户和待审核管理员账号。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class RegisterRequest {

    /** 企业名称 */
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 128, message = "企业名称长度不能超过128个字符")
    private String companyName;

    /** 联系人 */
    @NotBlank(message = "联系人不能为空")
    @Size(max = 64, message = "联系人长度不能超过64个字符")
    private String contactPerson;

    /** 联系电话（同时作为管理员登录账号） */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String contactPhone;

    /** 管理员登录密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须在6-64个字符之间")
    private String password;

    // ==================== getter / setter ====================

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
