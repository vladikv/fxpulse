package com.vlad.fxpulse.common;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class TimeConfig {

    /** NBP rate dates are Warsaw dates, so "today" is computed in that zone regardless of the server's zone. */
    public static final ZoneId NBP_ZONE = ZoneId.of("Europe/Warsaw");

    /** Injected instead of calling LocalDate.now() directly, so tests can use a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.system(NBP_ZONE);
    }
}
