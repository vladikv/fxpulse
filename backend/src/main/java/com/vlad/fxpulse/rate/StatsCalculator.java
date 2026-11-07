package com.vlad.fxpulse.rate;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;

/**
 * Descriptive statistics for a series of mid rates. A pure function with no Spring and no DB,
 * so it can be unit tested with plain lists.
 */
public final class StatsCalculator {

    /** Same scale as NBP mid rates. */
    static final int RATE_SCALE = 4;
    static final int PERCENT_SCALE = 2;

    /** 16 significant digits for intermediate results; rounding to the output scale happens once at the end. */
    private static final MathContext PRECISION = MathContext.DECIMAL64;

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private StatsCalculator() {
    }

    /**
     * @param mids mid rates ordered oldest first (first/last depend on this order)
     */
    public static RateStatistics calculate(List<BigDecimal> mids) {
        if (mids.isEmpty()) {
            return RateStatistics.EMPTY;
        }

        BigDecimal first = mids.getFirst();
        BigDecimal last = mids.getLast();
        BigDecimal min = mids.stream().min(Comparator.naturalOrder()).orElseThrow();
        BigDecimal max = mids.stream().max(Comparator.naturalOrder()).orElseThrow();

        BigDecimal count = BigDecimal.valueOf(mids.size());
        BigDecimal sum = mids.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal mean = sum.divide(count, PRECISION);

        // Population variance: average squared distance from the mean (divide by n, not n - 1).
        BigDecimal variance = mids.stream()
                .map(mid -> mid.subtract(mean).pow(2))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(count, PRECISION);
        BigDecimal stdDev = variance.sqrt(PRECISION);

        return new RateStatistics(
                mids.size(),
                min,
                max,
                mean.setScale(RATE_SCALE, RoundingMode.HALF_UP),
                first,
                last,
                changePercent(first, last),
                stdDev.setScale(RATE_SCALE, RoundingMode.HALF_UP));
    }

    private static BigDecimal changePercent(BigDecimal first, BigDecimal last) {
        if (first.signum() == 0) {
            return null; // not defined; NBP never publishes a zero rate, but don't divide by zero
        }
        return last.subtract(first)
                .multiply(HUNDRED)
                .divide(first, PERCENT_SCALE, RoundingMode.HALF_UP);
    }
}
