package com.jushan.system.controller;

import com.jushan.common.R;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.system.dto.SystemParamUpdateRequest;
import com.jushan.system.service.SystemParamService;
import com.jushan.system.vo.SystemParamVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 系统参数管理 Controller（Phase 1 A3）。
 * <p>
 * 提供运营端系统参数的分组查询、详情查看和修改能力。
 * 修改后即时失效 Redis 缓存，无需重启应用。
 * <p>
 * <strong>安全约束</strong>：
 * <ul>
 *   <li>所有接口需要 {@code system:param:manage} 权限</li>
 *   <li>参数修改会自动注册 Spring Cache 缓存失效</li>
 *   <li>不存在的参数 key 返回 404 错误</li>
 * </ul>
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/system-params")
public class SystemParamController {

    private static final Logger log = LoggerFactory.getLogger(SystemParamController.class);

    private final SystemParamService systemParamService;

    public SystemParamController(SystemParamService systemParamService) {
        this.systemParamService = systemParamService;
    }

    /**
     * 查询所有系统参数（按分组排序）。
     * <p>
     * GET /api/v1/system-params
     *
     * @return 所有系统参数列表
     */
    @GetMapping
    @RequirePermission("system:param:manage")
    public R<List<SystemParamVO>> getAllParams() {
        List<SystemParamVO> params = systemParamService.getAllParams();
        return R.ok(params);
    }

    /**
     * 查询所有系统参数（按分组聚合）。
     * <p>
     * GET /api/v1/system-params/grouped
     * <p>
     * 返回 Map，key 为分组名称（groupName），value 为该分组下的参数列表。
     * 前端可直接迭代 Map 渲染分组卡片。
     *
     * @return 按分组聚合的系统参数 Map
     */
    @GetMapping("/grouped")
    @RequirePermission("system:param:manage")
    public R<Map<String, List<SystemParamVO>>> getGroupedParams() {
        List<SystemParamVO> params = systemParamService.getAllParams();
        Map<String, List<SystemParamVO>> grouped = params.stream()
                .collect(Collectors.groupingBy(
                        p -> p.getGroupName() != null ? p.getGroupName() : "default",
                        Collectors.toList()));
        return R.ok(grouped);
    }

    /**
     * 查询单个系统参数详情。
     * <p>
     * GET /api/v1/system-params/{key}
     *
     * @param key 参数键
     * @return 参数详情
     */
    @GetMapping("/{key}")
    @RequirePermission("system:param:manage")
    public R<SystemParamVO> getParam(@PathVariable String key) {
        try {
            SystemParamVO param = systemParamService.getParam(key);
            return R.ok(param);
        } catch (BusinessException e) {
            return R.fail(CommonErrorCode.NOT_FOUND.getCode(), e.getMessage());
        }
    }

    /**
     * 修改系统参数值。
     * <p>
     * PUT /api/v1/system-params/{key}
     * <p>
     * 修改成功后自动失效 Redis 缓存，下次读取重新从数据库加载。
     *
     * @param key     参数键
     * @param request 更新请求体（含新值）
     * @return 更新后的参数详情
     */
    @PutMapping("/{key}")
    @RequirePermission("system:param:manage")
    public R<SystemParamVO> updateParam(@PathVariable String key,
                                         @RequestBody SystemParamUpdateRequest request) {
        if (request.getValue() == null) {
            return R.fail(CommonErrorCode.PARAM_ERROR.getCode(), "参数值不能为空");
        }

        try {
            SystemParamVO param = systemParamService.updateParam(key, request);
            log.info("系统参数已修改: key={} newValue={}", key, request.getValue());
            return R.ok(param);
        } catch (BusinessException e) {
            return R.fail(CommonErrorCode.NOT_FOUND.getCode(), e.getMessage());
        }
    }
}
