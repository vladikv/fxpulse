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

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA; not for application code. */
    protected AiInsight() {
    }

    public AiInsight(Currency currency, LocalDate periodFrom, LocalDate periodTo, String content, String model) {
        this.currency = currency;
        this.periodFrom = periodFrom;
        this.periodTo = periodTo;
        this.content = content;
        this.model = model;
        this.createdAt = Instant.now();
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
