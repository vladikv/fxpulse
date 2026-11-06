package com.vlad.fxpulse.ingestion;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vlad.fxpulse.TestcontainersConfiguration;
import com.vlad.fxpulse.currency.CurrencyRepository;
import com.vlad.fxpulse.rate.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * End to end: stubbed NBP (WireMock) -> IngestionService -> real PostgreSQL (Testcontainers).
 * Proves the phase goal: a second run with the same data inserts nothing.
 */
@SpringBootTest(properties = "app.ingestion.scheduler-enabled=false")
@Import({TestcontainersConfiguration.class, IngestionIntegrationTest.FixedClockConfig.class})
class IngestionIntegrationTest {

    @RegisterExtension
    static WireMockExtension nbp = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    @DynamicPropertySource
    static void nbpUrl(DynamicPropertyRegistry registry) {
        registry.add("app.nbp.base-url", nbp::baseUrl);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class FixedClockConfig {
        @Bean
        @Primary
        Clock fixedClock() {
            return Clock.fixed(Instant.parse("2026-11-06T12:00:00Z"), ZoneId.of("Europe/Warsaw"));
        }
    }

    @Autowired
    private IngestionService ingestionService;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;
    @Autowired
    private CurrencyRepository currencyRepository;

    @BeforeEach
    void setUp() {
        exchangeRateRepository.deleteAll();
        currencyRepository.deleteAll();

        nbp.stubFor(get(urlEqualTo("/exchangerates/tables/A/?format=json")).willReturn(okJson("""
                [{"table":"A","no":"217/A/NBP/2026","effectiveDate":"2026-11-06","rates":[
                  {"currency":"euro","code":"EUR","mid":4.4014},
                  {"currency":"dolar amerykański","code":"USD","mid":3.9321}]}]
                """)));
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(okJson("""
                {"table":"A","currency":"euro","code":"EUR","rates":[
                  {"no":"213/A/NBP/2026","effectiveDate":"2026-11-02","mid":4.4185},
                  {"no":"214/A/NBP/2026","effectiveDate":"2026-11-03","mid":4.4233},
                  {"no":"215/A/NBP/2026","effectiveDate":"2026-11-04","mid":4.4014}]}
                """)));
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/USD/.*")).willReturn(okJson("""
                {"table":"A","currency":"dolar amerykański","code":"USD","rates":[
                  {"no":"213/A/NBP/2026","effectiveDate":"2026-11-02","mid":3.9100},
                  {"no":"214/A/NBP/2026","effectiveDate":"2026-11-03","mid":3.9200}]}
                """)));
    }

    @Test
    void secondRunInsertsNothing() {
        IngestionResult first = ingestionService.ingest(30);
        IngestionResult second = ingestionService.ingest(30);

        assertThat(first.ratesInserted()).isEqualTo(5);
        assertThat(second.ratesFetched()).isEqualTo(5);
        assertThat(second.ratesInserted()).isZero();
        assertThat(second.failedCurrencies()).isEmpty();
        assertThat(currencyRepository.count()).isEqualTo(2);
        assertThat(exchangeRateRepository.count()).isEqualTo(5);
    }
}
