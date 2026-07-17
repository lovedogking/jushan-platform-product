package com.smartparking.deviceaccess.common.entity;

import com.smartparking.deviceaccess.common.enums.DeviceCapability;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DeviceProduct 能力检查单元测试。
 */
@DisplayName("DeviceProduct.hasCapability")
class DeviceProductTest {

    private DeviceProduct productWith(String capabilities) {
        DeviceProduct p = new DeviceProduct();
        p.setCapabilities(capabilities);
        return p;
    }

    @Nested
    @DisplayName("正常 JSON 数组")
    class ValidJson {

        @Test
        @DisplayName("Should return true for matching capability")
        void shouldReturnTrueForPresentCapability() {
            DeviceProduct p = productWith("[\"DISPLAY_TEXT\",\"TIME_SYNC\"]");

            assertThat(p.hasCapability(DeviceCapability.DISPLAY_TEXT)).isTrue();
            assertThat(p.hasCapability(DeviceCapability.TIME_SYNC)).isTrue();
        }

        @Test
        @DisplayName("Should return false for absent capability")
        void shouldReturnFalseForMissingCapability() {
            DeviceProduct p = productWith("[\"TIME_SYNC\"]");

            assertThat(p.hasCapability(DeviceCapability.DISPLAY_TEXT)).isFalse();
            assertThat(p.hasCapability(DeviceCapability.DISPLAY_SAVE)).isFalse();
            assertThat(p.hasCapability(DeviceCapability.PERIPHERAL_CONTROL)).isFalse();
        }

        @Test
        @DisplayName("Should return false for all capabilities when array is empty")
        void shouldReturnFalseForEmptyArray() {
            DeviceProduct p = productWith("[]");

            for (DeviceCapability c : DeviceCapability.values()) {
                assertThat(p.hasCapability(c))
                        .as("Empty capabilities should return false for " + c)
                        .isFalse();
            }
        }

        @Test
        @DisplayName("Should handle whitespace in JSON")
        void shouldHandleWhitespace() {
            DeviceProduct p = productWith("[ \"DISPLAY_TEXT\", \"TIME_SYNC\" ]");

            assertThat(p.hasCapability(DeviceCapability.DISPLAY_TEXT)).isTrue();
            assertThat(p.hasCapability(DeviceCapability.TIME_SYNC)).isTrue();
            assertThat(p.hasCapability(DeviceCapability.DISPLAY_SAVE)).isFalse();
        }
    }

    @Nested
    @DisplayName("边界和非法输入")
    class EdgeCases {

        @Test
        @DisplayName("Should return false for null capabilities")
        void shouldReturnFalseForNull() {
            DeviceProduct p = productWith(null);

            for (DeviceCapability c : DeviceCapability.values()) {
                assertThat(p.hasCapability(c)).isFalse();
            }
        }

        @Test
        @DisplayName("Should return false for blank capabilities")
        void shouldReturnFalseForBlank() {
            DeviceProduct p = productWith("   ");

            for (DeviceCapability c : DeviceCapability.values()) {
                assertThat(p.hasCapability(c)).isFalse();
            }
        }

        @Test
        @DisplayName("Should return false for malformed JSON (no brackets)")
        void shouldNotThrowForMalformedJson() {
            DeviceProduct p = productWith("DISPLAY_TEXT,TIME_SYNC");

            // 不抛异常，返回 false
            assertThat(p.hasCapability(DeviceCapability.DISPLAY_TEXT)).isFalse();
        }

        @Test
        @DisplayName("Should not match substrings")
        void shouldNotMatchSubstring() {
            // DISPLAY_TEXT 和 DISPLAY_SAVE 互相不能匹配
            DeviceProduct p = productWith("[\"DISPLAY_TEXT\"]");

            assertThat(p.hasCapability(DeviceCapability.DISPLAY_TEXT)).isTrue();
            // DISPLAY_SAVE is not a substring — but verify no false positive
            assertThat(p.hasCapability(DeviceCapability.DISPLAY_SAVE)).isFalse();
            // TIME_SYNC should not match either
            assertThat(p.hasCapability(DeviceCapability.TIME_SYNC)).isFalse();
        }
    }
}
