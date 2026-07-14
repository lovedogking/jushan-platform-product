package com.jushan.system.mybatis;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.jushan.common.auth.TenantContext;
import org.apache.ibatis.reflection.MetaObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * MyBatis-Plus 字段自动填充处理器。
 * <p>
 * 负责在 INSERT / UPDATE 时自动填充公共字段：
 * <ul>
 *   <li>{@code tenantId}：INSERT 时从 {@link TenantContext} 获取当前租户 ID 填充。
 *       平台用户未指定目标租户时保持 null（如注册流程）。</li>
 *   <li>{@code createdAt}：INSERT 时填充当前时间。</li>
 *   <li>{@code updatedAt}：INSERT / UPDATE 时填充当前时间。</li>
 * </ul>
 * <p>
 * 仅填充实体中已存在且当前值为 null 的字段，避免覆盖调用方显式设置。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@Component
public class TenantMetaObjectHandler implements MetaObjectHandler {

    private static final Logger log = LoggerFactory.getLogger(TenantMetaObjectHandler.class);

    @Override
    public void insertFill(MetaObject metaObject) {
        fillTenantId(metaObject);
        fillIfNull(metaObject, "createdAt", LocalDateTime.now());
        fillIfNull(metaObject, "updatedAt", LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        fillIfNull(metaObject, "updatedAt", LocalDateTime.now());
    }

    /**
     * 填充租户 ID。
     * <p>
     * 优先从当前 {@link TenantContext} 获取；平台用户（未代操作）返回 null，
     * 此时不填充，由业务代码自行决定（如注册流程允许 null）。
     */
    private void fillTenantId(MetaObject metaObject) {
        if (!hasField(metaObject, "tenantId")) {
            return;
        }
        Object currentValue = metaObject.getValue("tenantId");
        if (currentValue != null) {
            return;
        }
        Long tenantId = TenantContext.getTenantId();
        if (tenantId != null) {
            setFieldValByName("tenantId", tenantId, metaObject);
        }
    }

    /**
     * 如果实体存在指定字段且当前值为 null，则填充目标值。
     */
    private void fillIfNull(MetaObject metaObject, String fieldName, Object value) {
        if (!hasField(metaObject, fieldName)) {
            return;
        }
        Object currentValue = getFieldValByName(fieldName, metaObject);
        if (currentValue == null) {
            setFieldValByName(fieldName, value, metaObject);
        }
    }

    /**
     * 判断 MetaObject 中是否存在指定属性（支持驼峰命名）。
     */
    private boolean hasField(MetaObject metaObject, String fieldName) {
        return metaObject.findProperty(fieldName, false) != null;
    }
}
