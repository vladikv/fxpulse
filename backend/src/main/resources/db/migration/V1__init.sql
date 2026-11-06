-- V1: initial schema for currencies, NBP exchange rates and cached AI insights.

CREATE TABLE currency (
    code        CHAR(3)      PRIMARY KEY,               -- ISO 4217 code, e.g. EUR
    name        VARCHAR(100) NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE exchange_rate (
    id              BIGSERIAL     PRIMARY KEY,
    currency_code   CHAR(3)       NOT NULL REFERENCES currency (code),
    effective_date  DATE          NOT NULL,
    mid             NUMERIC(12,4) NOT NULL,             -- NBP table A mid rate, 4 decimal places
    table_no        VARCHAR(20)   NOT NULL,             -- NBP table number, e.g. 215/A/NBP/2026
    -- Makes ingestion idempotent: one rate per currency per day.
    -- PostgreSQL backs this constraint with a unique B-tree index on (currency_code, effective_date),
    -- which also serves time-series queries, so no separate CREATE INDEX is needed.
    CONSTRAINT uq_exchange_rate_currency_date UNIQUE (currency_code, effective_date)
);

CREATE TABLE ai_insight (
    id             BIGSERIAL    PRIMARY KEY,
    currency_code  CHAR(3)      NOT NULL REFERENCES currency (code),
    period_from    DATE         NOT NULL,
    period_to      DATE         NOT NULL,
    content        TEXT         NOT NULL,
    model          VARCHAR(100) NOT NULL,               -- AI model that produced the text
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    -- Cache key: at most one insight per currency and period (also indexed by the constraint).
    CONSTRAINT uq_ai_insight_currency_period UNIQUE (currency_code, period_from, period_to),
    CONSTRAINT ck_ai_insight_period CHECK (period_from <= period_to)
);
