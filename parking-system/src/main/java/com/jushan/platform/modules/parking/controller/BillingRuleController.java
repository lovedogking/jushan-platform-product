package com.jushan.platform.modules.parking.controller;

import com.jushan.platform.infra.security.RequirePermission;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.modules.parking.dto.CreateBillingRuleRequest;
import com.jushan.platform.modules.parking.dto.SwitchBillingRuleRequest;
import com.jushan.platform.modules.parking.dto.UpdateBillingRuleRequest;
import com.jushan.platform.modules.parking.service.BillingEngine;
import com.jushan.platform.modules.parking.service.BillingRuleService;
import com.jushan.platform.modules.parking.vo.BillingRuleVersionVO;
import com.jushan.platform.modules.parking.vo.BillingRuleVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 收费规则管理控制器（运营端）。
 * <p>
 * T34｜收费规则与版本 CRUD
 * <p>
 * 客户管理员和停车场管理员通过此接口管理本租户内的收费规则。
 * 所有操作从当前登录会话推导租户范围，不信任前端传入的 tenantId。
 * <p>
 * <strong>权限区分</strong>：
 * <ul>
 *   <li>{@code billing:read} — 查看收费规则列表和详情（管理员、财务、运维、岗亭均可用）</li>
 *   <li>{@code billing:write} — 创建/编辑收费规则（仅客户管理员和停车场管理员）</li>
 *   <li>{@code billing:switch} — 切换收费规则（仅客户管理员和停车场管理员，<b>岗亭人员无权</b>）</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/admin/billing-rules")
public class BillingRuleController {

    private static final Logger log = LoggerFactory.getLogger(BillingRuleController.class);

    private final BillingRuleService billingRuleService;
    private final BillingEngine billingEngine;

    public BillingRuleController(BillingRuleService billingRuleService, BillingEngine billingEngine) {
        this.billingRuleService = billingRuleService;
        this.billingEngine = billingEngine;
    }

    // ==================== 规则 CRUD ====================

    /**
     * 分页查询本租户收费规则列表。
     * <p>
     * 权限：billing:read
     *
     * @param page         页码（从 1 开始）
     * @param size         每页大小
     * @param parkingLotId 停车场 ID（可选）
     * @param status       状态筛选（可选：ENABLED / DISABLED）
     */
    @GetMapping
    @RequirePermission("billing:read")
    public R<IPage<BillingRuleVO>> list(@RequestParam(defaultValue = "1") int page,
                                        @RequestParam(defaultValue = "20") int size,
                                        @RequestParam(required = false) Long parkingLotId,
                                        @RequestParam(required = false) String status) {
        IPage<BillingRuleVO> result = billingRuleService.list(page, size, parkingLotId, status);
        return R.ok(result);
    }

    /**
     * 查询收费规则详情（含当前生效版本信息）。
     * <p>
     * 权限：billing:read
     *
     * @param id 规则 ID
     */
    @GetMapping("/{id}")
    @RequirePermission("billing:read")
    public R<BillingRuleVO> detail(@PathVariable Long id) {
        BillingRuleVO vo = billingRuleService.get(id);
        return R.ok(vo);
    }

    /**
     * 创建收费规则。
     * <p>
     * 权限：billing:write（仅客户管理员和停车场管理员）
     * <p>
     * 创建规则时会自动创建第一个版本并设为生效版本。
     *
     * @param request 创建请求
     */
    @PostMapping
    @RequirePermission("billing:write")
    @BusinessLog(value = "创建收费规则", module = "billing_rule", operationType = "CREATE",
            operationObject = "收费规则")
    public R<BillingRuleVO> create(@Valid @RequestBody CreateBillingRuleRequest request) {
        BillingRuleVO vo = billingRuleService.create(request);
        log.info("创建收费规则成功: ruleId={}, name={}, parkingLotId={}",
                vo.getId(), vo.getName(), request.getParkingLotId());
        return R.ok(vo);
    }

    /**
     * 更新收费规则。
     * <p>
     * 权限：billing:write
     * <p>
     * 如果计费配置字段有变更，将创建新版本而非修改已有版本，确保版本历史可追溯。
     *
     * @param id      规则 ID
     * @param request 更新请求
     */
    @PutMapping("/{id}")
    @RequirePermission("billing:write")
    @BusinessLog(value = "修改收费规则", module = "billing_rule", operationType = "UPDATE",
            operationObject = "收费规则", objectIdExpression = "#id")
    public R<BillingRuleVO> update(@PathVariable Long id, @Valid @RequestBody UpdateBillingRuleRequest request) {
        BillingRuleVO vo = billingRuleService.update(id, request);
        log.info("更新收费规则成功: ruleId={}", id);
        return R.ok(vo);
    }

    // ==================== 版本管理 ====================

    /**
     * 查询规则的版本历史。
     * <p>
     * 权限：billing:read
     *
     * @param id 规则 ID
     */
    @GetMapping("/{id}/versions")
    @RequirePermission("billing:read")
    public R<List<BillingRuleVersionVO>> listVersions(@PathVariable Long id) {
        List<BillingRuleVersionVO> versions = billingRuleService.listVersions(id);
        return R.ok(versions);
    }

    // ==================== 规则切换 ====================

    /**
     * 切换停车场当前生效的收费规则。
     * <p>
     * <strong>T34 停止条件</strong>：
     * 仅客户管理员和停车场管理员可执行切换，<b>岗亭人员无权切换</b>。
     * 切换操作必须记录原因。
     * <p>
     * 权限：billing:switch
     *
     * @param parkingLotId 停车场 ID
     * @param request      切换请求（targetRuleId, applyToExisting, reason）
     */
    @PostMapping("/parking-lots/{parkingLotId}/switch")
    @RequirePermission("billing:switch")
    @BusinessLog(value = "切换收费规则", module = "billing_rule", operationType = "UPDATE",
            operationObject = "收费规则切换")
    public R<Void> switchRule(@PathVariable Long parkingLotId,
                               @Valid @RequestBody SwitchBillingRuleRequest request) {
        billingRuleService.switchRule(parkingLotId, request);
        log.info("切换收费规则成功: parkingLotId={}, targetRuleId={}, reason={}",
                parkingLotId, request.getTargetRuleId(), request.getReason());
        return R.ok();
    }

    // ==================== 费用试算 ====================

    /**
     * 费用试算（基于当前生效规则）。
     * <p>
     * 权限：billing:read
     *
     * @param parkingLotId 停车场 ID
     * @param entryTime    入场时间（yyyy-MM-dd HH:mm:ss）
     * @param exitTime     出场时间（yyyy-MM-dd HH:mm:ss）
     */
    @GetMapping("/calculate")
    @RequirePermission("billing:read")
    public R<Map<String, Object>> calculate(@RequestParam Long parkingLotId,
                                             @RequestParam String entryTime,
                                             @RequestParam String exitTime) {
        LocalDateTime entry = LocalDateTime.parse(entryTime.replace(" ", "T"));
        LocalDateTime exit = LocalDateTime.parse(exitTime.replace(" ", "T"));
        int feeCents = billingEngine.calculateFee(parkingLotId, entry, exit);

        Map<String, Object> result = new HashMap<>();
        result.put("feeCents", feeCents);
        result.put("feeYuan", feeCents / 100.0);
        result.put("parkingLotId", parkingLotId);
        return R.ok(result);
    }
}