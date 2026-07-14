package com.jushan.platform.modules.company.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 公司更新命令。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class CompanyUpdateCmd {

    /** 上级公司ID（0表示顶级集团） */
    @NotNull(message = "上级公司ID不能为空")
    private Long parentId;

    /** 公司名称 */
    @NotBlank(message = "公司名称不能为空")
    private String name;

    /** 公司编码 */
    private String code;

    /** 级别：1集团 2公司 3分公司 */
    @NotNull(message = "公司级别不能为空")
    private Integer level;

    /** 联系人 */
    private String contactName;

    /** 联系电话 */
    private String contactPhone;

    /** 详细地址 */
    private String address;

    /** 排序 */
    private Integer sortOrder = 0;
}
