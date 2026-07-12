package com.jushan.boot.controller;

import com.jushan.boot.dto.DemoRequest;
import com.jushan.common.BusinessException;
import com.jushan.common.CommonErrorCode;
import com.jushan.common.R;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

/**
 * 演示控制器。
 * <p>
 * 覆盖四种响应类型：正常、参数校验失败、业务异常、未知异常。
 * 仅供 T05 验收使用，后续真实业务 Controller 在此基础上开发。
 * <p>
 * <strong>P0 安全修复（FIX-16）</strong>：
 * 限定为 dev/test Profile，生产环境不注册。
 */
@Profile({"dev", "test"})
@RestController
@RequestMapping("/demo")
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    /** ① 正常响应 */
    @GetMapping("/ok")
    public R<String> ok() {
        log.info("正常请求演示");
        return R.ok("Hello, Jushan Platform!");
    }

    /** ② 参数校验失败（触发 @Valid 校验） */
    @PostMapping("/param-error")
    public R<String> paramError(@Valid @RequestBody DemoRequest request) {
        // 如果进入此方法说明校验通过
        return R.ok("参数校验通过: " + request.getName());
    }

    /** ③ 业务异常 */
    @GetMapping("/business-error")
    public R<Void> businessError() {
        log.warn("模拟业务异常");
        throw new BusinessException(CommonErrorCode.BUSINESS_ERROR, "演示业务异常");
    }

    /** ④ 未知异常（兜底处理） */
    @GetMapping("/unknown-error")
    public R<Void> unknownError() {
        throw new RuntimeException("模拟未知运行时异常");
    }
}
