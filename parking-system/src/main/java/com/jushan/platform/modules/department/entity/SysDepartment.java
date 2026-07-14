package com.jushan.platform.modules.department.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 部门/组织架构实体。
 * <p>
 * 支持多级树形结构，通过 parentId 自关联。
 * 部门绑定停车场，删除前需校验无关联车辆。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_department")
public class SysDepartment extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 上级部门ID（0表示顶级部门） */
    private Long parentId;

    /** 部门名称 */
    private String name;

    /** 部门编码 */
    private String code;

    /** 所属停车场ID */
    private Long parkingLotId;

    /** 部门级别 */
    private Integer level;

    /** 负责人 */
    private String managerName;

    /** 联系电话 */
    private String contactPhone;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    private String remark;
}
