package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 储值车钱包视图对象。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class WalletVO {

    /** 钱包ID */
    private Long id;

    /** 车辆ID */
    private Long vehicleId;

    /** 当前余额（元） */
    private BigDecimal balance;

    /** 累计充值金额（元） */
    private BigDecimal totalRecharge;

    /** 累计消费金额（元） */
    private BigDecimal totalConsume;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
