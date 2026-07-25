package com.smartparking.deviceaccess.adapter.support.display;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 科发 OLM-M1D LED 显示屏 RS485 协议构造器 (v2.0)。
 * <p>
 * 负责将控制命令构造为 OLM-M1D 二进制协议帧。
 * 协议帧通过臻识摄像头的 serial_data MQTT 消息透传到 RS485 总线。
 * <p>
 * 帧格式：DA(1B) + VR(1B) + PN(2B) + CMD(1B) + DL(1-2B) + DATA(≤255B) + CRC(2B)
 * <p>
 * Device Access 只负责设备能力控制（启用/关闭/模式），不管理显示内容。
 * 显示内容（文字、字体、颜色、模板）属于摄像头自身配置。
 * <p>
 * 关键参数：
 * <ul>
 *   <li>DA = 0x00（单块屏地址）</li>
 *   <li>VR = 0x64（协议版本 1.00，DL 为 1 字节）</li>
 *   <li>PN = 0xFFFF（单包传输）</li>
 *   <li>分辨率 = 32×64 像素</li>
 * </ul>
 * <p>
 * v2.1 精简：移除文本管理能力，只保留外围控制相关方法。
 *
 * @see <a href="docs/对接资料/OLM-M1D/显示屏通信协议v2.0.pdf">OLM-M1D 协议文档</a>
 */
public class OlmM1dProtocol {

    /** 默认显示地址（单块屏） */
    public static final int DA_DEFAULT = 0x00;

    /** 协议版本 1.00 */
    private static final int VR = 0x64;

    /** 单包传输标志 */
    private static final int PN_HI = 0xFF;
    private static final int PN_LO = 0xFF;

    // ──────────────────── 命令码 ────────────────────

    /** 单包多行显示 */
    private static final int CMD_MULTI_LINE = 0x6F;

    /** 下载广告语 */
    private static final int CMD_AD = 0x67;

    /** 调整亮度 */
    private static final int CMD_BRIGHTNESS = 0x0C;

    /** 调整音量 */
    private static final int CMD_VOLUME = 0x0D;

    /** 屏卡时间同步 */
    private static final int CMD_SYNC_TIME = 0x05;

    /** 显示方向控制 */
    private static final int CMD_DISPLAY_DIRECTION = 0x19;

    /** 播放语音 */
    private static final int CMD_PLAY_VOICE = 0x30;

    /** 停止语音 */
    private static final int CMD_STOP_VOICE = 0x31;

    // ──────────────────── 0x6F 字段常量 ────────────────────

    /** 存储标志：临时区 */
    private static final int SF_TEMP = 0x00;

    /** 界面显示时间：使用模板默认 */
    private static final int GST_DEFAULT = 0x00;

    /** 显示模式：静态 */
    private static final int DM_STATIC = 0x00;

    /** 显示速度（协议建议取 0） */
    private static final int DS_DEFAULT = 0;

    /** 停留时间（秒） */
    private static final int DT_DEFAULT = 5;

    /** 显示次数：0 = 无限循环 */
    private static final int DR_INFINITE = 0;

    /** 字体：宋体16（最小中文字体） */
    private static final int FONT_SONG_16 = 0x03;

    /** 显示标志位：保留字段，固定 0x00 */
    private static final int FLAGS_DEFAULT = 0x00;

    /** 默认文字颜色：白色 (R=255, G=255, B=255) */
    private static final int[] COLOR_WHITE = {0xFF, 0xFF, 0xFF, 0x00};

    /** 预设颜色名 → RGBA */
    private static final java.util.Map<String, int[]> COLOR_MAP = java.util.Map.of(
            "RED",    new int[]{0xFF, 0x00, 0x00, 0x00},
            "GREEN",  new int[]{0x00, 0xFF, 0x00, 0x00},
            "YELLOW", new int[]{0xFF, 0xFF, 0x00, 0x00},
            "BLUE",   new int[]{0x00, 0x00, 0xFF, 0x00},
            "WHITE",  new int[]{0xFF, 0xFF, 0xFF, 0x00}
    );

    /** 根据颜色名称获取 RGBA 数组，未知名称返回白色 */
    public static int[] colorFromName(String colorName) {
        if (colorName == null) return COLOR_WHITE;
        return COLOR_MAP.getOrDefault(colorName.toUpperCase(), COLOR_WHITE);
    }

    // ──────────────────── 字体枚举 ────────────────────

