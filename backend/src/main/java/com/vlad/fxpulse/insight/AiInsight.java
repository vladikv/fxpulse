package com.vlad.fxpulse.insight;

import com.vlad.fxpulse.currency.Currency;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * An AI-generated trend summary, cached per (currency, period) so the AI is never called twice for the same key.
 */
@Entity
@Table(name = "ai_insight")
public class AiInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_code", nullable = false)   // type comes from Currency.code (CHAR(3))
    private Currency currency;

    @Column(name = "period_from", nullable = false)
    private LocalDate periodFrom;

    @Column(name = "period_to", nullable = false)
    private LocalDate periodTo;

    // TEXT in the DB; without columnDefinition ddl-auto=validate expects varchar(255).
    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "model", nullable = false, length = 100)
    private String model;

    /** Number of daily rates the text was generated from; null for rows older than migration V2. */
    @Column(name = "rate_count")
    private Integer rateCount;

    /** When the current text was generated (updated by {@link #refresh}). */
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA; not for application code. */
    protected AiInsight() {
    }

    public AiInsight(Currency currency, LocalDate periodFrom, LocalDate periodTo, String content, String model,
                     int rateCount) {
        this.currency = currency;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        setGenerated(content, model, rateCount);
    }

    /** Is this text based on exactly the rates that exist now? Rates are only ever added, never changed. */
    public boolean isUpToDate(int currentRateCount) {
        return rateCount != null && rateCount == currentRateCount;
    }

    /** Replaces a stale text with a new one for the same key (currency and period stay the same). */
    public void refresh(String content, String model, int rateCount) {
        setGenerated(content, model, rateCount);
    }

    private void setGenerated(String content, String model, int rateCount) {
        this.content = content;
        this.model = model;
        this.rateCount = rateCount;
        // Postgres timestamptz keeps microseconds; truncating here makes the fresh response show
        // the same value as the cached one read back from the DB later.
        this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    public Long getId() {
        return id;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getPeriodFrom() {
        return periodFrom;
    }

    public LocalDate getPeriodTo() {
        return periodTo;
    }

    public String getContent() {
        return content;
    }

    public String getModel() {
        return model;
    }

    public Integer getRateCount() {
        return rateCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
