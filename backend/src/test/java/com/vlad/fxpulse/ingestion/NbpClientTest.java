package com.vlad.fxpulse.ingestion;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vlad.fxpulse.common.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests NbpClient against a local WireMock server (no Spring context, no real internet).
 * Response bodies are trimmed copies of real NBP responses.
 */
class NbpClientTest {

    @RegisterExtension
    static WireMockExtension nbp = WireMockExtension.newInstance()
            .options(wireMockConfig().dynamicPort())
            .build();

    private NbpClient client;

    @BeforeEach
    void setUp() {
        client = new NbpClient(new NbpProperties(nbp.baseUrl(), 93, Duration.ofSeconds(2), Duration.ofSeconds(2)));
    }

    @Test
    void parsesCurrentTableAWithExactDecimals() {
        nbp.stubFor(get(urlEqualTo("/exchangerates/tables/A/?format=json")).willReturn(okJson("""
                [{"table":"A","no":"217/A/NBP/2026","effectiveDate":"2026-11-06","rates":[
                  {"currency":"dolar amerykański","code":"USD","mid":3.9321},
                  {"currency":"dolar australijski","code":"AUD","mid":2.7010}]}]
                """)));

        NbpDtos.Table table = client.fetchCurrentTableA();

        assertThat(table.no()).isEqualTo("217/A/NBP/2026");
        assertThat(table.effectiveDate()).isEqualTo(LocalDate.parse("2026-11-06"));
        assertThat(table.rates()).extracting(NbpDtos.TableRate::code).containsExactly("USD", "AUD");
        // BigDecimal keeps the trailing zero from the JSON text: no double rounding involved
        assertThat(table.rates().get(1).mid()).hasToString("2.7010");
    }

    @Test
    void parsesRatesForDateRange() {
        nbp.stubFor(get(urlEqualTo("/exchangerates/rates/A/EUR/2026-11-02/2026-11-04/?format=json")).willReturn(okJson("""
                {"table":"A","currency":"euro","code":"EUR","rates":[
                  {"no":"213/A/NBP/2026","effectiveDate":"2026-11-02","mid":4.4185},
                  {"no":"214/A/NBP/2026","effectiveDate":"2026-11-03","mid":4.4233},
                  {"no":"215/A/NBP/2026","effectiveDate":"2026-11-04","mid":4.4014}]}
                """)));

        List<NbpDtos.SeriesRate> rates = client.fetchRates("EUR", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04"));

        assertThat(rates).hasSize(3);
        assertThat(rates.getFirst().no()).isEqualTo("213/A/NBP/2026");
        assertThat(rates.getFirst().effectiveDate()).isEqualTo(LocalDate.parse("2026-11-02"));
        assertThat(rates.getFirst().mid()).isEqualByComparingTo("4.4185");
    }

    @Test
    void treatsNotFoundAsEmptyResult() {
        // Real NBP 404 for a weekend: plain text, not JSON
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "text/plain; charset=utf-8")
                .withBody("404 NotFound - Not Found - Brak danych")));

        List<NbpDtos.SeriesRate> rates = client.fetchRates("EUR", LocalDate.parse("2026-11-01"), LocalDate.parse("2026-11-01"));

        assertThat(rates).isEmpty();
    }

    @Test
    void splitsLongRangeIntoChunksOfMaxDays() {
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(okJson("""
                {"table":"A","currency":"euro","code":"EUR","rates":[{"no":"001/A/NBP/2026","effectiveDate":"2026-01-02","mid":4.2156}]}
                """)));

        // 200 days with max 93 per request -> 93 + 93 + 14
        List<NbpDtos.SeriesRate> rates = client.fetchRates("EUR", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-19"));

        assertThat(rates).hasSize(3); // one rate per stubbed chunk response
        nbp.verify(3, getRequestedFor(urlPathMatching("/exchangerates/rates/A/EUR/.*")));
        nbp.verify(getRequestedFor(urlEqualTo("/exchangerates/rates/A/EUR/2026-01-01/2026-04-03/?format=json")));
        nbp.verify(getRequestedFor(urlEqualTo("/exchangerates/rates/A/EUR/2026-04-04/2026-07-05/?format=json")));
        nbp.verify(getRequestedFor(urlEqualTo("/exchangerates/rates/A/EUR/2026-07-06/2026-07-19/?format=json")));
    }

    @Test
    void throwsOnServerError() {
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchRates("EUR", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04")))
                .isInstanceOf(RestClientException.class);
    }
}