    /**
     * LED 显示屏字体类型枚举。
     * <p>
     * 对应 OLM-M1D 协议 0x6F/0x67 命令中的 FINDEX 字段。
     */
    public enum FontType {
        /** ASCII 8x16 */
        ASCII_8(0x00),
        /** ASCII 10x20 */
        ASCII_10(0x01),
        /** ASCII 13x26 */
        ASCII_13(0x02),
        /** 宋体 16x16 */
        SONG_16(0x03),
        /** 宋体 24x24 */
        SONG_24(0x04),
        /** 宋体 32x32 */
        SONG_32(0x05),
        /** 宋体 48x48 */
        SONG_48(0x06),
        /** 宋体 64x64 */
        SONG_64(0x07);

        private final int value;

        FontType(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public static FontType fromValue(int value) {
            for (FontType ft : values()) {
                if (ft.value == value) {
                    return ft;
                }
            }
            return SONG_16; // 默认回退
        }
    }

    // ──────────────────── 0x67 字段常量 ────────────────────

    /** 文字进入方式：立即显示 */
    private static final int ETM_STATIC = 0x00;

    /** 文字进入速度（协议建议值） */
    private static final int ETS_DEFAULT = 1;

    /** 保留字节 R1（固定 0x00） */
    private static final int R1_DEFAULT = 0x00;

    /** 保留字节 R2（固定 0x0C） */
    private static final int R2_DEFAULT = 0x0C;

    /** 保留字节 R3（固定 0x00） */
    private static final int R3_DEFAULT = 0x00;

    /** 保留字节数组 RA1[2]（固定 0x00） */
    private static final int[] RA1_DEFAULT = {0x00, 0x00};

    /** 保留字节数组 RA2[4]（固定 0x00） */
    private static final int[] RA2_DEFAULT = {0x00, 0x00, 0x00, 0x00};

    // ──────────────────── 0x0C 字段常量 ────────────────────

    /** 亮度最小值（百分比），协议规定 10~100 */
    public static final int BRIGHTNESS_MIN = 10;
    /** 亮度最大值（百分比） */
    public static final int BRIGHTNESS_MAX = 100;
    /** 关闭显示屏使用的最低有效亮度 */
    public static final int BRIGHTNESS_OFF = 10;

    /** 语音标志：协议规定固定取值 0x0A */
    private static final int VF_DEFAULT = 0x0A;

    // ──────────────────── 音量命令常量 ────────────────────

    /** 音量最小值（百分比），协议规定 0~100 */
    public static final int VOLUME_MIN = 0;
    /** 音量最大值（百分比） */
    public static final int VOLUME_MAX = 100;

    // ──────────────────── 显示方向常量 ────────────────────

    /** 显示方向：正常 */
    public static final int DIRECTION_NORMAL = 0x00;
    /** 显示方向：旋转180度 */
    public static final int DIRECTION_ROTATE_180 = 0x01;

    // ──────────────────── CRC16 查找表（Modbus CRC16）────────────────────

    private static final int[] CRC_HI = {
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40, 0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41,
        0x00, 0xC1, 0x81, 0x40, 0x01, 0xC0, 0x80, 0x41, 0x01, 0xC0, 0x80, 0x41, 0x00, 0xC1, 0x81, 0x40
    };

