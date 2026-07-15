package com.jushan.system.vo;

import java.util.ArrayList;
import java.util.List;

/**
 * 公司/集团树形节点视图。
 *
 * <p><b>已废弃（@Deprecated）：</b>旧风格 {@code com.jushan.system.controller.CompanyController}
 * 的响应视图，已迁移至 {@code com.jushan.platform.modules.company.vo.CompanyTreeVO}。
 *
 * @author Jushan Platform
 * @since 1.0.0
 * @deprecated 自 1.0.0 起废弃，迁移目标见类注释。
 */
@Deprecated
public class CompanyTreeVO {

    /** 公司 ID */
    private Long id;

    /** 上级公司 ID */
    private Long parentId;

    /** 公司名称 */
    private String name;

    /** 公司级别：1-集团, 2-子公司, 3-分公司 */
    private Integer level;

    /** 状态：1-正常, 2-暂停, 3-注销 */
    private Integer status;

    /** 同级排序 */
    private Integer sortOrder;

    /** 树路径编码 */
    private String path;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 子节点 */
    private List<CompanyTreeVO> children = new ArrayList<>();

    // ==================== getter / setter ====================

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

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

    public List<CompanyTreeVO> getChildren() { return children; }
    public void setChildren(List<CompanyTreeVO> children) { this.children = children; }
}
