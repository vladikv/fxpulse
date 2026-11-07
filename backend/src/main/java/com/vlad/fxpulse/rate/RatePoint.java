package com.vlad.fxpulse.rate;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One point of a time series: the PLN mid rate on a given day. */
public record RatePoint(LocalDate date, BigDecimal mid) {
}
