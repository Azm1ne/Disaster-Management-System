# Load test (k6)

Characterizes p50/p95 latency of the core operator-shell read endpoints (`/world/disasters`,
`/allocations`, `/forecasts`, `/anomalies`) under a small, steady load. This is the "separate,
non-gating track" the master spec calls for: it runs against a real, already-running stack, not
as part of `./gradlew test`, `npm test`, or the GitHub Actions CI gate — see
`docs/metrics-report.md` for where its results get folded into the wider report.

## Prerequisites

- [k6](https://k6.io/docs/get-started/installation/) installed locally.
- A running stack: `docker compose up -d` (Postgres), then `cd backend && ./gradlew bootRun`.
  The default seeded users exist as soon as Flyway migrates on boot — no extra setup needed.

## Run

```
k6 run loadtest/read-endpoints.js --summary-export=loadtest/results.json
```

`results.json` (gitignored — it's a local run artifact, not checked in) holds the full summary,
including `p(50)`/`p(95)` per metric; the text summary k6 prints to stdout already includes both.

Override the target or the demo account if needed:

```
k6 run -e BASE_URL=http://localhost:8080 -e DEMO_USERNAME=coordinator loadtest/read-endpoints.js
```

## What it does not do

- It does not run in CI. A shared CI runner's ambient load makes p95 latency numbers noisy and
  non-reproducible in a way the deterministic scenario-driven metrics (forecast MAE, detector
  accuracy, allocation comparison — see `../backend/src/test/java/bd/dms/anomaly/MetricsReportGeneratorTest.java`)
  are not; keeping load testing manual and separate avoids that noise leaking into the CI gate.
- It does not exercise write endpoints (allocation transitions, donations, registrations) — this
  is a read-latency characterization, not a correctness or write-throughput test; those are
  already covered by the Spring integration test suite.
