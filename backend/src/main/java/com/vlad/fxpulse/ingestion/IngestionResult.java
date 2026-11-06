package com.vlad.fxpulse.ingestion;

import java.time.LocalDate;
import java.util.List;

/**
 * Summary of one ingestion run, returned by the admin endpoint and logged by the scheduler.
 *
 * @param ratesFetched     rates received from NBP
 * @param ratesInserted    rates that were new and got stored (0 on a repeated run = idempotent)
 * @param failedCurrencies codes whose download or save failed; the other currencies were still ingested
 */
public record IngestionResult(
        LocalDate from,
        LocalDate to,
        int currencies,
        int ratesFetched,
        int ratesInserted,
        List<String> failedCurrencies) {
}
