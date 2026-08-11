package com.jushan.platform.modules.parking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 收费规则版本历史实体。
 * <p>
 * 记录 {@link FeeRule} 每次修改前的完整快照，支持历史版本查看与回退。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("fee_rule_history")
public class FeeRuleHistory extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 当前规则 ID（逻辑外键：fee_rule.id） */
    private Long feeRuleId;

    /** 历史版本号，同一规则下自增 */
    private Integer versionNo;

    /** 规则完整快照 JSON（含时段列表 fee_rule_segment） */
    private String snapshotJson;

    /** 该版本生效开始时间 */
    private LocalDateTime effectiveFrom;

    /** 该版本失效时间（被新版本替换时填充） */
    private LocalDateTime effectiveTo;

    /** 修改人 ID */
    private Long createdBy;
}
