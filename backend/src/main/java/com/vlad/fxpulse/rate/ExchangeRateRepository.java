package com.vlad.fxpulse.rate;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ExchangeRateRepository extends JpaRepository<ExchangeRate, Long> {

    /** Time series for one currency, both dates inclusive, oldest first. */
    List<ExchangeRate> findByCurrencyCodeAndEffectiveDateBetweenOrderByEffectiveDateAsc(
            String currencyCode, LocalDate from, LocalDate to);

    /**
     * The most recent rate on or before {@code date}. NBP publishes nothing on weekends and holidays,
     * so "the rate on Saturday" is the one from Friday (or earlier).
     */
    Optional<ExchangeRate> findFirstByCurrencyCodeAndEffectiveDateLessThanEqualOrderByEffectiveDateDesc(
            String currencyCode, LocalDate date);

    /**
     * Dates already stored for a currency in [from, to]. Ingestion uses this to skip existing rows
     * with one query per currency instead of one query per rate.
     */
    @Query("""
            select r.effectiveDate from ExchangeRate r
            where r.currency.code = :code and r.effectiveDate between :from and :to
            """)
    Set<LocalDate> findEffectiveDates(@Param("code") String currencyCode,
                                      @Param("from") LocalDate from,
                                      @Param("to") LocalDate to);
}
