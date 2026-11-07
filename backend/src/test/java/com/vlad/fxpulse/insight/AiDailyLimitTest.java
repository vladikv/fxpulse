package com.vlad.fxpulse.insight;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class AiDailyLimitTest {

    /** A clock whose time the test can move. */
    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(String isoInstant) {
            this.now = Instant.parse(isoInstant);
        }

        void set(String isoInstant) {
            this.now = Instant.parse(isoInstant);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(now, zone);
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static AiDailyLimit limit(Clock clock, int max) {
        AiProperties properties = new AiProperties("http://localhost", "m", "k", 256,
                Duration.ofSeconds(2), Duration.ofSeconds(30), max);
        return new AiDailyLimit(clock, properties);
    }

    @Test
    void tryAcquire_belowLimit_allowsExactlyMaxThenRejects() {
        // Given
        AiDailyLimit limit = limit(new MutableClock("2026-11-07T12:00:00Z"), 3);

        // When
        boolean first = limit.tryAcquire();
        boolean second = limit.tryAcquire();
        boolean third = limit.tryAcquire();
        boolean fourth = limit.tryAcquire();

        // Then
        assertThat(first).isTrue();
        assertThat(second).isTrue();
        assertThat(third).isTrue();
        assertThat(fourth).isFalse();
    }

    @Test
    void tryAcquire_limitReached_keepsRejectingOnSameDay() {
        // Given
        AiDailyLimit limit = limit(new MutableClock("2026-11-07T12:00:00Z"), 1);
        limit.tryAcquire();

        // When / Then
        assertThat(limit.tryAcquire()).isFalse();
        assertThat(limit.tryAcquire()).isFalse();
    }

    @Test
    void tryAcquire_nextPacificDay_resetsCounter() {
        // Given
        MutableClock clock = new MutableClock("2026-11-07T12:00:00Z");
        AiDailyLimit limit = limit(clock, 1);
        limit.tryAcquire();
        assertThat(limit.tryAcquire()).isFalse();

        // When
        clock.set("2026-11-08T12:00:00Z");

        // Then
        assertThat(limit.tryAcquire()).isTrue();
        assertThat(limit.tryAcquire()).isFalse();
    }

    @Test
    void tryAcquire_justBeforePacificMidnight_stillSameDay() {
        // Given: PST is UTC-8, so Pacific midnight is 08:00Z; 07:59Z is still the previous Pacific day
        MutableClock clock = new MutableClock("2026-11-07T00:30:00Z");
        AiDailyLimit limit = limit(clock, 1);
        limit.tryAcquire();

        // When: UTC (and Warsaw) already changed day at 00:00Z / 23:00Z, but Pacific has not
        clock.set("2026-11-07T07:59:00Z");

        // Then
        assertThat(limit.tryAcquire()).isFalse();
    }

    @Test
    void tryAcquire_atPacificMidnight_resets() {
        // Given
        MutableClock clock = new MutableClock("2026-11-07T07:59:00Z");
        AiDailyLimit limit = limit(clock, 1);
        limit.tryAcquire();

        // When
        clock.set("2026-11-07T08:00:00Z");

        // Then
        assertThat(limit.tryAcquire()).isTrue();
    }

    @Test
    void tryAcquire_utcMidnightPassed_doesNotReset() {
        // Given: 2026-11-06T23:30Z is 15:30 Pacific on Nov 6
        MutableClock clock = new MutableClock("2026-11-06T23:30:00Z");
        AiDailyLimit limit = limit(clock, 1);
        limit.tryAcquire();

        // When: 00:30Z on Nov 7 is 16:30 Pacific on Nov 6, same Pacific day
        clock.set("2026-11-07T00:30:00Z");

        // Then
        assertThat(limit.tryAcquire()).isFalse();
    }

    @Test
    void tryAcquire_maxZero_alwaysRejects() {
        // Given
        AiDailyLimit limit = limit(new MutableClock("2026-11-07T12:00:00Z"), 0);

        // When / Then
        assertThat(limit.tryAcquire()).isFalse();
        assertThat(limit.tryAcquire()).isFalse();
    }
}
