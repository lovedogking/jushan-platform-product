package com.jushan.platform.modules.booth.dto;

import jakarta.validation.constraints.NotNull;

import java.io.Serializable;

/**
 * 岗亭端临时车牌入场请求体（任务包 3-4）。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
public class TempPlateEntryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull(message = "车场ID不能为空")
    private Long parkingLotId;

    @NotNull(message = "车道ID不能为空")
    private Long laneId;

    /** 可选：岗亭端传入固定车牌号；为空时自动生成 */
    private String tempPlate;

    // ==================== getter / setter ====================

    public Long getParkingLotId() { return parkingLotId; }
    public void setParkingLotId(Long parkingLotId) { this.parkingLotId = parkingLotId; }

    public Long getLaneId() { return laneId; }
    public void setLaneId(Long laneId) { this.laneId = laneId; }

    public String getTempPlate() { return tempPlate; }
    public void setTempPlate(String tempPlate) { this.tempPlate = tempPlate; }
}
