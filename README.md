# FxPulse

Ingests official PLN exchange rates from the [NBP Web API](https://api.nbp.pl/), stores them in PostgreSQL,
exposes a REST API with statistics and currency conversion, and generates AI trend summaries with Google Gemini.

## Tech stack
- **Backend:** Java 21, Spring Boot 4, Spring Web MVC, Spring Data JPA, Flyway, Actuator
- **Database:** PostgreSQL 16
- **AI:** Google Gemini API
- **Frontend:** React + TypeScript + Vite, Recharts *(coming soon)*
- **Infra:** Docker Compose, GitHub Actions, Render *(coming soon)*

## Run locally (Windows / PowerShell)
Prerequisites: Java 21, Docker.

```powershell
copy .env.example .env
docker compose up -d db
cd backend; .\mvnw spring-boot:run
```

Check: <http://localhost:8098/actuator/health> should return `{"status":"UP"}`.

Run tests: `cd backend; .\mvnw test`

## API endpoints
_TODO (Phase 3)_

## Architecture
_TODO_

## Decisions & trade-offs
_TODO_
