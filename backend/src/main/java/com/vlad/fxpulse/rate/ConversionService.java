package com.vlad.fxpulse.rate;

import com.vlad.fxpulse.common.NotFoundException;
import com.vlad.fxpulse.currency.CurrencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;

/**
 * Converts between any two currencies using NBP mid rates. NBP quotes everything against PLN,
 * so EUR -> USD is a cross rate: 1 EUR = midEUR PLN, 1 USD = midUSD PLN, therefore
 * 1 EUR = midEUR / midUSD USD.
 */
@Service
@Transactional(readOnly = true)
public class ConversionService {

    /** The base currency of NBP table A; not stored in the DB, its rate to itself is 1. */
    static final String PLN = "PLN";

    static final int RESULT_SCALE = 2;
    static final int CROSS_RATE_SCALE = 6;

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyService currencyService;
    private final Clock clock;

    public ConversionService(ExchangeRateRepository exchangeRateRepository, CurrencyService currencyService,
                             Clock clock) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyService = currencyService;
        this.clock = clock;
    }

    /**
     * @param date use the latest rates published on or before this day; null means today (Warsaw)
     */
    public ConversionResponse convert(String from, String to, BigDecimal amount, LocalDate date) {
        String fromCode = normalize(from);
        String toCode = normalize(to);
        LocalDate onOrBefore = date != null ? date : LocalDate.now(clock);

        PlnRate fromRate = plnRate(fromCode, onOrBefore);
        PlnRate toRate = plnRate(toCode, onOrBefore);

        // amount * midFrom / midTo in one expression, rounded once. Rounding the cross rate first
        // and then multiplying would add a second rounding error that grows with the amount.
        BigDecimal result = amount.multiply(fromRate.mid())
                .divide(toRate.mid(), RESULT_SCALE, RoundingMode.HALF_UP);
        BigDecimal crossRate = fromRate.mid().divide(toRate.mid(), CROSS_RATE_SCALE, RoundingMode.HALF_UP);

        return new ConversionResponse(fromCode, toCode, amount, result, crossRate,
                fromRate.date(), toRate.date());
    }

    /** Upper-cases the code; PLN is always valid, any other code must be a known currency. */
    private String normalize(String code) {
        String normalized = code.toUpperCase(Locale.ROOT);
        if (!PLN.equals(normalized)) {
            currencyService.requireExists(normalized);
        }
        return normalized;
    }

    /** How many PLN one unit of {@code code} cost, using the latest rate on or before the date. */
    private PlnRate plnRate(String code, LocalDate onOrBefore) {
        if (PLN.equals(code)) {
            return new PlnRate(BigDecimal.ONE, null);
        }
        ExchangeRate rate = exchangeRateRepository
                .findFirstByCurrencyCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(code, onOrBefore)
                .orElseThrow(() -> new NotFoundException(
                        "No " + code + " rate on or before " + onOrBefore));
        return new PlnRate(rate.getMid(), rate.getEffectiveDate());
    }

    private record PlnRate(BigDecimal mid, LocalDate date) {
    }
}
