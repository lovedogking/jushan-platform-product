package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 车道实体（对齐 DB parking_lane 表完整字段）。
 * <p>
 * 字段类型约定：
 * <ul>
 *   <li>{@code type} — 1=入口(ENTRY), 2=出口(EXIT), 3=双向(MIXED)</li>
 *   <li>{@code status} — 1=启用(ENABLED), 2=禁用(DISABLED), 3=维护中(MAINTENANCE)</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("parking_lane")
public class ParkingLane extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** DB 使用 AUTO_INCREMENT，覆盖 BaseEntity 的 ASSIGN_ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属车场 ID（逻辑外键：parking_lot.id） */
    private Long lotId;

    /** 所属区域 ID（逻辑外键：parking_zone.id） */
    private Long zoneId;

    /** 通道编号（停车场内唯一），如 A1、17 */
    private String laneNo;

    /** 通道名称，如东大门 */
    private String name;

    /** 通道类型：1-入口, 2-出口, 3-双向 */
    private Integer type;

    /** 入口相机 ID（逻辑外键：device.id） */
    private Long entryCameraId;

    /** 出口相机 ID（逻辑外键：device.id） */
    private Long exitCameraId;

    /** 状态：1-启用, 2-禁用, 3-维护中 */
    private Integer status;

    /** 潮汐模式：0-关闭, 1-早高峰入口, 2-晚高峰出口 */
    private Integer tideMode;

    /** 相机配置模式：1-单相机, 2-双相机, 3-主从相机 */
    private Integer cameraMode;

    /** 闸机模式：AUTO-自动, ALWAYS_OPEN-常开, ALWAYS_CLOSE-常关 */
    private String gateMode;

    /** 唯一控闸设备ID，指向 device.id（GATE 设备或接线控闸的 CAMERA）；NULL=待补录 */
    private Long gateDeviceId;

    /** 乐观锁版本号 */
    private Integer version;

    /** 软删除时间 */
    private LocalDateTime deletedAt;

    // gate_mode 常量
    public static final String GATE_MODE_AUTO = "AUTO";
    public static final String GATE_MODE_ALWAYS_OPEN = "ALWAYS_OPEN";
    public static final String GATE_MODE_ALWAYS_CLOSE = "ALWAYS_CLOSE";
}
