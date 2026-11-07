package com.vlad.fxpulse.insight;

import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * AI trend summaries. GET even though the first call creates a row: for the client it is a read,
 * and repeating it returns the same stored text (the cache makes it idempotent).
 */
@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final InsightService insightService;

    public InsightController(InsightService insightService) {
        this.insightService = insightService;
    }

    /** GET /api/insights/EUR?from=2026-10-01&to=2026-10-31 (dates optional, same defaults as /api/rates). */
    @GetMapping("/{code}")
    public InsightResponse insight(
            @PathVariable @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3-letter currency code") String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return insightService.getInsight(code, from, to);
    }
}
