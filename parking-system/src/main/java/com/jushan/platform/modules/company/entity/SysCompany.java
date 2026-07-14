package com.jushan.platform.modules.company.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 公司/集团档案实体。
 * <p>
 * 支持集团-子公司-分公司三级树形结构，通过 parentId 自关联。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_company")
public class SysCompany extends BaseEntity {

    private static final long serialVersionUID = 1L;

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

    /** 软删除标记（MyBatis-Plus @TableLogic 自动处理） */
    // deleted 字段继承自 BaseEntity
}
