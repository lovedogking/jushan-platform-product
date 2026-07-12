package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 创建员工请求参数。
 * <p>
 * 客户管理员在运营端创建员工账号，分配固定角色和停车场授权范围。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateEmployeeRequest {

    /** 员工姓名 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过64个字符")
    private String displayName;

    /** 登录手机号 */
    @NotBlank(message = "手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String phone;

    /** 登录密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度须在6-64个字符之间")
    private String password;

    /** 角色编码（仅限 parking_manager / finance / device_maintenance / booth_operator） */
    @NotBlank(message = "角色不能为空")
    private String roleCode;

    /** 授权停车场 ID 列表（至少一个） */
    @NotEmpty(message = "授权停车场不能为空")
    private List<Long> parkingLotIds;

    // ==================== getter / setter ====================

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }
}
