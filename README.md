# FxPulse

[![CI](https://github.com/vladikv/fxpulse/actions/workflows/ci.yml/badge.svg)](https://github.com/vladikv/fxpulse/actions/workflows/ci.yml)

Ingests official PLN exchange rates from the [NBP Web API](https://api.nbp.pl/), stores them in PostgreSQL,
exposes a REST API with statistics and currency conversion, and generates AI trend summaries with Google Gemini.

## Tech stack
- **Backend:** Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA, Flyway, Actuator
- **Database:** PostgreSQL 16
- **AI:** Google Gemini API
- **Frontend:** React + TypeScript + Vite, Recharts, flag-icons
- **Infra:** Docker (multi-stage images), Docker Compose, GitHub Actions CI, Render *(coming soon)*

## Run locally (Windows / PowerShell)
Prerequisites: Java 21, Docker, Node.js 22.12+ (required by Vite 8).

```powershell
copy .env.example .env          # put your GEMINI_API_KEY here (optional: without it AI insights return 503)
docker compose up -d db
cd backend; .\mvnw spring-boot:run
```
`spring-boot:run` reads the repo's `.env` itself (values without quotes). Tests, the jar and Docker never read it.

Check: <http://localhost:8098/actuator/health> should return `{"status":"UP"}`.
An empty database is filled with `POST http://localhost:8098/api/admin/ingest?days=365`.

Frontend (second terminal):
```powershell
cd frontend
npm install
npm run dev                     # http://localhost:5173
```
The frontend calls `VITE_API_BASE_URL` (default `http://localhost:8098`; see `frontend/.env.example`).
The backend allows browser calls from the origins in `CORS_ALLOWED_ORIGINS` (default `http://localhost:5173`).

Tests and checks: `cd backend; .\mvnw test` and `cd frontend; npm run lint; npm run build`

## Run everything with Docker
Prerequisite: Docker only (no Java or Node needed).

```powershell
copy .env.example .env          # optional: GEMINI_API_KEY for AI insights
docker compose up --build
```
- Frontend: <http://localhost:3000> (nginx serving the production build)
- Backend: <http://localhost:8098> (Swagger UI at `/swagger-ui.html`)
- Database: `localhost:25432` (data is kept in the `fxpulse-db-data` volume)

The first start has an empty database: run `POST http://localhost:8098/api/admin/ingest?days=365`.
Stop with `docker compose down` (add `-v` to also delete the data).

## CI
GitHub Actions (`.github/workflows/ci.yml`) runs on every pull request and every push to `main`:
backend build + all tests (Testcontainers uses the runner's Docker), frontend lint + build, and a
`docker compose build` of both images.

## API endpoints
_TODO (Phase 3)_

## Architecture
_TODO_

## Decisions & trade-offs
_TODO_
