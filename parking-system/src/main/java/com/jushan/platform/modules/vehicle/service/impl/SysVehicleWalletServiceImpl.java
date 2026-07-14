package com.jushan.platform.modules.vehicle.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.auth.TenantContext;
import com.jushan.platform.modules.vehicle.dto.WalletAdjustCmd;
import com.jushan.platform.modules.vehicle.dto.WalletRechargeCmd;
import com.jushan.platform.modules.vehicle.dto.WalletRefundCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicle;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWallet;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWalletLog;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletLogMapper;
import com.jushan.platform.modules.vehicle.mapper.SysVehicleWalletMapper;
import com.jushan.platform.modules.vehicle.service.SysVehicleWalletService;
import com.jushan.platform.modules.vehicle.vo.WalletLogVO;
import com.jushan.platform.modules.vehicle.vo.WalletVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 储值车钱包服务实现。
 * <p>
 * 使用数据库乐观锁（version 字段）实现并发控制，
 * 充值/退款/调账等资金操作通过 CAS 更新确保余额一致性。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@Service
public class SysVehicleWalletServiceImpl extends ServiceImpl<SysVehicleWalletMapper, SysVehicleWallet>
        implements SysVehicleWalletService {

    private final SysVehicleMapper vehicleMapper;
    private final SysVehicleWalletLogMapper walletLogMapper;

    public SysVehicleWalletServiceImpl(SysVehicleMapper vehicleMapper,
                                       SysVehicleWalletLogMapper walletLogMapper) {
        this.vehicleMapper = vehicleMapper;
        this.walletLogMapper = walletLogMapper;
    }

    @Override
    public WalletVO getWalletByVehicleId(Long vehicleId) {
        Long tenantId = TenantContext.getTenantId();
        SysVehicle vehicle = vehicleMapper.selectById(vehicleId);
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        SysVehicleWallet wallet = baseMapper.selectByVehicleId(vehicleId, tenantId);
        if (wallet == null) {
            // 自动初始化钱包
            wallet = initWallet(vehicleId, tenantId);
        }
        return toWalletVO(wallet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO recharge(WalletRechargeCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();
        String operatorName = ""; // TODO: 从用户服务获取操作人姓名

        SysVehicle vehicle = vehicleMapper.selectById(cmd.getVehicleId());
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        // 校验车辆类型必须是储值车
        if (!SysVehicle.TYPE_PREPAID.equals(vehicle.getVehicleType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅储值车支持充值操作");
        }

        BigDecimal amount = cmd.getAmount();
        // 金额精度校验：保留两位小数
        if (amount.scale() > 2) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "金额最多保留两位小数");
        }

        SysVehicleWallet wallet = baseMapper.selectByVehicleId(cmd.getVehicleId(), tenantId);
        if (wallet == null) {
            wallet = initWallet(cmd.getVehicleId(), tenantId);
        }

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);
        BigDecimal totalRecharge = wallet.getTotalRecharge().add(amount);

        // 乐观锁 CAS 更新
        int affected = baseMapper.update(null,
                new LambdaUpdateWrapper<SysVehicleWallet>()
                        .eq(SysVehicleWallet::getId, wallet.getId())
                        .eq(SysVehicleWallet::getVersion, wallet.getVersion())
                        .set(SysVehicleWallet::getBalance, balanceAfter)
                        .set(SysVehicleWallet::getTotalRecharge, totalRecharge)
                        .set(SysVehicleWallet::getVersion, wallet.getVersion() + 1)
                        .set(SysVehicleWallet::getUpdatedAt, LocalDateTime.now()));

        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "充值失败，请重试");
        }

        // 记录流水
        createLog(wallet.getId(), cmd.getVehicleId(), tenantId,
                SysVehicleWalletLog.TYPE_RECHARGE, amount,
                balanceBefore, balanceAfter, null, operatorId, operatorName, cmd.getRemark());

        // 刷新钱包
        wallet = baseMapper.selectById(wallet.getId());
        log.info("储值车充值成功: vehicleId={}, amount={}, balanceAfter={}",
                cmd.getVehicleId(), amount, balanceAfter);

        return toWalletVO(wallet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO refund(WalletRefundCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();
        String operatorName = ""; // TODO: 从用户服务获取操作人姓名

        SysVehicle vehicle = vehicleMapper.selectById(cmd.getVehicleId());
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        if (!SysVehicle.TYPE_PREPAID.equals(vehicle.getVehicleType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅储值车支持退款操作");
        }

        BigDecimal amount = cmd.getAmount();
        if (amount.scale() > 2) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "金额最多保留两位小数");
        }

        SysVehicleWallet wallet = baseMapper.selectByVehicleId(cmd.getVehicleId(), tenantId);
        if (wallet == null) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "钱包不存在");
        }

        BigDecimal balanceBefore = wallet.getBalance();

        // 校验余额充足
        if (balanceBefore.compareTo(amount) < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "退款金额不能超过当前余额，当前余额: " + balanceBefore + " 元");
        }

        BigDecimal balanceAfter = balanceBefore.subtract(amount);
        BigDecimal totalRecharge = wallet.getTotalRecharge().subtract(amount);

        // 退款金额不能超过累计充值金额
        if (totalRecharge.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "退款金额不能超过累计充值金额");
        }

        int affected = baseMapper.update(null,
                new LambdaUpdateWrapper<SysVehicleWallet>()
                        .eq(SysVehicleWallet::getId, wallet.getId())
                        .eq(SysVehicleWallet::getVersion, wallet.getVersion())
                        .set(SysVehicleWallet::getBalance, balanceAfter)
                        .set(SysVehicleWallet::getTotalRecharge, totalRecharge)
                        .set(SysVehicleWallet::getVersion, wallet.getVersion() + 1)
                        .set(SysVehicleWallet::getUpdatedAt, LocalDateTime.now()));

        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "退款失败，请重试");
        }

        createLog(wallet.getId(), cmd.getVehicleId(), tenantId,
                SysVehicleWalletLog.TYPE_REFUND, amount.negate(),
                balanceBefore, balanceAfter, null, operatorId, operatorName, cmd.getRemark());

        wallet = baseMapper.selectById(wallet.getId());
        log.info("储值车退款成功: vehicleId={}, amount={}, balanceAfter={}",
                cmd.getVehicleId(), amount, balanceAfter);

        return toWalletVO(wallet);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public WalletVO adjust(WalletAdjustCmd cmd) {
        Long tenantId = TenantContext.getTenantId();
        Long operatorId = TenantContext.getUserId();
        String operatorName = ""; // TODO: 从用户服务获取操作人姓名

        SysVehicle vehicle = vehicleMapper.selectById(cmd.getVehicleId());
        if (vehicle == null || !tenantId.equals(vehicle.getTenantId())) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND, "车辆不存在");
        }

        if (!SysVehicle.TYPE_PREPAID.equals(vehicle.getVehicleType())) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "仅储值车支持调账操作");
        }

        BigDecimal amount = cmd.getAmount();
        if (amount.scale() > 2) {
            throw new BusinessException(CommonErrorCode.PARAM_ERROR, "金额最多保留两位小数");
        }

        SysVehicleWallet wallet = baseMapper.selectByVehicleId(cmd.getVehicleId(), tenantId);
        if (wallet == null) {
            wallet = initWallet(cmd.getVehicleId(), tenantId);
        }

        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter = balanceBefore.add(amount);

        // 调账后余额不能为负数
        if (balanceAfter.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException(CommonErrorCode.BUSINESS_ERROR,
                    "调账后余额不能为负数，当前余额: " + balanceBefore + " 元");
        }

        int affected = baseMapper.update(null,
                new LambdaUpdateWrapper<SysVehicleWallet>()
                        .eq(SysVehicleWallet::getId, wallet.getId())
                        .eq(SysVehicleWallet::getVersion, wallet.getVersion())
                        .set(SysVehicleWallet::getBalance, balanceAfter)
                        .set(SysVehicleWallet::getVersion, wallet.getVersion() + 1)
                        .set(SysVehicleWallet::getUpdatedAt, LocalDateTime.now()));

        if (affected == 0) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "调账失败，请重试");
        }

        createLog(wallet.getId(), cmd.getVehicleId(), tenantId,
                SysVehicleWalletLog.TYPE_ADJUST, amount,
                balanceBefore, balanceAfter, null, operatorId, operatorName, cmd.getRemark());

        wallet = baseMapper.selectById(wallet.getId());
        log.info("储值车调账成功: vehicleId={}, amount={}, balanceAfter={}",
                cmd.getVehicleId(), amount, balanceAfter);

        return toWalletVO(wallet);
    }

    @Override
    public List<WalletLogVO> listLogs(Long vehicleId, Long walletId, String logType) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<SysVehicleWalletLog> wrapper = new LambdaQueryWrapper<SysVehicleWalletLog>()
                .eq(SysVehicleWalletLog::getTenantId, tenantId)
                .eq(vehicleId != null, SysVehicleWalletLog::getVehicleId, vehicleId)
                .eq(walletId != null, SysVehicleWalletLog::getWalletId, walletId)
                .eq(logType != null && !logType.isEmpty(), SysVehicleWalletLog::getLogType, logType)
                .orderByDesc(SysVehicleWalletLog::getCreatedAt);

        List<SysVehicleWalletLog> logs = walletLogMapper.selectList(wrapper);
        return logs.stream().map(this::toLogVO).collect(Collectors.toList());
    }

    @Override
    public IPage<WalletLogVO> pageLogs(IPage<SysVehicleWalletLog> page, Long vehicleId, Long walletId, String logType) {
        Long tenantId = TenantContext.getTenantId();

        LambdaQueryWrapper<SysVehicleWalletLog> wrapper = new LambdaQueryWrapper<SysVehicleWalletLog>()
                .eq(SysVehicleWalletLog::getTenantId, tenantId)
                .eq(vehicleId != null, SysVehicleWalletLog::getVehicleId, vehicleId)
                .eq(walletId != null, SysVehicleWalletLog::getWalletId, walletId)
                .eq(logType != null && !logType.isEmpty(), SysVehicleWalletLog::getLogType, logType)
                .orderByDesc(SysVehicleWalletLog::getCreatedAt);

        IPage<SysVehicleWalletLog> entityPage = walletLogMapper.selectPage(page, wrapper);
        return entityPage.convert(this::toLogVO);
    }

    /**
     * 初始化钱包（首次访问时自动创建）。
     */
    private SysVehicleWallet initWallet(Long vehicleId, Long tenantId) {
        SysVehicleWallet wallet = new SysVehicleWallet();
        wallet.setVehicleId(vehicleId);
        wallet.setTenantId(tenantId);
        wallet.setBalance(BigDecimal.ZERO);
        wallet.setTotalRecharge(BigDecimal.ZERO);
        wallet.setTotalConsume(BigDecimal.ZERO);
        wallet.setVersion(0);
        wallet.setCreatedAt(LocalDateTime.now());
        wallet.setUpdatedAt(LocalDateTime.now());
        baseMapper.insert(wallet);
        log.info("初始化储值车钱包: vehicleId={}, walletId={}", vehicleId, wallet.getId());
        return wallet;
    }

    /**
     * 创建流水记录。
     */
    private void createLog(Long walletId, Long vehicleId, Long tenantId,
                           String logType, BigDecimal amount,
                           BigDecimal balanceBefore, BigDecimal balanceAfter,
                           Long orderId, Long operatorId, String operatorName, String remark) {
        SysVehicleWalletLog log = new SysVehicleWalletLog();
        log.setWalletId(walletId);
        log.setVehicleId(vehicleId);
        log.setTenantId(tenantId);
        log.setLogType(logType);
        log.setAmount(amount);
        log.setBalanceBefore(balanceBefore);
        log.setBalanceAfter(balanceAfter);
        log.setOrderId(orderId);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        log.setUpdatedAt(LocalDateTime.now());
        walletLogMapper.insert(log);
    }

    private WalletVO toWalletVO(SysVehicleWallet wallet) {
        WalletVO vo = new WalletVO();
        BeanUtils.copyProperties(wallet, vo);
        return vo;
    }

    private WalletLogVO toLogVO(SysVehicleWalletLog log) {
        WalletLogVO vo = new WalletLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }
}
