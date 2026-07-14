package com.jushan.platform.modules.department.vo;

import lombok.Data;

import java.util.List;

/**
 * 部门树形视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class DepartmentTreeVO {

    /** 部门ID */
    private Long id;

    /** 上级部门ID */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 部门编码 */
    private String code;

    /** 部门级别 */
    private Integer level;

    /** 排序 */
    private Integer sortOrder;

    /** 子部门列表 */
    private List<DepartmentTreeVO> children;
}
