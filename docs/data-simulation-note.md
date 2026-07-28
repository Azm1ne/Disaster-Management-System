# Data & simulation note

Every entity, timestamp, and name in this system is synthetic — there is no real personal data
anywhere in the codebase or its seed data (see `docs/responsible-design-note.md`).

## Why a scripted scenario, not a random one

`bd.dms.sim.Scenario` (`backend/src/main/java/bd/dms/sim/Scenario.java`) is a **pure function of
tick number**: `stateAt(tick)` deterministically returns the entire simulated world at that tick.
There is no clock read and no shared `java.util.Random` inside it, so the same tick always
produces an equal `ScenarioState` — which is what lets a headless test assert on the world
directly, tick-for-tick, without flaking. `SimulationEngine` is the one production component that
turns `Scenario.stateAt(tick)` into real mutation of `camps`/`camp_resources` as the clock
advances (see `docs/architecture.md`); it is deliberately the *only* writer of that data, so no
other feature can corrupt it by writing concurrently.

## The story, in four phases

The scripted run covers `Scenario.LENGTH` = 60 ticks (~30 simulated minutes each, anchored at
2024-07-15T06:00Z, the flood's real-world onset date used only as a narrative timestamp):

1. **SURGE** (ticks 0–20) — riverine Jamuna camps fill toward and past capacity; water and food
   deplete.
2. **NEW_CAMP** (tick 20) — the seeded-but-closed overflow camp `jam-char-relief` opens and
   absorbs the surge.
3. **RELIEF_CONVOY** (tick 35) — water and food are replenished at the worst-hit camps.
4. **RECOVERY** (tick 45 onward) — the flood recedes; populations gently decline and hold at
   `stateAt(LENGTH)` for any tick beyond 60.

Throughout, the Patuakhali cyclone's camps are quiescent (`surgeAdd == 0`): they hold their
seeded baseline for every tick. This is deliberate — it proves the system handles **two disasters
at once**, one active and one stable, without one disaster's logic leaking into the other's.

`stateAt(0)` is constructed to equal the `V4`/`V5`/`V6` Flyway seed exactly (all pressure and
jitter terms are zero at tick 0); `ScenarioSeedConsistencyTest` guards that equivalence so the
seed data and the scripted run can never silently drift apart.

## Scripted data-quality conditions

Real telemetry is patchy, and the forecaster's confidence band only means something if patchy
data is actually exercised. `Scenario.DataQualityCondition` (`NORMAL` / `STALE_PRONE` /
`CONFLICTING_PRONE`) is a **fixed, deterministic** assignment of one condition to specific
camp+resource combinations — one combo is scripted to have gaps in when it reports
(`STALE_PRONE`), one to sometimes report two disagreeing values for the same tick
(`CONFLICTING_PRONE`), and everything else is `NORMAL`. `Scenario.shouldRecordObservation(...)`
and `Scenario.dataQualityCondition(...)` are what the forecast accuracy tests (and this ticket's
`MetricsReportGeneratorTest`) group errors by — see `docs/metrics-report.md` for the resulting
MAE-by-condition numbers, and confirm degraded conditions are measurably worse than normal ones.

## Why this is a defensible substitute for real historical data

A showcase system has no real incident history to replay. Scripting the scenario as a pure
function, rather than randomizing it, trades away variety for two properties real random data
can't give a demo: **reproducibility** (the same run always produces the same numbers, so a
metrics report is comparable across commits) and **engineered coverage** (every interesting
condition — a camp opening mid-run, a stale sensor, a conflicting report, two disasters at once
— is guaranteed to occur, rather than hoped for statistically). The tradeoff is namable: this
scenario does not claim to be representative of real disaster telemetry distributions, only to
exercise every code path this system needs to prove itself against.
