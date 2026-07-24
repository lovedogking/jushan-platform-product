package com.jushan.platform.modules.parking.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWallet;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWalletLog;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletLogMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletMapper;
import com.jushan.platform.modules.device.entity.MonitorAlert;
import com.jushan.platform.modules.parking.entity.ParkingOrder;
import com.jushan.platform.modules.parking.entity.ParkingRecord;
import com.jushan.platform.modules.device.mapper.MonitorAlertMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 储值车余额自动扣费服务（Sprint 8）。
 * <p>
 * 出场时根据车辆类型自动从储值车钱包扣费。
 * 使用数据库乐观锁（version 字段）防止并发超扣。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>金额使用 BigDecimal，禁止浮点数</li>
 *   <li>扣费使用 CAS 更新（WHERE version = 旧值）</li>
 *   <li>流水记录完整：变动类型、变动金额、变动前后余额、关联订单ID</li>
 *   <li>余额不足时自动创建 MonitorAlert 告警</li>
 *   <li>tenantId 从可信停车记录推导</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Service
public class PrepaidDeductionService {

    private static final Logger log = LoggerFactory.getLogger(PrepaidDeductionService.class);

    private final SysVehicleMapper vehicleMapper;
    private final SysVehicleWalletMapper walletMapper;
    private final SysVehicleWalletLogMapper walletLogMapper;
    private final MonitorAlertMapper alertMapper;

    public PrepaidDeductionService(SysVehicleMapper vehicleMapper,
                                   SysVehicleWalletMapper walletMapper,
                                   SysVehicleWalletLogMapper walletLogMapper,
                                   MonitorAlertMapper alertMapper) {
        this.vehicleMapper = vehicleMapper;
        this.walletMapper = walletMapper;
        this.walletLogMapper = walletLogMapper;
        this.alertMapper = alertMapper;
    }

    /**
     * 储值车余额扣费结果。
     */
    public static class DeductionResult {
        /** 是否适用（非储值车时返回 false，跳过扣费） */
        private final boolean applicable;
        /** 实际扣除金额（分） */
        private final int deductedCents;
        /** 剩余未付金额（分） */
        private final int remainingCents;
        /** 扣费后余额是否充足 */
        private final boolean fullyCovered;
        /** 钱包流水 ID */
        private final Long walletLogId;

        private DeductionResult(boolean applicable, int deductedCents, int remainingCents,
                                boolean fullyCovered, Long walletLogId) {
            this.applicable = applicable;
            this.deductedCents = deductedCents;
            this.remainingCents = remainingCents;
            this.fullyCovered = fullyCovered;
            this.walletLogId = walletLogId;
        }

        public static DeductionResult skipped() {
            return new DeductionResult(false, 0, 0, false, null);
        }

        public static DeductionResult noBalance() {
            return new DeductionResult(true, 0, 0, false, null);
        }

        public static DeductionResult fullCoverage(int deductedCents, Long walletLogId) {
            return new DeductionResult(true, deductedCents, 0, true, walletLogId);
        }

        public static DeductionResult partialCoverage(int deductedCents, int remainingCents, Long walletLogId) {
            return new DeductionResult(true, deductedCents, remainingCents, false, walletLogId);
        }

        public boolean isApplicable() { return applicable; }
        public int getDeductedCents() { return deductedCents; }
        public int getRemainingCents() { return remainingCents; }
        public boolean isFullyCovered() { return fullyCovered; }
        public Long getWalletLogId() { return walletLogId; }
    }

