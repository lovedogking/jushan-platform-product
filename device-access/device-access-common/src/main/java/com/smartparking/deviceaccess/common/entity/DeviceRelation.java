package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备关系实体。
 * <p>
 * 记录设备间的业务关联（辅助摄像头、RS485显示屏代理等）。
 * 关系支持启用/停用，不创建反向记录 — 双向查看通过查询实现。
 * v0.3 新增。
 */
@Data
@TableName("t_device_relation")
public class DeviceRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 关系源设备ID */
    private String sourceDeviceId;

    /** 关系目标设备ID */
    private String targetDeviceId;

    /** 关系类型：AUX_CAMERA / RS485_DISPLAY */
    private String relationType;

    /** 是否启用：0-停用, 1-启用 */
    private Boolean enabled;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    // ── 非持久化字段 ──

    /** 关联的目标设备摘要（查询时填充），不持久化 */
    @TableField(exist = false)
    private Device targetDevice;
}
