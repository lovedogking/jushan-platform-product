package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 车位管控策略实体。
 * <p>
 * 支持全场或分区域的车位管控，包含余位计算与满位动作配置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_space_policy")
public class ParkingSpacePolicy extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 停车场ID */
    private Long parkingLotId;

    /** 区域ID（NULL表示全场策略） */
    private Long zoneId;

    /** 总车位数 */
    private Integer totalSpaces;

    /** 固定车位数（月租/储值等） */
    private Integer fixedSpaces;

    /** 临时车位数 */
    private Integer tempSpaces;

    /** 预留车位数 */
    private Integer reservedSpaces;

    /** 余位预警阈值 */
    private Integer warningThreshold;

    /** 满位动作：WARN-仅预警, BLOCK-禁止入场, ALLOW_VIP-仅允许VIP/月租 */
    private String fullAction;

    /** 状态：ACTIVE-生效, DISABLED-已禁用 */
    private String status;

    // ==================== 常量定义 ====================

    public static final String ACTION_WARN = "WARN";
    public static final String ACTION_BLOCK = "BLOCK";
    public static final String ACTION_ALLOW_VIP = "ALLOW_VIP";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
}
