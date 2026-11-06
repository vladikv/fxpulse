package com.vlad.fxpulse.common;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on @Scheduled jobs only when app.ingestion.scheduler-enabled=true,
 * so tests (and, if needed, a second instance) can run without the daily job.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.ingestion.scheduler-enabled", havingValue = "true")
public class SchedulingConfig {
}
