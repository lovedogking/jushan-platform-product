package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.ParkingZoneCreateCmd;
import com.jushan.platform.modules.parking.dto.ParkingZoneUpdateCmd;
import com.jushan.platform.modules.parking.service.ParkingZoneService;
import com.jushan.platform.modules.parking.vo.ParkingZoneVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 区域管理控制器。
 * <p>
 * 提供区域的新增、编辑、软删除、详情、列表及状态管理接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/parking-zones")
@Tag(name = "二期计费体系候选(冻结)")
public class ParkingZoneController {

    private final ParkingZoneService parkingZoneService;

    public ParkingZoneController(ParkingZoneService parkingZoneService) {
        this.parkingZoneService = parkingZoneService;
    }

    /**
     * 创建区域。
     */
    @PostMapping
    @RequirePermission("parking:write")
    public R<ParkingZoneVO> create(@Valid @RequestBody ParkingZoneCreateCmd cmd) {
        ParkingZoneVO vo = parkingZoneService.create(cmd);
        log.info("创建区域成功: zoneId={}, name={}", vo.getId(), vo.getName());
        return R.ok(vo);
    }

    /**
     * 更新区域。
     */
    @PutMapping("/{id}")
    @RequirePermission("parking:write")
    public R<ParkingZoneVO> update(@PathVariable Long id, @Valid @RequestBody ParkingZoneUpdateCmd cmd) {
        parkingZoneService.update(id, cmd);
        ParkingZoneVO vo = parkingZoneService.detail(id);
        log.info("更新区域成功: zoneId={}", id);
        return R.ok(vo);
    }

    /**
     * 软删除区域。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("parking:write")
    public R<Void> delete(@PathVariable Long id) {
        parkingZoneService.removeById(id);
        log.info("删除区域成功: zoneId={}", id);
        return R.ok();
    }

    /**
     * 查询区域详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("parking:read")
    public R<ParkingZoneVO> detail(@PathVariable Long id) {
        return R.ok(parkingZoneService.detail(id));
    }

    /**
     * 按车场查询区域列表。
     */
    @GetMapping("/by-lot/{lotId}")
    @RequirePermission("parking:read")
    public R<List<ParkingZoneVO>> listByLotId(@PathVariable Long lotId) {
        return R.ok(parkingZoneService.listByLotId(lotId));
    }

    /**
     * 分页查询区域列表。
     */
    @GetMapping
    @RequirePermission("parking:read")
    public R<IPage<ParkingZoneVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Integer status) {
        IPage<ParkingZoneVO> result = parkingZoneService.pageList(current, size, lotId, status);
        return R.ok(result);
    }

    /**
     * 获取区域标签列表。
     */
    @GetMapping("/tags")
    @RequirePermission("parking:read")
    public R<List<Map<String, Object>>> getTags() {
        return R.ok(Arrays.asList(
            Map.of("value", "NORMAL", "label", "普通"),
            Map.of("value", "VIP", "label", "VIP"),
            Map.of("value", "EMPLOYEE", "label", "员工"),
            Map.of("value", "LOADING", "label", "装卸"),
            Map.of("value", "CHARGE", "label", "充电")
        ));
    }

    /**
     * 更新区域状态。
     */
    @PostMapping("/{id}/status")
    @RequirePermission("parking:write")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        parkingZoneService.updateStatus(id, status);
        log.info("更新区域状态成功: zoneId={}, status={}", id, status);
        return R.ok();
    }
}
