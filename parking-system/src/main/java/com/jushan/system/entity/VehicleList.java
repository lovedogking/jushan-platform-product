package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("vehicle_list")
public class VehicleList extends BaseEntity {

    private static final long serialVersionUID = 1L;

    private String plateNumber;
    private String listType;
    private Long parkingLotId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String triggerType;
    private String status;
    private String remark;

    // ==================== 常量 ====================
    public static final String TYPE_BLACK = "BLACK";
    public static final String TYPE_WHITE = "WHITE";
    public static final String TRIGGER_ARREARS = "ARREARS";
    public static final String TRIGGER_MANAGEMENT = "MANAGEMENT";
    public static final String TRIGGER_OTHER = "OTHER";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_DISABLED = "DISABLED";
}