    private static final int[] CRC_LO = {
        0x00, 0xC0, 0xC1, 0x01, 0xC3, 0x03, 0x02, 0xC2, 0xC6, 0x06, 0x07, 0xC7, 0x05, 0xC5, 0xC4, 0x04,
        0xCC, 0x0C, 0x0D, 0xCD, 0x0F, 0xCF, 0xCE, 0x0E, 0x0A, 0xCA, 0xCB, 0x0B, 0xC9, 0x09, 0x08, 0xC8,
        0xD8, 0x18, 0x19, 0xD9, 0x1B, 0xDB, 0xDA, 0x1A, 0x1E, 0xDE, 0xDF, 0x1F, 0xDD, 0x1D, 0x1C, 0xDC,
        0x14, 0xD4, 0xD5, 0x15, 0xD7, 0x17, 0x16, 0xD6, 0xD2, 0x12, 0x13, 0xD3, 0x11, 0xD1, 0xD0, 0x10,
        0xF0, 0x30, 0x31, 0xF1, 0x33, 0xF3, 0xF2, 0x32, 0x36, 0xF6, 0xF7, 0x37, 0xF5, 0x35, 0x34, 0xF4,
        0x3C, 0xFC, 0xFD, 0x3D, 0xFF, 0x3F, 0x3E, 0xFE, 0xFA, 0x3A, 0x3B, 0xFB, 0x39, 0xF9, 0xF8, 0x38,
        0x28, 0xE8, 0xE9, 0x29, 0xEB, 0x2B, 0x2A, 0xEA, 0xEE, 0x2E, 0x2F, 0xEF, 0x2D, 0xED, 0xEC, 0x2C,
        0xE4, 0x24, 0x25, 0xE5, 0x27, 0xE7, 0xE6, 0x26, 0x22, 0xE2, 0xE3, 0x23, 0xE1, 0x21, 0x20, 0xE0,
        0xA0, 0x60, 0x61, 0xA1, 0x63, 0xA3, 0xA2, 0x62, 0x66, 0xA6, 0xA7, 0x67, 0xA5, 0x65, 0x64, 0xA4,
        0x6C, 0xAC, 0xAD, 0x6D, 0xAF, 0x6F, 0x6E, 0xAE, 0xAA, 0x6A, 0x6B, 0xAB, 0x69, 0xA9, 0xA8, 0x68,
        0x78, 0xB8, 0xB9, 0x79, 0xBB, 0x7B, 0x7A, 0xBA, 0xBE, 0x7E, 0x7F, 0xBF, 0x7D, 0xBD, 0xBC, 0x7C,
        0xB4, 0x74, 0x75, 0xB5, 0x77, 0xB7, 0xB6, 0x76, 0x72, 0xB2, 0xB3, 0x73, 0xB1, 0x71, 0x70, 0xB0,
        0x50, 0x90, 0x91, 0x51, 0x93, 0x53, 0x52, 0x92, 0x96, 0x56, 0x57, 0x97, 0x55, 0x95, 0x94, 0x54,
        0x9C, 0x5C, 0x5D, 0x9D, 0x5F, 0x9F, 0x9E, 0x5E, 0x5A, 0x9A, 0x9B, 0x5B, 0x99, 0x59, 0x58, 0x98,
        0x88, 0x48, 0x49, 0x89, 0x4B, 0x8B, 0x8A, 0x4A, 0x4E, 0x8E, 0x8F, 0x4F, 0x8D, 0x4D, 0x4C, 0x8C,
        0x44, 0x84, 0x85, 0x45, 0x87, 0x47, 0x46, 0x86, 0x82, 0x42, 0x43, 0x83, 0x41, 0x81, 0x80, 0x40
    };

    // ═══════════════════════════════════════════
    // 公共方法
    // ═══════════════════════════════════════════

    /**
     * 构建多行显示模式配置帧（0x6F）。
     * <p>
     * Device Access 只配置显示卡的 zone 布局，不管理文本内容。
     * 每行文本为空字符串，实际显示内容由摄像头/停车软件通过其他机制管理。
     *
     * @param da   显示地址（单屏为 0x00）
     * @param rows 分区数（2 或 4）
     * @return 二进制帧（含 CRC），可直接通过 serial_data 发送
     */
    public static byte[] buildMultiLineFrame(int da, int rows) {
        return buildMultiLineFrame(da, rows, java.util.Collections.nCopies(rows, ""));
    }

