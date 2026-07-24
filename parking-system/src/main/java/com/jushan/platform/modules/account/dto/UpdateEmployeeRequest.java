package com.jushan.platform.modules.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 更新员工请求参数。
 * <p>
 * 客户管理员可修改员工的姓名、角色和停车场授权。
 * 手机号（登录账号）不可修改。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UpdateEmployeeRequest {

    /** 员工姓名 */
    @NotBlank(message = "员工姓名不能为空")
    @Size(max = 64, message = "员工姓名长度不能超过64个字符")
    private String displayName;

    /** 角色编码 */
    @NotBlank(message = "角色不能为空")
    private String roleCode;

    /** 授权停车场 ID 列表 */
    @NotEmpty(message = "授权停车场不能为空")
    private List<Long> parkingLotIds;

    // ==================== getter / setter ====================

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }
}
