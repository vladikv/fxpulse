package com.vlad.fxpulse.insight;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * AI provider settings, bound from {@code app.ai.*} in application.yml.
 *
 * @param baseUrl         Gemini REST base URL, e.g. https://generativelanguage.googleapis.com/v1beta
 * @param model           model id, e.g. gemini-3.5-flash-lite
 * @param apiKey          secret from the GEMINI_API_KEY env var; empty means "AI not configured"
 * @param maxOutputTokens upper bound for the length of one answer (keeps free-tier usage small)
 */
@ConfigurationProperties(prefix = "app.ai")
public record AiProperties(
        String baseUrl,
        String model,
        String apiKey,
        int maxOutputTokens,
        Duration connectTimeout,
        Duration readTimeout) {

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
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout + "]";
    }
}
