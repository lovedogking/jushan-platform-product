package com.jushan.platform.modules.parking.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.parking.dto.FeeRuleCreateCmd;
import com.jushan.platform.modules.parking.dto.FeeRuleUpdateCmd;
import com.jushan.platform.modules.parking.service.FeeRuleService;
import com.jushan.platform.modules.parking.vo.FeeRuleVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 收费规则管理控制器。
 * <p>
 * 提供收费规则的新增、编辑、软删除、详情、分页列表、复制及状态管理接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/fee-rules")
@Tag(name = "计费规则管理")
public class FeeRuleController {

    private final FeeRuleService feeRuleService;

    public FeeRuleController(FeeRuleService feeRuleService) {
        this.feeRuleService = feeRuleService;
    }

    /**
     * 创建收费规则。
     */
    @PostMapping
    @RequirePermission("fee:write")
    public R<FeeRuleVO> create(@Valid @RequestBody FeeRuleCreateCmd cmd) {
        FeeRuleVO vo = feeRuleService.create(cmd);
        log.info("创建收费规则成功: ruleId={}, name={}", vo.getId(), vo.getName());
        return R.ok(vo);
    }

    /**
     * 更新收费规则。
     */
    @PutMapping("/{id}")
    @RequirePermission("fee:write")
    public R<FeeRuleVO> update(@PathVariable Long id, @Valid @RequestBody FeeRuleUpdateCmd cmd) {
        FeeRuleVO vo = feeRuleService.update(id, cmd);
        log.info("更新收费规则成功: ruleId={}", id);
        return R.ok(vo);
    }

    /**
     * 软删除收费规则。
     */
    @DeleteMapping("/{id}")
    @RequirePermission("fee:write")
    public R<Void> delete(@PathVariable Long id) {
        feeRuleService.removeById(id);
        log.info("删除收费规则成功: ruleId={}", id);
        return R.ok();
    }

    /**
     * 查询收费规则详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("fee:read")
    public R<FeeRuleVO> detail(@PathVariable Long id) {
        return R.ok(feeRuleService.detail(id));
    }

    /**
     * 分页查询收费规则列表。
     */
    @GetMapping
    @RequirePermission("fee:read")
    public R<IPage<FeeRuleVO>> page(
            @RequestParam(defaultValue = "1") long current,
            @RequestParam(defaultValue = "10") long size,
            @RequestParam(required = false) Long lotId,
            @RequestParam(required = false) Long zoneId,
            @RequestParam(required = false) Integer billingMode,
            @RequestParam(required = false) Integer status) {
        IPage<FeeRuleVO> result = feeRuleService.pageList(current, size, lotId, zoneId, billingMode, status);
        return R.ok(result);
    }

    /**
     * 复制收费规则。
     */
    @PostMapping("/{id}/copy")
    @RequirePermission("fee:write")
    public R<FeeRuleVO> copy(@PathVariable Long id) {
        FeeRuleVO vo = feeRuleService.copy(id);
        log.info("复制收费规则成功: sourceRuleId={}, newRuleId={}", id, vo.getId());
        return R.ok(vo);
    }

    /**
     * 更新收费规则状态。
     */
    @PostMapping("/{id}/status")
    @RequirePermission("fee:write")
    public R<Void> updateStatus(@PathVariable Long id, @RequestParam Integer status) {
        feeRuleService.updateStatus(id, status);
        log.info("更新收费规则状态成功: ruleId={}, status={}", id, status);
        return R.ok();
    }
}
