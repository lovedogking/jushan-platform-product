package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 外围设备控制结果 DTO。
 * <p>
 * v0.3 引入，替代 DisplaySendResult。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PeripheralControlResult {

    /** 是否成功 */
    private boolean success;

    /** 执行的动作 */
    private String action;

    /** 结果描述 */
    private String message;
}
