package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 命令执行结果 DTO。
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommandResultDTO {

    /** 是否成功 */
    private Boolean success;

    /** 设备回复的状态码（200 表示成功） */
    private Integer deviceCode;

    /** 描述信息 */
    private String message;
}
