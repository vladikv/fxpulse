package com.vlad.fxpulse.ingestion;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * An inclusive date range [from, to].
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from (" + from + ") must not be after to (" + to + ")");
        }
    }

    /** Number of days in the range, counting both ends (a single day has length 1). */
    public long lengthInDays() {
        return ChronoUnit.DAYS.between(from, to) + 1;
    }

    /**
     * Splits the range into consecutive chunks of at most {@code maxDays} days each,
     * with no gaps and no overlaps. Needed because NBP limits how long one range query may be.
     */
    public List<DateRange> split(int maxDays) {
        if (maxDays < 1) {
            throw new IllegalArgumentException("maxDays must be at least 1, was " + maxDays);
        }
        List<DateRange> chunks = new ArrayList<>();
        LocalDate chunkStart = from;
        while (!chunkStart.isAfter(to)) {
            LocalDate chunkEnd = chunkStart.plusDays(maxDays - 1L);
            if (chunkEnd.isAfter(to)) {
                chunkEnd = to;
            }
            chunks.add(new DateRange(chunkStart, chunkEnd));
            chunkStart = chunkEnd.plusDays(1);
        }
        return chunks;
    }
}
