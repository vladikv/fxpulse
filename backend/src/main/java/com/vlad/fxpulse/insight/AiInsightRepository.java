package com.vlad.fxpulse.insight;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface AiInsightRepository extends JpaRepository<AiInsight, Long> {

    /** Cache lookup: the stored insight for exactly this currency and period, if any. */
    Optional<AiInsight> findByCurrencyCodeAndPeriodFromAndPeriodTo(
            String currencyCode, LocalDate periodFrom, LocalDate periodTo);
}
