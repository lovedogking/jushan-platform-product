package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.AccessPolicyCreateCmd;
import com.jushan.platform.modules.parking.entity.AccessPolicy;
import com.jushan.platform.modules.parking.service.AccessPolicyService;
import com.jushan.platform.modules.parking.vo.AccessPolicyVO;
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
import java.util.Map;

/**
 * 车辆进出策略配置控制器。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/access-policies")
public class AccessPolicyController {

    private final AccessPolicyService accessPolicyService;

    public AccessPolicyController(AccessPolicyService accessPolicyService) {
        this.accessPolicyService = accessPolicyService;
    }

    @PostMapping
    @RequirePermission("parking:update")
    public R<AccessPolicyVO> create(@Valid @RequestBody AccessPolicyCreateCmd cmd) {
        AccessPolicyVO vo = accessPolicyService.create(cmd);
        log.info("创建进出策略: policyId={}, lotId={}, type={}, key={}",
                vo.getId(), cmd.getParkingLotId(), cmd.getPolicyType(), cmd.getPolicyKey());
        return R.ok(vo);
    }

    @PutMapping("/{id}")
    @RequirePermission("parking:update")
    public R<AccessPolicyVO> update(@PathVariable Long id, @Valid @RequestBody AccessPolicyCreateCmd cmd) {
        AccessPolicyVO vo = accessPolicyService.update(id, cmd);
        log.info("更新进出策略: policyId={}", id);
        return R.ok(vo);
    }

    @DeleteMapping("/{id}")
    @RequirePermission("parking:delete")
    public R<Void> delete(@PathVariable Long id) {
        accessPolicyService.delete(id);
        log.info("删除进出策略: policyId={}", id);
        return R.ok();
    }

    @GetMapping("/{id}")
    @RequirePermission("parking:view")
    public R<AccessPolicyVO> detail(@PathVariable Long id) {
        return R.ok(accessPolicyService.detail(id));
    }

    @GetMapping
    @RequirePermission("parking:view")
    public R<IPage<AccessPolicyVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long parkingLotId,
            @RequestParam(required = false) String policyType) {
        IPage<AccessPolicyVO> result = accessPolicyService.pageList(
                new Page<AccessPolicy>(current, size), parkingLotId, policyType);
        return R.ok(result);
    }

    @GetMapping("/lot/{parkingLotId}")
    @RequirePermission("parking:view")
    public R<List<AccessPolicyVO>> listByParkingLotId(@PathVariable Long parkingLotId) {
        return R.ok(accessPolicyService.listByParkingLotId(parkingLotId));
    }

    @GetMapping("/lot/{parkingLotId}/type/{policyType}")
    @RequirePermission("parking:view")
    public R<List<AccessPolicyVO>> listByType(@PathVariable Long parkingLotId, @PathVariable String policyType) {
        return R.ok(accessPolicyService.listByType(parkingLotId, policyType));
    }

    @GetMapping("/lot/{parkingLotId}/type/{policyType}/map")
    @RequirePermission("parking:view")
    public R<Map<String, String>> getPolicyMap(@PathVariable Long parkingLotId, @PathVariable String policyType) {
        return R.ok(accessPolicyService.getPolicyMap(parkingLotId, policyType));
    }
}
