package com.jushan.system.constant;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 系统/车场参数键常量与车场级参数目录（任务包 1-1）。
 * <p>
 * <strong>禁止魔法字符串</strong>：所有参数键必须引用本类常量，业务代码不得直接书写字符串键。
 * <p>
 * 参数分两层（V1.1 6.1 数据字典：参数层级 1=全局 / 2=车场级）：
 * <ul>
 *   <li>全局层（{@link #LEVEL_GLOBAL}）：{@code parking_lot_id = }{@link #GLOBAL_LOT_ID}，
 *       由 {@code SystemParamController} 维护，全平台单值兜底。</li>
 *   <li>车场层（{@link #LEVEL_LOT}）：{@code parking_lot_id = 具体车场ID}，
 *       由车场设置页维护，按车场覆盖全局值。</li>
 * </ul>
 * 读取优先级：车场级 → 全局 → 代码默认值（{@link Definition#defaultValue()}），
 * 由 {@code ParamResolver} 统一解析。
 *
 * @author Jushan Platform
 * @since 1.1.0
 */
public final class ParamKeys {

    private ParamKeys() {}

    // ==================== 层级/存储哨兵 ====================

    /** 全局行的 {@code parking_lot_id} 哨兵值。使用 0 而非 NULL，以便唯一键 (config_key, parking_lot_id) 正确约束全局唯一性。 */
    public static final long GLOBAL_LOT_ID = 0L;

    /** 参数层级：全局。 */
    public static final int LEVEL_GLOBAL = 1;

    /** 参数层级：车场级。 */
    public static final int LEVEL_LOT = 2;

    // ==================== 值类型 ====================

    public static final String TYPE_STRING = "STRING";
    public static final String TYPE_INT = "INT";
    public static final String TYPE_BOOLEAN = "BOOLEAN";
    public static final String TYPE_ENUM = "ENUM";

    // ==================== 车场级参数键（7 项） ====================

    /** 模拟支付超时（分钟），默认 15。 */
    public static final String MOCK_PAYMENT_TIMEOUT_MINUTES = "mock_payment.timeout_minutes";

    /** 未支付出场策略：BLOCK=拦截 / ALLOW_ARREARS=允许欠费放行，默认 BLOCK。 */
    public static final String EXIT_UNPAID_STRATEGY = "exit.unpaid_strategy";

    /** 欠费车辆再次出场策略：MUST_PAY=必须补缴 / REMIND_ONLY=仅提醒，默认 MUST_PAY。 */
    public static final String ARREARS_REEXIT_STRATEGY = "arrears.reexit_strategy";

    /** 识别失败处理策略：MANUAL=人工处理 / AUTO_RELEASE=自动放行，默认 MANUAL。 */
    public static final String RECOGNITION_FAIL_STRATEGY = "recognition.fail_strategy";

    /** 月卡到期提醒天数，默认 7。 */
    public static final String MONTHLY_PASS_EXPIRY_REMINDER_DAYS = "monthly_pass.expiry_reminder_days";

    /** 月卡/固定车位是否计入余位，默认 false。 */
    public static final String MONTHLY_PASS_COUNT_IN_AVAILABLE_SPACE = "monthly_pass.count_in_available_space";

    /** 支付后出场窗口期（分钟），默认 15。 */
    public static final String PAY_EXIT_WINDOW_MINUTES = "pay.exit_window_minutes";

    /** 固定车位审核模式：AUTO=自动通过, MANUAL=需审核。 */
    public static final String FIXED_SPACE_REVIEW_MODE = "fixed_space.review_mode";

    /** 月卡单价（分/月），默认 30000（即 300 元/月）。 */
    public static final String MONTHLY_PASS_PRICE_PER_MONTH_CENTS = "monthly_pass.price_per_month_cents";

    /** 月卡审核模式：AUTO=自动通过, MANUAL=需审核。 */
    public static final String MONTHLY_FIXED_REVIEW_MODE = "monthly_fixed.review_mode";

    // ==================== 枚举选项常量 ====================

    public static final String EXIT_UNPAID_BLOCK = "BLOCK";
    public static final String EXIT_UNPAID_ALLOW_ARREARS = "ALLOW_ARREARS";

    public static final String ARREARS_MUST_PAY = "MUST_PAY";
    public static final String ARREARS_REMIND_ONLY = "REMIND_ONLY";

    public static final String RECOGNITION_MANUAL = "MANUAL";
    public static final String RECOGNITION_AUTO_RELEASE = "AUTO_RELEASE";

    // ==================== 车场级参数目录 ====================

    /**
     * 单个车场级参数的元数据定义。
     *
     * @param key          参数键
     * @param groupName    分组（用于运营端展示）
     * @param valueType    值类型（STRING/INT/BOOLEAN/ENUM）
     * @param options      ENUM 可选值 JSON 数组；非枚举为 {@code null}
     * @param defaultValue 代码内置默认值（车场级与全局均缺省时的兜底）
     * @param description  参数说明
     */
    public record Definition(
            String key,
            String groupName,
            String valueType,
            String options,
            String defaultValue,
            String description) {
    }

    /**
     * 7 项车场级参数目录（有序，前端按此顺序渲染）。
     * <p>
     * 这是车场级参数的<b>唯一权威定义源</b>：默认值、值类型、可选项、分组、说明均以此为准，
     * Flyway 注入全局行、{@code ParamResolver} 解析默认值、运营端渲染均引用本目录。
     */
    public static final List<Definition> LOT_PARAMS = List.of(
            new Definition(MOCK_PAYMENT_TIMEOUT_MINUTES, "岗亭设置", TYPE_INT, null,
                    "15", "模拟支付超时（分钟）"),
            new Definition(PAY_EXIT_WINDOW_MINUTES, "出场设置", TYPE_INT, null,
                    "15", "支付后出场窗口期（分钟）"),
            new Definition(EXIT_UNPAID_STRATEGY, "出场设置", TYPE_ENUM,
                    "[\"BLOCK\",\"ALLOW_ARREARS\"]", EXIT_UNPAID_BLOCK,
                    "未支付出场策略：BLOCK=拦截, ALLOW_ARREARS=允许欠费放行"),
            new Definition(ARREARS_REEXIT_STRATEGY, "出场设置", TYPE_ENUM,
                    "[\"MUST_PAY\",\"REMIND_ONLY\"]", ARREARS_MUST_PAY,
                    "欠费车辆再次出场策略：MUST_PAY=必须补缴, REMIND_ONLY=仅提醒"),
            new Definition(RECOGNITION_FAIL_STRATEGY, "出场设置", TYPE_ENUM,
                    "[\"MANUAL\",\"AUTO_RELEASE\"]", RECOGNITION_MANUAL,
                    "识别失败处理策略：MANUAL=人工处理, AUTO_RELEASE=自动放行"),
            new Definition(MONTHLY_PASS_EXPIRY_REMINDER_DAYS, "计费设置", TYPE_INT, null,
                    "7", "月卡到期提醒天数"),
            new Definition(MONTHLY_PASS_COUNT_IN_AVAILABLE_SPACE, "计费设置", TYPE_BOOLEAN, null,
                    "false", "月卡/固定车位是否计入余位"),
            new Definition(FIXED_SPACE_REVIEW_MODE, "固定车位设置", TYPE_ENUM,
                    "[\"AUTO\",\"MANUAL\"]", "AUTO",
                    "固定车位审核模式：AUTO=自动通过, MANUAL=需审核"),
            new Definition(MONTHLY_PASS_PRICE_PER_MONTH_CENTS, "计费设置", TYPE_INT, null,
                    "30000", "月卡单价（分/月）"));

    /** 键 → 定义 的快速查找表。 */
    public static final Map<String, Definition> LOT_PARAM_INDEX = LOT_PARAMS.stream()
            .collect(Collectors.collectingAndThen(
                    Collectors.toMap(Definition::key, Function.identity()),
                    Map::copyOf));

    /**
     * 判断某个键是否为受支持的车场级可覆盖参数。
     *
     * @param key 参数键
     * @return true=属于 7 项车场级参数
     */
    public static boolean isLotOverridable(String key) {
        return LOT_PARAM_INDEX.containsKey(key);
    }

    /**
     * 获取参数定义。
     *
     * @param key 参数键
     * @return 定义；键非车场级参数时返回 {@code null}
     */
    public static Definition definition(String key) {
        return LOT_PARAM_INDEX.get(key);
    }

    /**
     * 获取参数的代码内置默认值。
     *
     * @param key 参数键
     * @return 默认值；键非车场级参数时返回 {@code null}
     */
    public static String defaultValue(String key) {
        Definition def = LOT_PARAM_INDEX.get(key);
        return def != null ? def.defaultValue() : null;
    }
}
