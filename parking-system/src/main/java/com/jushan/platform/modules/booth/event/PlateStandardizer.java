package com.jushan.platform.modules.booth.event;

import java.util.regex.Pattern;

/**
 * 车牌号标准化工具（T29）。
 * <p>
 * 负责将原始车牌号（可能含空格、大小写混用、OCR 误差）标准化为统一格式。
 * 标准化规则：
 * <ol>
 *   <li>去除首尾及中间空格</li>
 *   <li>英文字母统一大写</li>
 *   <li>全角字符转半角</li>
 *   <li>基本格式校验（长度、省份汉字前缀）</li>
 * </ol>
 * <p>
 * <strong>中国车牌格式</strong>：
 * <ul>
 *   <li>标准蓝牌/黄牌：7 位（1 省份汉字 + 1 字母 + 5 位字母数字）</li>
 *   <li>新能源绿牌：8 位（1 省份汉字 + 1 字母 + 6 位字母数字）</li>
 *   <li>港澳入出：粤Zxxxx港/澳（6 位）</li>
 *   <li>警用/军用等特殊车牌不做严格关闭，仅标准化格式</li>
 * </ul>
 * <p>
 * <strong>注意</strong>：标准化不等于"车牌有效"判定。非法车牌的后续业务处理
 * 由上层调用方决定（如标记 FAILED 并记录 failure_reason）。
 *
 * @author Jushan Platform
 * @since 1.0.0
 */
public final class PlateStandardizer {

    private PlateStandardizer() {}

    /** 中国大陆合法省份/直辖市汉字前缀 */
    private static final Pattern PROVINCE_PREFIX = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁]$");

    /** 新能源绿牌：8 位（省份 + 字母 + 6 位） */
    private static final Pattern GREEN_PLATE = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁][A-Z][A-Z0-9]{6}$");

    /** 标准蓝牌/黄牌：7 位（省份 + 字母 + 5 位） */
    private static final Pattern STANDARD_PLATE = Pattern.compile(
            "^[京津沪渝冀豫云辽黑湘皖鲁新苏浙赣鄂桂甘晋蒙陕吉闽贵粤川青藏琼宁][A-Z][A-Z0-9]{5}$");

    /** 港澳入出牌照：粤Z + 4位数字 + 港/澳 */
    private static final Pattern HK_MO_PLATE = Pattern.compile(
            "^粤Z[0-9]{4}[港澳]$");

    /**
     * 标准化车牌号。
     *
     * @param rawPlate 原始车牌号（可能为空）
     * @return 标准化后的车牌号；输入为 null 或全空白时返回 null
     */
    public static String normalize(String rawPlate) {
        if (rawPlate == null || rawPlate.isBlank()) {
            return null;
        }

        // 1. 去除所有空白字符（全角 + 半角）
        String normalized = rawPlate
                .replaceAll("[\\s　]+", "");

        if (normalized.isEmpty()) {
            return null;
        }

        // 2. 全角英文字母转半角
        normalized = fullWidthToHalf(normalized);

        // 3. 统一大写
        normalized = normalized.toUpperCase();

        return normalized;
    }

    /**
     * 校验车牌号格式是否基本有效。
     * <p>
     * 仅校验长度和省份前缀，不做更细颗粒度的合法性判定。
     *
     * @param plate 标准化后的车牌号
     * @return true 格式基本有效，false 格式无效
     */
    public static boolean isValidFormat(String plate) {
        if (plate == null || plate.isEmpty()) {
            return false;
        }

        // 标准蓝牌/黄牌：7 位
        if (plate.length() == 7 && STANDARD_PLATE.matcher(plate).matches()) {
            return true;
        }

        // 新能源绿牌：8 位
        if (plate.length() == 8 && GREEN_PLATE.matcher(plate).matches()) {
            return true;
        }

        // 港澳入出
        if (HK_MO_PLATE.matcher(plate).matches()) {
            return true;
        }

        // 其他特殊牌照：至少以省份开头且长度合理（5-8 位）
        if (plate.length() >= 5 && plate.length() <= 8) {
            String prefix = plate.substring(0, 1);
            return PROVINCE_PREFIX.matcher(prefix).matches();
        }

        return false;
    }

    /**
     * 全角英文字母和数字转半角。
     */
    private static String fullWidthToHalf(String input) {
        StringBuilder sb = new StringBuilder(input.length());
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            // 全角大写字母 Ａ-Ｚ（U+FF21 - U+FF3A）→ 半角 A-Z（U+0041 - U+005A）
            if (c >= 'Ａ' && c <= 'Ｚ') {
                sb.append((char) (c - 0xFF21 + 0x0041));
            }
            // 全角小写字母 ａ-ｚ（U+FF41 - U+FF5A）→ 半角 a-z（U+0061 - U+007A）
            else if (c >= 'ａ' && c <= 'ｚ') {
                sb.append((char) (c - 0xFF41 + 0x0061));
            }
            // 全角数字 ０-９（U+FF10 - U+FF19）→ 半角 0-9（U+0030 - U+0039）
            else if (c >= '０' && c <= '９') {
                sb.append((char) (c - 0xFF10 + 0x0030));
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }
}
