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

    /** 是否开闸（预留/mock） */
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
}
