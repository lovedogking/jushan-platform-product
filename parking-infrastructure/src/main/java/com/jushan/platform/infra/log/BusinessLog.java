package com.jushan.platform.infra.log;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务操作日志注解。
 * <p>
 * 标记在需要记录操作日志的方法上，AOP 切面会自动捕获操作人、IP、时间、操作对象、
 * 变更前后的值等信息，并异步写入 sys_business_log 表。
 * <p>
 * 敏感字段（手机号、密码、身份证等）会在序列化前自动脱敏。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BusinessLog {

    /**
     * 操作说明。
     */
    String value() default "";

    /**
     * 所属模块。
     */
    String module() default "";

    /**
     * 操作类型：CREATE / UPDATE / DELETE / EXPORT / LOGIN 等。
     */
    String operationType() default "";

    /**
     * 操作对象描述（如：公司、账号、授权码）。
     */
    String operationObject() default "";

    /**
     * 对象ID SpEL 表达式，支持从方法参数中提取。
     * <p>
     * 例如：{@code #request.id}、{@code #id}</p>
     */
    String objectIdExpression() default "";
}
