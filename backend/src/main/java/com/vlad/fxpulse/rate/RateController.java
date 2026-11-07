package com.vlad.fxpulse.rate;

import jakarta.validation.constraints.Pattern;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Read API for stored NBP mid rates. Dates are ISO (yyyy-MM-dd); both are optional and inclusive.
 * The @Pattern check runs before the service is called; a mismatch becomes a 400 ApiError.
 */
@RestController
@RequestMapping("/api/rates")
public class RateController {

    private static final String CODE_REGEX = "[A-Za-z]{3}";
    private static final String CODE_MESSAGE = "must be a 3-letter currency code";

    private final RateService rateService;

    public RateController(RateService rateService) {
        this.rateService = rateService;
    }

    /** GET /api/rates/EUR?from=2026-01-01&to=2026-03-31 */
    @GetMapping("/{code}")
    public RateSeriesResponse series(
            @PathVariable @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return rateService.getSeries(code, from, to);
    }

    /** GET /api/rates/EUR/stats?from=2026-01-01&to=2026-03-31 */
    @GetMapping("/{code}/stats")
    public RateStatsResponse stats(
            @PathVariable @Pattern(regexp = CODE_REGEX, message = CODE_MESSAGE) String code,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return rateService.getStats(code, from, to);
    }
}
