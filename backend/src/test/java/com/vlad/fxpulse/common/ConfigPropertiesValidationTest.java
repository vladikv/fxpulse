package com.vlad.fxpulse.common;

import com.vlad.fxpulse.ingestion.IngestionProperties;
import com.vlad.fxpulse.ingestion.NbpProperties;
import com.vlad.fxpulse.insight.AiProperties;
import com.vlad.fxpulse.rate.RateProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.FieldError;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that every {@code app.*} settings record is validated at startup: a bad value stops the app
 * with a validation error on the right field, instead of failing later on the first request or job run.
 *
 * <p>ApplicationContextRunner starts a tiny context with only property binding + validation
 * (no DB, no web server), so these tests run in milliseconds.
 */
class ConfigPropertiesValidationTest {

    @Configuration
    @EnableConfigurationProperties({NbpProperties.class, IngestionProperties.class, RateProperties.class,
            AiProperties.class, CorsProperties.class})
    static class Config {
    }

    /** One valid value for every property; each test changes or removes exactly one of them. */
    private static final Map<String, String> VALID = new LinkedHashMap<>();

    static {
        VALID.put("app.nbp.base-url", "https://api.nbp.pl/api");
        VALID.put("app.nbp.max-days-per-request", "93");
        VALID.put("app.nbp.connect-timeout", "5s");
        VALID.put("app.nbp.read-timeout", "20s");
        VALID.put("app.ingestion.backfill-days", "365");
        VALID.put("app.ingestion.scheduled-days", "7");
        VALID.put("app.ingestion.cron", "0 30 12 * * MON-FRI");
        VALID.put("app.ingestion.scheduler-enabled", "false");
        VALID.put("app.rates.max-range-days", "366");
        VALID.put("app.rates.default-range-days", "30");
        VALID.put("app.ai.base-url", "https://generativelanguage.googleapis.com/v1beta");
        VALID.put("app.ai.model", "gemini-test");
        VALID.put("app.ai.api-key", "");   // empty is valid: it means "AI disabled", not a config error
        VALID.put("app.ai.max-output-tokens", "1024");
        VALID.put("app.ai.connect-timeout", "5s");
        VALID.put("app.ai.read-timeout", "30s");
        VALID.put("app.ai.max-generations-per-day", "50");
        VALID.put("app.cors.allowed-origins", "http://localhost:5173,https://fxpulse.example");
    }

    @Test
    void bindsValidProperties() {
        runner(VALID).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(NbpProperties.class).readTimeout()).isEqualTo(Duration.ofSeconds(20));
            assertThat(context.getBean(IngestionProperties.class).scheduledDays()).isEqualTo(7);
            assertThat(context.getBean(RateProperties.class).maxRangeDays()).isEqualTo(366);
            AiProperties ai = context.getBean(AiProperties.class);
            assertThat(ai.hasApiKey()).isFalse();
            assertThat(ai.maxGenerationsPerDay()).isEqualTo(50);
            assertThat(context.getBean(CorsProperties.class).allowedOrigins())
                    .containsExactly("http://localhost:5173", "https://fxpulse.example");
        });
    }

    @ParameterizedTest(name = "{0}={1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            // NBP
            "app.nbp.max-days-per-request | 0     | maxDaysPerRequest",
            "app.nbp.max-days-per-request | 400   | maxDaysPerRequest",
            "app.nbp.base-url             | ' '   | baseUrl",
            "app.nbp.connect-timeout      | 0s    | connectTimeout",
            "app.nbp.read-timeout         | -1s   | readTimeout",
            // ingestion
            "app.ingestion.backfill-days  | 0     | backfillDays",
            "app.ingestion.scheduled-days | 4000  | scheduledDays",
            "app.ingestion.cron           | ' '   | cron",
            // rates
            "app.rates.max-range-days     | 0     | maxRangeDays",
            "app.rates.default-range-days | 0     | defaultRangeDays",
            "app.rates.default-range-days | 366   | defaultRangeWithinMax",
            // AI
            "app.ai.base-url              | ' '   | baseUrl",
            "app.ai.model                 | ' '   | model",
            "app.ai.max-output-tokens     | 0     | maxOutputTokens",
            "app.ai.read-timeout          | 0s    | readTimeout",
            "app.ai.max-generations-per-day | -1  | maxGenerationsPerDay",
            // CORS
            "app.cors.allowed-origins     | ''    | allowedOrigins",
    })
    void failsAtStartupOnInvalidValue(String property, String value, String field) {
        Map<String, String> properties = new LinkedHashMap<>(VALID);
        properties.put(property, value);

        runner(properties).run(context -> assertValidationFailedOn(context, field));
    }

    @ParameterizedTest(name = "missing {0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "app.nbp.read-timeout   | readTimeout",
            "app.ai.connect-timeout | connectTimeout",
    })
    void failsAtStartupWhenTimeoutIsMissing(String property, String field) {
        Map<String, String> properties = new LinkedHashMap<>(VALID);
        properties.remove(property);

        runner(properties).run(context -> assertValidationFailedOn(context, field));
    }

    @Test
    void zeroDailyGenerationsIsAllowed() {
        // 0 is a valid way to switch fresh AI calls off while keeping cached insights.
        Map<String, String> properties = new LinkedHashMap<>(VALID);
        properties.put("app.ai.max-generations-per-day", "0");

        runner(properties).run(context -> assertThat(context).hasNotFailed());
    }

    private static ApplicationContextRunner runner(Map<String, String> properties) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(Config.class)
                .withPropertyValues(properties.entrySet().stream()
                        .map(e -> e.getKey() + "=" + e.getValue())
                        .toArray(String[]::new));
    }

    /**
     * Startup must fail because Bean Validation rejected exactly this field. Searching the cause chain for
     * {@link BindValidationException} (not just the field name in the stack trace) means a conversion error
     * or an unrelated exception mentioning the field cannot make the test pass.
     */
    private static void assertValidationFailedOn(AssertableApplicationContext context, String field) {
        assertThat(context).hasFailed();
        BindValidationException validation = findCause(context.getStartupFailure());
        assertThat(validation)
                .as("startup failure should be a validation error, but was: %s", context.getStartupFailure())
                .isNotNull();
        List<String> invalidFields = StreamSupport.stream(validation.getValidationErrors().spliterator(), false)
                .filter(FieldError.class::isInstance)
                .map(FieldError.class::cast)
                .map(FieldError::getField)
                .toList();
        // contains, not containsExactly: one bad value can break two rules (max-range-days=0 also breaks
        // "default < max").
        assertThat(invalidFields).contains(field);
    }

    private static BindValidationException findCause(Throwable failure) {
        for (Throwable t = failure; t != null; t = t.getCause()) {
            if (t instanceof BindValidationException bve) {
                return bve;
            }
        }
        return null;
    }
}
