package com.vlad.fxpulse.rate;

import java.time.LocalDate;
import java.util.List;

/**
 * Response of GET /api/rates/{code}. Echoes the resolved range, because the client may have
 * omitted from/to and should still know which period the points cover.
 */
public record RateSeriesResponse(String code, LocalDate from, LocalDate to, List<RatePoint> rates) {
}
