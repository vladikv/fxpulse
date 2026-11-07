package com.vlad.fxpulse.rate;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Response of GET /api/convert.
 *
 * @param rate         units of {@code to} per one unit of {@code from}, scale 6
 * @param result       amount converted into {@code to}, scale 2
 * @param fromRateDate date of the NBP rate used for {@code from}; null for PLN (it has no NBP rate)
 * @param toRateDate   date of the NBP rate used for {@code to}; null for PLN
 */
public record ConversionResponse(
        String from,
        String to,
        BigDecimal amount,
        BigDecimal result,
        BigDecimal rate,
        LocalDate fromRateDate,
        LocalDate toRateDate) {
}
