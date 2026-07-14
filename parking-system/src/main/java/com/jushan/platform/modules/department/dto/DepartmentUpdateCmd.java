package com.jushan.platform.modules.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 部门更新命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class DepartmentUpdateCmd {

    /** 上级部门ID（0表示顶级部门） */
    @NotNull(message = "上级部门ID不能为空")
    private Long parentId;

    /** 部门名称 */
    @NotBlank(message = "部门名称不能为空")
    @Size(max = 50, message = "部门名称最多50个字符")
    private String name;

    /** 部门编码 */
    @Size(max = 30, message = "部门编码最多30个字符")
    private String code;

    /** 所属停车场ID */
    @NotNull(message = "所属停车场不能为空")
    private Long parkingLotId;

    /** 部门级别 */
    private Integer level;

    /** 负责人 */
    @Size(max = 30, message = "负责人姓名最多30个字符")
    private String managerName;

    /** 联系电话 */
    @Size(max = 20, message = "联系电话最多20个字符")
    private String contactPhone;

    /** 排序 */
    private Integer sortOrder;

    /** 备注 */
    @Size(max = 200, message = "备注最多200个字符")
    private String remark;
}
