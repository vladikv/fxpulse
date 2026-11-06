package com.vlad.fxpulse.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.http.HttpClient;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin HTTP client for the NBP Web API (table A, mid rates). No API key needed.
 * Errors other than "no data" are thrown as {@link RestClientException}; the caller decides how to react.
 */
@Component
public class NbpClient {

    private static final Logger log = LoggerFactory.getLogger(NbpClient.class);

    private final RestClient restClient;
    private final int maxDaysPerRequest;

    public NbpClient(NbpProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        this.restClient = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
        this.maxDaysPerRequest = properties.maxDaysPerRequest();
    }

    /** Latest published table A: all currencies with their current mid rate. */
    public NbpDtos.Table fetchCurrentTableA() {
        log.debug("Fetching current NBP table A");
        // The endpoint returns a JSON array that always holds exactly one table.
        NbpDtos.Table[] tables = restClient.get()
                .uri("/exchangerates/tables/A/?format=json")
                .retrieve()
                .body(NbpDtos.Table[].class);
        if (tables == null || tables.length == 0) {
            throw new RestClientException("NBP returned no table A");
        }
        return tables[0];
    }

    /**
     * Mid rates of one currency for every business day in [from, to].
     * Long ranges are split into chunks of at most {@code maxDaysPerRequest} days (one request each).
     * Returns an empty list when NBP has no data (weekends, holidays).
     */
    public List<NbpDtos.SeriesRate> fetchRates(String currencyCode, LocalDate from, LocalDate to) {
        List<NbpDtos.SeriesRate> result = new ArrayList<>();
        for (DateRange chunk : new DateRange(from, to).split(maxDaysPerRequest)) {
            result.addAll(fetchRatesChunk(currencyCode, chunk));
        }
        return result;
    }

    private List<NbpDtos.SeriesRate> fetchRatesChunk(String currencyCode, DateRange chunk) {
        log.debug("Fetching NBP rates for {} from {} to {}", currencyCode, chunk.from(), chunk.to());
        try {
            NbpDtos.Series series = restClient.get()
                    .uri("/exchangerates/rates/A/{code}/{from}/{to}/?format=json",
                            currencyCode, chunk.from(), chunk.to())
                    .retrieve()
                    .body(NbpDtos.Series.class);
            return series == null || series.rates() == null ? List.of() : series.rates();
        } catch (HttpClientErrorException.NotFound e) {
            // NBP answers 404 ("Brak danych") when there are no rates in the range, e.g. a weekend.
            return List.of();
        }
    }
}
