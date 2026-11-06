package com.vlad.fxpulse.rate;

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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One NBP table A mid rate: how many PLN one unit of the currency cost on a given day.
 */
@Entity
@Table(name = "exchange_rate")
public class ExchangeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // BIGSERIAL: the DB generates the id
    private Long id;

    // LAZY: a time series has many rows for the same currency; don't load it for each row.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "currency_code", nullable = false)   // type comes from Currency.code (CHAR(3))
    private Currency currency;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "mid", nullable = false, precision = 12, scale = 4)
    private BigDecimal mid;

    @Column(name = "table_no", nullable = false, length = 20)
    private String tableNo;

    /** Required by JPA; not for application code. */
    protected ExchangeRate() {
    }

    public ExchangeRate(Currency currency, LocalDate effectiveDate, BigDecimal mid, String tableNo) {
        this.currency = currency;
        this.effectiveDate = effectiveDate;
        this.mid = mid;
        this.tableNo = tableNo;
    }

    public Long getId() {
        return id;
    }

    public Currency getCurrency() {
        return currency;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public BigDecimal getMid() {
        return mid;
    }

    public String getTableNo() {
        return tableNo;
    }
}
