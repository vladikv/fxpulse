package com.vlad.fxpulse.rate;

import com.vlad.fxpulse.TestcontainersConfiguration;
import com.vlad.fxpulse.currency.Currency;
import com.vlad.fxpulse.currency.CurrencyRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class ExchangeRateRepositoryTest {

    @Autowired
    private ExchangeRateRepository exchangeRateRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private EntityManager entityManager;

    private Currency eur;
    private Currency usd;

    @BeforeEach
    void setUp() {
        eur = currencyRepository.save(new Currency("EUR", "euro"));
        usd = currencyRepository.save(new Currency("USD", "dolar amerykański"));
    }

    @Test
    void findsRatesInRangeOrderedByDate() {
        // saved out of order on purpose, plus rows outside the range and for another currency
        exchangeRateRepository.save(rate(eur, "2026-01-07", "4.2600"));
        exchangeRateRepository.save(rate(eur, "2026-01-05", "4.2500"));
        exchangeRateRepository.save(rate(eur, "2026-01-06", "4.2550"));
        exchangeRateRepository.save(rate(eur, "2026-01-02", "4.2400")); // before range
        exchangeRateRepository.save(rate(eur, "2026-01-09", "4.2700")); // after range
        exchangeRateRepository.save(rate(usd, "2026-01-06", "3.9000")); // other currency
        exchangeRateRepository.flush();

        List<ExchangeRate> result = exchangeRateRepository
                .findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                        "EUR", LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-07"));

        assertThat(result)
                .extracting(ExchangeRate::getEffectiveDate)
                .containsExactly(
                        LocalDate.parse("2026-01-05"),
                        LocalDate.parse("2026-01-06"),
                        LocalDate.parse("2026-01-07"));
    }

    @Test
    void keepsFourDecimalPlacesOfMidRate() {
        exchangeRateRepository.saveAndFlush(rate(eur, "2026-01-05", "4.2512"));
        entityManager.clear(); // read back from PostgreSQL, not from the persistence context

        ExchangeRate loaded = exchangeRateRepository
                .findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
                        "EUR", LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-05"))
                .getFirst();

        assertThat(loaded.getMid()).isEqualByComparingTo("4.2512");
        assertThat(loaded.getMid().scale()).isEqualTo(4);
        assertThat(loaded.getTableNo()).isEqualTo("001/A/NBP/2026");
    }

    @Test
    void rejectsDuplicateRateForSameCurrencyAndDate() {
        exchangeRateRepository.saveAndFlush(rate(eur, "2026-01-05", "4.2500"));

        assertThatThrownBy(() -> exchangeRateRepository.saveAndFlush(rate(eur, "2026-01-05", "4.3000")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void findsStoredDatesOnlyForGivenCurrencyAndRange() {
        exchangeRateRepository.save(rate(eur, "2026-01-05", "4.2500"));
        exchangeRateRepository.save(rate(eur, "2026-01-06", "4.2550"));
        exchangeRateRepository.save(rate(eur, "2026-01-09", "4.2700")); // outside range
        exchangeRateRepository.save(rate(usd, "2026-01-07", "3.9000")); // other currency
        exchangeRateRepository.flush();

        Set<LocalDate> dates = exchangeRateRepository.findEffectiveDates(
                "EUR", LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-08"));

        assertThat(dates).containsExactlyInAnyOrder(LocalDate.parse("2026-01-05"), LocalDate.parse("2026-01-06"));
    }

    private static ExchangeRate rate(Currency currency, String date, String mid) {
        return new ExchangeRate(currency, LocalDate.parse(date), new BigDecimal(mid), "001/A/NBP/2026");
    }
}
