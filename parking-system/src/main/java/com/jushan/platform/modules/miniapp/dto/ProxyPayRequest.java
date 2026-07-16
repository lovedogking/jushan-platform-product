package com.jushan.platform.modules.miniapp.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 代理支付请求（Phase 3 E1）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ProxyPayRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 车牌号（必填） */
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;

    /** 备注（可选） */
    private String remark;

    public String getPlateNumber() { return plateNumber; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public String getRemark() { return remark; }
    public void setRemark(String remark) { this.remark = remark; }
}
