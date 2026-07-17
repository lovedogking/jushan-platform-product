package com.smartparking.deviceaccess.adapter.zhenshi.display;

import com.smartparking.deviceaccess.adapter.support.display.OlmM1dProtocol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OLM-M1D 协议一致性测试。
 * <p>
 * 对照《显示屏通信协议 v2.0》中的示例，逐项验证帧格式和 CRC 计算。
 */
@DisplayName("OLM-M1D Protocol Conformance")
class OlmM1dProtocolTest {

    // ──────── CRC16 验证 ────────

    @Test
    @DisplayName("CRC16: 0x0C 亮度50% 示例 → 0x6277（协议 p1-20 第302行）")
    void crc16ShouldMatchBrightnessExample() {
        byte[] data = {0x00, 0x64, (byte) 0xFF, (byte) 0xFF, 0x0C, 0x01, 0x32};
        int crc = OlmM1dProtocol.crc16(data);
        // 协议：77 62（低字节在前） → CRC 值 = 0x6277
        assertThat(crc).isEqualTo(0x6277);
    }

    // ──────── 0x0D 音量帧 ────────

    @Test
    @DisplayName("0x0D: 音量帧结构正确且值被钳位到 0~100")
    void volumeFrameShouldBeCorrectAndClamped() {
        byte[] frame = OlmM1dProtocol.buildVolumeFrame(0x00, 50);
        assertThat(frame[4] & 0xFF).isEqualTo(0x0D).as("CMD should be 0x0D");
        assertThat(frame[5] & 0xFF).isEqualTo(1).as("DL should be 1");
        assertThat(frame[6] & 0xFF).isEqualTo(50).as("Volume should be 50");
        assertThat(frame.length).isEqualTo(9).as("Total length = 6(header) + 1(DL) + 1(data) + 2(CRC)");

        // 钳位测试
        byte[] fLow = OlmM1dProtocol.buildVolumeFrame(0x00, -10);
        assertThat(fLow[6] & 0xFF).isEqualTo(0).as("Volume clamped to 0");

        byte[] fHigh = OlmM1dProtocol.buildVolumeFrame(0x00, 150);
        assertThat(fHigh[6] & 0xFF).isEqualTo(100).as("Volume clamped to 100");
    }

    // ──────── 0x05 时间同步帧 ────────

    @Test
    @DisplayName("0x05: 时间同步帧结构正确，年月日时分秒星期各1字节")
    void syncTimeFrameShouldHaveCorrectStructure() {
        byte[] frame = OlmM1dProtocol.buildSyncTimeFrame(0x00, 2026, 7, 13, 14, 30, 0, 1);
        assertThat(frame[4] & 0xFF).isEqualTo(0x05).as("CMD should be 0x05");
        assertThat(frame[5] & 0xFF).isEqualTo(7).as("DL should be 7");
        assertThat(frame[6] & 0xFF).isEqualTo(26).as("Year offset = 2026-2000 = 26");
        assertThat(frame[7] & 0xFF).isEqualTo(7).as("Month = 7");
        assertThat(frame[8] & 0xFF).isEqualTo(13).as("Day = 13");
        assertThat(frame[9] & 0xFF).isEqualTo(14).as("Hour = 14");
        assertThat(frame[10] & 0xFF).isEqualTo(30).as("Minute = 30");
        assertThat(frame[11] & 0xFF).isEqualTo(0).as("Second = 0");
        assertThat(frame[12] & 0xFF).isEqualTo(1).as("Week = 1 (Monday)");
        assertThat(frame.length).isEqualTo(15).as("Total length = 6 + 7 + 2");
    }

    // ──────── 0x19 显示方向帧 ────────

