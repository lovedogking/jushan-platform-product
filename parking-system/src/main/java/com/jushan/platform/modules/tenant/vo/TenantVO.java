package com.jushan.platform.modules.tenant.vo;

import java.time.LocalDateTime;

/**
 * 租户信息视图。
 * <p>
 * 返回给总后台客户管理页面的租户数据。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class TenantVO {

    /** 租户 ID */
    private Long id;

    /** 企业名称 */     
    private String name;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话（脱敏） */
    private String contactPhone;

    /** 状态 */
    private String status;

    /** 管理员账号 ID */
    private Long adminUserId;

    /** 管理员登录账号（脱敏） */
    private String adminUsername;

    /** 最大停车场数量 */
    private Integer maxParkingLots;

    /** 最大设备数量 */
    private Integer maxDevices;

    /** 最大员工账号数量 */
    private Integer maxEmployees;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Long getAdminUserId() { return adminUserId; }
    public void setAdminUserId(Long adminUserId) { this.adminUserId = adminUserId; }

    public String getAdminUsername() { return adminUsername; }
    public void setAdminUsername(String adminUsername) { this.adminUsername = adminUsername; }

    public Integer getMaxParkingLots() { return maxParkingLots; }
    public void setMaxParkingLots(Integer maxParkingLots) { this.maxParkingLots = maxParkingLots; }

    public Integer getMaxDevices() { return maxDevices; }
    public void setMaxDevices(Integer maxDevices) { this.maxDevices = maxDevices; }

    public Integer getMaxEmployees() { return maxEmployees; }
    public void setMaxEmployees(Integer maxEmployees) { this.maxEmployees = maxEmployees; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