    /**
     * 尝试从储值车余额扣费。
     * <p>
     * 在出场计费完成后、订单状态决策前调用。
     * 非储值车或余额为零时直接返回，不改变订单状态。
     * <p>
     * 调用方需在同一事务内：
     * <ol>
     *   <li>根据返回结果更新订单状态（PAID / PENDING_PAY）</li>
     *   <li>根据返回结果更新出口记录的 paidCents</li>
     * </ol>
     *
     * @param record   停车记录（可信来源，包含 tenantId、parkingLotId、plate）
     * @param feeCents 已计算费用（分）
     * @param order    已创建的订单（用于关联钱包流水）
     * @return 扣费结果
     */
    @Transactional(rollbackFor = Exception.class)
    public DeductionResult tryDeduct(ParkingRecord record, int feeCents, ParkingOrder order) {
        Long tenantId = record.getTenantId();
        String plate = record.getStandardizedPlate();

        // 1. 根据车牌号 + 租户查询车辆
        SysVehicle vehicle = vehicleMapper.selectByPlateNumber(plate, tenantId);
        if (vehicle == null) {
            log.debug("未找到车辆绑定信息，跳过储值扣费: plate={} tenantId={}", plate, tenantId);
            return DeductionResult.skipped();
        }

        // 2. 仅储值车触发自动扣费
        if (!SysVehicle.TYPE_PREPAID.equals(vehicle.getVehicleType())) {
            log.debug("车辆非储值车，跳过自动扣费: plate={} vehicleType={}", plate, vehicle.getVehicleType());
            return DeductionResult.skipped();
        }

        // 3. 零费订单跳过扣费
        if (feeCents <= 0) {
            log.debug("零费订单，跳过储值扣费: plate={}", plate);
            return DeductionResult.skipped();
        }

        log.info("储值车出场，开始余额扣费: plate={} vehicleId={} feeCents={}",
                plate, vehicle.getId(), feeCents);

        // 4. 查询钱包
        SysVehicleWallet wallet = walletMapper.selectByVehicleId(vehicle.getId(), tenantId);
        if (wallet == null) {
            log.warn("储值车钱包不存在: vehicleId={} plate={}", vehicle.getId(), plate);
            createBalanceInsufficientAlert(record, vehicle, feeCents, "钱包不存在");
            return DeductionResult.noBalance();
        }

        BigDecimal balance = wallet.getBalance();
        if (balance.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("储值车余额为零: vehicleId={} plate={}", vehicle.getId(), plate);
            createBalanceInsufficientAlert(record, vehicle, feeCents, "余额为零");
            return DeductionResult.noBalance();
        }

        // 5. 计算扣除金额：分转元
        BigDecimal feeYuan = BigDecimal.valueOf(feeCents)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        if (balance.compareTo(feeYuan) >= 0) {
            // 余额充足：全额扣除
            return applyFullDeduction(wallet, vehicle, balance, feeYuan, order, record);
        } else {
            // 余额不足：扣除全部余额
            return applyPartialDeduction(wallet, vehicle, balance, feeYuan, feeCents, order, record);
        }
    }

    /**
     * 全额扣费。
     */
    private DeductionResult applyFullDeduction(SysVehicleWallet wallet, SysVehicle vehicle,
                                                BigDecimal balanceBefore, BigDecimal feeYuan,
                                                ParkingOrder order, ParkingRecord record) {
        BigDecimal balanceAfter = balanceBefore.subtract(feeYuan);
        BigDecimal totalConsume = wallet.getTotalConsume().add(feeYuan);

        int affected = walletMapper.update(null,
                new LambdaUpdateWrapper<SysVehicleWallet>()
                        .eq(SysVehicleWallet::getId, wallet.getId())
                        .eq(SysVehicleWallet::getVersion, wallet.getVersion())
                        .set(SysVehicleWallet::getBalance, balanceAfter)
                        .set(SysVehicleWallet::getTotalConsume, totalConsume)
                        .set(SysVehicleWallet::getVersion, wallet.getVersion() + 1)
                        .set(SysVehicleWallet::getUpdatedAt, LocalDateTime.now()));

        if (affected == 0) {
            log.error("储值车扣费乐观锁冲突（全额）: walletId={} version={}", wallet.getId(), wallet.getVersion());
            throw new BusinessException(CommonErrorCode.CONFLICT, "扣费失败，请重试（并发冲突）");
        }

        Long walletLogId = createConsumeLog(wallet.getId(), vehicle.getId(),
                record.getTenantId(), feeYuan, balanceBefore, balanceAfter, order.getId());

        log.info("储值车全额扣费成功: vehicleId={} plate={} feeYuan={} balanceAfter={} walletLogId={}",
                vehicle.getId(), vehicle.getPlateNumber(), feeYuan, balanceAfter, walletLogId);

        int feeCents = feeYuan.movePointRight(2).intValueExact();
        return DeductionResult.fullCoverage(feeCents, walletLogId);
    }

