package com.jushan.platform.modules.company.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 公司树形视图对象。
 * <p>
 * 用于返回集团-公司-分公司的层级树结构，包含递归子节点列表。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class CompanyTreeVO {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 上级公司ID（0表示顶级集团） */
    private Long parentId;

    /** 公司名称 */
    private String name;

    /** 公司编码 */
    private String code;

    /** 级别：1集团 2公司 3分公司 */
    private Integer level;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 详细地址 */
    private String address;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 子节点列表 */
    private List<CompanyTreeVO> children = new ArrayList<>();
}
