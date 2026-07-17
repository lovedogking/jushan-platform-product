package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备实体。
 * <p>
 * v0.3: brand/model 迁移到 t_device_product 表，设备通过 productId 关联产品目录。
 * 新增 direction 字段表示设备安装方向（入口/出口/双向）。
 * v0.4: 新增 platformDeviceId / tenantId / parkingLotId / laneId，用于业务侧关联设备维度，
 * 不参与 Device Access 内部逻辑，仅做透传和日志上下文。
 */
@Data
@TableName("t_device")
public class Device {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备唯一标识（通常为设备序列号或 MAC） */
    private String deviceId;

    /** 设备名称（如"东门入口摄像头"） */
    private String deviceName;

    /** 产品ID，FK → t_device_product.id */
    private Long productId;

    /** 设备安装方向：ENTRANCE / EXIT / BIDIRECTIONAL（仅 CAMERA 有意义） */
    private String direction;

    /** 平台设备ID（业务侧关联标识，可选，不参与业务规则） */
    private String platformDeviceId;

    /** 租户ID（业务侧维度，可选，不参与业务规则） */
    private String tenantId;

    /** 停车场ID（业务侧维度，可选，不参与业务规则） */
    private String parkingLotId;

    /** 车道ID（业务侧维度，可选，不参与业务规则） */
    private String laneId;

    /** 显示屏启用: 0-关闭, 1-启用（v0.3 显示配置持久化） */
    private Boolean displayEnabled;

    /** 显示屏模式: TWO_LINE / FOUR_LINE（v0.3 显示配置持久化） */
    private String displayMode;

    /** 设备状态 */
    private String status;

    /** 最后在线时间 */
    private LocalDateTime lastOnlineTime;

    /** 备注 */
    private String remark;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ── 非持久化字段 ──

    /** 关联的产品信息（查询时 JOIN 填充），不持久化 */
    @TableField(exist = false)
    private DeviceProduct product;
}
