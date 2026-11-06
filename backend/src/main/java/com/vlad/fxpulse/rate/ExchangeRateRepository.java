package com.vlad.fxpulse.rate;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /** Time series for one currency, both dates inclusive, oldest first. */
    List<ExchangeRate> findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            String currencyCode, LocalDate from, LocalDate to);

    /** Used by ingestion to skip rates that are already stored. */
    boolean existsByCurrencyCodeAndEffectiveDate(String currencyCode, LocalDate effectiveDate);
}
