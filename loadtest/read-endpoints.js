import http from 'k6/http'
import { check } from 'k6'
import { Trend } from 'k6/metrics'

/**
 * Characterizes p50/p95 latency of the operator shell's core read endpoints under a small,
 * steady load. This is the "Load (separate track)" gate from the master spec: it runs against a
 * real, already-running stack (docker compose db + `./gradlew bootRun`), not as part of unit CI,
 * and its output is folded into the metrics report by hand (see ../docs/metrics-report.md) rather
 * than gating any build.
 *
 * Run:
 *   k6 run loadtest/read-endpoints.js --summary-export=loadtest/results.json
 *
 * Override the target host if the backend isn't on the default port:
 *   k6 run -e BASE_URL=http://localhost:8080 loadtest/read-endpoints.js
 */

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080'
const USERNAME = __ENV.DEMO_USERNAME || 'coordinator'
const PASSWORD = __ENV.DEMO_PASSWORD || 'relief2026'

const worldTrend = new Trend('world_disasters_duration', true)
const allocationsTrend = new Trend('allocations_duration', true)
const forecastsTrend = new Trend('forecasts_duration', true)
const anomaliesTrend = new Trend('anomalies_duration', true)

export const options = {
  scenarios: {
    steady_read_load: {
      executor: 'constant-vus',
      vus: 10,
      duration: '30s',
    },
  },
  // Applies to the built-in http_req_duration metric across every request in the run.
  thresholds: {
    http_req_duration: ['p(95)<500'],
  },
  summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(95)', 'max'],
}

// One login per VU (k6's `setup()` runs once for the whole run, not once per VU, and its return
// value is shared read-only with every VU — exactly what a single reused bearer token needs).
export function setup() {
  const loginRes = http.post(
    `${BASE_URL}/auth/login`,
    JSON.stringify({ username: USERNAME, password: PASSWORD }),
    { headers: { 'Content-Type': 'application/json' } },
  )
  check(loginRes, { 'login succeeded': (r) => r.status === 200 })
  const accessToken = loginRes.json('accessToken')
  return { accessToken }
}

export default function (data) {
  const headers = { Authorization: `Bearer ${data.accessToken}` }

  const world = http.get(`${BASE_URL}/world/disasters`, { headers })
  worldTrend.add(world.timings.duration)
  check(world, { 'world/disasters 200': (r) => r.status === 200 })

  const allocations = http.get(`${BASE_URL}/allocations`, { headers })
  allocationsTrend.add(allocations.timings.duration)
  check(allocations, { 'allocations 200': (r) => r.status === 200 })

  const forecasts = http.get(`${BASE_URL}/forecasts`, { headers })
  forecastsTrend.add(forecasts.timings.duration)
  check(forecasts, { 'forecasts 200': (r) => r.status === 200 })

  const anomalies = http.get(`${BASE_URL}/anomalies`, { headers })
  anomaliesTrend.add(anomalies.timings.duration)
  check(anomalies, { 'anomalies 200': (r) => r.status === 200 })
}
