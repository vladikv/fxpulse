package com.vlad.fxpulse.rate;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Rate API settings, bound from {@code app.rates.*} in application.yml.
 * {@code @Validated}: a bad value stops the app at startup instead of rejecting every request later.
 *
 * @param maxRangeDays     longest [from, to] range one request may ask for (both ends counted)
 * @param defaultRangeDays how far back "from" goes when the client omits it
 */
@Validated
@ConfigurationProperties(prefix = "app.rates")
public record RateProperties(
        @Min(1) int maxRangeDays,
        @Min(1) int defaultRangeDays) {

    /**
     * Cross-field rule: the default range ({@code to - defaultRangeDays .. to}, both ends counted, so
     * defaultRangeDays + 1 days) must itself be allowed, or a request without dates would always get a 400.
     * Bean Validation checks {@code is...()} methods like properties, so this runs at startup too.
     */
    @AssertTrue(message = "default-range-days must be smaller than max-range-days")
    public boolean isDefaultRangeWithinMax() {
        return defaultRangeDays < maxRangeDays;
    }
}
