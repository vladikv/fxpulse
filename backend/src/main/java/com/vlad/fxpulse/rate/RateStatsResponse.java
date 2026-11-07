package com.vlad.fxpulse.rate;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Response of GET /api/rates/{code}/stats: the resolved range plus flat statistics fields. */
public record RateStatsResponse(
        String code,
        LocalDate from,
        LocalDate to,
        int count,
        BigDecimal min,
        BigDecimal max,
        BigDecimal avg,
        BigDecimal first,
        BigDecimal last,
        BigDecimal changePercent,
        BigDecimal volatility) {

    static RateStatsResponse of(String code, LocalDate from, LocalDate to, RateStatistics stats) {
        return new RateStatsResponse(code, from, to, stats.count(), stats.min(), stats.max(), stats.avg(),
                stats.first(), stats.last(), stats.changePercent(), stats.volatility());
    }
}