    @Test
    @DisplayName("0x19: 方向帧结构正确，0=正常 1=旋转180度")
    void directionFrameShouldBeCorrect() {
        byte[] frameNormal = OlmM1dProtocol.buildDirectionFrame(0x00, 0);
        assertThat(frameNormal[4] & 0xFF).isEqualTo(0x19).as("CMD should be 0x19");
        assertThat(frameNormal[5] & 0xFF).isEqualTo(1).as("DL should be 1");
        assertThat(frameNormal[6] & 0xFF).isEqualTo(0).as("Direction = 0 (normal)");

        byte[] frameRotate = OlmM1dProtocol.buildDirectionFrame(0x00, 1);
        assertThat(frameRotate[6] & 0xFF).isEqualTo(1).as("Direction = 1 (rotate 180)");
    }

    // ──────── 0x30 播放语音帧 ────────

    @Test
    @DisplayName("0x30: 播放语音帧结构正确，含 voiceId + variable")
    void playVoiceFrameShouldBeCorrect() {
        byte[] frame = OlmM1dProtocol.buildPlayVoiceFrame(0x00, "5.00", 42);
        assertThat(frame[4] & 0xFF).isEqualTo(0x30).as("CMD should be 0x30");

        // voiceId at offset 6 (after header 6 + DL 1)
        assertThat(frame[6] & 0xFF).isEqualTo(42).as("Voice ID = 42");

        // variable length
        byte[] varBytes = "5.00".getBytes(java.nio.charset.Charset.forName("GBK"));
        assertThat(frame[7] & 0xFF).isEqualTo(varBytes.length).as("Variable length correct");

        // DL = 1 (voiceId) + 1 (varLen) + varBytes.length
        int expectedDl = 2 + varBytes.length;
        assertThat(frame[5] & 0xFF).isEqualTo(expectedDl).as("DL should be 2 + varLen");
    }

    @Test
    @DisplayName("0x30: 无变量时 variable length = 0")
    void playVoiceFrameWithoutVariableShouldHaveZeroVarLen() {
        byte[] frame = OlmM1dProtocol.buildPlayVoiceFrame(0x00, null, 100);
        assertThat(frame[6] & 0xFF).isEqualTo(100).as("Voice ID = 100");
        assertThat(frame[7] & 0xFF).isEqualTo(0).as("Variable length = 0 (null)");
        assertThat(frame[5] & 0xFF).isEqualTo(2).as("DL = 2 (voiceId + varLen)");
    }

    // ──────── 0x31 停止语音帧 ────────

    @Test
    @DisplayName("0x31: 停止语音帧结构正确，DL=0")
    void stopVoiceFrameShouldBeCorrect() {
        byte[] frame = OlmM1dProtocol.buildStopVoiceFrame(0x00);
        assertThat(frame[4] & 0xFF).isEqualTo(0x31).as("CMD should be 0x31");
        assertThat(frame[5] & 0xFF).isEqualTo(0).as("DL should be 0");
        assertThat(frame.length).isEqualTo(8).as("Total length = 6 + 0 + 2");
    }

    // ──────── FontType 枚举 ────────

    @Test
    @DisplayName("FontType: 8 种字体值应与协议一致")
    void fontTypeValuesShouldMatchProtocol() {
        assertThat(OlmM1dProtocol.FontType.ASCII_8.getValue()).isEqualTo(0x00);
        assertThat(OlmM1dProtocol.FontType.ASCII_10.getValue()).isEqualTo(0x01);
        assertThat(OlmM1dProtocol.FontType.ASCII_13.getValue()).isEqualTo(0x02);
        assertThat(OlmM1dProtocol.FontType.SONG_16.getValue()).isEqualTo(0x03);
        assertThat(OlmM1dProtocol.FontType.SONG_24.getValue()).isEqualTo(0x04);
        assertThat(OlmM1dProtocol.FontType.SONG_32.getValue()).isEqualTo(0x05);
        assertThat(OlmM1dProtocol.FontType.SONG_48.getValue()).isEqualTo(0x06);
        assertThat(OlmM1dProtocol.FontType.SONG_64.getValue()).isEqualTo(0x07);
    }

