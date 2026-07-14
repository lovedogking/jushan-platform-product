package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 业务实体公共基类。
 * <p>
 * 所有新建业务表实体建议继承本类，统一包含：
 * <ul>
 *   <li>{@code id}：自增主键</li>
 *   <li>{@code tenantId}：租户 ID（INSERT 时自动填充）</li>
 *   <li>{@code createdAt}：创建时间（INSERT 时自动填充）</li>
 *   <li>{@code updatedAt}：更新时间（INSERT / UPDATE 时自动填充）</li>
 * </ul>
 * <p>
 * <strong>多租户安全</strong>：{@code tenantId} 由 {@link com.jushan.system.mybatis.TenantMetaObjectHandler}
 * 从 {@link com.jushan.platform.infra.auth.TenantContext} 自动填充，禁止从前端直接写入。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public abstract class BaseEntity implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户 ID */
    @TableField(fill = FieldFill.INSERT)
    private Long tenantId;

    /** 创建时间 */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    /** 更新时间 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    // ==================== getter / setter ====================

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTenantId() {
        return tenantId;
    }

    public void setTenantId(Long tenantId) {
        this.tenantId = tenantId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
