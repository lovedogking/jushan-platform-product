package com.jushan.platform.modules.vehicle.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.vehicle.dto.WalletAdjustCmd;
import com.jushan.platform.modules.vehicle.dto.WalletRechargeCmd;
import com.jushan.platform.modules.vehicle.dto.WalletRefundCmd;
import com.jushan.platform.modules.vehicle.entity.SysVehicleWalletLog;
import com.jushan.platform.modules.vehicle.service.SysVehicleWalletService;
import com.jushan.platform.modules.vehicle.vo.WalletLogVO;
import com.jushan.platform.modules.vehicle.vo.WalletVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 储值车钱包管理控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/vehicle-wallets")
public class SysVehicleWalletController {

    private final SysVehicleWalletService walletService;

    public SysVehicleWalletController(SysVehicleWalletService walletService) {
        this.walletService = walletService;
    }

    /**
     * 查询车辆钱包详情。
     */
    @GetMapping("/vehicle/{vehicleId}")
    @RequirePermission("vehicle:view")
    public R<WalletVO> getByVehicleId(@PathVariable Long vehicleId) {
        WalletVO vo = walletService.getWalletByVehicleId(vehicleId);
        return R.ok(vo);
    }

    /**
     * 充值。
     */
    @PostMapping("/recharge")
    @RequirePermission("vehicle:update")
    public R<WalletVO> recharge(@Valid @RequestBody WalletRechargeCmd cmd) {
        WalletVO vo = walletService.recharge(cmd);
        log.info("储值车充值成功: vehicleId={}, amount={}, balance={}",
                cmd.getVehicleId(), cmd.getAmount(), vo.getBalance());
        return R.ok(vo);
    }

    /**
     * 退款。
     */
    @PostMapping("/refund")
    @RequirePermission("vehicle:update")
    public R<WalletVO> refund(@Valid @RequestBody WalletRefundCmd cmd) {
        WalletVO vo = walletService.refund(cmd);
        log.info("储值车退款成功: vehicleId={}, amount={}, balance={}",
                cmd.getVehicleId(), cmd.getAmount(), vo.getBalance());
        return R.ok(vo);
    }

    /**
     * 调账。
     */
    @PostMapping("/adjust")
    @RequirePermission("vehicle:update")
    public R<WalletVO> adjust(@Valid @RequestBody WalletAdjustCmd cmd) {
        WalletVO vo = walletService.adjust(cmd);
        log.info("储值车调账成功: vehicleId={}, amount={}, balance={}",
                cmd.getVehicleId(), cmd.getAmount(), vo.getBalance());
        return R.ok(vo);
    }

    /**
     * 查询钱包流水列表。
     */
    @GetMapping("/logs")
    @RequirePermission("vehicle:view")
    public R<List<WalletLogVO>> listLogs(
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) String logType) {
        List<WalletLogVO> logs = walletService.listLogs(vehicleId, walletId, logType);
        return R.ok(logs);
    }

    /**
     * 分页查询钱包流水。
     */
    @GetMapping("/logs/page")
    @RequirePermission("vehicle:view")
    public R<IPage<WalletLogVO>> pageLogs(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long vehicleId,
            @RequestParam(required = false) Long walletId,
            @RequestParam(required = false) String logType) {
        IPage<WalletLogVO> result = walletService.pageLogs(
                new Page<SysVehicleWalletLog>(current, size), vehicleId, walletId, logType);
        return R.ok(result);
    }
}
