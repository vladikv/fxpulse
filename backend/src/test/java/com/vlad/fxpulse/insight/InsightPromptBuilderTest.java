package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.rate.RateStatsResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;

import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InsightPromptBuilderTest {

    private final InsightPromptBuilder builder =
            new InsightPromptBuilder(new ClassPathResource("prompts/trend-summary.txt"));

    private static RateStatsResponse stats(BigDecimal volatility, BigDecimal changePercent) {
        return new RateStatsResponse("EUR", LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"), 21,
                new BigDecimal("4.2000"), new BigDecimal("4.4000"), new BigDecimal("4.3000"),
                new BigDecimal("4.2500"), new BigDecimal("4.3500"), changePercent, volatility);
    }

    @Test
    void build_fullStats_replacesAllPlaceholders() {
        // When
        String prompt = builder.build(stats(new BigDecimal("0.0500"), new BigDecimal("2.35")));

        // Then
        assertThat(prompt).doesNotContain("{{").doesNotContain("}}");
    }

    @Test
    void build_fullStats_containsAllValues() {
        // When
        String prompt = builder.build(stats(new BigDecimal("0.0500"), new BigDecimal("2.35")));

        // Then
        assertThat(prompt).contains("EUR", "2026-10-01", "2026-10-31", "21 business days",
                "First rate: 4.2500 PLN", "Last rate: 4.3500 PLN", "2.35 %",
                "Lowest: 4.2000 PLN", "highest: 4.4000 PLN", "average: 4.3000 PLN", "0.0500 PLN");
    }

    @Test
    void build_verySmallValue_usesPlainNotation() {
        // Given: new BigDecimal("0.0000001").toString() is "1E-7"
        BigDecimal tiny = new BigDecimal("0.0000001");

        // When
        String prompt = builder.build(stats(tiny, new BigDecimal("0.0001")));

        // Then
        assertThat(prompt).contains("0.0000001").contains("0.0001").doesNotContain("E-");
    }

    @Test
    void build_nullStatsFields_showsNotAvailable() {
        // Given: an empty series has no change and no volatility
        RateStatsResponse noStats = new RateStatsResponse("EUR", LocalDate.parse("2026-10-01"),
                LocalDate.parse("2026-10-31"), 0, null, null, null, null, null, null, null);

        // When
        String prompt = builder.build(noStats);

        // Then
        assertThat(prompt).contains("First rate: n/a PLN", "Change from first to last: n/a %",
                "Volatility (standard deviation of the daily rates): n/a PLN").doesNotContain("{{");
    }

    @Test
    void build_customTemplate_replacesRepeatedPlaceholder() {
        // Given
        InsightPromptBuilder custom = new InsightPromptBuilder(
                new ByteArrayResource("{{code}} and again {{code}}".getBytes()));

        // When
        String prompt = custom.build(stats(null, null));

        // Then
        assertThat(prompt).isEqualTo("EUR and again EUR");
    }

    @Test
    void constructor_missingResource_throws() {
        assertThatThrownBy(() -> new InsightPromptBuilder(new ClassPathResource("prompts/does-not-exist.txt")))
                .isInstanceOf(UncheckedIOException.class);
    }
}
