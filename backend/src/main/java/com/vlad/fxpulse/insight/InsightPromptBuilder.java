package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.rate.RateStatsResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Fills the prompt template ({@code resources/prompts/trend-summary.txt}) with aggregated statistics.
 * Only about ten numbers go to the AI, never the raw daily rates, so prompts stay small and cheap.
 *
 * <p>The template lives in a resource file rather than a Java string, so the wording can be changed
 * without touching code. It is read once at startup; if the file is missing the app fails to start.
 */
@Component
public class InsightPromptBuilder {

    private final String template;

    public InsightPromptBuilder(@Value("classpath:prompts/trend-summary.txt") Resource templateResource) {
        try {
            this.template = templateResource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read prompt template " + templateResource, e);
        }
    }

    public String build(RateStatsResponse stats) {
        Map<String, String> values = Map.ofEntries(
                Map.entry("code", stats.code()),
                Map.entry("from", stats.from().toString()),
                Map.entry("to", stats.to().toString()),
                Map.entry("count", String.valueOf(stats.count())),
                Map.entry("first", plain(stats.first())),
                Map.entry("last", plain(stats.last())),
                Map.entry("min", plain(stats.min())),
                Map.entry("max", plain(stats.max())),
                Map.entry("avg", plain(stats.avg())),
                Map.entry("changePercent", plain(stats.changePercent())),
                Map.entry("volatility", plain(stats.volatility())));

        String prompt = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            prompt = prompt.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }
        return prompt;
    }

    /** toPlainString avoids scientific notation such as 1E-4 for very small values. */
    private static String plain(BigDecimal value) {
        return value == null ? "n/a" : value.toPlainString();
    }
}
