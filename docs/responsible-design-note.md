# Responsible design note

This system makes several exclusions on purpose. Recording them here is meant to make clear that
each is a deliberate design decision made against a real tradeoff, not a gap that was simply
never gotten to.

## No real personal data, anywhere

Every family, victim, donor, and volunteer in this system is synthetic — generated for the demo,
never sourced from a real person. There is no real payment processing either: the donation ledger
(`bd.dms.funds`) models money flow and reconciliation faithfully, but is not connected to any real
payment provider. A system built to showcase disaster-response software should not, in the
process, become a vector for handling real victims' real data or real people's real money.

## No open-ended chat, anywhere

In-app communication (`bd.dms.broadcast`, `bd.dms.dm`, `bd.dms.note`, see `docs/comms-scope.md`
for the full rationale) is deliberately narrow: case notes bound to a specific record, broadcasts
that are one-way from Coordinator/Admin to a whole role, and DMs restricted to real operational
relationships (Coordinator↔Camp Manager, Camp Manager↔their Volunteers) and refused server-side
otherwise. **Victim-facing chat and NGO/donor chat do not exist**, on purpose:

- A displaced person is not well served by an open inbox to relief staff during a crisis — it
  invites requests staff have no channel to triage, and this demo's staffing model (one
  Coordinator, a handful of Camp Managers) could not realistically absorb it. Victims already
  have the channel they actually need: family registration and reunification search
  (`bd.dms.family`) and the public locator.
- Donors and NGOs get **read-only transparency** — visibility into where resources went — instead
  of a direct line into an operational channel already carrying live triage traffic.

## No LLM anywhere in the product runtime

Forecasting and alerting are statistical and template-based by decision (a weighted rolling rate
with a confidence band; typed alert templates), not generative. A disaster-response system's
outputs need to be explainable and reproducible under audit — "why did the system say this" has
to have a deterministic, inspectable answer, which a generative model does not reliably give.

## Anomaly detection is flag-for-review only

The three anomaly detectors (`bd.dms.anomaly`) never take action on their own — a burst, a
duplicate, or a donation pattern only ever produces a reviewable flag. No detector pauses an
allocation, blocks a donation, or merges a family registration automatically. A false positive in
an automated system with real consequences is much more costly than a false positive that a human
reviewer dismisses in a few seconds; keeping a human in the loop is the entire point of a
detect-and-flag design instead of a detect-and-act one.

## Central authority is inbox-only

The central-authority role that approves or rejects a coordinator's proposed disaster or geometry
change (ticket 13) is intentionally a minimal approval inbox, not a full workspace. Giving it more
surface than the one decision it exists to make would blur who is actually operating the response
day to day.

## What this buys, and what it costs

Every exclusion above trades some capability for a narrower, more auditable, more defensible
system. The cost is real: a production disaster-response tool would eventually need some of what's
excluded here (some transport for a victim's urgent, unanticipated need; some machine-assisted
triage at higher alert volumes than three synthetic detectors' rule thresholds can express). The
decisions above are the right shape for a showcase system whose job is to demonstrate credible,
measurable relief-coordination logic — not to be mistaken for a finished, production-hardened
product (see `docs/README.md` and the "Out of Scope" section of the master spec for the full
list, including the production-hardening items — multi-tenant, HA, real secrets management — this
demo does not attempt).
