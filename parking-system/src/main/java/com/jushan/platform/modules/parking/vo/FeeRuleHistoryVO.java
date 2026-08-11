package com.jushan.platform.modules.parking.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 收费规则版本历史视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class FeeRuleHistoryVO {

    private Long id;
    private Long feeRuleId;
    private Integer versionNo;
    private LocalDateTime effectiveFrom;
    private LocalDateTime effectiveTo;
    private Long createdBy;
    private LocalDateTime createdAt;
}