    /**
     * 部分扣费（余额不足时扣除全部余额）。
     */
    private DeductionResult applyPartialDeduction(SysVehicleWallet wallet, SysVehicle vehicle,
                                                   BigDecimal balanceBefore, BigDecimal feeYuan,
                                                   int feeCents, ParkingOrder order, ParkingRecord record) {
        // 扣除全部余额
        BigDecimal deductedYuan = balanceBefore;
        BigDecimal balanceAfter = BigDecimal.ZERO;
        BigDecimal totalConsume = wallet.getTotalConsume().add(deductedYuan);

        int affected = walletMapper.update(null,
                new LambdaUpdateWrapper<SysVehicleWallet>()
                        .eq(SysVehicleWallet::getId, wallet.getId())
                        .eq(SysVehicleWallet::getVersion, wallet.getVersion())
                        .set(SysVehicleWallet::getBalance, balanceAfter)
                        .set(SysVehicleWallet::getTotalConsume, totalConsume)
                        .set(SysVehicleWallet::getVersion, wallet.getVersion() + 1)
                        .set(SysVehicleWallet::getUpdatedAt, LocalDateTime.now()));

        if (affected == 0) {
            log.error("储值车扣费乐观锁冲突（部分）: walletId={} version={}", wallet.getId(), wallet.getVersion());
            throw new BusinessException(CommonErrorCode.CONFLICT, "扣费失败，请重试（并发冲突）");
        }

        Long walletLogId = createConsumeLog(wallet.getId(), vehicle.getId(),
                record.getTenantId(), deductedYuan, balanceBefore, balanceAfter, order.getId());

        // 计算剩余未付
        int deductedCents = deductedYuan.movePointRight(2).intValueExact();
        int remainingCents = feeCents - deductedCents;

        // 创建余额不足告警
        createBalanceInsufficientAlert(record, vehicle, remainingCents,
                String.format("余额不足：应收 %.2f 元，余额 %.2f 元，已扣 %.2f 元，剩余 %d 分",
                        feeYuan, balanceBefore, deductedYuan, remainingCents));

        log.info("储值车部分扣费成功（余额不足）: vehicleId={} plate={} deductedYuan={} remainingCents={} walletLogId={}",
                vehicle.getId(), vehicle.getPlateNumber(), deductedYuan, remainingCents, walletLogId);

        return DeductionResult.partialCoverage(deductedCents, remainingCents, walletLogId);
    }

    /**
     * 创建消费流水记录。
     */
    private Long createConsumeLog(Long walletId, Long vehicleId, Long tenantId,
                                   BigDecimal amount, BigDecimal balanceBefore,
                                   BigDecimal balanceAfter, Long orderId) {
        SysVehicleWalletLog walletLog = new SysVehicleWalletLog();
        walletLog.setWalletId(walletId);
        walletLog.setVehicleId(vehicleId);
        walletLog.setTenantId(tenantId);
        walletLog.setLogType(SysVehicleWalletLog.TYPE_CONSUME);
        walletLog.setAmount(amount.negate()); // 消费金额为负数
        walletLog.setBalanceBefore(balanceBefore);
        walletLog.setBalanceAfter(balanceAfter);
        walletLog.setOrderId(orderId);
        walletLog.setRemark("出场自动扣费");
        walletLog.setCreatedAt(LocalDateTime.now());
        walletLog.setUpdatedAt(LocalDateTime.now());
        walletLogMapper.insert(walletLog);
        return walletLog.getId();
    }

    /**
     * 创建余额不足告警。
     */
    private void createBalanceInsufficientAlert(ParkingRecord record, SysVehicle vehicle,
                                                 int remainingCents, String detail) {
        try {
            MonitorAlert alert = new MonitorAlert();
            alert.setTenantId(record.getTenantId());
            alert.setParkingLotId(record.getParkingLotId());
            alert.setAlertType(MonitorAlert.TYPE_BALANCE_INSUFFICIENT);
            alert.setSeverity(MonitorAlert.SEVERITY_WARNING);
            alert.setSourceId(vehicle.getPlateNumber());
            alert.setMessage(String.format("储值车余额不足: 车牌 %s, %s",
                    vehicle.getPlateNumber(), detail));
            alert.setAcknowledged(0);
            alert.setCreatedAt(LocalDateTime.now());
            alertMapper.insert(alert);
            log.info("储值车余额不足告警已创建: plate={} parkingLotId={}",
                    vehicle.getPlateNumber(), record.getParkingLotId());
        } catch (Exception e) {
            log.warn("创建余额不足告警失败（不影响主业务）: plate={} error={}",
                    vehicle.getPlateNumber(), e.getMessage());
        }
    }
}
