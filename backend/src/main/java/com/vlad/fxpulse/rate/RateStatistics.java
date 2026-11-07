package com.vlad.fxpulse.rate;

import java.math.BigDecimal;

/**
 * Result of {@link StatsCalculator}. When there are no data points, {@code count} is 0
 * and every other field is {@code null}.
 *
 * @param changePercent (last - first) / first * 100, scale 2
 * @param volatility    population standard deviation of the mid rates, in PLN, scale 4
 */
public record RateStatistics(
        int count,
        BigDecimal min,
        BigDecimal max,
        BigDecimal avg,
        BigDecimal first,
        BigDecimal last,
        BigDecimal changePercent,
        BigDecimal volatility) {

    static final RateStatistics EMPTY = new RateStatistics(0, null, null, null, null, null, null, null);
}
