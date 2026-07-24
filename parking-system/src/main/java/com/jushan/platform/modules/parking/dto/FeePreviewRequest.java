package com.jushan.platform.modules.parking.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

/**
 * 停车费用结算预览请求 DTO。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class FeePreviewRequest {

    /** 停车记录 ID */
    @NotNull(message = "停车记录 ID 不能为空")
    private Long recordId;

    /** 预览出场时间，为空时取当前时间 */
    private LocalDateTime previewExitTime;

    // ==================== getter / setter ====================

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }

    public LocalDateTime getPreviewExitTime() { return previewExitTime; }
    public void setPreviewExitTime(LocalDateTime previewExitTime) { this.previewExitTime = previewExitTime; }
}