    /**
     * 构建多行显示帧（0x6F），带文本内容。
     * <p>
     * 内部方法，供 ZhenshiMessageHandler 调用。
     *
     * @param da    显示地址
     * @param rows  行数
     * @param texts 每行文字（GBK 编码）
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildMultiLineFrame(int da, int rows, List<String> texts) {
        return buildMultiLineFrame(da, rows, texts, FontType.SONG_16, java.util.List.of(COLOR_WHITE), null);
    }

    /**
     * 构建多行显示帧（0x6F），带完整自定义参数。
     * <p>
     * 支持字体、颜色、语音参数自定义。
     * 每行可独立设置颜色。
     *
     * @param da           显示地址
     * @param rows         行数
     * @param texts        每行文字（GBK 编码）
     * @param font         字体类型
     * @param colors       每行文字颜色 RGBA，List 中每个元素是4字节数组，对应一行颜色
     * @param voiceParams  语音参数 [voiceFlag, voiceTextLength, voiceTextBytes...]，null 表示无语音
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildMultiLineFrame(int da, int rows, List<String> texts,
                                              FontType font, List<int[]> colors, byte[] voiceParams) {
        List<Byte> frame = new ArrayList<>();

        // 帧头：DA + VR + PN(0xFFFF) + CMD
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_MULTI_LINE);
        // DL 暂占位
        int dlPos = frame.size();
        frame.add((byte) 0x00);

        // SF: 临时区
        frame.add((byte) SF_TEMP);
        // GST: 模板默认显示时间
        frame.add((byte) GST_DEFAULT);
        // TEXT_CONTEXT_NUMBER: 行数
        frame.add((byte) rows);

        Charset gbk = Charset.forName("GBK");
        int fontValue = font != null ? font.getValue() : FONT_SONG_16;

        for (int i = 0; i < rows; i++) {
            String text = i < texts.size() ? texts.get(i) : "";
            byte[] textBytes = text.getBytes(gbk);

            // 每行独立颜色：从 colors List 中取对应行的颜色，缺失则回退白色
            int[] textColor = COLOR_WHITE;
            if (colors != null && !colors.isEmpty()) {
                if (i < colors.size() && colors.get(i) != null && colors.get(i).length >= 4) {
                    textColor = colors.get(i);
                } else if (colors.get(colors.size() - 1) != null && colors.get(colors.size() - 1).length >= 4) {
                    // 如果行数超出 colors 大小，使用最后一个颜色
                    textColor = colors.get(colors.size() - 1);
                }
            }

            // LID: 行号
            frame.add((byte) i);
            // DM: 静态
            frame.add((byte) DM_STATIC);
            // DS: 速度
            frame.add((byte) DS_DEFAULT);
            // DT: 停留时间
            frame.add((byte) DT_DEFAULT);
            // DR: 无限循环
            frame.add((byte) DR_INFINITE);
            // FINDEX: 字体
            frame.add((byte) fontValue);
            // FLAGS: 保留字段，固定 0x00
            frame.add((byte) FLAGS_DEFAULT);
            // TC[4]: 颜色
            for (int c : textColor) {
                frame.add((byte) c);
            }
            // TL: 文本字节长度
            frame.add((byte) textBytes.length);
            // TEXT: GBK 文本
            for (byte b : textBytes) {
                frame.add(b);
            }
            // 分隔符
            frame.add((byte) (i < rows - 1 ? 0x0D : 0x00));
        }

        // VF + VTL + VoiceText
        if (voiceParams != null && voiceParams.length > 0) {
            for (byte b : voiceParams) {
                frame.add(b);
            }
        } else {
            // VF: 语音标志（协议固定取值 0x0A）
            frame.add((byte) VF_DEFAULT);
            // VTL: 语音文本长度（无语音时 = 0）
            frame.add((byte) 0x00);
        }

        // 回填 DL
        int dataLen = frame.size() - dlPos - 1;
        frame.set(dlPos, (byte) dataLen);

        byte[] raw = toByteArray(frame);
        return appendCrc(raw);
    }

    /**
     * 构建广告语下载帧（0x67）。
     * <p>
     * 将文字写入控制卡外部存储器（Flash），掉电不丢失。
     * 控制卡空闲时会自动循环显示已存储的广告语。
     * <p>
     * <b>注意：不要频繁调用此方法，否则会降低控制卡 Flash 存储器寿命。</b>
     * 频繁变动的内容请使用 {@link #buildMultiLineFrame(int, int, List)} (SF=0)。
     *
     * @param da   显示地址
     * @param twid 显示窗口 ID（0=第1行，1=第2行…）
     * @param text 文字内容（GBK 编码）
     * @return 二进制帧（含 CRC）
     */
    public static byte[] build0x67Frame(int da, int twid, String text) {
        return build0x67Frame(da, twid, text, FontType.SONG_16, COLOR_WHITE);
    }

    /**
     * 构建广告语下载帧（0x67），带字体和颜色自定义。
     *
     * @param da    显示地址
     * @param twid  显示窗口 ID
     * @param text  文字内容（GBK 编码）
     * @param font  字体类型
     * @param color 文字颜色 RGBA
     * @return 二进制帧（含 CRC）
     */
    public static byte[] build0x67Frame(int da, int twid, String text, FontType font, int[] color) {
        Charset gbk = Charset.forName("GBK");
        byte[] textBytes = text.getBytes(gbk);

        List<Byte> frame = new ArrayList<>();

        // Header: DA + VR + PN(0xFFFF) + CMD
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_AD);
        // DL placeholder
        int dlPos = frame.size();
        frame.add((byte) 0x00);

