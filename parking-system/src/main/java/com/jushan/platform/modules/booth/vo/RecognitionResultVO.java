package com.jushan.platform.modules.booth.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 识别事件处理结果视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class RecognitionResultVO {

    /** 事件ID */
    private Long eventId;

    /** 车牌号 */
    private String plateNumber;

    /** 车辆类型判定结果 */
    private String vehicleType;

    /** 是否允许通行 */
    private Boolean allowPass;

    /** 开闸命令是否已发送到 Device Access */
    private Boolean gateCommandSent;

    /** 设备是否回复开闸成功 */
    private Boolean gateDeviceAck;

    /**
     * 闸杆实际是否抬起。
     * 一期：始终为 null（无法确认第三层状态，gateDeviceAck 不代表闸杆动作）。
     * 二期：接入设备状态反馈后填充。
     */
    private Boolean gateOpened;

    /** 开闸结果说明 */
    private String gateResult;

    /** 应收费用（元） */
    private BigDecimal feeAmount;

    /** 在场记录ID（入场时创建） */
    private Long sessionId;

    /** 处理结果说明 */
    private String resultMessage;

    /** 是否异常 */
    private Boolean exception;

    /** 异常类型 */
    private String exceptionType;

    /**
     * 是否为重复识别幂等忽略。
     * true 时调用方不应持久化/推送岗亭事件流（出场成功后相机持续上报同一车辆的场景）。
     */
    private Boolean duplicateIgnored;
}
