package com.vlad.fxpulse.rate;

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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversionServiceTest {

    private static final ZoneId WARSAW = ZoneId.of("Europe/Warsaw");
    private static final LocalDate TODAY = LocalDate.parse("2026-11-07");      // a Saturday
    private static final LocalDate FRIDAY = LocalDate.parse("2026-11-06");

    @Mock
    private ExchangeRateRepository exchangeRateRepository;

    @Mock
    private CurrencyService currencyService;

    private ConversionService conversionService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(ZonedDateTime.of(TODAY.atTime(10, 0), WARSAW).toInstant(), WARSAW);
        conversionService = new ConversionService(exchangeRateRepository, currencyService, fixedClock);
    }

    @Test
    void convertsViaPlnCrossRateUsingLatestRateBeforeToday() {
        stubRate("EUR", TODAY, FRIDAY, "4.2500");
        stubRate("USD", TODAY, FRIDAY, "4.0000");

        ConversionResponse response = conversionService.convert("eur", "usd", new BigDecimal("100"), null);

        assertThat(response.from()).isEqualTo("EUR");
        assertThat(response.to()).isEqualTo("USD");
        assertThat(response.amount()).isEqualTo("100");
        assertThat(response.result()).isEqualTo("106.25");       // 100 * 4.25 / 4.00
        assertThat(response.rate()).isEqualTo("1.062500");
        assertThat(response.fromRateDate()).isEqualTo(FRIDAY);
        assertThat(response.toRateDate()).isEqualTo(FRIDAY);
    }

    @Test
    void usesRequestedDate() {
        LocalDate date = LocalDate.parse("2026-03-02");
        stubRate("EUR", date, date, "4.3000");
        stubRate("USD", date, date, "4.3000");

        ConversionResponse response = conversionService.convert("EUR", "USD", new BigDecimal("10"), date);

        assertThat(response.result()).isEqualTo("10.00");
        assertThat(response.rate()).isEqualTo("1.000000");
    }

    @Test
    void convertsFromPln() {
        stubRate("EUR", TODAY, FRIDAY, "4.2500");

        ConversionResponse response = conversionService.convert("PLN", "EUR", new BigDecimal("100"), null);

        assertThat(response.result()).isEqualTo("23.53");        // 100 / 4.25 = 23.5294...
        assertThat(response.rate()).isEqualTo("0.235294");
        assertThat(response.fromRateDate()).isNull();
        assertThat(response.toRateDate()).isEqualTo(FRIDAY);
        verify(currencyService, never()).requireExists("PLN");   // PLN is not stored in the DB
    }

    @Test
    void convertsToPln() {
        stubRate("EUR", TODAY, FRIDAY, "4.2500");

        ConversionResponse response = conversionService.convert("EUR", "pln", new BigDecimal("100"), null);

        assertThat(response.to()).isEqualTo("PLN");
        assertThat(response.result()).isEqualTo("425.00");
        assertThat(response.toRateDate()).isNull();
    }

    @Test
    void plnToPlnNeedsNoLookup() {
        ConversionResponse response = conversionService.convert("PLN", "PLN", new BigDecimal("5"), null);

        assertThat(response.result()).isEqualTo("5.00");
        assertThat(response.rate()).isEqualTo("1.000000");
        verifyNoInteractions(exchangeRateRepository, currencyService);
    }

    @Test
    void roundsOnceInsteadOfMultiplyingTheRoundedCrossRate() {
        stubRate("EUR", TODAY, FRIDAY, "4.3210");
        stubRate("USD", TODAY, FRIDAY, "3.7777");

        ConversionResponse response = conversionService.convert("EUR", "USD", new BigDecimal("1000000"), null);

        // exact: 4,321,000 / 3.7777 = 1,143,817.666...; via the rounded rate 1.143818 it would be 1,143,818.00
        assertThat(response.rate()).isEqualTo("1.143818");
        assertThat(response.result()).isEqualTo("1143817.67");
    }

    @Test
    void missingRateGivesNotFound() {
        LocalDate date = LocalDate.parse("2000-01-03");
        when(exchangeRateRepository
                .findFirstByCurrencyCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc("EUR", date))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> conversionService.convert("EUR", "PLN", BigDecimal.ONE, date))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("No EUR rate on or before 2000-01-03");
    }

    @Test
    void unknownCurrencyGivesNotFoundBeforeAnyRateLookup() {
        doThrow(new NotFoundException("Unknown currency: XYZ")).when(currencyService).requireExists("XYZ");

        assertThatThrownBy(() -> conversionService.convert("XYZ", "PLN", BigDecimal.ONE, null))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Unknown currency: XYZ");
        verify(exchangeRateRepository, never())
                .findFirstByCurrencyCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(anyString(), any());
    }

    private void stubRate(String code, LocalDate onOrBefore, LocalDate effectiveDate, String mid) {
        ExchangeRate rate = new ExchangeRate(new Currency(code, code), effectiveDate, new BigDecimal(mid),
                "216/A/NBP/2026");
        when(exchangeRateRepository
                .findFirstByCurrencyCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(code, onOrBefore))
                .thenReturn(Optional.of(rate));
    }
}
