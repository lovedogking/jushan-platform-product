package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 通道权限视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class LanePermissionVO {

    /** 权限ID */
    private Long id;

    /** 通道ID */
    private Long laneId;

    /** 权限目标类型：VEHICLE-车辆, DEPARTMENT-部门 */
    private String targetType;

    /** 权限目标ID */
    private Long targetId;

    /** 允许方向：ENTRY-仅入口, EXIT-仅出口, BOTH-双向 */
    private String direction;

    /** 有效期开始 */
    private LocalDateTime validStart;

    /** 有效期结束 */
    private LocalDateTime validEnd;

    /** 状态 */
    private String status;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
