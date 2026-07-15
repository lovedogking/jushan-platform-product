package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 小程序支付预下单请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class MiniPayPrepareRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 停车记录 ID（ParkingRecord.id） */
    @NotNull(message = "停车记录 ID 不能为空")
    private Long recordId;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
}
