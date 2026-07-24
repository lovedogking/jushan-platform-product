package com.jushan.platform.modules.parking.vo;

/**
 * 车场级参数视图对象（任务包 1-1）。
 * <p>
 * 用于运营端车场设置页"车场参数"配置区：展示每项参数的生效值、是否覆盖、继承值与来源。
 * 未覆盖（{@code overridden=false}）时前端展示"继承"标识并回显 {@code inheritedValue}。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public class ParkingLotParamVO {

    /** 参数键 */
    private String key;

    /** 参数说明 */
    private String description;

    /** 参数分组 */
    private String groupName;

    /** 值类型：STRING / INT / BOOLEAN / ENUM */
    private String valueType;

    /** ENUM 类型的可选值，JSON 数组 */
    private String options;

    /** 当前车场生效值（覆盖值或继承值） */
    private String value;

    /** 继承值（全局值 → 代码默认值），用于未覆盖时展示 */
    private String inheritedValue;

    /** 是否存在车场级覆盖（true=已覆盖，false=继承） */
    private boolean overridden;

    /** 生效来源：LOT=车场级 / GLOBAL=全局 / DEFAULT=默认值 */
    private String source;

    // ===== Getters & Setters =====

    public String getKey() { return key; }
    public void setKey(String key) { this.key = key; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }

    public String getValueType() { return valueType; }
    public void setValueType(String valueType) { this.valueType = valueType; }

    public String getOptions() { return options; }
    public void setOptions(String options) { this.options = options; }

    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }

    public String getInheritedValue() { return inheritedValue; }
    public void setInheritedValue(String inheritedValue) { this.inheritedValue = inheritedValue; }

    public boolean isOverridden() { return overridden; }
    public void setOverridden(boolean overridden) { this.overridden = overridden; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
}
