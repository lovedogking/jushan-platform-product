package com.jushan.system.vo;

/**
 * 停车场就绪检查单条结果（T22）。
 * <p>
 * 每条检查结果包含检查编码、严重级别、分类、可读消息和是否已由后端实现的标记。
 * 未实现项（如收费规则、支付配置）必须标记 {@code implemented=false}，不得假通过。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public class ReadinessItem {

    /** 检查项编码，如 NO_ENTRY_LANE / ENTRY_CAMERA_MISSING */
    private String code;

    /** 严重级别：BLOCKER（阻塞启用） / WARNING（提示但不阻塞） */
    private String level;

    /** 分类：LANE / DEVICE / CAPACITY / CHARGE_RULE / PAYMENT / DEVICE_STATUS */
    private String category;

    /** 可读提示消息（中文） */
    private String message;

    /** 关联车道名称（非车道级检查时为 null） */
    private String laneName;

    /** 该检查项的后端逻辑是否已实现（false 表示占位） */
    private boolean implemented;

    // ==================== 工厂方法 ====================

    public static ReadinessItem blocker(String code, String category, String message) {
        return new ReadinessItem(code, "BLOCKER", category, message, null, true);
    }

    public static ReadinessItem blocker(String code, String category, String message, String laneName) {
        return new ReadinessItem(code, "BLOCKER", category, message, laneName, true);
    }

    public static ReadinessItem warning(String code, String category, String message) {
        return new ReadinessItem(code, "WARNING", category, message, null, true);
    }

    public static ReadinessItem warning(String code, String category, String message, String laneName) {
        return new ReadinessItem(code, "WARNING", category, message, laneName, true);
    }

    /** 占位检查项（后端未实现，仅提示用户该功能尚未上线） */
    public static ReadinessItem placeholder(String code, String category, String message) {
        return new ReadinessItem(code, "WARNING", category, message, null, false);
    }

    // ==================== 构造器 ====================

    public ReadinessItem() {}

    public ReadinessItem(String code, String level, String category, String message,
                         String laneName, boolean implemented) {
        this.code = code;
        this.level = level;
        this.category = category;
        this.message = message;
        this.laneName = laneName;
        this.implemented = implemented;
    }

    // ==================== getter / setter ====================

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getLaneName() { return laneName; }
    public void setLaneName(String laneName) { this.laneName = laneName; }

    public boolean isImplemented() { return implemented; }
    public void setImplemented(boolean implemented) { this.implemented = implemented; }
}
