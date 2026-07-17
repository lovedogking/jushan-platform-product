package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 显示操作结果 DTO。
 * <p>
 * v0.3 引入。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisplayResult {

    /** 是否成功 */
    private boolean success;

    /** 失败时的错误描述 */
    private String errorMessage;
}
