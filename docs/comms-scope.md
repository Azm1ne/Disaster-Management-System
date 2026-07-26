# In-app communication: what exists, and what deliberately doesn't

Ticket 12 builds three narrow communication surfaces, all bound to operational context:

1. **Case notes** — threaded notes attached to the evidence they're about (alerts, allocations,
   and the cross-camp transfers allocations represent). Not a chat: a note only exists in the
   context of the record it's discussing.
2. **Broadcasts** — bilingual, one-way announcements from a Coordinator/Admin to every Camp
   Manager or every Volunteer, with read receipts so the sender knows it landed.
3. **Direct messages** — narrow 1:1 threads, permitted only along a genuine operational
   relationship (Coordinator &lt;-&gt; Camp Manager, Camp Manager &lt;-&gt; their Volunteers) and
   refused server-side otherwise.

## What is intentionally excluded

**Victim-facing chat** and **NGO/donor chat** do not exist anywhere in this system, and that is a
deliberate scope decision, not an oversight:

- A displaced person in a camp is not well served by putting them one tap away from an open
  inbox to relief staff during a crisis — it invites requests staff have no channel to triage,
  and it creates a support burden this demo's staffing model (one Coordinator, a handful of
  Camp Managers) cannot realistically absorb. Victims already have a purpose-built channel for
  the thing they actually need: the family registration/reunification search (ticket 05) and the
  public locator. Anything else routes through a Camp Manager or Volunteer physically present at
  the camp, the same as it would in a real camp.
- Donors and NGOs are given **read-only transparency** instead of a chat surface — visibility
  into where resources are going, not a direct line to operational staff mid-response. Donor/NGO
  chat would mean relief coordinators fielding messages from funders while triaging a live
  disaster, which is the wrong shape of communication for either party: donors want to see
  outcomes, not relay instructions into an operational channel that already has its own
  Coordinator-driven decision process (see ticket 08, the allocation decision queue).

Both exclusions keep every communication surface bound to an actual operational relationship or
an actual piece of evidence, which is the same design principle the three tiers above are built
on — communication in this system is never open-ended.
