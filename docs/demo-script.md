# Demo script

A ~10-minute guided walkthrough of the system, role by role. All accounts use the shared demo
password **`relief2026`** (see `frontend/e2e/helpers.ts`). Start the stack with `docker compose up`
plus the backend (`./gradlew bootRun`) and frontend (`npm run dev`), or use `npm run test:e2e`'s
own bring-up if you just want to confirm it starts.

## 0. The premise

Two concurrent Bangladesh disasters, always running: the **Jamuna flood** (11 camps, an active
scripted surge) and the **Patuakhali cyclone** (a stable, quiescent second disaster, proving the
system handles more than one at once). The simulated clock advances on its own — every operator
screen carries a **DEMO** badge next to the clock so it's never mistaken for a live production
system.

## 1. Coordinator — the situation room

Sign in as `coordinator` → lands on `/coordinator`, the dark, map-first operator shell.

- **World map** — both disasters on one map, flood cyan / cyclone amber, camp markers colored by
  status. Click a camp for its headline resource state.
- **Alerts tab** — raise, transition, and add a note to an alert; watch the SLA-driven
  re-escalation described in `bd.dms.alert`.
- **Forecasts tab** — each camp/resource shows a rolling-rate prediction with a confidence band.
  If nothing has crossed the shortage threshold yet in the current run, `POST
  /forecasts/demo/{campId}/{resourceType}` (Coordinator/Admin-only) drives the real forecast-alert
  pipeline once against a real camp, so the alert is reachable live without waiting out the whole
  scripted run.
- **Allocations tab** — the cross-camp allocation queue, scored by severity/gap/surplus/priority.
  `POST /allocations/demo/{shortageCampId}/{surplusCampId}/{resourceType}` produces a real
  recommendation on demand, the same DEMO-trigger precedent as forecasts. Approve, Modify, or
  Reject one and watch the hard over-allocation block refuse an over-commit.
- **Anomalies tab** — the review workspace for the three rule-based detectors. `POST
  /anomalies/demo/burst/{shortageCampId}/{surplusCampId}` produces a real
  `AllocationBurstDetector` flag on demand.
- **Broadcasts** — send a bilingual announcement to every Camp Manager; note the read receipts.

## 2. Camp Manager — one camp's view

Sign in as `camp_manager` → lands on `/camp`, scoped to that manager's own camp only (an
allocation-visibility check worth calling out: a Camp Manager sees only their camp's approved
allocation rows, never another camp's queue).

## 3. Admin — declaring/editing a disaster

Sign in as `admin` → `/admin`. Draws a disaster's area as a map polygon, click-places affected
areas and camps, and can edit or close a disaster. Every geometry change is retained as audit
history (ticket 13).

## 4. Victim — registration and reunification

Sign in as `victim` → `/victim`, the light, large-type field shell. Register a family group,
confirm a dual-source arrival, and search for a reunification match — note that the search result
never exposes another family's full roster, only a match confirmation.

## 5. Volunteer — task queue

Sign in as `volunteer` → `/volunteer`. See a push-assigned task from the scoring/routing system,
self-accept an open one, and check the skill-gap indicator when a task needs a skill the
volunteer doesn't have. The volunteer's shell also carries the broadcast/DM panels, since a
volunteer is a genuine Camp-Manager operational relationship.

## 6. Donor — impact, not access

Sign in as `donor` → `/donor`. Make a donation, then see it reflected in the donor impact view —
read-only transparency into where funds went, with no operational chat surface (see
`docs/responsible-design-note.md` for why).

## 7. NGO — read-only workspace

Sign in as `ngo` → `/ngo` (ticket 15; a placeholder shell until that ticket lands).

## 8. The public locator — no account needed

Visit `/locator` with no sign-in. Confirm it never shows camp capacity or any operational detail —
it's the one place camp data is shown to nobody in particular, deliberately kept to "is there a
shelter near me."

## 9. The language toggle

On any screen, click the EN↔বাংলা toggle in the header and confirm every visible string switches —
English-only text anywhere in this system is treated as a bug, not a missing translation.

## 10. The receipts

Everything demoed above is also measured, not just shown: run
`./gradlew test --tests "bd.dms.anomaly.MetricsReportGeneratorTest"` from `backend/` and open
`backend/build/reports/metrics-report.md` (or `docs/metrics-report.md` for the narrative version)
for forecast accuracy, anomaly-detector precision/recall, and the priority-vs-FCFS allocation
comparison — quantitative backing for the qualitative walkthrough above.
