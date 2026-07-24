package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class VehicleListVO {
    private Long id;
    private String plateNumber;
    private String listType;
    private String listTypeLabel;
    private Long parkingLotId;
    private String parkingLotName;
    private LocalDate startDate;
    private LocalDate endDate;
    private String triggerType;
    private String triggerTypeLabel;
    private String status;
    private String statusLabel;
    private String remark;
    private LocalDateTime createdAt;
}
