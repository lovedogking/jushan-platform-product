package com.jushan.system.entity;

import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 系统配置（sys_config 表实体）。
 * <p>
 * 存储平台级 key-value 配置项，如系统参数、开关、阈值等。
 * 此表为全局表，无租户隔离，由 TenantLineHandler 白名单放行。
 * <p>
 * A3 扩展了分组（groupName）、值类型（valueType）和枚举选项（options）字段，
 * 支持前端分组展示和类型感知输入。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
@TableName("sys_config")
public class SysConfig implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 参数分组 */
    private String groupName;

    /** 值类型：STRING / INT / BOOLEAN / ENUM */
    private String valueType;

    /** ENUM 类型的可选值，JSON 数组 */
    private String options;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ===== Getters & Setters =====

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
