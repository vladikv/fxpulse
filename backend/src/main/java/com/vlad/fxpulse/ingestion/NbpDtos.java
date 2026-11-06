package com.vlad.fxpulse.ingestion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * JSON shapes returned by the NBP Web API (table A). Internal to ingestion; never exposed by our REST API.
 * Rates are BigDecimal so Jackson keeps the exact decimal text (e.g. 2.7010), never a binary double.
 */
public final class NbpDtos {

    private NbpDtos() {
    }

    /** One element of {@code GET /exchangerates/tables/A/} (the response is a JSON array of these). */
    public record Table(String table, String no, LocalDate effectiveDate, List<TableRate> rates) {
    }

    /** A currency's rate inside a table; {@code currency} is the Polish name, e.g. "dolar amerykański". */
    public record TableRate(String currency, String code, BigDecimal mid) {
    }

    /** Response of {@code GET /exchangerates/rates/A/{code}/{from}/{to}/}. */
    public record Series(String table, String currency, String code, List<SeriesRate> rates) {
    }

    /** One day of a series; {@code no} is the table number, e.g. "215/A/NBP/2026". */
    public record SeriesRate(String no, LocalDate effectiveDate, BigDecimal mid) {
    }
}
