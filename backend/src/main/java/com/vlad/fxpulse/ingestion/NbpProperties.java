package com.vlad.fxpulse.ingestion;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * NBP Web API settings, bound from {@code app.nbp.*} in application.yml.
 *
 * @param maxDaysPerRequest longest date range sent in one request; longer ranges are split into chunks
 */
@ConfigurationProperties(prefix = "app.nbp")
public record NbpProperties(
        String baseUrl,
        int maxDaysPerRequest,
        Duration connectTimeout,
        Duration readTimeout) {
}
