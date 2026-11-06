package com.vlad.fxpulse.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Ingestion settings, bound from {@code app.ingestion.*} in application.yml.
 *
 * @param backfillDays  default number of days loaded by the manual admin endpoint
 * @param scheduledDays number of days the daily scheduled job re-checks
 * @param cron          when the scheduled job runs (Europe/Warsaw time)
 */
@ConfigurationProperties(prefix = "app.ingestion")
public record IngestionProperties(
        int backfillDays,
        int scheduledDays,
        String cron,
        boolean schedulerEnabled) {
}
