package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 租户实体（客户/商户）。
 * <p>
 * 一个客户对应一个租户，一个租户可以管理多个停车场。
 * 注册后状态为 PENDING_REVIEW，总后台审核通过后变为 ENABLED。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("tenant")
public class Tenant implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 企业名称 */
    private String name;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 状态：PENDING_REVIEW / ENABLED / DISABLED / REJECTED */
    private String status;

    /** 关联 sys_user 的管理员账号 ID */
    private Long adminUserId;

    /** 最大停车场数量 */
    private Integer maxParkingLots;

    /** 最大设备数量 */
    private Integer maxDevices;

    /** 最大员工账号数量 */
    private Integer maxEmployees;

    private LocalDateTime createdAt;

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
