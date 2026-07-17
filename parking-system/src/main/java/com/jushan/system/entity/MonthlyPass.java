package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.jushan.common.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDate;

/**
 * 月卡独立实体（任务包 3-1）。
 * <p>
 * 替代原有的 {@code sys_vehicle.vehicleType=MONTHLY} 标签化实现，
 * 管理月卡全生命周期：办理、续期、注销、过期。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("monthly_pass")
public class MonthlyPass extends BaseEntity {

    private static final long serialVersionUID = 1L;

    /** 所属车场ID */
    private Long parkingLotId;

    /** 车牌号（标准化大写） */
    private String plateNumber;

    /** 车牌颜色 */
    private String plateColor;

    /** 车辆类型（小型车/大型车等，选填） */
    private String vehicleType;

    /** 有效期起 */
    private LocalDate validStartDate;

    /** 有效期止 */
    private LocalDate validEndDate;

    /** 费用（分） */
    private Integer amountCents;

    /** 实收金额（分） */
    private Integer paidAmountCents;

    /** 缴费方式：CASH / OFFLINE_TRANSFER / SIMULATED_PAY / OTHER */
    private String payMethod;

    /** 月卡状态：ACTIVE-生效中 / EXPIRED-已过期 / CANCELLED-已注销 */
    private String passStatus;

    /** 申请人ID（小程序用户ID；运营端录入为NULL） */
    private Long applicantId;

    /** 来源：ADMIN-运营端 / MINIAPP-小程序端 */
    private String source;

    /** 车主姓名 */
    private String ownerName;

    /** 车主电话 */
    private String ownerPhone;

    /** 备注 */
    private String remark;

    // ==================== 常量 ====================

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_EXPIRED = "EXPIRED";
    public static final String STATUS_CANCELLED = "CANCELLED";

    public static final String PAY_METHOD_CASH = "CASH";
    public static final String PAY_METHOD_OFFLINE_TRANSFER = "OFFLINE_TRANSFER";
    public static final String PAY_METHOD_SIMULATED_PAY = "SIMULATED_PAY";
    public static final String PAY_METHOD_OTHER = "OTHER";

    public static final String SOURCE_ADMIN = "ADMIN";
    public static final String SOURCE_MINIAPP = "MINIAPP";
}
