package com.vlad.fxpulse.ingestion;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import com.vlad.fxpulse.common.DateRange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.time.LocalDate;
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
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

    @Test
    void throwsOnBadRequest() {
        // Real NBP answer when a range exceeds the server limit: only 404 means "no data", other 4xx must surface
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(aResponse()
                .withStatus(400)
                .withHeader("Content-Type", "text/plain; charset=utf-8")
                .withBody("400 BadRequest - Przekroczony limit 367 dni / Limit of 367 days has been exceeded")));

        assertThatThrownBy(() -> client.fetchRates("EUR", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04")))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
    }

    @Test
    void throwsOnReadTimeout() {
        NbpClient impatientClient = new NbpClient(
                new NbpProperties(nbp.baseUrl(), 93, Duration.ofMillis(200), Duration.ofMillis(200)));
        // A short timeout against a longer delay tests the same thing without slowing the suite by seconds.
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*"))
                .willReturn(okJson("{\"rates\":[]}").withFixedDelay(1000)));

        assertThatThrownBy(() -> impatientClient.fetchRates("EUR", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04")))
                .isInstanceOf(ResourceAccessException.class);
    }

    @Test
    void throwsOnMalformedJson() {
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/json")
                .withBody("<html>not json</html>")));

        assertThatThrownBy(() -> client.fetchRates("EUR", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04")))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void returnsEmptyListWhenRatesAreNull() {
        nbp.stubFor(get(urlPathMatching("/exchangerates/rates/A/EUR/.*")).willReturn(okJson("""
                {"table":"A","currency":"euro","code":"EUR","rates":null}
                """)));

        List<NbpDtos.SeriesRate> rates = client.fetchRates("EUR", LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-04"));

        assertThat(rates).isEmpty();
    }

    @Test
    void keepsOtherChunksWhenOneChunkIsEmpty() {
        // 200 days -> 3 chunks; the middle one has no data (404), the outer ones have one rate each
        nbp.stubFor(get(urlEqualTo("/exchangerates/rates/A/EUR/2026-01-01/2026-04-03/?format=json")).willReturn(okJson("""
                {"table":"A","currency":"euro","code":"EUR","rates":[{"no":"001/A/NBP/2026","effectiveDate":"2026-01-02","mid":4.2156}]}
                """)));
        nbp.stubFor(get(urlEqualTo("/exchangerates/rates/A/EUR/2026-04-04/2026-07-05/?format=json")).willReturn(aResponse()
                .withStatus(404)
                .withHeader("Content-Type", "text/plain; charset=utf-8")
                .withBody("404 NotFound - Not Found - Brak danych")));
        nbp.stubFor(get(urlEqualTo("/exchangerates/rates/A/EUR/2026-07-06/2026-07-19/?format=json")).willReturn(okJson("""
                {"table":"A","currency":"euro","code":"EUR","rates":[{"no":"130/A/NBP/2026","effectiveDate":"2026-07-07","mid":4.2611}]}
                """)));

        List<NbpDtos.SeriesRate> rates = client.fetchRates("EUR", LocalDate.parse("2026-01-01"), LocalDate.parse("2026-07-19"));

        assertThat(rates).extracting(NbpDtos.SeriesRate::effectiveDate)
                .containsExactly(LocalDate.parse("2026-01-02"), LocalDate.parse("2026-07-07"));
        // WireMock answers an unmatched URL with 404, which the client also reads as "no data". So the
        // result alone can't prove the chunk boundaries; verify the exact three requests instead.
        nbp.verify(1, getRequestedFor(urlEqualTo("/exchangerates/rates/A/EUR/2026-01-01/2026-04-03/?format=json")));
        nbp.verify(1, getRequestedFor(urlEqualTo("/exchangerates/rates/A/EUR/2026-04-04/2026-07-05/?format=json")));
        nbp.verify(1, getRequestedFor(urlEqualTo("/exchangerates/rates/A/EUR/2026-07-06/2026-07-19/?format=json")));
        nbp.verify(3, getRequestedFor(urlPathMatching("/exchangerates/rates/A/EUR/.*")));
    }

    @Test
    void throwsWhenTableAIsEmpty() {
        nbp.stubFor(get(urlEqualTo("/exchangerates/tables/A/?format=json")).willReturn(okJson("[]")));

        assertThatThrownBy(() -> client.fetchCurrentTableA())
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("no table A");
    }

    @Test
    void throwsOnTableAServerError() {
        nbp.stubFor(get(urlEqualTo("/exchangerates/tables/A/?format=json")).willReturn(aResponse().withStatus(500)));

        assertThatThrownBy(() -> client.fetchCurrentTableA())
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void sendsJsonAcceptHeader() {
        nbp.stubFor(get(urlEqualTo("/exchangerates/tables/A/?format=json")).willReturn(okJson("""
                [{"table":"A","no":"217/A/NBP/2026","effectiveDate":"2026-11-06","rates":[]}]
                """)));

        client.fetchCurrentTableA();

        nbp.verify(getRequestedFor(urlEqualTo("/exchangerates/tables/A/?format=json"))
                .withHeader("Accept", equalTo("application/json")));
    }
}
