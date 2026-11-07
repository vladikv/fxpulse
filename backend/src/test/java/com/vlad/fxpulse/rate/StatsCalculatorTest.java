package com.vlad.fxpulse.rate;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatsCalculatorTest {

    @Test
    void emptySeriesGivesCountZeroAndNullFields() {
        RateStatistics stats = StatsCalculator.calculate(List.of());

        assertThat(stats.count()).isZero();
        assertThat(stats.min()).isNull();
        assertThat(stats.max()).isNull();
        assertThat(stats.avg()).isNull();
        assertThat(stats.first()).isNull();
        assertThat(stats.last()).isNull();
        assertThat(stats.changePercent()).isNull();
        assertThat(stats.volatility()).isNull();
    }

    @Test
    void singlePointHasZeroChangeAndZeroVolatility() {
        RateStatistics stats = StatsCalculator.calculate(mids("4.2500"));

        assertThat(stats.count()).isEqualTo(1);
        assertThat(stats.min()).isEqualTo("4.2500");
        assertThat(stats.max()).isEqualTo("4.2500");
        assertThat(stats.avg()).isEqualTo("4.2500");
        assertThat(stats.first()).isEqualTo("4.2500");
        assertThat(stats.last()).isEqualTo("4.2500");
        assertThat(stats.changePercent()).isEqualTo("0.00");
        assertThat(stats.volatility()).isEqualTo("0.0000");
    }

    @Test
    void calculatesAllFieldsForSeveralPoints() {
        // mean 4.1; deviations -0.1, +0.1, 0 -> variance 0.02 / 3 -> std dev 0.08164...
        RateStatistics stats = StatsCalculator.calculate(mids("4.0000", "4.2000", "4.1000"));

        assertThat(stats.count()).isEqualTo(3);
        assertThat(stats.min()).isEqualTo("4.0000");
        assertThat(stats.max()).isEqualTo("4.2000");
        assertThat(stats.avg()).isEqualTo("4.1000");
        assertThat(stats.first()).isEqualTo("4.0000");   // oldest, not the smallest
        assertThat(stats.last()).isEqualTo("4.1000");    // newest, not the largest
        assertThat(stats.changePercent()).isEqualTo("2.50");
        assertThat(stats.volatility()).isEqualTo("0.0816");
    }

    @Test
    void negativeChangeWhenRateFalls() {
        RateStatistics stats = StatsCalculator.calculate(mids("4.0000", "3.9000"));

        assertThat(stats.changePercent()).isEqualTo("-2.50");
    }

    @Test
    void roundsHalfUpToOutputScale() {
        // avg 1.00005 and std dev 0.00005 sit exactly on the rounding boundary -> HALF_UP rounds up
        RateStatistics stats = StatsCalculator.calculate(mids("1.0000", "1.0001"));

        assertThat(stats.avg()).isEqualTo("1.0001");
        assertThat(stats.volatility()).isEqualTo("0.0001");
        assertThat(stats.changePercent()).isEqualTo("0.01");
    }

    private static List<BigDecimal> mids(String... values) {
        return Arrays.stream(values).map(BigDecimal::new).toList();
    }
}