        // TWID
        frame.add((byte) twid);
        // R1: 保留字节
        frame.add((byte) R1_DEFAULT);
        // R2: 保留字节
        frame.add((byte) R2_DEFAULT);
        // ETM: 立即显示
        frame.add((byte) ETM_STATIC);
        // ETS: 进入速度
        frame.add((byte) ETS_DEFAULT);
        // R3: 保留字节
        frame.add((byte) R3_DEFAULT);
        // DT: 停留时间
        frame.add((byte) DT_DEFAULT);
        // RA1[2]: 保留字节
        for (int b : RA1_DEFAULT) {
            frame.add((byte) b);
        }
        // FINDEX: 字体
        int fontValue = font != null ? font.getValue() : FONT_SONG_16;
        frame.add((byte) fontValue);
        // TC[4]: 颜色
        int[] textColor = (color != null && color.length >= 4) ? color : COLOR_WHITE;
        for (int c : textColor) {
            frame.add((byte) c);
        }
        // RA2[4]: 保留字节
        for (int b : RA2_DEFAULT) {
            frame.add((byte) b);
        }
        // TL: 文本字节长度
        frame.add((byte) textBytes.length);
        // R3: 保留字节
        frame.add((byte) R3_DEFAULT);
        // TEXT: GBK 文本
        for (byte b : textBytes) {
            frame.add(b);
        }

        // Fill DL: 20 + TEXT length
        int dataLen = frame.size() - dlPos - 1;
        frame.set(dlPos, (byte) dataLen);

        byte[] raw = toByteArray(frame);
        return appendCrc(raw);
    }

    /**
     * 构建音量命令（0x0D）。
     * <p>
     * 协议规定音量范围为 {@value #VOLUME_MIN}~{@value #VOLUME_MAX}。
     * 传入值若超出范围会被钳位（clamp）。
     *
     * @param da      显示地址
     * @param percent 音量百分比
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildVolumeFrame(int da, int percent) {
        int clamped = Math.max(VOLUME_MIN, Math.min(VOLUME_MAX, percent));
        List<Byte> frame = new ArrayList<>();
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_VOLUME);
        frame.add((byte) 1);                // DL = 1
        frame.add((byte) clamped);          // VOLUME: 0~100
        return appendCrc(toByteArray(frame));
    }

    /**
     * 构建屏卡时间同步命令（0x05）。
     * <p>
     * 同步控制卡的实时时钟。
     *
     * @param da   显示地址
     * @param year 年（2000~2099）
     * @param month 月（1~12）
     * @param day  日（1~31）
     * @param hour 时（0~23）
     * @param minute 分（0~59）
     * @param second 秒（0~59）
     * @param week 星期（0=周日，1=周一…6=周六）
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildSyncTimeFrame(int da, int year, int month, int day,
                                             int hour, int minute, int second, int week) {
        List<Byte> frame = new ArrayList<>();
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_SYNC_TIME);
        frame.add((byte) 7);                // DL = 7
        frame.add((byte) (year - 2000));    // Year offset from 2000
        frame.add((byte) month);
        frame.add((byte) day);
        frame.add((byte) hour);
        frame.add((byte) minute);
        frame.add((byte) second);
        frame.add((byte) week);
        return appendCrc(toByteArray(frame));
    }

    /**
     * 构建显示方向控制命令（0x19）。
     *
     * @param da        显示地址
     * @param direction 方向：0=正常，1=旋转180度
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildDirectionFrame(int da, int direction) {
        int dir = (direction == DIRECTION_ROTATE_180) ? DIRECTION_ROTATE_180 : DIRECTION_NORMAL;
        List<Byte> frame = new ArrayList<>();
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_DISPLAY_DIRECTION);
        frame.add((byte) 1);                // DL = 1
        frame.add((byte) dir);              // Direction
        return appendCrc(toByteArray(frame));
    }

    /**
     * 构建播放语音命令（0x30）。
     * <p>
     * 采用文本匹配模式：发送文字内容，显示屏通过词组匹配查找内置语音库播放。
     * 支持变量替换（如金额、车牌号）。
     * <p>
     * 协议格式：DA + VR + PN + 0x30 + DL + OTP + TEXT(GBK编码)
     *
     * @param da       显示地址
     * @param text     播报文本（GBK编码），如"欢迎光临,请入场停车"
     * @param opt      操作选项：0x00=添加到队列不播放, 0x01=添加到队列并播放, 0x02=清除队列后播放
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildPlayVoiceFrame(int da, String text, int opt) {
        Charset gbk = Charset.forName("GBK");
        byte[] textBytes = (text != null && !text.isEmpty()) ? text.getBytes(gbk) : new byte[0];

        List<Byte> frame = new ArrayList<>();
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_PLAY_VOICE);
        // DL placeholder
        int dlPos = frame.size();
        frame.add((byte) 0x00);

        frame.add((byte) opt);              // OTP: 0x01=立即播放
        for (byte b : textBytes) {
            frame.add(b);                   // TEXT: GBK编码文本
        }

        int dataLen = frame.size() - dlPos - 1;
        frame.set(dlPos, (byte) dataLen);

        return appendCrc(toByteArray(frame));
    }

    /**
     * 构建停止语音命令（0x31）。
     *
     * @param da 显示地址
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildStopVoiceFrame(int da) {
        List<Byte> frame = new ArrayList<>();
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_STOP_VOICE);
        frame.add((byte) 0);                // DL = 0
        return appendCrc(toByteArray(frame));
    }

    /**
     * 构建亮度命令（0x0C）。
     * <p>
     * 协议规定亮度范围为 {@value #BRIGHTNESS_MIN}~{@value #BRIGHTNESS_MAX}。
     * 传入值若超出范围会被钳位（clamp）。
     *
     * @param da      显示地址
     * @param percent 亮度百分比
     * @return 二进制帧（含 CRC）
     */
    public static byte[] buildBrightnessFrame(int da, int percent) {
        int clamped = Math.max(BRIGHTNESS_MIN, Math.min(BRIGHTNESS_MAX, percent));
        List<Byte> frame = new ArrayList<>();
        frame.add((byte) da);
        frame.add((byte) VR);
        frame.add((byte) PN_HI);
        frame.add((byte) PN_LO);
        frame.add((byte) CMD_BRIGHTNESS);
        frame.add((byte) 1);                // DL = 1
        frame.add((byte) clamped);          // LIGHT: 10~100
        return appendCrc(toByteArray(frame));
    }

