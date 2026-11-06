package com.vlad.fxpulse.currency;

import com.vlad.fxpulse.TestcontainersConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

// Runs Flyway V1 against real PostgreSQL; ddl-auto=validate then checks the entities match the schema.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
class CurrencyRepositoryTest {

    // Field injection is the standard way to get beans into JUnit test classes.
    @Autowired
    private CurrencyRepository currencyRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndLoadsCurrency() {
        currencyRepository.saveAndFlush(new Currency("EUR", "euro"));
        entityManager.clear(); // force the next read to hit the database, not the persistence context

        Currency loaded = currencyRepository.findById("EUR").orElseThrow();

        assertThat(loaded.getCode()).isEqualTo("EUR");
        assertThat(loaded.getName()).isEqualTo("euro");
        assertThat(loaded.getCreatedAt()).isNotNull();
    }

    @Test
    void returnsEmptyForUnknownCode() {
        assertThat(currencyRepository.findById("XXX")).isEmpty();
    }
}
