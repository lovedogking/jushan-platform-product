package com.jushan.platform.modules.department.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 部门视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class DepartmentVO {

    /** 部门ID */
    private Long id;

    /** 上级部门ID */
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

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
