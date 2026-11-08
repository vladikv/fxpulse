package com.vlad.fxpulse.insight;

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

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class AiInsightRepositoryTest {

    private static final LocalDate FROM = LocalDate.parse("2026-01-01");
    private static final LocalDate TO = LocalDate.parse("2026-03-31");

    @Autowired
    private AiInsightRepository aiInsightRepository;

    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private EntityManager entityManager;

    private Currency eur;

    @BeforeEach
    void setUp() {
        eur = currencyRepository.save(new Currency("EUR", "euro"));
    }

    @Test
    void findsInsightByCurrencyAndExactPeriod() {
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "EUR rose slightly.", "test-model", 10));
        entityManager.clear();

        AiInsight found = aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO).orElseThrow();

        assertThat(found.getContent()).isEqualTo("EUR rose slightly.");
        assertThat(found.getModel()).isEqualTo("test-model");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void returnsEmptyWhenPeriodDiffers() {
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "EUR rose slightly.", "test-model", 10));

        assertThat(aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO.minusDays(1))).isEmpty();
    }

    @Test
    void rejectsDuplicateInsightForSamePeriod() {
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "first", "test-model", 10));

        assertThatThrownBy(() -> aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "second", "test-model", 10)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void savesAndReadsBackRateCount() {
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "text", "test-model", 42));
        entityManager.clear();

        AiInsight found = aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO).orElseThrow();

        assertThat(found.getRateCount()).isEqualTo(42);
        assertThat(found.isUpToDate(42)).isTrue();
    }

    @Test
    void refreshedRowIsUpdatedInPlace() {
        // Given
        Long id = aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "old", "old-model", 9)).getId();
        entityManager.clear();
        AiInsight loaded = aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO).orElseThrow();

        // When
        loaded.refresh("new", "new-model", 10);
        aiInsightRepository.saveAndFlush(loaded);
        entityManager.clear();

        // Then
        AiInsight reloaded = aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO).orElseThrow();
        assertThat(reloaded.getId()).isEqualTo(id);
        assertThat(reloaded.getContent()).isEqualTo("new");
        assertThat(reloaded.getModel()).isEqualTo("new-model");
        assertThat(reloaded.getRateCount()).isEqualTo(10);
        assertThat(aiInsightRepository.count()).isEqualTo(1);
    }

    @Test
    void rowInsertedWithNullRateCount_loadsAsNeverUpToDate() {
        // Given: a row as it exists after migration V2 for data written before it
        entityManager.createNativeQuery("""
                INSERT INTO ai_insight (currency_code, period_from, period_to, content, model, created_at)
                VALUES ('EUR', :from, :to, 'legacy', 'old-model', now())
                """)
                .setParameter("from", FROM)
                .setParameter("to", TO)
                .executeUpdate();
        entityManager.clear();

        // When
        AiInsight found = aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO).orElseThrow();

        // Then
        assertThat(found.getRateCount()).isNull();
        assertThat(found.isUpToDate(0)).isFalse();
        assertThat(found.isUpToDate(10)).isFalse();
    }
}
