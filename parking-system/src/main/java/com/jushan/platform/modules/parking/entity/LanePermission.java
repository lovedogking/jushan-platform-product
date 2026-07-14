package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 通道权限配置实体。
 * <p>
 * 支持按车辆或部门配置通道通行权限，可限制方向（入口/出口/双向）和有效期。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lane_permission")
public class LanePermission extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 通道ID */
    private Long laneId;

    /** 权限目标类型：VEHICLE-车辆, DEPARTMENT-部门 */
    private String targetType;

    /** 权限目标ID（车辆ID或部门ID） */
    private Long targetId;

    /** 允许方向：ENTRY-仅入口, EXIT-仅出口, BOTH-双向 */
    private String direction;

    /** 有效期开始（NULL表示永久） */
    private LocalDateTime validStart;

    /** 有效期结束（NULL表示永久） */
    private LocalDateTime validEnd;

    /** 状态：ACTIVE-生效, DISABLED-已禁用 */
    private String status;

    // ==================== 常量定义 ====================

    public static final String TARGET_VEHICLE = "VEHICLE";
    public static final String TARGET_DEPARTMENT = "DEPARTMENT";

    public static final String DIRECTION_ENTRY = "ENTRY";
    public static final String DIRECTION_EXIT = "EXIT";
    public static final String DIRECTION_BOTH = "BOTH";

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_DISABLED = "DISABLED";
}
