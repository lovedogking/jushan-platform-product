package com.jushan.platform.modules.vehicle.service;

import com.jushan.platform.modules.vehicle.dto.VehicleRenewalCmd;
import com.jushan.platform.modules.vehicle.vo.RenewalOrderVO;
import com.jushan.platform.modules.vehicle.vo.RenewalPreviewVO;

/**
 * 月卡/固定车续费服务。
 * <p>
 * 负责从“管理员发起续费 → 生成 MONTH_RENEW 订单 → 支付成功回调/查询确认 → 回写有效期”
 * 的完整闭环。时间计算统一使用 {@link java.time.LocalDate}，正确处理跨月、跨年。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface VehicleRenewalService {

    /**
     * 发起续费：校验车辆类型并创建 MONTH_RENEW 订单（PENDING_PAY）。
     *
     * @param vehicleId 车辆 ID（租户内可信）
     * @param cmd       续费命令（续费月数、支付方式、金额）
     * @return 续费订单视图
     */
    RenewalOrderVO createRenewalOrder(Long vehicleId, VehicleRenewalCmd cmd);

    /**
     * 预览续费后有效期（不落库）。
     *
     * @param vehicleId      车辆 ID
     * @param renewalMonths  续费月数
     * @return 续费前后有效期预览
     */
    RenewalPreviewVO previewRenewal(Long vehicleId, int renewalMonths);

    /**
     * 支付成功生效（支付回调或人工查询确认时调用）。
     * <p>
     * 幂等：同一订单仅生效一次。自动延长 valid_end_date、回写在场车辆类型（原月卡过期场景）、记录审计。
     *
     * @param orderId   续费订单 ID
     * @param paySerial 支付流水号
     * @return 续费订单视图（含续费后有效期）
     */
    RenewalOrderVO applyRenewalEffect(Long orderId, String paySerial);

    /**
     * 按车牌解析并生效续费（供 P云 renewal-notify 等外部回调使用）。
     *
     * @param tenantId     租户 ID
     * @param parkingLotId 停车场 ID
     * @param plateNumber  车牌号
     * @param paySerial    支付流水号
     * @return 续费订单视图；未找到可生效订单时返回 null（调用方应视为成功）
     */
    RenewalOrderVO applyRenewalByPlate(Long tenantId, Long parkingLotId, String plateNumber, String paySerial);
}