    @Test
    @DisplayName("FontType: 应能通过值反向查找")
    void fontTypeShouldBeFindableByValue() {
        assertThat(OlmM1dProtocol.FontType.fromValue(0x05)).isEqualTo(OlmM1dProtocol.FontType.SONG_32);
        assertThat(OlmM1dProtocol.FontType.fromValue(0x00)).isEqualTo(OlmM1dProtocol.FontType.ASCII_8);
    }

    // ──────── 新增帧 CRC 自校验 ────────

    @Test
    @DisplayName("所有新增帧 CRC 自校验通过")
    void allNewFramesShouldHaveValidCrc() {
        verifyCrc(OlmM1dProtocol.buildVolumeFrame(0x00, 50), "volume");
        verifyCrc(OlmM1dProtocol.buildSyncTimeFrame(0x00, 2026, 7, 13, 10, 0, 0, 1), "syncTime");
        verifyCrc(OlmM1dProtocol.buildDirectionFrame(0x00, 1), "direction");
        verifyCrc(OlmM1dProtocol.buildPlayVoiceFrame(0x00, "test", 42), "playVoice");
        verifyCrc(OlmM1dProtocol.buildStopVoiceFrame(0x00), "stopVoice");
    }

    // ──────── 0x0C 亮度帧 ────────

    @Test
    @DisplayName("0x0C: 亮度50% 帧应与协议示例完全一致")
    void brightnessFrameShouldMatchProtocolExample() {
        byte[] frame = OlmM1dProtocol.buildBrightnessFrame(0x00, 50);
        // 协议示例：00 64 FF FF 0C 01 32 77 62
        assertThat(frame).containsExactly(
                0x00, 0x64, (byte) 0xFF, (byte) 0xFF, // DA+VR+PN
                0x0C,                                     // CMD
                0x01,                                     // DL
                0x32,                                     // LIGHT=50
                0x77, 0x62                                // CRC (low first)
        );
    }

    @Test
    @DisplayName("0x0C: 超出范围的值应被钳位到 10~100")
    void brightnessShouldClampToValidRange() {
        // 下限：0 → 10
        byte[] f1 = OlmM1dProtocol.buildBrightnessFrame(0x00, 0);
        assertThat(f1[6] & 0xFF).isEqualTo(10);   // LIGHT byte

        // 上限：200 → 100
        byte[] f2 = OlmM1dProtocol.buildBrightnessFrame(0x00, 200);
        assertThat(f2[6] & 0xFF).isEqualTo(100);  // LIGHT byte

        // 正常值不变
        byte[] f3 = OlmM1dProtocol.buildBrightnessFrame(0x00, 50);
        assertThat(f3[6] & 0xFF).isEqualTo(50);
    }

    // ──────── 0x6F 多行显示帧 ────────

    @Test
    @DisplayName("0x6F: TEXT_CONTEXT 应包含 FLAGS=0x00（协议 p21-40 第13行）")
    void multiLineFrameShouldIncludeFlagsByte() {
        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(0x00, 2);

        // 帧头：DA(1)+VR(1)+PN(2)+CMD(1)+DL(1)+SF(1)+GST(1)+TEXT_CONTEXT_NUMBER(1) = 9 bytes
        int pos = 9;
        // TEXT_CONTEXT[0]: LID(1)+DM(1)+DS(1)+DT(1)+DR(1)+FINDEX(1)+FLAGS(1)+TC(4)+TL(1)+TEXT(0)+0x0D(1)
        // = 13 bytes

        // 验证 FINDEX = 0x03（宋体16）
        assertThat(frame[pos + 5] & 0xFF).isEqualTo(0x03).as("FINDEX should be 0x03 (宋体16)");
        // 验证 FLAGS = 0x00
        assertThat(frame[pos + 6] & 0xFF).isEqualTo(0x00).as("FLAGS should be 0x00");

        // TEXT_CONTEXT[1] 结构相同，findex 和 flags 偏移 +13
        assertThat(frame[pos + 13 + 5] & 0xFF).isEqualTo(0x03).as("FINDEX of 2nd row should be 0x03");
        assertThat(frame[pos + 13 + 6] & 0xFF).isEqualTo(0x00).as("FLAGS of 2nd row should be 0x00");
    }

