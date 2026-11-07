package com.vlad.fxpulse.insight;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * AI provider settings, bound from {@code app.ai.*} in application.yml.
 *
 * @param baseUrl         Gemini REST base URL, e.g. https://generativelanguage.googleapis.com/v1beta
 * @param model           model id, e.g. gemini-3.5-flash-lite
 * @param apiKey          secret from the GEMINI_API_KEY env var; empty means "AI not configured"
 * @param maxOutputTokens upper bound for the length of one answer (keeps free-tier usage small)
 * @param maxGenerationsPerDay how many fresh AI calls the whole app may make per day (cache hits are free);
 *                             0 switches fresh generation off
 *
 * <p>{@code @Validated}: a bad value stops the app at startup. {@code apiKey} has no constraint on purpose:
 * an empty key is valid and means "AI disabled" (503), so the rest of the app still runs.
 */
@Validated
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        @NotBlank String baseUrl,
        @NotBlank String model,
        String apiKey,
        @Min(1) int maxOutputTokens,
        @NotNull @DurationMin(millis = 1) Duration connectTimeout,
        @NotNull @DurationMin(millis = 1) Duration readTimeout,
        @Min(0) int maxGenerationsPerDay) {

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * A record's generated toString() prints every field, so the key would end up in any log line or
     * error message that prints this object. Override it and show only whether a key is set.
     */
    @Override
    public String toString() {
        return "AiProperties[baseUrl=" + baseUrl + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "***" : "<not set>")
                + ", maxOutputTokens=" + maxOutputTokens
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", maxGenerationsPerDay=" + maxGenerationsPerDay + "]";
    }
}