    // ═══════════════════════════════════════════
    // 帧分析（联调日志用，不修改协议）
    // ═══════════════════════════════════════════

    /**
     * 将原始帧解析为人类可读的字段描述，用于联调日志。
     * <p>
     * 纯诊断方法，不修改帧数据，不参与业务逻辑。
     * 尽可能解析已知字段，无法识别的部分标为 UNKNOWN。
     *
     * @param frame 完整协议帧（含 CRC）
     * @return 多行格式化的字段描述字符串
     */
    public static String formatFrameForDebug(byte[] frame) {
        if (frame == null || frame.length < 8) {
            return "  (frame too short to parse: " + (frame == null ? "null" : frame.length + " bytes") + ")";
        }

        StringBuilder sb = new StringBuilder();
        int da = frame[0] & 0xFF;
        int vr = frame[1] & 0xFF;
        int pnHi = frame[2] & 0xFF;
        int pnLo = frame[3] & 0xFF;
        int cmd = frame[4] & 0xFF;

        sb.append(String.format("  DA=0x%02X  VR=0x%02X  PN=0x%02X%02X  CMD=0x%02X",
                da, vr, pnHi, pnLo, cmd));

        // 解析已知命令
        switch (cmd) {
            case 0x6F -> append0x6FAnalysis(sb, frame);
            case 0x0C -> append0x0CAnalysis(sb, frame);
            case 0x0D -> append0x0DAnalysis(sb, frame);
            case 0x05 -> append0x05Analysis(sb, frame);
            case 0x19 -> append0x19Analysis(sb, frame);
            case 0x30 -> append0x30Analysis(sb, frame);
            case 0x31 -> append0x31Analysis(sb, frame);
            case 0x67 -> append0x67Analysis(sb, frame);
            default -> sb.append("  (unknown command)");
        }

        // CRC
        int frameLen = frame.length;
        int crcLo = frame[frameLen - 2] & 0xFF;
        int crcHi = frame[frameLen - 1] & 0xFF;
        int frameCrc = (crcHi << 8) | crcLo;
        byte[] crcData = new byte[frameLen - 2];
        System.arraycopy(frame, 0, crcData, 0, crcData.length);
        int calcCrc = crc16(crcData);
        sb.append(String.format("\n  CRC=0x%04X (calc=0x%04X %s)  totalLen=%d",
                frameCrc, calcCrc, frameCrc == calcCrc ? "OK" : "MISMATCH!", frameLen));

        return sb.toString();
    }

    private static void append0x0CAnalysis(StringBuilder sb, byte[] frame) {
        // 0x0C 亮度命令：DA(1)+VR(1)+PN(2)+CMD(1)+DL(1)+LIGHT(1)+CRC(2) = 9 bytes
        if (frame.length >= 7) {
            int dl = frame[5] & 0xFF;
            int brightness = frame.length >= 7 ? (frame[6] & 0xFF) : -1;
            sb.append(String.format("\n  DL=%d  Brightness=%d%%", dl, brightness));
        }
    }

