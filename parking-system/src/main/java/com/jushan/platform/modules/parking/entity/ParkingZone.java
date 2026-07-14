package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 区域管理实体。
 * <p>
 * 一个停车场至少包含 1 个区域。
 * tempSpaces = totalSpaces - fixedSpaces，由业务层维护。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_zone")
public class ParkingZone extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属车场 ID（逻辑外键：parking_lot.id） */
    private Long lotId;

    /** 区域名称 */
    private String name;

    /** 区域标签：NORMAL普通 VIP员工 LOADING装卸 CHARGE充电 支持自定义 */
    private String tag;

    /** 区域等级：1普通 2VIP 3员工 */
    private Integer level;

    /** 收费标准 ID（逻辑外键：fee_rule.id）【预留】 */
    private Long feeRuleId;

    /** 车位总数 */
    private Integer totalSpaces;

    /** 固定车位数 */
    private Integer fixedSpaces;

    /** 临停车位数（= totalSpaces - fixedSpaces） */
    private Integer tempSpaces;

    /** 状态：1启用 2禁用 */
    private Integer status;

    /** 区域负责人 ID（逻辑外键：sys_admin_account.id） */
    private Long managerId;

    /** 备注 */
    private String remark;

    /** 乐观锁版本号 */
    private Integer version;
}
