package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 公司/集团档案实体。
 * <p>
 * 支持集团-子公司-分公司三级树形结构，通过 {@link #parentId} 自关联，
 * {@link #path} 字段用于一次性查询子树。
 * <p>
 * 软删除通过 {@link #deletedAt} 手动实现，未使用 MyBatis-Plus 逻辑删除注解，
 * 与当前项目其他业务表保持一致风格。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("company")
public class Company extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上级公司 ID（顶级为 null） */
    private Long parentId;

    /** 公司名称 */
    private String name;

    /** 公司级别：1-集团, 2-子公司, 3-分公司 */
    private Integer level;

    /** 状态：1-正常, 2-暂停, 3-注销 */
    private Integer status;

    /** 同级排序 */
    private Integer sortOrder;

    /** 树路径编码，如 /1/12/123/ */
    private String path;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 软删除时间（null 表示未删除） */
    private LocalDateTime deletedAt;

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

    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }

    public String getContactName() { return contactName; }
    public void setContactName(String contactName) { this.contactName = contactName; }

    public String getContactPhone() { return contactPhone; }
    public void setContactPhone(String contactPhone) { this.contactPhone = contactPhone; }

    public LocalDateTime getDeletedAt() { return deletedAt; }
    public void setDeletedAt(LocalDateTime deletedAt) { this.deletedAt = deletedAt; }
}
