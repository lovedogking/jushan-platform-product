package com.jushan.boot.sprint1;

import com.jushan.platform.infra.log.BusinessLog;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 业务日志测试配置。
 * <p>
 * 由于 Sprint 1 产品代码尚未广泛使用 {@link BusinessLog} 注解，
 * 提供测试专用 REST 控制器以验证异步业务日志管道、字段脱敏与失败记录。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TestConfiguration
public class BusinessLogTestConfig {

    @Component
    @RestController
    @RequestMapping("/api/v1/test-business-log")
    public static class TestBusinessLogController {

        /**
         * 成功场景：记录敏感字段。
         */
        @PostMapping
        @BusinessLog(operationType = "CREATE", operationObject = "SensitiveCustomer")
        public Map<String, String> create(@RequestBody Map<String, String> body) {
            return body;
        }

        /**
         * 失败场景：触发异常，验证日志记录失败结果。
         */
        @PostMapping("/fail")
        @BusinessLog(operationType = "CREATE", operationObject = "FailingOperation")
        public Map<String, String> fail(@RequestBody Map<String, String> body) {
            throw new RuntimeException("业务操作失败");
        }
    }
}
