package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

@Data
public class VehicleListDecisionVO {
    private boolean denyEntry;
    private boolean alert;
    private String listType;
    private String triggerType;
    private String reason;
}
