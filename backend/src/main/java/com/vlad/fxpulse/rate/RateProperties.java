package com.vlad.fxpulse.rate;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Rate API settings, bound from {@code app.rates.*} in application.yml.
 *
 * @param maxRangeDays     longest [from, to] range one request may ask for (both ends counted)
 * @param defaultRangeDays how far back "from" goes when the client omits it
 */
@ConfigurationProperties(prefix = "app.rates")
public record RateProperties(int maxRangeDays, int defaultRangeDays) {
}
