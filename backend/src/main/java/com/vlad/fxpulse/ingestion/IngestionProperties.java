package com.vlad.fxpulse.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Ingestion settings, bound from {@code app.ingestion.*} in application.yml.
 * {@code @Validated}: a bad value stops the app at startup instead of breaking the first run.
 *
 * @param backfillDays  default number of days loaded by the manual admin endpoint
 * @param scheduledDays number of days the daily scheduled job re-checks
 * @param cron          when the scheduled job runs (Europe/Warsaw time)
 */
@Validated
@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        // Same 1..3650 bounds as the ?days= parameter of POST /api/admin/ingest.
        @Min(1) @Max(3650) int backfillDays,
        @Min(1) @Max(3650) int scheduledDays,
        @NotBlank String cron,
        boolean schedulerEnabled) {
}
