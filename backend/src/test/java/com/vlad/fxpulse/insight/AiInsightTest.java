package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.currency.Currency;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class AiInsightTest {

    private static final LocalDate FROM = LocalDate.parse("2026-10-01");
    private static final LocalDate TO = LocalDate.parse("2026-10-31");

    private static AiInsight insight(int rateCount) {
        return new AiInsight(new Currency("EUR", "euro"), FROM, TO, "text", "model", rateCount);
    }

    @Test
    void isUpToDate_sameCount_returnsTrue() {
        assertThat(insight(10).isUpToDate(10)).isTrue();
    }

    @Test
    void isUpToDate_differentCount_returnsFalse() {
        assertThat(insight(10).isUpToDate(11)).isFalse();
    }

    @Test
    void isUpToDate_zeroCountMatchesZero_returnsTrue() {
        assertThat(insight(0).isUpToDate(0)).isTrue();
    }

    @Test
    void isUpToDate_nullRateCount_returnsFalseForAnyCount() {
        // Given: a row from before migration V2
        AiInsight legacy = insight(0);
        ReflectionTestUtils.setField(legacy, "rateCount", null);

        // When / Then
        assertThat(legacy.isUpToDate(0)).isFalse();
        assertThat(legacy.isUpToDate(10)).isFalse();
    }

    @Test
    void refresh_newValues_replacesContentModelAndCountButKeepsKey() {
        // Given
        AiInsight insight = insight(9);

        // When
        insight.refresh("new text", "new-model", 10);

        // Then
        assertThat(insight.getContent()).isEqualTo("new text");
        assertThat(insight.getModel()).isEqualTo("new-model");
        assertThat(insight.getRateCount()).isEqualTo(10);
        assertThat(insight.getPeriodFrom()).isEqualTo(FROM);
        assertThat(insight.getPeriodTo()).isEqualTo(TO);
        assertThat(insight.getCurrency().getCode()).isEqualTo("EUR");
        assertThat(insight.isUpToDate(10)).isTrue();
        assertThat(insight.isUpToDate(9)).isFalse();
    }

    @Test
    void refresh_afterCreation_createdAtDoesNotGoBackwards() {
        // Given
        AiInsight insight = insight(9);
        Instant before = insight.getCreatedAt();

        // When
        insight.refresh("new text", "new-model", 10);

        // Then
        assertThat(insight.getCreatedAt()).isAfterOrEqualTo(before);
    }
}
