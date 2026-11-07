package com.vlad.fxpulse.ingestion;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * NBP Web API settings, bound from {@code app.nbp.*} in application.yml.
 * {@code @Validated} makes a bad value fail at startup instead of on the first ingestion run.
 *
 * @param maxDaysPerRequest longest date range sent in one request; longer ranges are split into chunks
 */
@Validated
@ConfigurationProperties(prefix = "app.nbp")
public record NbpProperties(
        @NotBlank String baseUrl,
        // 367 is the hard limit the NBP server enforces (longer ranges return 400); the default stays 93,
        // the documented limit.
        @Min(1) @Max(367) int maxDaysPerRequest,
        // @NotNull alone would let 0s or -1s through, and the JDK HttpClient rejects those only when the
        // client is built or the first request is sent. @DurationMin (Hibernate Validator) requires > 0.
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout) {
}
