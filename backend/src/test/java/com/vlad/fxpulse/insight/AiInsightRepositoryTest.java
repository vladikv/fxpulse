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
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "EUR rose slightly.", "test-model"));
        entityManager.clear();

        AiInsight found = aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO).orElseThrow();

        assertThat(found.getContent()).isEqualTo("EUR rose slightly.");
        assertThat(found.getModel()).isEqualTo("test-model");
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void returnsEmptyWhenPeriodDiffers() {
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "EUR rose slightly.", "test-model"));

        assertThat(aiInsightRepository.findByCurrencyCodeAndPeriodFromAndPeriodTo("EUR", FROM, TO.minusDays(1))).isEmpty();
    }

    @Test
    void rejectsDuplicateInsightForSamePeriod() {
        aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "first", "test-model"));

        assertThatThrownBy(() -> aiInsightRepository.saveAndFlush(new AiInsight(eur, FROM, TO, "second", "test-model")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
