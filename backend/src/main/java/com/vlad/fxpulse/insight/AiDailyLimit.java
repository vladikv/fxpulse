package com.vlad.fxpulse.insight;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * A global cap on fresh AI calls per day. Cache hits never reach it, so it only limits new summaries.
 *
 * <p>The day is counted in Pacific time because Gemini's free-tier daily quota resets at midnight
 * Pacific; our counter resets at the same moment.
 *
 * <p>Trade-offs, on purpose: the counter lives in memory, so it resets on restart and is per instance
 * (fine for one Render instance). A failed AI call still uses up one slot, which also protects against
 * hammering Gemini while it is failing.
 */
@Component
public class AiDailyLimit {

    static final ZoneId QUOTA_ZONE = ZoneId.of("America/Los_Angeles");

    private final Clock clock;
    private final int maxPerDay;

    private LocalDate day;
    private int used;

    public AiDailyLimit(Clock clock, AiProperties properties) {
        this.clock = clock;
        this.maxPerDay = properties.maxGenerationsPerDay();
    }

    /**
     * Takes one slot for today. Returns false when today's slots are used up.
     * {@code synchronized} because two requests may ask at the same moment; it is held for nanoseconds only.
     */
    public synchronized boolean tryAcquire() {
        LocalDate today = LocalDate.now(clock.withZone(QUOTA_ZONE));
        if (!today.equals(day)) {
            day = today;
            used = 0;
        }
        if (used >= maxPerDay) {
            return false;
        }
        used++;
        return true;
    }
}
