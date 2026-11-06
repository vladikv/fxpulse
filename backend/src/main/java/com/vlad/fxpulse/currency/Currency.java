package com.vlad.fxpulse.currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * A currency from NBP table A, identified by its ISO 4217 code (e.g. EUR).
 */
@Entity
@Table(name = "currency")
public class Currency {

    // Column is CHAR(3) in V1__init.sql. A String maps to VARCHAR by default, and ddl-auto=validate
    // compares JDBC types, so we tell Hibernate explicitly that this is a fixed-length CHAR.
    @Id
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "code", length = 3)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** Required by JPA; not for application code. */
    protected Currency() {
    }

    public Currency(String code, String name) {
        this.code = code;
        this.name = name;
        // Hibernate inserts every mapped column, so the DB default would be overridden by NULL.
        this.createdAt = Instant.now();
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
