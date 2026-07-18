package com.jushan.platform.modules.booth.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 费用减免结果视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeReductionVO {

    private Long sessionId;
    private Integer originalFeeCents;
    private Integer reducedFeeCents;
    private Integer reductionCents;
    private LocalDateTime appliedAt;
}
