# Interview questions — FxPulse

Questions an interviewer is likely to ask about this codebase, collected from code reviews.
This file is updated after every `code-reviewer` run: new questions are added under their topic,
with the review they came from. Each question has short answer pointers. Say them in your own words.

Status: `[ ]` not practised yet, `[x]` I can answer it confidently without notes.

---

## Ingestion — NBP client

*Source: review of `NbpClient.java`, 2026-11-07 (PR #4)*

- [ ] **Why is a 404 from NBP treated as an empty list instead of an error? What is the downside, given that a 404 also means "unknown currency code"? How would you tell the two apart?**
  - NBP returns 404 ("Brak danych", plain text, not JSON) when a range has no business days, e.g. weekends and holidays. That is a normal situation, not a failure.
  - Downside: a typo like `XYZ` also gets a 404, so it looks like a successful run with no data.
  - Telling them apart: validate codes against the `tables/A/` list before fetching (we already take our codes from there), or log the 404 with the code and range so it shows up in the logs.
  - Only 404 is caught. Other 4xx/5xx still throw (see `throwsOnBadRequest`).

- [ ] **The server enforces 367 days, but you split ranges at 93. Why use the documented limit? Where does the splitting happen, and how do you know chunk boundaries have no gaps or overlaps?**
  - 93 is what NBP *documents*. 367 is what the server enforces *today* and could change without notice. Depending on the documented contract is safer.
  - The value is configurable (`app.nbp.max-days-per-request`) and validated at startup with `@Min(1) @Max(367)`.
  - The splitting lives in `DateRange.split()` and is called from `NbpClient.fetchRates()`, so every caller is protected.
  - Each chunk is inclusive `[from, to]`. The next chunk starts at `previousTo + 1` and the last chunk is clamped to `to`. This is covered by `DateRangeTest` and by `splitsLongRangeIntoChunksOfMaxDays` (93 + 93 + 14 days with the exact URLs).

- [ ] **A timeout or 5xx happens halfway through a multi-chunk backfill. What state is left, why is re-running safe, and would you add retries or backoff?**
  - Rates from the chunks already saved stay in the DB. The failed currency is reported in `IngestionResult.failedCurrencies`, and the other currencies continue.
  - Re-running is safe because ingestion is idempotent. The unique constraint on `(currency_code, effective_date)` means duplicates are skipped, not inserted twice.
  - No retries by design: the daily scheduler re-checks the last 7 days, so a missed day is picked up on the next run. Retries with backoff are possible (e.g. Spring Retry), but they add a dependency and complexity for little gain here.

- [ ] **Why does a bad configuration value (e.g. `max-days-per-request: 0`) fail at startup? How does that work?**
  - `NbpProperties` is a `@ConfigurationProperties` record with `@Validated` and Jakarta constraints. Spring validates it while binding, during context startup.
  - Fail fast: a broken config stops the deploy immediately, instead of failing hours later on the first scheduled run.
  - It is tested with `ApplicationContextRunner`, a tiny context with no DB or web server. The test checks that startup failed *because of the expected field*.

- [ ] **How do you test an HTTP client without hitting the real internet? Which failure modes do you cover?**
  - WireMock on a dynamic port, with response bodies copied from real NBP responses (including the plain-text 404).
  - Covered: happy path, 404 → empty, 400 and 500 → exception, read timeout → `ResourceAccessException`, malformed JSON, `rates: null`, a mix of empty and non-empty chunks, empty table A, and that the `Accept` header is sent.
  - No Spring context is needed: the client is built directly with `new NbpClient(properties)`, so the tests run fast.

- [ ] **Why do the exchange-rate DTOs use `BigDecimal` and not `double`? How do you prove nothing is lost?**
  - `double` is binary floating point and can't represent most decimal fractions exactly. Money must be exact.
  - Jackson parses the JSON number straight into `BigDecimal`, and even keeps the trailing zero: `2.7010` stays `2.7010` (asserted in `parsesCurrentTableAWithExactDecimals`).

- [ ] **Why does the client throw plain Spring `RestClientException`s instead of a custom `NbpClientException`?**
  - It keeps the code simple, the caller already catches `RestClientException`, and Spring's subclasses (`HttpClientErrorException.BadRequest`, `ResourceAccessException`) already say *what* went wrong.
  - Trade-off: the exception doesn't carry the currency or date range. `IngestionService` adds that context when it logs the failure. A custom exception would be the next step if more callers appeared.
