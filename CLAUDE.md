# CLAUDE.md — FxPulse

## Project
Full-stack app that ingests official PLN exchange rates from the NBP Web API, stores them in PostgreSQL,
exposes a REST API with statistics and conversion, and generates AI trend summaries with Gemini.
Built as interview prep for a Java internship (Xelerit). The author must be able to explain every line —
prefer clear, conventional code over clever code.

The full roadmap lives in `PLAN.md`. Work phase by phase; never jump ahead without being asked.

## Tech stack
- Backend: Java 21, Spring Boot (latest stable), Maven, Spring Web, Spring Data JPA, Validation, Actuator
- DB: PostgreSQL 16 (Docker locally, Neon in the cloud), Flyway migrations
- HTTP clients: Spring `RestClient` (no extra HTTP libraries)
- AI: Google Gemini API (free tier) via `RestClient`, behind an interface (`AiInsightClient`) so Ollama can be swapped in
- Frontend: React + TypeScript + Vite, Recharts for charts (in `/frontend`)
- Tests: JUnit 5, Mockito, AssertJ, Testcontainers (PostgreSQL), WireMock (NBP + Gemini stubs)
- Infra: Docker Compose, GitHub Actions CI, Render (deploy)

## Repository layout
```
/backend     Spring Boot app
/frontend    React app
/docs        architecture notes, AI workflow notes
docker-compose.yml
PLAN.md
CLAUDE.md
```

## Backend architecture
Package root: `com.vlad.fxpulse`. Package by feature:
```
currency/   Currency entity, repository, service, controller, DTOs
rate/       ExchangeRate entity, repository, service, controller, DTOs, StatsCalculator
ingestion/  NbpClient, NbpDtos, IngestionService, IngestionScheduler
insight/    AiInsightClient (interface), GeminiClient, AiInsight entity, InsightService, controller
common/     GlobalExceptionHandler (@RestControllerAdvice), ApiError, config classes
```
Layering: controller -> service -> repository. Controllers never touch repositories or entities directly; they return DTOs (Java `record`s).

## Commands (Windows / PowerShell, run from repo root unless noted)
- Start DB: `docker compose up -d db`
- Run backend: `cd backend; .\mvnw spring-boot:run` — also reads the repo's `.env` (GEMINI_API_KEY, DB_*) via
  `--spring.config.import` in the spring-boot-maven-plugin config; tests, the jar and Docker never read `.env`.
  `.env` is parsed as a `.properties` file: write values without quotes.
- Backend tests: `cd backend; .\mvnw test`
- Run frontend: `cd frontend; npm run dev`
- Full stack in Docker: `docker compose up --build` — frontend http://localhost:3000, backend :8098, DB :25432
- CI: `.github/workflows/ci.yml` (backend `./mvnw verify`, frontend lint + build, `docker compose build`)

## Conventions
- Money and rates: always `BigDecimal` (never `double`), scale 4 for NBP mid rates, `RoundingMode.HALF_UP` for results.
- Dates: `LocalDate` for rate dates, `Instant` for timestamps.
- Use constructor injection only (no field `@Autowired`). Use Lombok sparingly or not at all — records preferred.
- Validation with Jakarta annotations on request DTOs; errors returned as a consistent `ApiError` JSON.
- Configuration in `application.yml` with `@ConfigurationProperties` classes; secrets only from env vars.
- Every schema change is a new Flyway migration (`V{n}__description.sql`); never edit an applied migration.
- Logging with SLF4J; no `System.out.println`.
- Code, comments, commit messages: English.

## NBP API facts (important)
- Base: `https://api.nbp.pl/api` — always add `?format=json`. No API key.
- Table A (mid rates): `/exchangerates/tables/A/` and `/exchangerates/rates/A/{code}/{startDate}/{endDate}/`
- Range limit: NBP documents 93 days per date-range query; the server actually enforces **367 days**
  (verified 2026-11-06: longer ranges return `400 ... Limit of 367 days has been exceeded`).
  We split longer ranges into chunks of `app.nbp.max-days-per-request` (default 93, the documented limit).
- No rates on weekends/holidays; the API returns **404** when there is no data for a range — treat as "empty", not as an error.
  The 404 body is **plain text** (`404 NotFound - Not Found - Brak danych`), not JSON; an unknown currency code also returns this 404.
- `tables/A/` returns a JSON **array** with one table; currency names are in Polish (32 currencies as of 2026-11).
- Ingestion must be **idempotent**: unique constraint on (currency_code, effective_date), upsert/skip duplicates.

