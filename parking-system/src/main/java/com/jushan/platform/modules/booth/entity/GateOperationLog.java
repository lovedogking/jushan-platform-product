package com.jushan.platform.modules.booth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("gate_operation_log")
public class GateOperationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long tenantId;
    private Long parkingLotId;
    private Long laneId;
    private String laneName;
    private String operationType;
    private String reason;
    private String plateNumber;
    private String direction;
    private Long operatorId;
    private String operatorName;
    private Integer feeCents;
    private String entryImage;
    private String exitImage;
    private String remark;
    private LocalDateTime operationTime;
    private LocalDateTime createdAt;
}
