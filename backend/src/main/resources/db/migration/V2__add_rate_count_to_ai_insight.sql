-- How many daily rates the summary was generated from. The cache key (currency, period_from, period_to)
-- can be requested before NBP has published the last day of the period (e.g. "to = today" at 09:00);
-- when more rates exist later, the stored text is stale and gets regenerated.
-- Nullable: rows created before this migration have an unknown count and are regenerated once.
ALTER TABLE ai_insight ADD COLUMN rate_count INTEGER;
