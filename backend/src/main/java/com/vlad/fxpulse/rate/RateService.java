package com.vlad.fxpulse.rate;

import com.vlad.fxpulse.common.BadRequestException;
import com.vlad.fxpulse.common.DateRange;
import com.vlad.fxpulse.currency.CurrencyService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Service
@Transactional(readOnly = true)
public class RateService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final CurrencyService currencyService;
    private final RateProperties properties;
    private final Clock clock;

    public RateService(ExchangeRateRepository exchangeRateRepository, CurrencyService currencyService,
                       RateProperties properties, Clock clock) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.currencyService = currencyService;
        this.properties = properties;
        this.clock = clock;
    }

    /** Time series for one currency; from/to may be null and then get defaults. */
    public RateSeriesResponse getSeries(String code, LocalDate from, LocalDate to) {
        String normalizedCode = normalize(code);
        DateRange range = resolveRange(from, to);
        List<RatePoint> points = loadRates(normalizedCode, range).stream()
                .map(rate -> new RatePoint(rate.getEffectiveDate(), rate.getMid()))
                .toList();
        return new RateSeriesResponse(normalizedCode, range.from(), range.to(), points);
    }

    /** Statistics over the same series; an empty range gives count 0, not an error. */
    public RateStatsResponse getStats(String code, LocalDate from, LocalDate to) {
        String normalizedCode = normalize(code);
        DateRange range = resolveRange(from, to);
        List<BigDecimal> mids = loadRates(normalizedCode, range).stream()
                .map(ExchangeRate::getMid)
                .toList();
        return RateStatsResponse.of(normalizedCode, range.from(), range.to(), StatsCalculator.calculate(mids));
    }

    /** Accepts "eur" as well as "EUR" and fails with 404 for codes we have never ingested. */
    private String normalize(String code) {
        String normalized = code.toUpperCase(Locale.ROOT);
        currencyService.requireExists(normalized);
        return normalized;
    }

    /**
     * Fills in defaults (to = today in Warsaw, from = to minus default-range-days) and enforces
     * from <= to and the maximum range, so one request cannot ask for years of rows.
     */
    DateRange resolveRange(LocalDate from, LocalDate to) {
        LocalDate effectiveTo = to != null ? to : LocalDate.now(clock);
        LocalDate effectiveFrom = from != null ? from : effectiveTo.minusDays(properties.defaultRangeDays());

        if (effectiveFrom.isAfter(effectiveTo)) {
            throw new BadRequestException(
                    "'from' (" + effectiveFrom + ") must not be after 'to' (" + effectiveTo + ")");
        }
        DateRange range = new DateRange(effectiveFrom, effectiveTo);
        if (range.lengthInDays() > properties.maxRangeDays()) {
            throw new BadRequestException("Date range is " + range.lengthInDays()
                    + " days; the maximum is " + properties.maxRangeDays());
        }
        return range;
    }

    private List<ExchangeRate> loadRates(String code, DateRange range) {
        return exchangeRateRepository.findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                code, range.from(), range.to());
    }
}
