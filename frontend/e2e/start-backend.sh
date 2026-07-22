#!/usr/bin/env bash
# Starts the API for an end-to-end run, with the database it needs already listening.
# Playwright brings its webServers up before any setup hook runs, so the wait belongs here
# rather than in a global setup — otherwise the API races Postgres and exits.
set -euo pipefail

cd "$(dirname "$0")/../.."

docker compose up -d db

until docker compose exec -T db pg_isready -U dms -d dms >/dev/null 2>&1; do
  sleep 1
done

cd backend
exec ./gradlew bootRun
