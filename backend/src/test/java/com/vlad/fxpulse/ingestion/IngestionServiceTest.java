package com.vlad.fxpulse.ingestion;

import com.vlad.fxpulse.currency.Currency;
import com.vlad.fxpulse.currency.CurrencyRepository;
import com.vlad.fxpulse.rate.ExchangeRate;
import com.vlad.fxpulse.rate.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClientException;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    // "Today" is fixed: 2026-11-06 in Warsaw
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneId.of("Europe/Warsaw"));
    private static final LocalDate TO = LocalDate.parse("2026-11-06");
    private static final LocalDate FROM = LocalDate.parse("2026-10-31"); // 7 days including today

    @Mock
    private NbpClient nbpClient;
    @Mock
    private CurrencyRepository currencyRepository;
    @Mock
    private ExchangeRateRepository exchangeRateRepository;
    @Captor
    private ArgumentCaptor<List<ExchangeRate>> savedRates;

    private IngestionService service;

    @BeforeEach
    void setUp() {
        service = new IngestionService(nbpClient, currencyRepository, exchangeRateRepository, CLOCK);
    }

    /** Table A with EUR and USD. Not in setUp: Mockito's strict stubs fail tests that never use a stub. */
    private void stubTableA() {
        when(nbpClient.fetchCurrentTableA()).thenReturn(new NbpDtos.Table("A", "217/A/NBP/2026", TO, List.of(
                new NbpDtos.TableRate("euro", "EUR", new BigDecimal("4.4014")),
                new NbpDtos.TableRate("dolar amerykański", "USD", new BigDecimal("3.9321")))));
    }

    @Test
    void firstRunStoresNewCurrenciesAndAllRates() {
        stubTableA();
        when(currencyRepository.findAllById(any())).thenReturn(List.of());
        when(currencyRepository.saveAll(anyIterable())).thenAnswer(invocation -> invocation.getArgument(0));
        when(nbpClient.fetchRates(eq("EUR"), eq(FROM), eq(TO))).thenReturn(twoRates("4.4185", "4.4233"));
        when(nbpClient.fetchRates(eq("USD"), eq(FROM), eq(TO))).thenReturn(twoRates("3.9100", "3.92"));
        when(exchangeRateRepository.findEffectiveDates(any(), eq(FROM), eq(TO))).thenReturn(Set.of());

        IngestionResult result = service.ingest(7);

        assertThat(result).isEqualTo(new IngestionResult(FROM, TO, 2, 4, 4, List.of()));
        verify(exchangeRateRepository, times(2)).saveAll(savedRates.capture());
        List<ExchangeRate> usdRates = savedRates.getAllValues().get(1);
        assertThat(usdRates).extracting(r -> r.getCurrency().getCode()).containsOnly("USD");
        // "3.92" from NBP is normalised to scale 4
        assertThat(usdRates.get(1).getMid()).hasToString("3.9200");
    }

    @Test
    void rerunSkipsStoredDatesAndExistingCurrencies() {
        stubTableA();
        Currency eur = new Currency("EUR", "euro");
        Currency usd = new Currency("USD", "dolar amerykański");
        when(currencyRepository.findAllById(any())).thenReturn(List.of(eur, usd));
        when(nbpClient.fetchRates(any(), eq(FROM), eq(TO))).thenReturn(twoRates("4.4185", "4.4233"));
        when(exchangeRateRepository.findEffectiveDates(any(), eq(FROM), eq(TO)))
                .thenReturn(Set.of(LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-03")));

        IngestionResult result = service.ingest(7);

        assertThat(result.ratesFetched()).isEqualTo(4);
        assertThat(result.ratesInserted()).isZero();
        verify(currencyRepository, never()).saveAll(anyIterable());
        verify(exchangeRateRepository, never()).saveAll(anyIterable());
    }

    @Test
    void failureOfOneCurrencyDoesNotStopTheOthers() {
        stubTableA();
        when(currencyRepository.findAllById(any())).thenReturn(List.of(
                new Currency("EUR", "euro"), new Currency("USD", "dolar amerykański")));
        when(nbpClient.fetchRates(eq("EUR"), eq(FROM), eq(TO))).thenThrow(new RestClientException("NBP timeout"));
        when(nbpClient.fetchRates(eq("USD"), eq(FROM), eq(TO))).thenReturn(twoRates("3.9100", "3.9200"));
        when(exchangeRateRepository.findEffectiveDates(eq("USD"), eq(FROM), eq(TO))).thenReturn(Set.of());

        IngestionResult result = service.ingest(7);

        assertThat(result.failedCurrencies()).containsExactly("EUR");
        assertThat(result.ratesInserted()).isEqualTo(2);
    }

    @Test
    void rejectsNonPositiveDays() {
        assertThatThrownBy(() -> service.ingest(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static List<NbpDtos.SeriesRate> twoRates(String firstMid, String secondMid) {
        return List.of(
                new NbpDtos.SeriesRate("213/A/NBP/2026", LocalDate.parse("2026-11-02"), new BigDecimal(firstMid)),
                new NbpDtos.SeriesRate("214/A/NBP/2026", LocalDate.parse("2026-11-03"), new BigDecimal(secondMid)));
    }
}
