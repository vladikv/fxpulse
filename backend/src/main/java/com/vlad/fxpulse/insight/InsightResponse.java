package com.vlad.fxpulse.insight;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Response of GET /api/insights/{code}.
 *
 * @param cached true when the text came from the database instead of a fresh AI call
 */
public record InsightResponse(
        String code,
        LocalDate from,
        LocalDate to,
        String content,
        String model,
        Instant createdAt,
        boolean cached) {

    /** {@code code} is passed in, not read from the LAZY currency association (no extra query). */
    static InsightResponse of(String code, AiInsight insight, boolean cached) {
        return new InsightResponse(code, insight.getPeriodFrom(), insight.getPeriodTo(), insight.getContent(),
                insight.getModel(), insight.getCreatedAt(), cached);
    }
}