    private static void append0x0DAnalysis(StringBuilder sb, byte[] frame) {
        // 0x0D 音量命令：DA(1)+VR(1)+PN(2)+CMD(1)+DL(1)+VOLUME(1)+CRC(2) = 9 bytes
        if (frame.length >= 7) {
            int dl = frame[5] & 0xFF;
            int volume = frame.length >= 7 ? (frame[6] & 0xFF) : -1;
            sb.append(String.format("\n  DL=%d  Volume=%d%%", dl, volume));
        }
    }

    private static void append0x05Analysis(StringBuilder sb, byte[] frame) {
        // 0x05 时间同步命令：DA(1)+VR(1)+PN(2)+CMD(1)+DL(7)+Y+M+D+H+m+S+W+CRC(2)
        if (frame.length >= 12) {
            int dl = frame[5] & 0xFF;
            int year = 2000 + (frame[6] & 0xFF);
            int month = frame[7] & 0xFF;
            int day = frame[8] & 0xFF;
            int hour = frame[9] & 0xFF;
            int minute = frame[10] & 0xFF;
            int second = frame[11] & 0xFF;
            int week = frame.length >= 13 ? (frame[12] & 0xFF) : -1;
            sb.append(String.format("\n  DL=%d  Time=%04d-%02d-%02d %02d:%02d:%02d  Week=%d",
                    dl, year, month, day, hour, minute, second, week));
        }
    }

    private static void append0x19Analysis(StringBuilder sb, byte[] frame) {
        // 0x19 显示方向命令：DA(1)+VR(1)+PN(2)+CMD(1)+DL(1)+DIR(1)+CRC(2)
        if (frame.length >= 7) {
            int dl = frame[5] & 0xFF;
            int dir = frame.length >= 7 ? (frame[6] & 0xFF) : -1;
            String dirStr = (dir == 1) ? "ROTATE_180" : "NORMAL";
            sb.append(String.format("\n  DL=%d  Direction=%s(%d)", dl, dirStr, dir));
        }
    }

    private static void append0x30Analysis(StringBuilder sb, byte[] frame) {
        // 0x30 播放语音命令（文本匹配模式）：DA(1)+VR(1)+PN(2)+CMD(1)+DL(1+)+OTP(1)+TEXT(?)+CRC(2)
        if (frame.length >= 7) {
            int dl = frame[5] & 0xFF;
            int opt = frame[6] & 0xFF;
            String optDesc = switch (opt) {
                case 0x00 -> "QUEUE";
                case 0x01 -> "PLAY";
                case 0x02 -> "CLEAR&PLAY";
                default -> "UNKNOWN";
            };
            sb.append(String.format("\n  DL=%d  OTP=0x%02X(%s)", dl, opt, optDesc));
            if (dl > 1 && frame.length >= 7 + (dl - 1)) {
                byte[] textBytes = new byte[dl - 1];
                System.arraycopy(frame, 7, textBytes, 0, dl - 1);
                String text = new String(textBytes, java.nio.charset.Charset.forName("GBK"));
                sb.append(String.format("  TEXT=\"%s\"", text));
            }
        }
    }

    private static void append0x31Analysis(StringBuilder sb, byte[] frame) {
        // 0x31 停止语音命令：DA(1)+VR(1)+PN(2)+CMD(1)+DL(0)+CRC(2)
        if (frame.length >= 6) {
            int dl = frame[5] & 0xFF;
            sb.append(String.format("\n  DL=%d  (stop voice)", dl));
        }
    }

