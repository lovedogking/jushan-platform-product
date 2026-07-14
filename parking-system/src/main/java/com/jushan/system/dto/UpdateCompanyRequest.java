package com.jushan.system.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

/**
 * 更新公司/集团请求。
 * <p>
 * 所有字段可选；传入非 null 字段才更新。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class UpdateCompanyRequest {

    /** 上级公司 ID（null 表示顶级集团） */
    private Long parentId;

    /** 公司名称 */
    @Size(max = 128, message = "公司名称最长128个字符")
    private String name;

    /** 公司级别：1-集团, 2-子公司, 3-分公司 */
    @Min(value = 1, message = "公司级别只能在 1-3 之间")
    @Max(value = 3, message = "公司级别只能在 1-3 之间")
    private Integer level;

    /** 状态：1-正常, 2-暂停, 3-注销 */
    @Min(value = 1, message = "状态只能在 1-3 之间")
    @Max(value = 3, message = "状态只能在 1-3 之间")
    private Integer status;

    /** 同级排序 */
    private Integer sortOrder;

    /** 联系人 */
    @Size(max = 64, message = "联系人最长64个字符")
    private String contactName;

    /** 联系电话 */
    @Size(max = 32, message = "联系电话最长32个字符")
    private String contactPhone;

    // ==================== getter / setter ====================

    public Long getParentId() { return parentId; }
    public void setParentId(Long parentId) { this.parentId = parentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Integer getLevel() { return level; }
    public void setLevel(Integer level) { this.level = level; }

    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }

    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }
}
