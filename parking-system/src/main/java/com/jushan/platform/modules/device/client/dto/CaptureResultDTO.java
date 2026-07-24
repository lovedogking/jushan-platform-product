package com.jushan.platform.modules.device.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Device Access v0.7 主动抓拍响应 data。
 * <p>
 * 字段对齐 DA {@link CaptureResultDTO}：{@code success}、{@code imageUrl}、
 * {@code plateImageUrl}、{@code message}。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CaptureResultDTO {

    /** 是否成功 */
    private Boolean success;

    /** 全景抓拍图 URL */
    private String imageUrl;

    /** 车牌特写图 URL */
    private String plateImageUrl;

    /** 结果描述 */
    private String message;

    // ==================== getter / setter ====================

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }

    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }

    public String getPlateImageUrl() { return plateImageUrl; }
    public void setPlateImageUrl(String plateImageUrl) { this.plateImageUrl = plateImageUrl; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    /**
     * 判断抓拍是否执行成功。
     */
    public boolean isSuccessful() {
        return Boolean.TRUE.equals(success);
    }

    @Override
    public String toString() {
        return "CaptureResultDTO{" +
                "success=" + success +
                ", imageUrl='" + imageUrl + '\'' +
                ", plateImageUrl='" + plateImageUrl + '\'' +
                ", message='" + message + '\'' +
                '}';
    }
}