## Gemini facts
- Key from env var `GEMINI_API_KEY` — never hardcode, never commit, never log it.
- Model name is configurable (`app.ai.model`); default to a free-tier Flash / Flash-Lite model.
- Free tier has rate limits: handle HTTP 429 gracefully (return a clear message, don't crash).
- Cache insights in the DB per (currency, period_from, period_to) with the `rate_count` they were based on;
  never call Gemini twice for the same key and data (regenerate only when the period has more rates now).
- Send only aggregated numbers (stats), not huge raw datasets, to keep prompts small.
- API (verified 2026-11-07): we use `POST {base}/v1beta/models/{model}:generateContent`. Google's docs now call
  `generateContent` **legacy** (still supported, no sunset date); the new default is the Interactions API
  (`POST /v1beta/interactions`, stateful unless `store=false`). Switching later only touches `GeminiClient`.
- Send the key in the `x-goog-api-key` header, never as `?key=` in the URL (URLs end up in logs).
- Stable free-tier models (2026-11): `gemini-3.5-flash-lite` (our default), `gemini-3.1-flash-lite`, `gemini-3.6-flash`.
  `temperature`/`topP`/`topK` are silently ignored on 3.6 Flash and newer, so we don't send them.
- Free-tier limits are per project (not per key); daily quotas reset at midnight Pacific time.

## Cloud deploy facts (verified 2026-11-08)
- Live: frontend `https://fxpulse-web.onrender.com`, backend `https://fxpulse-api.onrender.com`.
- Render is configured by `render.yaml` (Blueprint). With `rootDir` set, `dockerfilePath`, `dockerContext`
  and `staticPublishPath` are resolved **relative to rootDir** (the Blueprint spec page says "repo root",
  the monorepo docs and real behaviour say rootDir: `./backend` became `backend/backend` and failed).
- Render sets `PORT`; the backend listens on `${PORT:8098}`. Free instance: 512 MB, sleeps after 15 min idle.
- `VITE_API_BASE_URL` is baked in at build time: changing it needs a rebuild of `fxpulse-web`.
- Neon: Postgres 16, AWS Frankfurt, **direct** (non-pooled) connection with `?sslmode=require`;
  Neon's `postgresql://user:pass@host/db` string must become `jdbc:postgresql://host/db?sslmode=require`.
  Hikari pool `DB_POOL_SIZE=5` on Render.
- Daily ingestion runs from GitHub Actions (`daily-ingest.yml`, secret `ADMIN_TOKEN`, variable
  `FXPULSE_API_URL`). GitHub pauses scheduled workflows after 60 days without repo activity.
- Secrets are typed by the user into Render / GitHub (website or their own terminal), never into this chat.

## Testing rules
- Every service gets unit tests; every controller gets a `@WebMvcTest`.
- Integration tests use Testcontainers PostgreSQL — no H2.
- External HTTP (NBP, Gemini) is stubbed with WireMock in tests; tests never hit the real internet.
- Run `.\mvnw test` before declaring a task done.
- Use the test-writer subagent only for changes that add or change behavior:
  services, business logic (calculations, conversion, ingestion), controllers/endpoints,
  external clients (NBP, Gemini) and DB queries/migrations.
  Skip it for config, DTOs without logic, docs, renames/refactors without behavior change,
  and frontend styling. If unsure, ask me. After tests are written, run `.\mvnw test`.

## Git
- Commit after each finished, working step with a clear message (Conventional Commits: `feat:`, `fix:`, `test:`, `docs:`, `chore:`).
- Every feature/phase step gets its own branch from an up-to-date `main` (e.g. `feat/ingestion`, `fix/...`, `docs/...`; no phase words in branch names).
  Never commit feature work directly to `main`.
- Push the branch and open a pull request into `main` (via `gh pr create`); merge only after tests pass and I approve.
- Keep branches after merging: never delete them, locally or on GitHub (no `--delete-branch`).
- Never commit `.env`, keys, `target/`, `node_modules/`.

## How to work with me (Claude)
- Start every phase in plan mode: list files to create/change and wait for approval.
- Keep changes small and focused; one phase step at a time.
- Do not add dependencies that are not listed above without asking first.
- After finishing a step, briefly explain the key decisions (I will be asked about them in the interview).
- At the end of every phase, explain what was done in the whole phase: endpoints/features added, files and
  packages touched, key decisions and trade-offs, deviations from the plan, test count, and what comes next.
- At the end of every phase, also update `docs/about.md` (local only, git-ignored, never commit it):
  it explains every folder and file in the repo. Add new files, update changed ones, remove deleted ones,
  and refresh the "State after Phase N" line and the test count.
- If something in the NBP/Gemini API behaves differently than described here, tell me and update this file.
