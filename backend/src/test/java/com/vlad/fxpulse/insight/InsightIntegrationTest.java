package com.vlad.fxpulse.insight;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import com.vlad.fxpulse.TestcontainersConfiguration;
import com.vlad.fxpulse.currency.Currency;
import com.vlad.fxpulse.currency.CurrencyRepository;
import com.vlad.fxpulse.rate.ExchangeRate;
import com.vlad.fxpulse.rate.ExchangeRateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End to end: HTTP -> InsightService -> stubbed Gemini (WireMock) -> real PostgreSQL (Testcontainers).
 * Proves the phase goal: the AI is called once per (currency, period); the second request is served from the DB.
 */
@SpringBootTest(properties = "app.ingestion.scheduler-enabled=false")
@AutoConfigureMockMvc
@Import({TestcontainersConfiguration.class, InsightIntegrationTest.FixedClockConfig.class})
class InsightIntegrationTest {

    private static final String MODEL = "gemini-it";
    private static final String GENERATE_PATH = "/models/gemini-it:generateContent";
    private static final String URL = "/api/insights/EUR?from=2026-11-02&to=2026-11-04";

    @RegisterExtension
    static WireMockExtension gemini = WireMockExtension.newInstance()
            // JDK HttpClient's h2c upgrade on POST makes WireMock's Jetty reset connections
            .options(wireMockConfig().dynamicPort().http2PlainDisabled(true))
            .build();

    @DynamicPropertySource
    static void geminiProperties(DynamicPropertyRegistry registry) {
        registry.add("app.ai.base-url", gemini::baseUrl);
        registry.add("app.ai.api-key", () -> "it-test-key");
        registry.add("app.ai.model", () -> MODEL);
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
    private MockMvc mockMvc;
    @Autowired
    private AiInsightRepository insightRepository;
    @Autowired
    private ExchangeRateRepository exchangeRateRepository;
    @Autowired
    private CurrencyRepository currencyRepository;

    @BeforeEach
    void setUp() {
        // order matters: foreign keys
        insightRepository.deleteAll();
        exchangeRateRepository.deleteAll();
        currencyRepository.deleteAll();

        Currency eur = currencyRepository.save(new Currency("EUR", "euro"));
        exchangeRateRepository.saveAll(List.of(
                new ExchangeRate(eur, LocalDate.parse("2026-11-02"), new BigDecimal("4.0000"), "213/A/NBP/2026"),
                new ExchangeRate(eur, LocalDate.parse("2026-11-03"), new BigDecimal("4.1234"), "214/A/NBP/2026"),
                new ExchangeRate(eur, LocalDate.parse("2026-11-04"), new BigDecimal("4.2500"), "215/A/NBP/2026")));

        gemini.resetAll();
    }

    private void stubGeminiOk() {
        gemini.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(okJson("""
                {"candidates":[{"content":{"role":"model","parts":[{"text":"EUR rose from 4.0 to 4.25 PLN.\\n"}]},
                  "finishReason":"STOP"}],"modelVersion":"gemini-it-001"}
                """)));
    }

    @Test
    void getInsight_twice_callsGeminiOnceAndServesSecondFromCache() throws Exception {
        // Given
        stubGeminiOk();

        // When / Then: first call generates and stores
        String firstBody = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("EUR"))
                .andExpect(jsonPath("$.from").value("2026-11-02"))
                .andExpect(jsonPath("$.to").value("2026-11-04"))
                .andExpect(jsonPath("$.content").value("EUR rose from 4.0 to 4.25 PLN."))
                .andExpect(jsonPath("$.model").value(MODEL))
                .andExpect(jsonPath("$.cached").value(false))
                .andReturn().getResponse().getContentAsString();
        String firstCreatedAt = JsonPath.read(firstBody, "$.createdAt");
        assertThat(insightRepository.count()).isEqualTo(1);

        // second identical call comes from the DB, with the exact same createdAt (microsecond precision)
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("EUR rose from 4.0 to 4.25 PLN."))
                .andExpect(jsonPath("$.createdAt").value(firstCreatedAt))
                .andExpect(jsonPath("$.cached").value(true));
        assertThat(insightRepository.count()).isEqualTo(1);
        gemini.verify(1, postRequestedFor(urlEqualTo(GENERATE_PATH)));
    }

    @Test
    void getInsight_geminiRateLimited_returns429AndStoresNothing() throws Exception {
        // Given
        gemini.stubFor(post(urlEqualTo(GENERATE_PATH)).willReturn(aResponse().withStatus(429).withBody("quota")));

        // When / Then
        mockMvc.perform(get(URL))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.error").value("Too Many Requests"))
                .andExpect(jsonPath("$.message").value("AI rate limit reached, try again later"));
        assertThat(insightRepository.count()).isZero();
    }

    @Test
    void getInsight_cacheMiss_sendsAggregatedStatsNotRawRates() throws Exception {
        // Given
        stubGeminiOk();

        // When
        mockMvc.perform(get(URL)).andExpect(status().isOk());

        // Then
        List<LoggedRequest> requests = gemini.findAll(postRequestedFor(urlEqualTo(GENERATE_PATH)));
        assertThat(requests).hasSize(1);
        String body = requests.getFirst().getBodyAsString();
        assertThat(body).contains("First rate: 4.0000 PLN", "Last rate: 4.2500 PLN", "3 business days");
        // the middle daily rate is neither first, last, min nor max, so it must not be sent
        assertThat(body).doesNotContain("4.1234");
    }

    @Test
    void getInsight_noRatesInRange_returns404AndNeverCallsGemini() throws Exception {
        // Given
        stubGeminiOk();

        // When / Then
        mockMvc.perform(get("/api/insights/EUR?from=2025-01-01&to=2025-01-10"))
                .andExpect(status().isNotFound());
        gemini.verify(0, postRequestedFor(urlEqualTo(GENERATE_PATH)));
        assertThat(insightRepository.count()).isZero();
    }
}
