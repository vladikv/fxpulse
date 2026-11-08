# PLAN.md — FxPulse

Goal: a deployed full-stack app (Java + SQL + React + AI + cloud) that matches Xelerit's internship profile,
plus a practiced, explainable AI-assisted workflow with Claude Code.

Target: MVP (Phases 0–5) in ~2–3 days of focused work. Phases 6–8 are polish.

---

## Phase 0 — Setup
- [x] Repo structure: `/backend`, `/frontend`, `/docs`, `.gitignore`, `README.md` skeleton
- [x] Spring Boot project in `/backend` (Java 21, Maven wrapper, Web, Data JPA, Validation, Actuator, Flyway, PostgreSQL driver)
- [x] `docker-compose.yml` with `db` service (postgres:16, volume, healthcheck)
- [x] `application.yml` reading DB URL/user/password from env with local defaults
- [x] `.env.example` (DB vars, `GEMINI_API_KEY=`)
- [x] App starts and `/actuator/health` returns UP
- **Done when:** `docker compose up -d db` + `.\mvnw spring-boot:run` works, first commit pushed.

## Phase 1 — Data model
- [x] `V1__init.sql`:
  - `currency(code CHAR(3) PK, name VARCHAR, created_at)`
  - `exchange_rate(id BIGSERIAL PK, currency_code FK, effective_date DATE, mid NUMERIC(12,4), table_no VARCHAR, UNIQUE(currency_code, effective_date))`
  - `ai_insight(id BIGSERIAL PK, currency_code FK, period_from DATE, period_to DATE, content TEXT, model VARCHAR, created_at, UNIQUE(currency_code, period_from, period_to))`
  - index on `exchange_rate(currency_code, effective_date)` — provided by the UNIQUE constraint's B-tree index (no separate `CREATE INDEX`, it would be a duplicate)
- [x] JPA entities + repositories
- [x] Repository integration tests with Testcontainers PostgreSQL
- **Done when:** Flyway applies migration on startup; repository integration test with Testcontainers passes.

## Phase 2 — NBP ingestion (ETL)
- [x] `NbpClient` (RestClient): fetch table A (current), fetch rates for a currency in a date range
- [x] Split ranges into chunks (configurable, default 93 days = documented limit; server enforces 367); treat 404 as empty result
- [x] `IngestionService`: load currency list, backfill last N days (configurable, e.g. 365), idempotent save
- [x] `IngestionScheduler`: `@Scheduled` daily run on workdays (cron in config)
- [x] `POST /api/admin/ingest?days=365` to trigger manually
- [x] Tests: WireMock stubs for NBP (normal, 404, chunking), unit test for idempotency, end-to-end ingestion test (WireMock + Testcontainers)
- **Done when:** after triggering ingestion the DB contains ~a year of rates for all table A currencies, re-running adds no duplicates.

## Phase 3 — REST API
- [x] `GET /api/currencies` — list
- [x] `GET /api/rates/{code}?from=&to=` — time series (validate dates, from <= to, max range)
- [x] `GET /api/rates/{code}/stats?from=&to=` — min, max, avg, first, last, change %, volatility (std dev)
- [x] `GET /api/convert?from=EUR&to=USD&amount=100&date=` — cross-rate via PLN, latest available date if none
- [x] `GlobalExceptionHandler` + `ApiError` (404 unknown currency, 400 validation)
- [x] OpenAPI docs (springdoc 3.2.0, approved) — Swagger UI at `/swagger-ui.html`, spec at `/v3/api-docs`
- [x] Tests: `StatsCalculator` unit tests (edge cases: 1 point, empty), `@WebMvcTest` for controllers
- **Done when:** all endpoints work in Postman/HTTP client and are covered by tests.

## Phase 4 — AI insights
- [x] `AiInsightClient` interface + `GeminiClient` implementation (RestClient, key from env)
- [x] `InsightService`: compute stats -> build compact prompt -> call AI -> save to `ai_insight`
- [x] Cache: return saved insight if (currency, period) already exists
- [x] Handle 429 / timeouts / missing key with a clear error response (no crash)
- [x] `GET /api/insights/{code}?from=&to=`
- [x] Prompt template in resources (`prompts/trend-summary.txt`), output: short plain-English summary, no financial advice
- [x] Tests: WireMock Gemini stub (success, 429), cache hit test
- **Done when:** first call generates and stores an insight, second call returns it from DB instantly.

## Phase 5 — Frontend
- [x] Vite + React + TypeScript in `/frontend`, API base URL from env (`VITE_API_BASE_URL`); linting with oxlint (the Vite template's default)
- [x] Dashboard: currency selector, date range picker (presets + native date inputs), line chart (Recharts) with data table view, stats cards
- [x] Converter widget (all currencies + PLN, optional date, shows the NBP rate dates used)
- [x] "Generate AI insight" panel with loading/error states (button-triggered to save quota; 429/503/404 headings)
- [x] CORS config on backend (allowed origins from `app.cors.allowed-origins` / `CORS_ALLOWED_ORIGINS`, GET only)
- [x] Extra: currency flags (flag-icons, approved), readable AI summary layout, `spring-boot:run` reads `.env`
- **Done when:** whole flow works in the browser locally.

## Phase 6 — Docker & CI
- [x] Multi-stage `Dockerfile` for backend (JDK build -> JRE run, non-root, `MaxRAMPercentage`), frontend (Node build -> nginx)
- [x] `docker compose up --build` runs db + backend + frontend (frontend on host port 3000, because 8080 is often taken)
- [x] GitHub Actions: build + test backend (Testcontainers works on ubuntu runners), lint + build frontend, `docker compose build`
- **Done when:** green CI badge in README.

## Phase 7 — Cloud deploy (free)
- [x] Neon PostgreSQL (free, no expiry) — connection via env vars
- [x] Render: backend as Docker web service, frontend as static site
- [x] Env vars set in Render dashboard (DB, `GEMINI_API_KEY`, CORS origin)
- [x] Note in README: free instance sleeps after 15 min, first request takes ~1 min
- [x] Extra: admin API protected by `X-Admin-Token`; daily ingestion via GitHub Actions; cold-start hint in the UI
- **Done when:** public URL works end to end.

## Phase 8 — Polish & interview prep
- [x] README: what/why, architecture diagram, how to run, endpoints, decisions & trade-offs (no screenshots: the live demo link instead)
- [x] Extra: frontend unit tests (Vitest) in CI; subagent definitions committed in `.claude/agents/`
- [ ] `docs/ai-workflow.md`: how Claude Code was used (CLAUDE.md, plan mode, subagents, what I reviewed/changed myself)
- [ ] Optional: `.claude/agents/code-reviewer.md` subagent, run it over the codebase, fix findings
- [ ] Ask Claude: "Ask me 15 interview questions about this codebase" — answer them out loud
- [ ] Timed rehearsal: rebuild a mini version (one endpoint + one test) from scratch in 2 hours

---

## Interview talking points to be able to explain
- Why `BigDecimal`; why idempotent ingestion; why chunking (documented 93 days vs. enforced 367, configurable)
- Why Flyway instead of `ddl-auto`
- Why the AI client is behind an interface; how caching saves free-tier quota
- Why Testcontainers over H2; how WireMock isolates external APIs
- What I would change for production (auth, retries with backoff, observability, paid tier)
