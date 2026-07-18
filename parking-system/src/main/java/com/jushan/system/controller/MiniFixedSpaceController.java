package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.MiniFixedSpaceApplyRequest;
import com.jushan.system.dto.MiniFixedSpaceRenewRequest;
import com.jushan.system.service.MiniFixedSpaceService;
import com.jushan.system.vo.FixedSpaceVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 小程序端固定车位管理 Controller。
 * <p>
 * 对标 {@link MiniMonthlyPassController}，新增可用车位查询接口。
 *
 * @author Jushan Platform
 * @since 1.2.0
 */
@RestController
@RequestMapping("/api/v1/mini/fixed-spaces")
public class MiniFixedSpaceController {

    private final MiniFixedSpaceService miniFixedSpaceService;

    public MiniFixedSpaceController(MiniFixedSpaceService miniFixedSpaceService) {
        this.miniFixedSpaceService = miniFixedSpaceService;
    }

    /**
     * 提交固定车位申请。
     */
    @PostMapping
    @RequirePermission("miniapp:view")
    public R<FixedSpaceVO> apply(@Valid @RequestBody MiniFixedSpaceApplyRequest request) {
        return R.ok(miniFixedSpaceService.apply(request));
    }

    /**
     * 我的固定车位列表。
     */
    @GetMapping
    @RequirePermission("miniapp:view")
    public R<List<FixedSpaceVO>> listMyBindings() {
        return R.ok(miniFixedSpaceService.listMyBindings());
    }

    /**
     * 固定车位详情。
     */
    @GetMapping("/{id}")
    @RequirePermission("miniapp:view")
    public R<FixedSpaceVO> detail(@PathVariable Long id) {
        return R.ok(miniFixedSpaceService.detail(id));
    }

    /**
     * 确认支付（审核通过后支付使固定车位生效）。
     */
    @PostMapping("/{id}/pay")
    @RequirePermission("miniapp:view")
    public R<FixedSpaceVO> pay(@PathVariable Long id) {
        return R.ok(miniFixedSpaceService.pay(id));
    }

    /**
     * 续费。
     */
    @PostMapping("/{id}/renew")
    @RequirePermission("miniapp:view")
    public R<FixedSpaceVO> renew(@PathVariable Long id,
                                  @Valid @RequestBody MiniFixedSpaceRenewRequest request) {
        return R.ok(miniFixedSpaceService.renew(id, request));
    }

    /**
     * 查询指定车场中未被占用的固定车位列表。
     */
    @GetMapping("/available")
    @RequirePermission("miniapp:view")
    public R<List<Map<String, Object>>> availableSpaces(@RequestParam Long parkingLotId) {
        return R.ok(miniFixedSpaceService.getAvailableSpaces(parkingLotId));
    }
}
