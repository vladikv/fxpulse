package com.vlad.fxpulse.ingestion;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final LocalDate START = LocalDate.parse("2026-01-01");

    @Test
    void singleDayIsOneChunk() {
        List<DateRange> chunks = new DateRange(START, START).split(93);

        assertThat(chunks).containsExactly(new DateRange(START, START));
    }

    @Test
    void rangeOfExactlyMaxDaysIsOneChunk() {
        DateRange range = new DateRange(START, START.plusDays(92)); // 93 days inclusive

        assertThat(range.lengthInDays()).isEqualTo(93);
        assertThat(range.split(93)).containsExactly(range);
    }

    @Test
    void oneDayOverMaxGivesTwoChunks() {
        List<DateRange> chunks = new DateRange(START, START.plusDays(93)).split(93); // 94 days

        assertThat(chunks).containsExactly(
                new DateRange(START, START.plusDays(92)),
                new DateRange(START.plusDays(93), START.plusDays(93)));
    }

    @Test
    void yearIsSplitIntoContiguousChunksWithoutGapsOrOverlaps() {
        DateRange year = new DateRange(START, START.plusDays(364)); // 365 days

        List<DateRange> chunks = year.split(93);

        assertThat(chunks).hasSize(4); // 93 + 93 + 93 + 86
        assertThat(chunks.getFirst().from()).isEqualTo(year.from());
        assertThat(chunks.getLast().to()).isEqualTo(year.to());
        for (int i = 1; i < chunks.size(); i++) {
            assertThat(chunks.get(i).from()).isEqualTo(chunks.get(i - 1).to().plusDays(1));
        }
        assertThat(chunks).allSatisfy(chunk -> assertThat(chunk.lengthInDays()).isLessThanOrEqualTo(93));
        assertThat(chunks.stream().mapToLong(DateRange::lengthInDays).sum()).isEqualTo(365);
    }

    @Test
    void rejectsFromAfterTo() {
        assertThatThrownBy(() -> new DateRange(START, START.minusDays(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonPositiveMaxDays() {
        assertThatThrownBy(() -> new DateRange(START, START).split(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
