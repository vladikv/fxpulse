package com.vlad.fxpulse.ingestion;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily job that keeps rates up to date. Only active when scheduling is enabled (see SchedulingConfig).
 * It re-checks the last few days, not just today, so a missed run (outage, holiday) is caught up automatically;
 * ingestion is idempotent, so re-checking costs nothing in the DB.
 */
@Component
public class IngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(IngestionScheduler.class);

    private final IngestionService ingestionService;
    private final IngestionProperties properties;

    public IngestionScheduler(IngestionService ingestionService, IngestionProperties properties) {
        this.ingestionService = ingestionService;
        this.properties = properties;
    }

    @Scheduled(cron = "${app.ingestion.cron}", zone = "Europe/Warsaw")
    public void runDailyIngestion() {
        try {
            ingestionService.ingest(properties.scheduledDays());
        } catch (RuntimeException e) {
            // e.g. NBP table A unreachable: log and wait for the next run instead of propagating
            log.error("Scheduled ingestion failed", e);
        }
    }
}
