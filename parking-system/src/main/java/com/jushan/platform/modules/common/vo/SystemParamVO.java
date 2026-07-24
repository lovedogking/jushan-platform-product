package com.jushan.platform.modules.common.vo;

/**
 * 系统参数视图对象（A3）。
 * <p>
 * 返回给前端用于分组展示和编辑。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class SystemParamVO {

    /** 参数键 */
    private String key;

    /** 参数值 */
    private String value;

    /** 参数分组 */
    private String groupName;

    /** 值类型：STRING / INT / BOOLEAN / ENUM */
    private String valueType;

    /** ENUM 类型的可选值，JSON 数组 */
    private String options;

    /** 参数说明 */
    private String description;

    // ===== Getters & Setters =====

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