    @Test
    @DisplayName("0x6F: DS 应为 0（协议建议值）")
    void multiLineFrameShouldHaveDsZero() {
        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(0x00, 2);

        int pos = 9;  // skip header
        // DS is at offset 2 within TEXT_CONTEXT
        assertThat(frame[pos + 2] & 0xFF).isEqualTo(0x00).as("DS should be 0 (protocol recommended value)");
        assertThat(frame[pos + 13 + 2] & 0xFF).isEqualTo(0x00).as("DS of 2nd row should also be 0");
    }

    @Test
    @DisplayName("0x6F: 2行/4行帧结构完整且 CRC 可验证")
    void multiLineFrameStructureIsCorrect() {
        // 2 行
        byte[] f2 = OlmM1dProtocol.buildMultiLineFrame(0x00, 2);
        assertThat(f2[4] & 0xFF).isEqualTo(0x6F).as("CMD should be 0x6F");
        assertThat(f2[8] & 0xFF).isEqualTo(2).as("TEXT_CONTEXT_NUMBER should be 2");
        // 每行 TEXT_CONTEXT = LID(1)+DM(1)+DS(1)+DT(1)+DR(1)+FINDEX(1)+FLAGS(1)+TC(4)+TL(1)+TEXT(0)+sep(1) = 13
        int tcLen = 13;
        int headerEnd = 9; // DA+VR+PN+CMD+DL+SF+GST+TEXT_CONTEXT_NUMBER
        // 第1行分隔符位置
        assertThat(f2[headerEnd + tcLen - 1] & 0xFF).isEqualTo(0x0D).as("1st row separator should be 0x0D");
        // 第2行分隔符位置
        assertThat(f2[headerEnd + 2 * tcLen - 1] & 0xFF).isEqualTo(0x00).as("2nd row separator should be 0x00");
        // VF 在第2行分隔符之后，应为 0x0A（协议固定值）
        assertThat(f2[headerEnd + 2 * tcLen] & 0xFF).isEqualTo(0x0A).as("VF should be 0x0A (protocol fixed value)");
        // VTL 在 VF 之后，无语音时为 0
        assertThat(f2[headerEnd + 2 * tcLen + 1] & 0xFF).isEqualTo(0x00).as("VTL should be 0 (no voice)");
        // CRC 自校验
        byte[] data = new byte[f2.length - 2];
        System.arraycopy(f2, 0, data, 0, data.length);
        int crc = OlmM1dProtocol.crc16(data);
        assertThat(f2[f2.length - 2] & 0xFF).isEqualTo(crc & 0xFF).as("CRC low byte");
        assertThat(f2[f2.length - 1] & 0xFF).isEqualTo((crc >> 8) & 0xFF).as("CRC high byte");

        // 4 行
        byte[] f4 = OlmM1dProtocol.buildMultiLineFrame(0x00, 4);
        assertThat(f4[8] & 0xFF).isEqualTo(4).as("TEXT_CONTEXT_NUMBER should be 4");
        assertThat(f4[headerEnd + 4 * tcLen - 1] & 0xFF).isEqualTo(0x00).as("4th row separator should be 0x00");
        // VF 应为 0x0A
        assertThat(f4[headerEnd + 4 * tcLen] & 0xFF).isEqualTo(0x0A).as("VF should be 0x0A");
        // VTL 应为 0
        assertThat(f4[headerEnd + 4 * tcLen + 1] & 0xFF).isEqualTo(0x00).as("VTL should be 0 (no voice)");
        // 自校验
        byte[] d4 = new byte[f4.length - 2];
        System.arraycopy(f4, 0, d4, 0, d4.length);
        assertThat(OlmM1dProtocol.crc16(d4))
                .isEqualTo((f4[f4.length - 2] & 0xFF) | ((f4[f4.length - 1] & 0xFF) << 8));
    }

    // ──────── 0x67 广告语下载帧 ────────

