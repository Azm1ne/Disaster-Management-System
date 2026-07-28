# Metrics report

This is the narrative version of the numbers `bd.dms.anomaly.MetricsReportGeneratorTest` produces
as `backend/build/reports/metrics-report.{json,md}` (also archived by CI as the `metrics-report`
build artifact on every run). Regenerate it locally with:

```
cd backend && ./gradlew test --tests "bd.dms.anomaly.MetricsReportGeneratorTest"
```

The numbers below are from a run against the scripted Jamuna/Patuakhali scenario on this branch;
since the scenario is a deterministic pure function of tick (see `docs/data-simulation-note.md`),
re-running it reproduces the same numbers exactly, so this snapshot stays accurate unless the
scenario or the scored logic itself changes.

## Forecast MAE by data-quality condition

One-step-ahead mean absolute error of `ForecastService`'s rolling-rate prediction, grouped by the
scripted data-quality condition of the camp+resource combo being forecast.

| Condition | MAE |
|---|---|
| NORMAL | 15.42 |
| STALE_PRONE | 49.20 |
| CONFLICTING_PRONE | 120.66 |

Both degraded conditions are measurably — and substantially — worse than `NORMAL`, which is the
property the forecaster's confidence band exists to reflect: a camp with sparse or conflicting
reports should get a wider, less confident forecast, and the error numbers confirm its raw
predictions really are less accurate there.

## Anomaly detector accuracy

Precision/recall/false-positive rate over each detector's synthetic ground-truth case table
(true positives varied at the margin, "innocent look-alike" negatives designed to resemble a
positive on a shallow read, and a clear negative baseline), plus detection lead time — how many
ticks/events elapse between the anomalous condition first becoming true and the detector flagging
it.

| Detector | Precision | Recall | False-positive rate | Lead time |
|---|---|---|---|---|
| AllocationBurstDetector | 1.00 | 1.00 | 0.00 | 0 ticks |
| DonationPatternDetector | 1.00 | 1.00 | 0.00 | 0 events |
| DuplicateRegistrationDetector | 1.00 | 1.00 | 0.00 | 0 events |

All three clear the 0.8 precision/recall and 0.2 false-positive-rate floors the individual
per-detector accuracy tests already assert. Lead time is 0 for all three by design: every
detector's `scan*` method evaluates the anomalous condition synchronously on the very event that
makes it true (a burst is checked on the tick it completes; a duplicate is checked against every
existing group at the moment the new group registers) — there is no separate, later detection
pass. A production system ingesting a real, continuous stream might reasonably see non-zero lead
time; this scripted, event-driven design does not have that gap.

## Allocation: priority ordering vs. FCFS

A fixed three-camp scenario (one severe-shortage camp, one mild-shortage camp, one surplus camp
with enough stock to fully serve only one of them) compares two allocation strategies over the
same shared surplus pool, reporting each strategy's total unmet medical-severity-weighted
shortage. The high-severity camp is deliberately given a *later* camp id than the low-severity
camp, so naive FCFS (camp-id order) would serve the wrong one first.

| Strategy | Unmet severity-weighted shortage |
|---|---|
| FCFS (camp-id order) | 13.50 |
| Priority-ranked | 0.00 |

Priority ordering fully eliminates the unmet, severity-weighted shortage that FCFS leaves behind
in this scenario — the quantitative case for scoring allocations by severity/gap/surplus/priority
(`bd.dms.allocation.AllocationScoringService`) rather than by arrival or camp order.

## Load: p50/p95 latency

Characterized separately by the k6 scripts in `loadtest/` against a running compose stack — see
`loadtest/README.md` for how to run it and where results land. This is a manual, non-gating track,
not part of `./gradlew test` or the GitHub Actions CI gate, per the ticket's own scope decision:
load characteristics of a showcase system are worth recording, but shouldn't make every commit's
CI run flaky against a shared machine's ambient load.
