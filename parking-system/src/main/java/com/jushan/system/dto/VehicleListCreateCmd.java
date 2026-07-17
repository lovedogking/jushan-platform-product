package com.jushan.system.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDate;

@Data
public class VehicleListCreateCmd {
    @NotBlank(message = "车牌号不能为空")
    private String plateNumber;
    @NotBlank(message = "名单类型不能为空")
    private String listType;
    @NotNull(message = "生效车场不能为空")
    private Long parkingLotId;
    private LocalDate startDate;
    private LocalDate endDate;
    private String triggerType;
    private String remark;
}