    @Test
    @DisplayName("0x67: CMD 应为 0x67")
    void adFrameShouldHaveCorrectCmd() {
        byte[] frame = OlmM1dProtocol.build0x67Frame(0x00, 0, "测试");

        assertThat(frame[4] & 0xFF).isEqualTo(0x67).as("CMD should be 0x67");
    }

    @Test
    @DisplayName("0x67: TWID 应位于正确偏移且值正确")
    void adFrameShouldHaveCorrectTwid() {
        byte[] frame = OlmM1dProtocol.build0x67Frame(0x00, 2, "测试");

        // Header: DA(1)+VR(1)+PN(2)+CMD(1)+DL(1) = 6 bytes
        // TWID is at offset 6
        assertThat(frame[6] & 0xFF).isEqualTo(2).as("TWID should be 2");
    }

    @Test
    @DisplayName("0x67: DL 应等于 20 + 文本字节长度")
    void adFrameShouldHaveCorrectDataLength() {
        // "自动识别减速慢行" = 16 bytes in GBK
        String text = "自动识别减速慢行";
        byte[] frame = OlmM1dProtocol.build0x67Frame(0x00, 0, text);

        // DL is at offset 5 (after DA+VR+PN+CMD)
        int dl = frame[5] & 0xFF;
        int expectedDl = 20 + text.getBytes(java.nio.charset.Charset.forName("GBK")).length;
        assertThat(dl).isEqualTo(expectedDl).as("DL should be 20 + text length in GBK");
    }

    @Test
    @DisplayName("0x67: 保留字段 R1/R2/R3 应为协议定义值")
    void adFrameShouldHaveCorrectReservedBytes() {
        byte[] frame = OlmM1dProtocol.build0x67Frame(0x00, 0, "测试");

        // Header(6) + TWID(1) = 7 → R1 at offset 7
        assertThat(frame[7] & 0xFF).isEqualTo(0x00).as("R1 should be 0x00");
        // R2 at offset 8
        assertThat(frame[8] & 0xFF).isEqualTo(0x0C).as("R2 should be 0x0C");
        // ETM(1)+ETS(1) = offsets 9, 10 → R3 at offset 11
        assertThat(frame[11] & 0xFF).isEqualTo(0x00).as("R3 after ETS should be 0x00");
    }

    @Test
    @DisplayName("0x67: CRC 自校验通过")
    void adFrameShouldHaveValidCrc() {
        String text = "自动识别减速慢行";
        byte[] frame = OlmM1dProtocol.build0x67Frame(0x00, 0, text);

        // Verify CRC self-check
        byte[] data = new byte[frame.length - 2];
        System.arraycopy(frame, 0, data, 0, data.length);
        int crc = OlmM1dProtocol.crc16(data);

        assertThat(frame[frame.length - 2] & 0xFF).isEqualTo(crc & 0xFF)
                .as("CRC low byte should match");
        assertThat(frame[frame.length - 1] & 0xFF).isEqualTo((crc >> 8) & 0xFF)
                .as("CRC high byte should match");
    }

    @Test
    @DisplayName("0x67: 帧总长度应为 6(header) + DL(=20+textLen) + 2(CRC)")
    void adFrameShouldHaveCorrectTotalLength() {
        String text = "欢迎光临";
        byte[] textBytes = text.getBytes(java.nio.charset.Charset.forName("GBK"));
        byte[] frame = OlmM1dProtocol.build0x67Frame(0x00, 0, text);

        // Total = DA(1)+VR(1)+PN(2)+CMD(1)+DL(1) + DATA(DL) + CRC(2)
        // = 6 + DL + 2 = 8 + DL = 8 + 20 + textLen = 28 + textLen
        int expected = 28 + textBytes.length;
        assertThat(frame.length).isEqualTo(expected);
    }

