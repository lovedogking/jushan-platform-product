package com.jushan.platform.modules.parking.controller;

import com.jushan.common.R;
import com.jushan.platform.infra.log.BusinessLog;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.common.dto.SystemParamUpdateRequest;
import com.jushan.platform.modules.common.service.ParamResolver;
import com.jushan.platform.modules.parking.service.ParkingLotScopeResolver;
import com.jushan.platform.modules.parking.vo.ParkingLotParamVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 车场级参数管理 Controller（任务包 1-1）。
 * <p>
 * 提供运营端车场设置页"车场参数"配置区的读写能力：列举 7 项车场级参数（含继承信息）、
 * 设置车场覆盖值、重置为继承全局/默认值。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>读：{@code parking:read} + {@link ParkingLotScopeResolver#validateReadAccess(Long)}（租户安全）</li>
 *   <li>写：{@code parking:write} + {@link ParkingLotScopeResolver#validateParamWriteAccess(Long)}
 *       （仅超级管理员与租户管理员，且限本租户车场）</li>
 *   <li>写操作经 {@link BusinessLog} 记录操作日志</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
@RestController
@RequestMapping("/api/admin/parking-lots/{lotId}/params")
public class ParkingLotParamController {

    private static final Logger log = LoggerFactory.getLogger(ParkingLotParamController.class);

    private final ParamResolver paramResolver;
    private final ParkingLotScopeResolver scopeResolver;

    public ParkingLotParamController(ParamResolver paramResolver,
                                     ParkingLotScopeResolver scopeResolver) {
        this.paramResolver = paramResolver;
        this.scopeResolver = scopeResolver;
    }

    /**
     * 列举某车场的 7 项车场级参数（含生效值 / 是否覆盖 / 继承值 / 来源）。
     * <p>
     * GET /api/admin/parking-lots/{lotId}/params
     */
    @GetMapping
    @RequirePermission("parking:read")
    public R<List<ParkingLotParamVO>> list(@PathVariable Long lotId) {
        scopeResolver.validateReadAccess(lotId);
        return R.ok(paramResolver.listLotParams(lotId));
    }

    /**
     * 设置（新增或更新）某车场的参数覆盖值。
     * <p>
     * PUT /api/admin/parking-lots/{lotId}/params/{key}
     *
     * @return 更新后的全部车场级参数列表（便于前端刷新继承标识）
     */
    @PutMapping("/{key}")
    @RequirePermission("parking:write")
    @BusinessLog(value = "设置车场参数", module = "parking_param", operationType = "UPDATE",
            operationObject = "车场参数", objectIdExpression = "#lotId")
    public R<List<ParkingLotParamVO>> update(@PathVariable Long lotId,
                                             @PathVariable String key,
                                             @RequestBody SystemParamUpdateRequest request) {
        scopeResolver.validateParamWriteAccess(lotId);
        paramResolver.setLotParam(lotId, key, request != null ? request.getValue() : null);
        log.info("车场参数已更新: lotId={} key={} value={}", lotId, key, request != null ? request.getValue() : null);
        return R.ok(paramResolver.listLotParams(lotId));
    }

    /**
     * 重置某车场的参数为"继承全局/默认值"（删除车场覆盖）。
     * <p>
     * DELETE /api/admin/parking-lots/{lotId}/params/{key}
     *
     * @return 重置后的全部车场级参数列表
     */
    @DeleteMapping("/{key}")
    @RequirePermission("parking:write")
    @BusinessLog(value = "重置车场参数为继承", module = "parking_param", operationType = "UPDATE",
            operationObject = "车场参数", objectIdExpression = "#lotId")
    public R<List<ParkingLotParamVO>> reset(@PathVariable Long lotId,
                                            @PathVariable String key) {
        scopeResolver.validateParamWriteAccess(lotId);
        paramResolver.resetLotParam(lotId, key);
        log.info("车场参数已重置为继承: lotId={} key={}", lotId, key);
        return R.ok(paramResolver.listLotParams(lotId));
    }
}
