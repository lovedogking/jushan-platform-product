package com.smartparking.deviceaccess.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * 主动抓拍结果 DTO。
 * <p>
 * v0.7 新增，支持岗亭端手动触发相机抓拍并返回图片 URL。
 *
 * @since v0.7
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CaptureResultDTO {

    /** 是否成功 */
    private boolean success;

    /** 全景抓拍图 URL */
    private String imageUrl;

    /** 车牌特写图 URL（部分相机支持） */
    private String plateImageUrl;

    /** 失败时的错误描述 */
    private String message;
}
