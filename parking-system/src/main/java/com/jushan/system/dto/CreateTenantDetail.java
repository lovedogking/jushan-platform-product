package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 超管直接创建租户请求参数。
 * <p>
 * 超管在租户管理页面直接创建租户，无需走自助注册→审核链路。
 * 创建后状态直接为 {@code ENABLED}，同时自动创建默认集团。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class CreateTenantDetail {

    /** 企业名称 */
    @NotBlank(message = "企业名称不能为空")
    @Size(max = 128, message = "企业名称长度不能超过128个字符")
    private String name;

    /** 联系人 */
    @NotBlank(message = "联系人不能为空")
    @Size(max = 64, message = "联系人长度不能超过64个字符")
    private String contactPerson;

    /** 联系电话 */
    @NotBlank(message = "联系电话不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "请输入正确的手机号")
    private String contactPhone;

    /** 最大停车场数量（默认 3） */
    private Integer maxParkingLots = 3;

    /** 最大设备数量（默认 10） */
    private Integer maxDevices = 10;

    /** 最大员工账号数量（默认 20） */
    private Integer maxEmployees = 20;

    // ==================== getter / setter ====================

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getContactPerson() { return contactPerson; }
    public void setContactPerson(String contactPerson) { this.contactPerson = contactPerson; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public Integer getMaxParkingLots() { return maxParkingLots; }
    public void setMaxParkingLots(Integer maxParkingLots) { this.maxParkingLots = maxParkingLots; }

    public Integer getMaxDevices() { return maxDevices; }
    public void setMaxDevices(Integer maxDevices) { this.maxDevices = maxDevices; }

    public Integer getMaxEmployees() { return maxEmployees; }
    public void setMaxEmployees(Integer maxEmployees) { this.maxEmployees = maxEmployees; }
}
