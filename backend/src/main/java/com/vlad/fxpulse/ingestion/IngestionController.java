package com.vlad.fxpulse.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Manual trigger for ingestion, e.g. the initial one-year backfill or the daily GitHub Actions job.
 * Protected by {@link com.vlad.fxpulse.common.AdminTokenInterceptor}: callers must send the
 * {@code X-Admin-Token} header, so this method itself contains no security code.
 */
@RestController
@RequestMapping("/api/admin")
public class IngestionController {

    private final IngestionService ingestionService;
    private final IngestionProperties properties;

    public IngestionController(IngestionService ingestionService, IngestionProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    /**
     * POST /api/admin/ingest?days=365 (header X-Admin-Token) - synchronous; a full year takes some seconds.
     * Spring validates the @Min/@Max constraints itself and answers 400 when they are violated.
     */
    @PostMapping("/ingest")
    public IngestionResult ingest(@RequestParam(required = false) @Min(1) @Max(3650) Integer days) {
        int effectiveDays = days != null ? days : properties.backfillDays();
        return ingestionService.ingest(effectiveDays);
    }
}
