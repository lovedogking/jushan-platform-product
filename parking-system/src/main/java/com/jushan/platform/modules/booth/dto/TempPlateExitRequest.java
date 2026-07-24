package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 岗亭端临时车牌出场匹配请求体（任务包 3-4）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class TempPlateExitRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "临时车牌号不能为空")
    private String tempPlate;

    @NotNull(message = "车场ID不能为空")
    private Long parkingLotId;

    @NotNull(message = "车道ID不能为空")
    private Long laneId;

    // ==================== getter / setter ====================

    public String getTempPlate() { return tempPlate; }
    public void setTempPlate(String tempPlate) { this.tempPlate = tempPlate; }

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }
}
