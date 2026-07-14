package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.ParkingSpacePolicyCreateCmd;
import com.jushan.platform.modules.parking.entity.ParkingSpacePolicy;
import com.jushan.platform.modules.parking.service.ParkingSpacePolicyService;
import com.jushan.platform.modules.parking.vo.ParkingSpacePolicyVO;
import com.jushan.platform.modules.parking.vo.ParkingSpaceRemainVO;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 车位管控策略控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parking-space-policies")
public class ParkingSpacePolicyController {

    private final ParkingSpacePolicyService parkingSpacePolicyService;

    public ParkingSpacePolicyController(ParkingSpacePolicyService parkingSpacePolicyService) {
        this.parkingSpacePolicyService = parkingSpacePolicyService;
    }

    @PostMapping
    @RequirePermission("parking:update")
    public R<ParkingSpacePolicyVO> create(@Valid @RequestBody ParkingSpacePolicyCreateCmd cmd) {
        ParkingSpacePolicyVO vo = parkingSpacePolicyService.create(cmd);
        log.info("创建车位管控策略: policyId={}, lotId={}, zoneId={}",
                vo.getId(), cmd.getParkingLotId(), cmd.getZoneId());
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("parking:update")
    public R<ParkingSpacePolicyVO> update(@PathVariable Long id, @Valid @RequestBody ParkingSpacePolicyCreateCmd cmd) {
        ParkingSpacePolicyVO vo = parkingSpacePolicyService.update(id, cmd);
        log.info("更新车位管控策略: policyId={}", id);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("parking:delete")
    public R<Void> delete(@PathVariable Long id) {
        parkingSpacePolicyService.delete(id);
        log.info("删除车位管控策略: policyId={}", id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequirePermission("parking:view")
    public R<ParkingSpacePolicyVO> detail(@PathVariable Long id) {
        return R.ok(parkingSpacePolicyService.detail(id));
    }

    @GetMapping
    @RequirePermission("parking:view")
    public R<IPage<ParkingSpacePolicyVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long parkingLotId) {
        IPage<ParkingSpacePolicyVO> result = parkingSpacePolicyService.pageList(
                new Page<ParkingSpacePolicy>(current, size), parkingLotId);
        return R.ok(result);
    }

    @GetMapping("/lot/{parkingLotId}")
    @RequirePermission("parking:view")
    public R<List<ParkingSpacePolicyVO>> listByParkingLotId(@PathVariable Long parkingLotId) {
        return R.ok(parkingSpacePolicyService.listByParkingLotId(parkingLotId));
    }

    @GetMapping("/zone/{zoneId}")
    @RequirePermission("parking:view")
    public R<ParkingSpacePolicyVO> getByZoneId(@PathVariable Long zoneId) {
        return R.ok(parkingSpacePolicyService.getByZoneId(zoneId));
    }

    @GetMapping("/lot/{parkingLotId}/remain")
    @RequirePermission("parking:view")
    public R<ParkingSpaceRemainVO> calculateRemain(@PathVariable Long parkingLotId) {
        return R.ok(parkingSpacePolicyService.calculateRemain(parkingLotId));
    }
}
