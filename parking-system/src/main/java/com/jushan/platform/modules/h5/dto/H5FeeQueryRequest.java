package com.jushan.platform.modules.h5.dto;

import jakarta.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * H5 查费请求。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class H5FeeQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 车牌号 */
    @NotBlank(message = "车牌号不能为空")
    private String plate;

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
}
