package com.vlad.fxpulse.rate;

import com.vlad.fxpulse.common.BadRequestException;
import com.vlad.fxpulse.common.NotFoundException;
import com.vlad.fxpulse.currency.Currency;
import com.vlad.fxpulse.currency.CurrencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-06");

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyService currencyService;

    private RateService rateService;

    private final Currency eur = new Currency("EUR", "euro");

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(ZonedDateTime.of(TODAY.atTime(10, 0), WARSAW).toInstant(), WARSAW);
        rateService = new RateService(exchangeRateRepository, currencyService,
                new RateProperties(366, 30), fixedClock);
    }

    @Test
    void seriesUsesDefaultRangeAndUpperCasesCode() {
        LocalDate expectedFrom = TODAY.minusDays(30);
        when(exchangeRateRepository.findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                "EUR", expectedFrom, TODAY))
                .thenReturn(List.of(rate("2026-11-05", "4.2500"), rate("2026-11-06", "4.2600")));

        RateSeriesResponse response = rateService.getSeries("eur", null, null);

        verify(currencyService).requireExists("EUR");
        assertThat(response.code()).isEqualTo("EUR");
        assertThat(response.from()).isEqualTo(expectedFrom);
        assertThat(response.to()).isEqualTo(TODAY);
        assertThat(response.rates()).containsExactly(
                new RatePoint(LocalDate.parse("2026-11-05"), new BigDecimal("4.2500")),
                new RatePoint(LocalDate.parse("2026-11-06"), new BigDecimal("4.2600")));
    }

    @Test
    void defaultFromIsCountedBackFromGivenTo() {
        LocalDate to = LocalDate.parse("2026-06-30");

        RateSeriesResponse response = rateService.getSeries("EUR", null, to);

        assertThat(response.from()).isEqualTo(to.minusDays(30));
        assertThat(response.to()).isEqualTo(to);
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> rateService.getSeries("EUR",
                LocalDate.parse("2026-02-01"), LocalDate.parse("2026-01-01")))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("must not be after");

        verifyNoRatesLoaded();
    }

    @Test
    void acceptsRangeOfExactlyMaxDays() {
        // 2025-01-01..2026-01-01 inclusive = 366 days
        RateSeriesResponse response = rateService.getSeries("EUR",
                LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-01"));

        assertThat(response.rates()).isEmpty();
    }

    @Test
    void rejectsRangeLongerThanMaxDays() {
        assertThatThrownBy(() -> rateService.getSeries("EUR",
                LocalDate.parse("2025-01-01"), LocalDate.parse("2026-01-02")))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Date range is 367 days; the maximum is 366");

        verifyNoRatesLoaded();
    }

    @Test
    void unknownCurrencyPropagatesNotFound() {
        doThrow(new NotFoundException("Unknown currency: XYZ")).when(currencyService).requireExists("XYZ");

        assertThatThrownBy(() -> rateService.getStats("xyz", null, null))
                .isInstanceOf(NotFoundException.class);

        verifyNoRatesLoaded();
    }

    @Test
    void statsAreCalculatedFromStoredRates() {
        LocalDate from = LocalDate.parse("2026-11-01");
        when(exchangeRateRepository.findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                "EUR", from, TODAY))
                .thenReturn(List.of(rate("2026-11-05", "4.0000"), rate("2026-11-06", "4.2000")));

        RateStatsResponse stats = rateService.getStats("EUR", from, null);

        assertThat(stats.code()).isEqualTo("EUR");
        assertThat(stats.from()).isEqualTo(from);
        assertThat(stats.to()).isEqualTo(TODAY);
        assertThat(stats.count()).isEqualTo(2);
        assertThat(stats.first()).isEqualTo("4.0000");
        assertThat(stats.last()).isEqualTo("4.2000");
        assertThat(stats.changePercent()).isEqualTo("5.00");
    }

    @Test
    void statsForRangeWithoutRatesHaveCountZero() {
        RateStatsResponse stats = rateService.getStats("EUR", null, null);

        assertThat(stats.count()).isZero();
        assertThat(stats.avg()).isNull();
    }

    private void verifyNoRatesLoaded() {
        verify(exchangeRateRepository, never())
                .findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(anyString(), any(), any());
    }

    private ExchangeRate rate(String date, String mid) {
        return new ExchangeRate(eur, LocalDate.parse(date), new BigDecimal(mid), "001/A/NBP/2026");
    }
}
