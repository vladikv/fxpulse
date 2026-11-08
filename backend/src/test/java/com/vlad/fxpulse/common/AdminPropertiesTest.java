package com.vlad.fxpulse.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPropertiesTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void isEnabled_tokenMissing_returnsFalse(String token) {
        assertThat(new AdminProperties(token).isEnabled()).isFalse();
    }

    @Test
    void isEnabled_tokenSet_returnsTrue() {
        assertThat(new AdminProperties("x").isEnabled()).isTrue();
    }

    @Test
    void toString_tokenSet_masksToken() {
        // Given
        AdminProperties properties = new AdminProperties("super-secret-token");

        // When
        String text = properties.toString();

        // Then
        assertThat(text).contains("token=***").doesNotContain("super-secret-token");
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "  "})
    void toString_tokenMissing_showsNotSet(String token) {
        assertThat(new AdminProperties(token).toString()).contains("token=<not set>");
    }
}
