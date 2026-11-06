package com.vlad.fxpulse.ingestion;

import com.vlad.fxpulse.currency.Currency;
import com.vlad.fxpulse.currency.CurrencyRepository;
import com.vlad.fxpulse.rate.ExchangeRate;
import com.vlad.fxpulse.rate.ExchangeRateRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads NBP table A currencies and their mid rates into the database (extract -> transform -> load).
 *
 * <p>Idempotent: dates already stored are skipped, and the unique constraint on
 * (currency_code, effective_date) is the final guard. Re-running inserts nothing new.
 *
 * <p>No transaction spans the whole run on purpose: HTTP calls must not hold a DB transaction open.
 * Each {@code saveAll} runs in its own transaction, so one currency's failure doesn't undo the others.
 */
@Service
public class IngestionService {

    private static final Logger log = LoggerFactory.getLogger(IngestionService.class);
    private static final int MID_SCALE = 4;

    private final NbpClient nbpClient;
    private final CurrencyRepository currencyRepository;
    private final ExchangeRateRepository exchangeRateRepository;
    private final Clock clock;

    public IngestionService(NbpClient nbpClient,
                            CurrencyRepository currencyRepository,
                            ExchangeRateRepository exchangeRateRepository,
                            Clock clock) {
        this.nbpClient = nbpClient;
        this.currencyRepository = currencyRepository;
        this.exchangeRateRepository = exchangeRateRepository;
        this.clock = clock;
    }

    /** Ingests the last {@code days} days (including today) for every currency in NBP table A. */
    public IngestionResult ingest(int days) {
        if (days < 1) {
            throw new IllegalArgumentException("days must be at least 1, was " + days);
        }
        LocalDate to = LocalDate.now(clock);
        LocalDate from = to.minusDays(days - 1L);
        log.info("Ingestion started for {} days ({} .. {})", days, from, to);

        NbpDtos.Table table = nbpClient.fetchCurrentTableA();
        List<Currency> currencies = saveMissingCurrencies(table.rates());

        int fetched = 0;
        int inserted = 0;
        List<String> failed = new ArrayList<>();
        for (Currency currency : currencies) {
            try {
                List<NbpDtos.SeriesRate> rates = nbpClient.fetchRates(currency.getCode(), from, to);
                fetched += rates.size();
                inserted += saveNewRates(currency, rates, from, to);
            } catch (RestClientException | DataAccessException e) {
                log.warn("Ingestion failed for {}: {}", currency.getCode(), e.getMessage());
                failed.add(currency.getCode());
            }
        }

        IngestionResult result = new IngestionResult(from, to, currencies.size(), fetched, inserted, List.copyOf(failed));
        log.info("Ingestion finished: {}", result);
        return result;
    }

    /** Stores currencies that are not in the DB yet; returns all table A currencies in table order. */
    private List<Currency> saveMissingCurrencies(List<NbpDtos.TableRate> tableRates) {
        List<String> codes = tableRates.stream().map(NbpDtos.TableRate::code).toList();
        Map<String, Currency> existing = currencyRepository.findAllById(codes).stream()
                .collect(Collectors.toMap(Currency::getCode, Function.identity()));

        List<Currency> missing = tableRates.stream()
                .filter(rate -> !existing.containsKey(rate.code()))
                .map(rate -> new Currency(rate.code(), rate.currency()))
                .toList();
        Map<String, Currency> byCode = new LinkedHashMap<>(existing);
        if (!missing.isEmpty()) {
            currencyRepository.saveAll(missing).forEach(c -> byCode.put(c.getCode(), c));
            log.info("Added {} new currencies", missing.size());
        }
        return codes.stream().map(byCode::get).toList();
    }

    /** Saves only rates whose date is not stored yet; returns how many were inserted. */
    private int saveNewRates(Currency currency, List<NbpDtos.SeriesRate> rates, LocalDate from, LocalDate to) {
        Set<LocalDate> storedDates = exchangeRateRepository.findEffectiveDates(currency.getCode(), from, to);
        List<ExchangeRate> newRates = rates.stream()
                .filter(rate -> !storedDates.contains(rate.effectiveDate()))
                .map(rate -> new ExchangeRate(
                        currency,
                        rate.effectiveDate(),
                        rate.mid().setScale(MID_SCALE, RoundingMode.HALF_UP),
                        rate.no()))
                .toList();
        if (!newRates.isEmpty()) {
            exchangeRateRepository.saveAll(newRates);
        }
        return newRates.size();
    }
}
