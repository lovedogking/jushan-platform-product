package com.jushan.platform.modules.account.vo;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 员工视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class EmployeeVO {

    /** 员工 ID（sys_user.id） */
    private Long id;

    /** 登录手机号 */
    private String phone;

    /** 员工姓名 */
    private String displayName;

    /** 角色编码 */
    private String roleCode;

    /** 角色显示名称 */
    private String roleName;

    /** 账号状态 */
    private String status;

    /** 授权停车场 ID 列表 */
    private List<Long> parkingLotIds;

    /** 授权停车场名称列表 */
    private List<String> parkingLotNames;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getRoleCode() { return roleCode; }
    public void setRoleCode(String roleCode) { this.roleCode = roleCode; }

    public String getRoleName() { return roleName; }
    public void setRoleName(String roleName) { this.roleName = roleName; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public List<Long> getParkingLotIds() { return parkingLotIds; }
    public void setParkingLotIds(List<Long> parkingLotIds) { this.parkingLotIds = parkingLotIds; }

    public List<String> getParkingLotNames() { return parkingLotNames; }
    public void setParkingLotNames(List<String> parkingLotNames) { this.parkingLotNames = parkingLotNames; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
