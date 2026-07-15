package com.jushan.platform.modules.vehicle.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 续费有效期预览视图对象。
 * <p>
 * 用于前端在发起续费前展示“续费前有效期 / 续费后有效期”。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Data
public class RenewalPreviewVO {

    /** 车辆 ID */
    private Long vehicleId;

    /** 车牌号 */
    private String plateNumber;

    /** 车辆类型 */
    private String vehicleType;

    /** 续费前有效期结束日（可能为 null） */
    private LocalDate currentEndDate;

    /** 续费后有效期结束日（基于续费前有效期顺延续费月数） */
    private LocalDate newEndDate;

    /** 续费月数 */
    private Integer renewalMonths;
}
