# Disaster Management System

Coordinated disaster-relief platform: a Spring Boot API and a React single-page app for
managing camps, resources, volunteers, and alerts across concurrent disasters.

## Stack

- **Backend:** Spring Boot 3.5 · Java 21 · Spring Data JPA · Flyway · PostgreSQL 16
- **Frontend:** React 19 · Vite · TypeScript · React Router · Tailwind CSS v4 · shadcn/ui · react-i18next (EN / বাংলা) · self-hosted Noto Sans / Noto Sans Bengali
- **Auth:** Spring Security · JWT access tokens · server-side (revocable) refresh tokens · BCrypt
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

## Signing in (demo accounts)

There is no self-registration in the demo. One account is seeded per role at startup; all of
them share the same password. On the login screen you can tap a role to fill the form in.

| Role              | Username       | Workspace                                | Password     |
| ----------------- | -------------- | ---------------------------------------- | ------------ |
| Relief Coordinator| `coordinator`  | Situation room (dense, dark)             | `relief2026` |
| Camp Manager      | `camp_manager` | Situation room (dense, dark)             | `relief2026` |
| Administrator     | `admin`        | Situation room (dense, dark)             | `relief2026` |
| Donor             | `donor`        | Field view (light, large-type, mobile)   | `relief2026` |
| Volunteer         | `volunteer`    | Field view (light, large-type, mobile)   | `relief2026` |
| Victim / Family   | `victim`       | Field view (light, large-type, mobile)   | `relief2026` |
| NGO Partner       | `ngo`          | Field view (light, large-type, mobile)   | `relief2026` |

The password and JWT signing secret are configurable via `DMS_DEMO_PASSWORD`, `DMS_JWT_SECRET`,
`DMS_JWT_ACCESS_TTL`, and `DMS_JWT_REFRESH_TTL` (see `backend/src/main/resources/application.yml`).
Authorization is enforced server-side on every request — a wrong-role request is rejected by the
API, not merely hidden in the UI. Access tokens are short-lived JWTs; refresh tokens are stored
server-side so that signing out actually ends the session.

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