    @Test
    @DisplayName("0x6F: SRF=临时区(0), GST=模板默认(0), DM=静态(0), DR=无限循环(0)")
    void multiLineFrameDefaultFields() {
        byte[] frame = OlmM1dProtocol.buildMultiLineFrame(0x00, 2);

        // 帧头: DA(1)+VR(1)+PN(2)+CMD(1)+DL(1) = 6
        assertThat(frame[6] & 0xFF).isEqualTo(0x00).as("SF should be 0 (临时区)");
        assertThat(frame[7] & 0xFF).isEqualTo(0x00).as("GST should be 0 (模板默认)");

        int pos = 9;  // TEXT_CONTEXT[0] start
        assertThat(frame[pos + 0] & 0xFF).isEqualTo(0x00).as("LID should be 0 for 1st row");
        assertThat(frame[pos + 1] & 0xFF).isEqualTo(0x00).as("DM should be 0 (静态)");
        assertThat(frame[pos + 4] & 0xFF).isEqualTo(0x00).as("DR should be 0 (无限循环)");
    }

    // ──────── v0.3 联调验证：0x6F 帧逐字节对比 ────────

    @Test
    @DisplayName("0x6F 联调验证：输出空文本2行/4行 + 真实文本4行 HEX 帧并逐字节解析")
    void hexDumpForDebugging() {
        byte[] f2empty = OlmM1dProtocol.buildMultiLineFrame(0x00, 2);
        byte[] f4empty = OlmM1dProtocol.buildMultiLineFrame(0x00, 4);
        List<String> abcdTexts = Arrays.asList("A", "B", "C", "D");
        byte[] f4real = OlmM1dProtocol.buildMultiLineFrame(0x00, 4, abcdTexts);

        // ── 帧1: setDisplayMode TWO_LINE → 空文本2行 ──
        System.out.println("\n=== 0x6F rows=2 texts=[\"\",\"\"] (setDisplayMode TWO_LINE) ===");
        System.out.println("length=" + f2empty.length + " bytes, DL=" + (f2empty[5] & 0xFF));
        printHex(f2empty);
        printFieldLayout(f2empty, 2, "空2行");

        // ── 帧2: setDisplayMode FOUR_LINE → 空文本4行 ──
        System.out.println("\n=== 0x6F rows=4 texts=[\"\",\"\",\"\",\"\"] (setDisplayMode FOUR_LINE) ===");
        System.out.println("length=" + f4empty.length + " bytes, DL=" + (f4empty[5] & 0xFF));
        printHex(f4empty);
        printFieldLayout(f4empty, 4, "空4行");

        // ── 帧3: displayText → 真实文本4行 A/B/C/D ──
        System.out.println("\n=== 0x6F rows=4 texts=[\"A\",\"B\",\"C\",\"D\"] (displayText 4-line) ===");
        System.out.println("length=" + f4real.length + " bytes, DL=" + (f4real[5] & 0xFF));
        printHex(f4real);
        printFieldLayout(f4real, 4, "ABCD");

        // ── CRC 自校验 ──
        System.out.println("\n=== CRC 自校验 ===");
        verifyCrc(f2empty, "2-empty");
        verifyCrc(f4empty, "4-empty");
        verifyCrc(f4real, "4-ABCD");

        // ── 关键差异验证 ──
        System.out.println("\n=== 关键差异 ===");

        // TCN 验证
        assertThat(f2empty[8] & 0xFF).isEqualTo(2).as("TCN=2");
        assertThat(f4empty[8] & 0xFF).isEqualTo(4).as("TCN=4");
        assertThat(f4real[8] & 0xFF).isEqualTo(4).as("TCN=4");

        // 行固定字段：LID(1)+DM(1)+DS(1)+DT(1)+DR(1)+FINDEX(1)+FLAGS(1)+TC(4)+TL(1)=12 bytes
        // TL 位于每行偏移 +11 处
        // 空文本行总长 = 12+0+1 = 13 bytes (含 SEP)
        // 单字符文本行总长 = 12+1+1 = 14 bytes

        // 空文本2行：每行 TL=0
        int pos2 = 9;
        assertThat(f2empty[pos2 + 11] & 0xFF).isEqualTo(0).as("2-empty row0 TL=0 (at +11)");
        assertThat(f2empty[pos2 + 13 + 11] & 0xFF).isEqualTo(0).as("2-empty row1 TL=0 (at +11)");

        // 空文本4行：每行 TL=0
        int pos4 = 9;
        for (int i = 0; i < 4; i++) {
            int tl = f4empty[pos4 + i * 13 + 11] & 0xFF;
            assertThat(tl).isEqualTo(0).as("4-empty row" + i + " TL=0");
        }

        // 真实文本4行：每行 TL=1（'A'=0x41, 1 byte in GBK）
        for (int i = 0; i < 4; i++) {
            int tl4 = f4real[pos4 + i * 14 + 11] & 0xFF;
            assertThat(tl4).isEqualTo(1).as("4-real row" + i + " TL=1 (single ASCII char)");

            // 验证实际文本字节
            int textOffset = pos4 + i * 14 + 12; // 12 bytes fixed, then TEXT
            byte textByte = f4real[textOffset];
            char expectedChar = (char) ('A' + i);
            assertThat(textByte & 0xFF).isEqualTo((int) expectedChar)
                    .as("4-real row" + i + " text byte = '" + expectedChar + "'");
            System.out.println("4-real   Row" + i + " TL=" + tl4 + " TEXT=0x"
                    + String.format("%02X", textByte) + " ('" + (char) (textByte & 0xFF) + "')");
        }
    }