    private static void append0x6FAnalysis(StringBuilder sb, byte[] frame) {
        if (frame.length < 9) return;
        int dl = frame[5] & 0xFF;
        int sf = frame[6] & 0xFF;
        int gst = frame[7] & 0xFF;
        int tcn = frame[8] & 0xFF;
        sb.append(String.format("\n  DL=%d  SF=0x%02X(%s)  GST=%d  TCN(rows)=%d",
                dl, sf, sf == 0x00 ? "TEMP" : "FLASH", gst, tcn));

        // 逐行解析
        int pos = 9; // after header + SF + GST + TCN
        java.nio.charset.Charset gbk = java.nio.charset.Charset.forName("GBK");
        for (int i = 0; i < tcn && pos + 12 <= frame.length; i++) {
            int lid = frame[pos] & 0xFF;
            int dm = frame[pos + 1] & 0xFF;
            int ds = frame[pos + 2] & 0xFF;
            int dt = frame[pos + 3] & 0xFF;
            int dr = frame[pos + 4] & 0xFF;
            int findex = frame[pos + 5] & 0xFF;
            int flags = frame[pos + 6] & 0xFF;
            int r = frame[pos + 7] & 0xFF, g = frame[pos + 8] & 0xFF;
            int b = frame[pos + 9] & 0xFF, a = frame[pos + 10] & 0xFF;
            int tl = frame[pos + 11] & 0xFF;

            String fontName = switch (findex) {
                case 0x00 -> "ASCII8"; case 0x01 -> "ASCII10"; case 0x02 -> "ASCII13";
                case 0x03 -> "SONG_16"; case 0x04 -> "SONG_24"; case 0x05 -> "SONG_32";
                case 0x06 -> "SONG_48"; case 0x07 -> "SONG_64";
                default -> "UNKNOWN";
            };

            String text = "";
            if (tl > 0 && pos + 12 + tl <= frame.length) {
                byte[] textBytes = new byte[tl];
                System.arraycopy(frame, pos + 12, textBytes, 0, tl);
                text = new String(textBytes, gbk);
            }
            sb.append(String.format(
                    "\n  Row%d: LID=%d FONT=0x%02X(%s) TC=RGBA(%d,%d,%d,%d) TL=%d TEXT=\"%s\"",
                    i, lid, findex, fontName, r, g, b, a, tl, text));

            pos += 13 + tl; // 12 fixed + TL text + 1 separator
        }

        // VF + VTL (after all rows)
        if (pos + 2 <= frame.length - 2) { // -2 for CRC
            int vf = frame[pos] & 0xFF;
            int vtl = frame[pos + 1] & 0xFF;
            sb.append(String.format("\n  VF=0x%02X  VTL=%d", vf, vtl));
        }
    }

    private static void append0x67Analysis(StringBuilder sb, byte[] frame) {
        if (frame.length < 6) return;
        int dl = frame[5] & 0xFF;
        sb.append(String.format("\n  DL=%d", dl));
        if (frame.length >= 7) {
            int twid = frame[6] & 0xFF;
            sb.append(String.format("  TWID(row)=%d", twid));
        }
        // 找到 TL 字段的位置：跳过 header(6) + TWID(1) + R1(1) + R2(1) + ETM(1) + ETS(1)
        //   + R3(1) + DT(1) + RA1(2) + FINDEX(1) + TC(4) + RA2(4) = 18, TL at pos 24
        int tlPos = 6 + 1 + 1 + 1 + 1 + 1 + 1 + 1 + 2 + 1 + 4 + 4; // = 24
        if (frame.length > tlPos) {
            int tl = frame[tlPos] & 0xFF;
            sb.append(String.format("  TL=%d", tl));
            if (tl > 0 && frame.length > tlPos + 1 + tl) {
                byte[] textBytes = new byte[tl];
                System.arraycopy(frame, tlPos + 1, textBytes, 0, tl);
                String text = new String(textBytes, java.nio.charset.Charset.forName("GBK"));
                sb.append(String.format("  TEXT=\"%s\"", text));
            }
        }
    }

    // ──────────────────── CRC16 ────────────────────

    /**
     * Modbus CRC16 校验。
     */
    public static int crc16(byte[] data) {
        int crcHi = 0xFF;
        int crcLo = 0xFF;
        for (byte b : data) {
            int index = crcLo ^ (b & 0xFF);
            crcLo = (crcHi ^ CRC_HI[index]) & 0xFF;
            crcHi = CRC_LO[index] & 0xFF;
        }
        return (crcHi << 8) | crcLo;
    }

    // ──────────────────── 内部工具 ────────────────────

    private static byte[] toByteArray(List<Byte> list) {
        byte[] result = new byte[list.size()];
        for (int i = 0; i < list.size(); i++) {
            result[i] = list.get(i);
        }
        return result;
    }

    /**
     * 追加 CRC16（低字节在前）。
     */
    private static byte[] appendCrc(byte[] raw) {
        int crc = crc16(raw);
        byte[] result = new byte[raw.length + 2];
        System.arraycopy(raw, 0, result, 0, raw.length);
        result[raw.length] = (byte) (crc & 0xFF);
        result[raw.length + 1] = (byte) ((crc >> 8) & 0xFF);
        return result;
    }
}
