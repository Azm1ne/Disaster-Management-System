import { expect, test, type Page } from '@playwright/test'
import { resetSimulation, signIn } from './helpers'

/** Mirrors frontend/src/anomalies/api.ts's AnomalyFlagView — kept local, like every other spec in
 * this suite inlines its own response shapes rather than importing from src. */
interface AnomalyFlagView {
  detectorType: 'ALLOCATION_BURST' | 'DUPLICATE_REGISTRATION' | 'DONATION_PATTERN'
  detectedAtTick: number | null
}

/**
 * The Anomalies tab, wired into the operator shell (ticket 11): a Coordinator can see the review
 * workspace, trigger a real ALLOCATION_BURST flag through the Scenario-B demo endpoint — the
 * scripted `Scenario` never naturally produces a burst of allocation recommendations, same
 * structural fact ticket 08 documented for allocations — see its innocent explanation, and
 * confirm it.
 *
 * Two things about the real backend make this trickier than the other demo-trigger specs:
 * - `AllocationGenerationService.upsert` reuses the same three `AllocationDecision` rows for the
 *   jam-fulchhari/jam-sundarganj pair on every call, so a flag's `subjectIds` are identical run to
 *   run — not a usable key to tell "this run's flag" apart from an older one.
 * - `AllocationBurstDetector` refuses to flag the same tick twice (real production logic), and
 *   `anomaly_flags` rows are never reset between runs. A backend restart resets the in-memory tick
 *   to 0, but the Postgres volume persists across separate `npm run test:e2e` invocations, so a
 *   naive second run would silently land on a tick an earlier run already flagged and produce no
 *   new flag at all.
 * `advancePastEveryKnownBurstTick` drives the clock past every tick any earlier run has ever
 * flagged before triggering, so this run's flag is always new. The queue lists flags newest first
 * (`AnomalyReviewService.list`), and reviewing one never changes its `createdAt`, so the first
 * "Allocation burst" card is reliably this run's flag both before and after it is confirmed.
 */

async function tokenFor(page: Page): Promise<string> {
  const token = await page.evaluate(() => localStorage.getItem('dms.access'))
  if (!token) throw new Error('Not signed in')
  return token
}

/** Triggers the real allocation-burst detection pipeline against two real camps, the same
 * Scenario-B DEMO endpoint AnomalyController exposes. Camp discovery mirrors
 * comms.spec.ts's triggerDemoAllocation: real seeded camp codes, not hardcoded ids. */
async function triggerDemoBurst(page: Page, token: string): Promise<void> {
  const disasters = await page.request.get('http://localhost:8080/world/disasters', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(disasters.ok()).toBeTruthy()
  const world = (await disasters.json()) as { camps: { id: number; code: string }[] }[]
  const camps = world.flatMap((disaster) => disaster.camps)
  const shortageCampId = camps.find((c) => c.code === 'jam-fulchhari')!.id
  const surplusCampId = camps.find((c) => c.code === 'jam-sundarganj')!.id

  const response = await page.request.post(
    `http://localhost:8080/anomalies/demo/burst/${shortageCampId}/${surplusCampId}`,
    { headers: { Authorization: `Bearer ${token}` } },
  )
  expect(response.ok()).toBeTruthy()
}

/** The highest `detectedAtTick` among every ALLOCATION_BURST flag already in the (never-reset)
 * table, or -1 if there are none yet. */
async function maxKnownBurstTick(page: Page, token: string): Promise<number> {
  const response = await page.request.get('http://localhost:8080/anomalies', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.ok()).toBeTruthy()
  const flags = (await response.json()) as AnomalyFlagView[]
  const ticks = flags
    .filter((flag) => flag.detectorType === 'ALLOCATION_BURST')
    .map((flag) => flag.detectedAtTick ?? -1)
  return ticks.length > 0 ? Math.max(...ticks) : -1
}

/** Resets the clock, resumes it, and waits until it has climbed strictly past every tick any
 * earlier run has ever flagged, then pauses — see the file doc for why. */
async function advancePastEveryKnownBurstTick(page: Page, token: string): Promise<void> {
  await resetSimulation(page)
  const floor = await maxKnownBurstTick(page, token)

  await page.request.post('http://localhost:8080/simulation/resume', {
    headers: { Authorization: `Bearer ${token}` },
  })
  await expect(async () => {
    const clock = await page.request.get('http://localhost:8080/simulation/clock', {
      headers: { Authorization: `Bearer ${token}` },
    })
    const { tick } = (await clock.json()) as { tick: number }
    expect(tick).toBeGreaterThan(floor)
  }).toPass({ timeout: 30_000 })
  await page.request.post('http://localhost:8080/simulation/pause', {
    headers: { Authorization: `Bearer ${token}` },
  })
}

/** The newest ALLOCATION_BURST card — first in the queue's newest-first order, and stable across
 * the Confirm click since reviewing a flag never changes its `createdAt`. */
function latestBurstCard(page: Page) {
  return page
    .locator('div.rounded-lg.border-line.bg-surface')
    .filter({ hasText: 'Allocation burst' })
    .first()
}

test('a coordinator reviews and confirms a real allocation-burst flag', async ({ page }) => {
  await signIn(page, 'coordinator')
  const token = await tokenFor(page)

  await page.getByRole('button', { name: /anomalies/i }).click()
  // The workspace renders nothing until its own query resolves, and may legitimately be empty —
  // settle into the title before assuming anything about seed data.
  await expect(page.getByText('Anomaly review')).toBeVisible()

  await advancePastEveryKnownBurstTick(page, token)
  await triggerDemoBurst(page, token)

  // The tab has no dedicated STOMP topic (same tradeoff as forecasts/allocations) and reverts to
  // "overview" on reload since it's plain component state, not URL-routed — so each retry both
  // reloads and re-opens the tab before checking for the card. Never branch on `isVisible()`
  // directly; wrap the whole scan in `toPass`, the pattern this suite's memory notes require.
  await expect(async () => {
    await page.reload()
    await page.getByRole('button', { name: /anomalies/i }).click()
    await expect(latestBurstCard(page).getByRole('button', { name: 'Confirm', exact: true })).toBeVisible({
      timeout: 2000,
    })
  }).toPass({ timeout: 15_000 })

  const card = latestBurstCard(page)
  await expect(
    card.getByText(/genuine region-wide shortage can also produce several recommendations/i),
  ).toBeVisible()
  await expect(card.getByText('Open', { exact: true })).toBeVisible()

  await card.getByRole('button', { name: 'Confirm', exact: true }).click()

  await expect(card.getByText('Confirmed', { exact: true })).toBeVisible()
  await expect(card.getByRole('button', { name: 'Confirm', exact: true })).toHaveCount(0)
  await expect(card.getByRole('button', { name: 'Dismiss', exact: true })).toHaveCount(0)

  // Leave the clock as most other specs expect to find it, since this run drove it forward.
  await resetSimulation(page)
})
