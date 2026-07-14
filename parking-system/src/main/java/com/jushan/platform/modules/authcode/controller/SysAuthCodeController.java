package com.jushan.platform.modules.authcode.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.jushan.common.R;
import com.jushan.platform.infra.security.RequirePermission;
import com.jushan.platform.modules.authcode.dto.BatchGenerateAuthCodeRequest;
import com.jushan.platform.modules.authcode.service.SysAuthCodeService;
import com.jushan.platform.modules.authcode.vo.SysAuthCodeVO;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * 授权码管理控制器。
 *
 * <p>
 * 提供授权码的批量生成、分页查询、激活、禁用与导出接口。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/auth-codes")
public class SysAuthCodeController {

    private static final Logger log = LoggerFactory.getLogger(SysAuthCodeController.class);

    private final SysAuthCodeService authCodeService;

    public SysAuthCodeController(SysAuthCodeService authCodeService) {
        this.authCodeService = authCodeService;
    }

    /**
     * 批量生成授权码。
     *
     * <p>权限：仅一级管理员 / 平台管理员可访问。
     * 本接口要求已登录，具体权限由 Service 层通过 {@link TenantContext} 校验。</p>
     *
     * @param request 批量生成请求
     * @return 生成的授权码列表
     */
    @PostMapping("/batch")
    @RequirePermission
    public R<List<SysAuthCodeVO>> batchGenerate(
            @Valid @RequestBody BatchGenerateAuthCodeRequest request) {
        List<SysAuthCodeVO> result = authCodeService.batchGenerate(request);
        log.info("批量生成授权码接口调用成功: count={}", result.size());
        return R.ok(result);
    }

    /**
     * 分页查询授权码列表。
     *
     * <p>权限：一级管理员看全部，二级管理员看本租户已激活的码。
     * 本接口要求已登录，具体数据范围由 Service 层通过 {@link TenantContext} 控制。</p>
     *
     * @param page   页码（从 1 开始，默认 1）
     * @param size   每页大小（默认 20）
     * @param status 状态筛选（可选：0未使用, 1已激活, 2已过期, 3已禁用）
     * @param code   授权码模糊查询（可选）
     * @return 分页结果
     */
    @GetMapping
    @RequirePermission
    public R<IPage<SysAuthCodeVO>> list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String code) {
        IPage<SysAuthCodeVO> result = authCodeService.list(page, size, status, code);
        return R.ok(result);
    }

    /**
     * 激活授权码。
     *
     * <p>权限：仅二级管理员 / 租户管理员可访问。
     * 本接口要求已登录，具体权限由 Service 层通过 {@link TenantContext} 校验。</p>
     *
     * @param code 授权码
     * @return 激活后的授权码信息
     */
    @PostMapping("/{code}/activate")
    @RequirePermission
    public R<SysAuthCodeVO> activate(@PathVariable String code) {
        SysAuthCodeVO result = authCodeService.activate(code);
        log.info("激活授权码接口调用成功: code={}", code);
        return R.ok(result);
    }

    /**
     * 禁用授权码。
     *
     * <p>权限：仅一级管理员 / 平台管理员可访问。
     * 本接口要求已登录，具体权限由 Service 层通过 {@link TenantContext} 校验。</p>
     *
     * @param id 授权码 ID
     * @return 空结果
     */
    @PutMapping("/{id}/disable")
    @RequirePermission
    public R<Void> disable(@PathVariable Long id) {
        authCodeService.disable(id);
        log.info("禁用授权码接口调用成功: id={}", id);
        return R.ok();
    }

    /**
     * 导出授权码列表。
     *
     * <p>权限：一级管理员看全部，二级管理员看本租户已激活的码。
     * 本接口要求已登录，具体数据范围由 Service 层通过 {@link TenantContext} 控制。</p>
     *
     * <p>简易实现：直接返回 List，由调用方自行处理为 CSV / Excel。</p>
     *
     * @param status 状态筛选（可选）
     * @param code   授权码模糊查询（可选）
     * @return 授权码列表
     */
    @GetMapping("/export")
    @RequirePermission
    public R<List<SysAuthCodeVO>> export(
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String code) {
        List<SysAuthCodeVO> result = authCodeService.export(status, code);
        return R.ok(result);
    }
}
