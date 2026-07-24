package com.jushan.platform.modules.company.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公司视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class CompanyVO {

    /** 主键ID */
    private Long id;

    /** 租户ID */
    private Long tenantId;

    /** 上级公司ID */
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

    /** 状态：1-正常, 2-暂停, 3-注销 */
    private Integer status;

    /** 树路径编码，如 /1/12/123/ */
    private String path;

    /** 详细地址 */
    private String address;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 子节点列表（树形结构） */
    private List<CompanyVO> children;
}
