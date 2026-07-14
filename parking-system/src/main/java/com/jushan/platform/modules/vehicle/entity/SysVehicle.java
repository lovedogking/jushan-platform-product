package com.jushan.platform.modules.vehicle.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 车辆主表实体。
 * <p>
 * 支持多种车辆类型：免费车、固定车（月租车）、储值车、贵宾车、超级车牌、黑名单。
 * 车牌统一大写存储，入库前 toUpperCase() 标准化。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("sys_vehicle")
public class SysVehicle extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 车牌号（标准化大写） */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型：FREE-免费车, MONTHLY-月租车, PREPAID-储值车, VIP-贵宾车, SUPER-超级车牌, BLACKLIST-黑名单 */
    private String vehicleType;

    /** 车主姓名 */
    private String ownerName;

    /** 车主电话 */
    private String ownerPhone;

    /** 所属部门ID */
    private Long departmentId;

    /** 所属停车场ID */
    private Long parkingLotId;

    /** 月卡/固定车有效期开始 */
    private LocalDate validStartDate;

    /** 月卡/固定车有效期结束 */
    private LocalDate validEndDate;

    /** 储值车余额（分） */
    private BigDecimal prepaidBalance;

    /** 月卡/固定车收费标准ID */
    private Long feeRuleId;

    /** 备注 */
    private String remark;

    /** 状态：ACTIVE-正常, EXPIRED-已过期, DISABLED-已禁用 */
    private String status;

    // ==================== 常量定义 ====================

    public static final String TYPE_FREE = "FREE";
    public static final String TYPE_MONTHLY = "MONTHLY";
    public static final String TYPE_PREPAID = "PREPAID";
    public static final String TYPE_VIP = "VIP";
    public static final String TYPE_SUPER = "SUPER";
    public static final String TYPE_BLACKLIST = "BLACKLIST";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_DISABLED = "DISABLED";
}
