package com.smartparking.deviceaccess.common.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 设备命令执行日志实体。
 * <p>
 * 记录所有下发给设备的命令请求及响应结果，用于历史追溯和故障排查。
 * 日志表不做逻辑删除（物理保留）。
 */
@Data
@TableName("t_device_command_log")
public class DeviceCommandLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 设备唯一标识 */
    private String deviceId;

    /** 设备品牌 */
    private String brand;

    /** 命令类型: OPEN_GATE / CLOSE_GATE / SYNC_TIME / DISPLAY_TEXT / PERIPHERAL_CONTROL / DISPLAY_SAVE */
    private String commandType;

    /** 关联车牌号（设备识别结果，非业务逻辑） */
    private String plateNo;

    /** 是否成功: 0-失败, 1-成功 */
    private Boolean success;

    /** 设备返回码 */
    private Integer deviceCode;

    /** 执行结果描述 */
    private String message;

    /** 平台设备ID（业务侧关联标识，仅透传/追溯） */
    private String platformDeviceId;

    /** 租户ID（业务侧维度，仅透传/追溯） */
    private String tenantId;

    /** 停车场ID（业务侧维度，仅透传/追溯） */
    private String parkingLotId;

    /** 车道ID（业务侧维度，仅透传/追溯） */
    private String laneId;

    /** 请求时间 */
    private LocalDateTime requestTime;

    /** 响应时间 */
    private LocalDateTime responseTime;

    /** 创建时间 */
    private LocalDateTime createTime;
}
