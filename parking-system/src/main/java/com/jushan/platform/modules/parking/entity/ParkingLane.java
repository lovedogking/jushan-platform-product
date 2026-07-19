package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 通道管理实体。
 * <p>
 * 支持入口/出口/双向三种类型，双向通道支持单相机/双相机/主从相机三种模式。
 * 潮汐模式仅双向通道可用。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_lane")
public class ParkingLane extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属车场 ID（逻辑外键：parking_lot.id） */
    private Long lotId;

    /** 所属区域 ID（逻辑外键：parking_zone.id） */
    private Long zoneId;

    /** 通道编号，如 A1、17 */
    private String laneNo;

    /** 通道名称，如东大门 */
    private String name;

    /** 通道类型：1入口 2出口 3双向 */
    private Integer type;

    /** 入口相机 ID（逻辑外键：device.id）【预留】 */
    private Long entryCameraId;

    /** 出口相机 ID（逻辑外键：device.id）【预留】 */
    private Long exitCameraId;

    /** 状态：1启用 2禁用 3维护中 */
    private Integer status;

    /** 潮汐模式：0关闭 1早高峰入口 2晚高峰出口 */
    private Integer tideMode;

    /** 相机配置模式：1单相机 2双相机 3主从相机 */
    private Integer cameraMode;

    /** 闸机模式：AUTO-自动, ALWAYS_OPEN-常开, ALWAYS_CLOSE-常关 */
    private String gateMode;

    /** 乐观锁版本号 */
    private Integer version;

    // gate_mode 常量
    public static final String GATE_MODE_AUTO = "AUTO";
    public static final String GATE_MODE_ALWAYS_OPEN = "ALWAYS_OPEN";
    public static final String GATE_MODE_ALWAYS_CLOSE = "ALWAYS_CLOSE";
}