    // ──────── 辅助方法 ────────

    private void printHex(byte[] data) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < data.length; i++) {
            sb.append(String.format("%02X ", data[i] & 0xFF));
            if ((i + 1) % 16 == 0 && i < data.length - 1) {
                sb.append("\n");
            }
        }
        System.out.println(sb.toString().trim());
    }

    private void printFieldLayout(byte[] frame, int rows, String label) {
        System.out.println("--- Field map (" + label + ") ---");
        System.out.println("  [0-4]   DA VR PN_HI PN_LO CMD");
        System.out.println("  [5]     DL=" + (frame[5] & 0xFF));
        System.out.println("  [6]     SF=" + (frame[6] & 0xFF));
        System.out.println("  [7]     GST=" + (frame[7] & 0xFF));
        System.out.println("  [8]     TCN=" + (frame[8] & 0xFF));
        int pos = 9;
        for (int i = 0; i < rows; i++) {
            // 行固定字段 12 bytes: LID+DM+DS+DT+DR+FINDEX+FLAGS+TC[4]+TL
            int tl = frame[pos + 11] & 0xFF;
            int tcLen = 12 + tl + 1; // 12 fixed + TEXT + SEP
            System.out.println("  Row" + i + ": LID=" + (frame[pos] & 0xFF)
                    + " DM=" + (frame[pos + 1] & 0xFF)
                    + " DS=" + (frame[pos + 2] & 0xFF)
                    + " DT=" + (frame[pos + 3] & 0xFF)
                    + " DR=" + (frame[pos + 4] & 0xFF)
                    + " FINDEX=" + (frame[pos + 5] & 0xFF)
                    + " FLAGS=" + (frame[pos + 6] & 0xFF)
                    + " TL=" + tl
                    + " SEP=" + (frame[pos + tcLen - 1] & 0xFF));
            pos += tcLen;
        }
        System.out.println("  VF=" + (frame[pos] & 0xFF) + " (at offset " + pos + ")");
        System.out.println("  CRC=" + String.format("%02X%02X", frame[frame.length - 2] & 0xFF, frame[frame.length - 1] & 0xFF));
    }

    private void verifyCrc(byte[] frame, String label) {
        byte[] data = new byte[frame.length - 2];
        System.arraycopy(frame, 0, data, 0, data.length);
        int expected = (frame[frame.length - 2] & 0xFF) | ((frame[frame.length - 1] & 0xFF) << 8);
        int actual = OlmM1dProtocol.crc16(data);
        System.out.println(label + ": CRC=0x" + Integer.toHexString(actual)
                + " " + (expected == actual ? "PASS" : "FAIL(expected=0x" + Integer.toHexString(expected) + ")"));
    }
}
