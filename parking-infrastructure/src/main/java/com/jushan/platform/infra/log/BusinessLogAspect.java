package com.jushan.platform.infra.log;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jushan.common.auth.TenantContext;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.regex.Pattern;

/**
 * 业务操作日志 AOP 切面。
 * <p>
 * 拦截 {@link BusinessLog} 注解，收集操作人、IP、操作类型、操作对象、变更值等信息，
 * 并发布 {@link BusinessLogEvent} 由监听器异步持久化。
 * <p>
 * 敏感字段（手机号、密码、身份证、邮箱）在序列化前会进行脱敏处理。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Aspect
@Component
public class BusinessLogAspect {

    private static final Logger log = LoggerFactory.getLogger(BusinessLogAspect.class);

    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    // 手机号脱敏：138****1234
    private static final Pattern PHONE_PATTERN = Pattern.compile("(1[3-9]\\d)\\d{4}(\\d{4})");
    // 身份证脱敏：保留前3后4
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("(\\d{3})\\d{11}(\\d{4})");
    // 邮箱脱敏：a***@example.com
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(^[^@]{1,2})[^@]*(@.*$)");

    public BusinessLogAspect(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.findAndRegisterModules();
    }

    /**
     * 拦截带 {@link BusinessLog} 注解的方法。
     */
    @Around("@annotation(businessLog)")
    public Object around(ProceedingJoinPoint point, BusinessLog businessLog) throws Throwable {
        BusinessLogEvent event = new BusinessLogEvent();
        event.setOperationType(businessLog.operationType());
        event.setOperationObject(businessLog.operationObject());
        event.setTenantId(TenantContext.getTenantId());
        event.setOperatorId(TenantContext.getUserId());
        event.setIp(getClientIp());

        Object result = null;
        Throwable throwable = null;
        try {
            // 序列化方法参数作为变更后值（CREATE/UPDATE 场景）
            Object[] args = point.getArgs();
            if (args != null && args.length > 0) {
                event.setAfterValue(desensitizeJson(args[0]));
            }

            result = point.proceed();
            event.setResult(1);
            return result;
        } catch (Throwable t) {
            throwable = t;
            event.setResult(0);
            event.setErrorMsg(t.getMessage());
            throw t;
        } finally {
            try {
                // 尝试提取对象ID
                event.setObjectId(resolveObjectId(businessLog, point, result));
                eventPublisher.publishEvent(event);
            } catch (Exception ex) {
                // 日志收集失败不能影响主业务
                log.error("发布业务日志事件失败", ex);
            }
        }
    }

    /**
     * 获取客户端真实 IP。
     */
    private String getClientIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return null;
        }
        HttpServletRequest request = attributes.getRequest();
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isBlank() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        // 多级代理时取第一个 IP
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    /**
     * 将对象序列化为 JSON 并脱敏。
     */
    private String desensitizeJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            String json = objectMapper.writeValueAsString(obj);
            return desensitize(json);
        } catch (JsonProcessingException e) {
            log.warn("操作日志序列化失败: {}", obj.getClass().getName(), e);
            return "{\"error\":\"序列化失败\"}";
        }
    }

    /**
     * 对 JSON 字符串中的敏感字段进行脱敏。
     */
    private String desensitize(String json) {
        if (json == null) {
            return null;
        }
        // 密码类字段统一替换
        String result = json.replaceAll("\"(password|passwd|pwd|secret|token|apiKey|apiSecret)\":\"[^\"]*\"",
                "\"$1\":\"***\"");
        // 手机号
        result = PHONE_PATTERN.matcher(result).replaceAll("$1****$2");
        // 身份证
        result = ID_CARD_PATTERN.matcher(result).replaceAll("$1***********$2");
        // 邮箱
        result = EMAIL_PATTERN.matcher(result).replaceAll("$1***$2");
        return result;
    }

    /**
     * 解析对象ID。
     * <p>
     * 优先使用 SpEL 表达式，其次尝试从第一个参数的 id 字段获取。
     */
    private String resolveObjectId(BusinessLog businessLog, ProceedingJoinPoint point, Object result) {
        // TODO: 支持 SpEL 表达式解析 #request.id 等
        // 当前简化实现：尝试从第一个参数获取 id 字段
        Object[] args = point.getArgs();
        if (args == null || args.length == 0) {
            return null;
        }
        Object firstArg = args[0];
        try {
            Method getIdMethod = firstArg.getClass().getMethod("getId");
            Object id = getIdMethod.invoke(firstArg);
            return id != null ? id.toString() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
