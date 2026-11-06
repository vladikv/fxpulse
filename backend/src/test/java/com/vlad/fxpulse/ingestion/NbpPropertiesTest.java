package com.vlad.fxpulse.ingestion;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks that invalid {@code app.nbp.*} values stop the application at startup.
 * ApplicationContextRunner starts a tiny context with only the binding + validation (no DB, no web server).
 */
class NbpPropertiesTest {

    @Configuration
    @EnableConfigurationProperties(NbpProperties.class)
    static class Config {
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
            .withUserConfiguration(Config.class)
            .withPropertyValues(
                    "app.nbp.base-url=https://api.nbp.pl/api",
                    "app.nbp.max-days-per-request=93",
                    "app.nbp.connect-timeout=5s",
                    "app.nbp.read-timeout=20s");

    @Test
    void bindsValidProperties() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            NbpProperties properties = context.getBean(NbpProperties.class);
            assertThat(properties.maxDaysPerRequest()).isEqualTo(93);
            assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(20));
        });
    }

    @Test
    void failsWhenMaxDaysIsZero() {
        runner.withPropertyValues("app.nbp.max-days-per-request=0")
                .run(context -> assertFailedOn(context, "maxDaysPerRequest"));
    }

    @Test
    void failsWhenMaxDaysExceedsServerLimit() {
        runner.withPropertyValues("app.nbp.max-days-per-request=400")
                .run(context -> assertFailedOn(context, "maxDaysPerRequest"));
    }

    @Test
    void failsWhenBaseUrlIsBlank() {
        runner.withPropertyValues("app.nbp.base-url=")
                .run(context -> assertFailedOn(context, "baseUrl"));
    }

    @Test
    void failsWhenTimeoutIsMissing() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                .withUserConfiguration(Config.class)
                .withPropertyValues(
                        "app.nbp.base-url=https://api.nbp.pl/api",
                        "app.nbp.max-days-per-request=93",
                        "app.nbp.connect-timeout=5s")
                .run(context -> assertFailedOn(context, "readTimeout"));
    }

    /** Startup must fail because of validation on this field, not for some unrelated reason. */
    private static void assertFailedOn(AssertableApplicationContext context, String field) {
        assertThat(context).hasFailed();
        assertThat(context.getStartupFailure()).hasStackTraceContaining(field);
    }
}
