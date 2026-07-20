# Disaster Management System

Coordinated disaster-relief platform: a Spring Boot API and a React single-page app for
managing camps, resources, volunteers, and alerts across concurrent disasters.

## Stack

- **Backend:** Spring Boot 3.5 · Java 21 · Spring Data JPA · Flyway · PostgreSQL 16
- **Frontend:** React 19 · Vite · TypeScript · Tailwind CSS v4 · shadcn/ui · react-i18next (EN / বাংলা)
- **Infra:** Docker Compose (Postgres + API + nginx-served frontend)

## Quick start

Requires Docker with Compose.

```bash
docker compose up --build
```

Once all services report healthy:

- Frontend: <http://localhost:8081>
- API health: <http://localhost:8080/actuator/health>

The frontend reaches the API through nginx at `/api/*` (the prefix is stripped when proxied),
so `/api/actuator/health` maps to the backend's `/actuator/health`.

## Local development

**Backend** (needs a Postgres on `localhost:5432`, e.g. `docker compose up db`):

```bash
cd backend
./gradlew bootRun
```

**Frontend** (Vite dev server proxies `/api` to `localhost:8080`):

```bash
cd frontend
npm install
npm run dev
```

## Tests

```bash
cd backend  && ./gradlew test   # boots the Spring context on H2 + Flyway, checks the health seam
cd frontend && npm test         # Vitest + Testing Library
```

CI (GitHub Actions, `.github/workflows/ci.yml`) runs both suites on every push and pull request.

## Layout

```
backend/            Spring Boot API (Gradle, wrapper committed)
frontend/           React + Vite SPA, served by nginx in production
docker-compose.yml  Postgres + backend + frontend
docs/               Project documentation
```
