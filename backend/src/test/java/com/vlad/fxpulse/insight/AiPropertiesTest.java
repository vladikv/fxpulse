package com.vlad.fxpulse.insight;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class AiPropertiesTest {

    private static AiProperties withKey(String apiKey) {
        return new AiProperties("http://localhost/v1beta", "gemini-test", apiKey, 256,
                Duration.ofSeconds(2), Duration.ofSeconds(30), 50);
    }

    @Test
    void toString_always_includesMaxGenerationsPerDayAndStillMasksKey() {
        // Given
        AiProperties properties = withKey("super-secret-key");

        // When
        String text = properties.toString();

        // Then
        assertThat(text).contains("maxGenerationsPerDay=50")
                .contains("apiKey=***")
                .doesNotContain("super-secret-key");
    }

    @Test
    void toString_keySet_masksKey() {
        // Given
        AiProperties properties = withKey("super-secret-key");

        // When
        String text = properties.toString();

        // Then
        assertThat(text).doesNotContain("super-secret-key").contains("apiKey=***");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void toString_keyMissing_showsNotSet(String apiKey) {
        // When
        String text = withKey(apiKey).toString();

        // Then
        assertThat(text).contains("apiKey=<not set>");
    }

    @Test
    void hasApiKey_keySet_returnsTrue() {
        assertThat(withKey("abc").hasApiKey()).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void hasApiKey_keyMissing_returnsFalse(String apiKey) {
        assertThat(withKey(apiKey).hasApiKey()).isFalse();
    }
}
