package com.jushan.platform.modules.vehicle.dto;

import lombok.Data;

@Data
public class VehicleListPageQuery {
    private Integer page = 1;
    private Integer size = 20;
    private Long parkingLotId;
    private String listType;
    private String plateNumber;
}
