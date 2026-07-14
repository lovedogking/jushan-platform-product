package com.jushan.platform.modules.vehicle.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.jushan.platform.modules.vehicle.dto.WalletAdjustCmd;
import com.jushan.platform.modules.vehicle.dto.WalletRechargeCmd;
import com.jushan.platform.modules.vehicle.dto.WalletRefundCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWallet;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWalletLog;
import com.jushan.platform.modules.vehicle.vo.WalletLogVO;
import com.jushan.platform.modules.vehicle.vo.WalletVO;

import java.util.List;

/**
 * 储值车钱包服务接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public interface SysVehicleWalletService extends IService<SysVehicleWallet> {

    /**
     * 查询车辆钱包详情。
     *
     * @param vehicleId 车辆ID
     * @return 钱包视图对象
     */
    WalletVO getWalletByVehicleId(Long vehicleId);

    /**
     * 充值。
     *
     * @param cmd 充值命令
     * @return 充值后的钱包视图对象
     */
    WalletVO recharge(WalletRechargeCmd cmd);

    /**
     * 退款。
     *
     * @param cmd 退款命令
     * @return 退款后的钱包视图对象
     */
    WalletVO refund(WalletRefundCmd cmd);

    /**
     * 调账。
     *
     * @param cmd 调账命令
     * @return 调账后的钱包视图对象
     */
    WalletVO adjust(WalletAdjustCmd cmd);

    /**
     * 查询钱包流水列表。
     *
     * @param vehicleId 车辆ID
     * @param walletId  钱包ID
     * @param logType   流水类型（可选）
     * @return 流水列表
     */
    List<WalletLogVO> listLogs(Long vehicleId, Long walletId, String logType);

    /**
     * 分页查询钱包流水。
     *
     * @param page     分页参数
     * @param vehicleId 车辆ID
     * @param walletId 钱包ID
     * @param logType  流水类型（可选）
     * @return 分页结果
     */
    IPage<WalletLogVO> pageLogs(IPage<SysVehicleWalletLog> page, Long vehicleId, Long walletId, String logType);
}
