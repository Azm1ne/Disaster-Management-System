import { expect, test, type Page } from '@playwright/test'
import { signIn } from './helpers'

/**
 * Ticket 13's two flows, end to end in a real browser: an Admin can declare a disaster by
 * drawing its boundary on the map, and a Coordinator's proposal reaches the Central Authority's
 * inbox and disappears from it once approved. Both flows drive real leaflet-draw interaction —
 * mouse clicks against the map's pixel coordinates — rather than only filling form fields, since
 * that is the part no unit test in this repo exercises.
 *
 * A unique suffix per run keeps `code` values from colliding against whatever the shared demo
 * database already holds from a previous run — this stack's Postgres volume is not reset between
 * `npm run test:e2e` invocations.
 */
const RUN_ID = Date.now()

/**
 * Clicks a sequence of points inside a Leaflet map to draw a small triangle boundary.
 *
 * leaflet-draw's `L.Draw.Polygon` adds a vertex on each mousedown/mouseup pair (not on the
 * synthetic `dblclick` event, which does not reliably land on the freshly-created vertex
 * marker's own hit area). The documented, reliable way to finish a polygon is instead to click
 * the *first* vertex again — `L.Draw.Polygon._updateFinishHandler` wires a permanent `click`
 * listener onto that first marker as soon as it exists, exactly for this purpose.
 *
 * leaflet-draw also debounces new vertices for 50ms after each one is added
 * (`_enableNewMarkers`'s internal `setTimeout`), so firing clicks back-to-back can silently drop
 * one — hence the explicit wait between each click below.
 */
async function drawTrianglePolygon(page: Page, mapContainer: ReturnType<Page['locator']>) {
  const box = await mapContainer.boundingBox()
  if (!box) throw new Error('map container has no bounding box')

  const p1 = { x: box.x + box.width * 0.4, y: box.y + box.height * 0.35 }
  const p2 = { x: box.x + box.width * 0.6, y: box.y + box.height * 0.35 }
  const p3 = { x: box.x + box.width * 0.5, y: box.y + box.height * 0.6 }

  await page.mouse.click(p1.x, p1.y)
  await page.waitForTimeout(150)
  await page.mouse.click(p2.x, p2.y)
  await page.waitForTimeout(150)
  await page.mouse.click(p3.x, p3.y)
  await page.waitForTimeout(150)
  // Click the first vertex again to close the ring and fire leaflet-draw's CREATED event.
  await page.mouse.click(p1.x, p1.y)
}

/** Clicks a single point inside a Leaflet map to place a camp. */
async function clickPoint(page: Page, mapContainer: ReturnType<Page['locator']>) {
  const box = await mapContainer.boundingBox()
  if (!box) throw new Error('map container has no bounding box')
  await page.mouse.click(box.x + box.width * 0.5, box.y + box.height * 0.5)
}

async function signOut(page: Page) {
  await page.getByRole('button', { name: 'Sign out' }).click()
  await expect(page.getByRole('heading', { name: 'Sign in to the operation' })).toBeVisible()
}

test('an admin can declare a disaster by drawing its boundary, and see it on the roster', async ({ page }) => {
  const code = `E2E-${RUN_ID}`
  const nameEn = `E2E Test Disaster ${RUN_ID}`

  await signIn(page, 'admin')
  await page.getByRole('button', { name: 'Disasters' }).click()
  await expect(page.getByRole('heading', { name: 'Disaster registration' })).toBeVisible()

  await page.getByRole('button', { name: 'Declare new disaster' }).click()
  await expect(page.getByText('Declare a new disaster')).toBeVisible()

  await drawTrianglePolygon(page, page.locator('.leaflet-container').first())

  await page.getByLabel('Code').fill(code)
  await page.getByLabel('Name (English)').fill(nameEn)
  await page.getByLabel('Name (Bangla)').fill(`ই২ই পরীক্ষা দুর্যোগ ${RUN_ID}`)

  await page.getByRole('button', { name: 'Declare disaster' }).click()

  // The declare form closes and the new disaster shows up in the roster, proving the create
  // round-tripped through the API rather than just being a local optimistic update.
  await expect(page.getByText('Declare a new disaster')).toHaveCount(0)
  await expect(page.getByText(nameEn)).toBeVisible()
  // The roster renders "<code> · <type>" as one text node, so match the code as a substring.
  await expect(page.getByText(new RegExp(code))).toBeVisible()
})

test('a coordinator proposal reaches the central authority inbox and clears once approved', async ({ page }) => {
  const campCode = `E2ECAMP-${RUN_ID}`
  const campNameEn = `E2E Test Camp ${RUN_ID}`

  await signIn(page, 'coordinator')
  await page.getByRole('button', { name: 'Propose change' }).click()
  await expect(page.getByRole('heading', { name: 'Propose a change' })).toBeVisible()

  // CAMP_CREATE is already selected by default only for DISASTER_CREATE, so pick it explicitly.
  await page.getByRole('button', { name: 'New camp', exact: true }).click()

  await page.getByLabel('Disaster').selectOption({ index: 1 })
  await clickPoint(page, page.locator('.leaflet-container').first())

  await page.getByLabel('Camp code').fill(campCode)
  await page.getByLabel('Name (English)').fill(campNameEn)
  await page.getByLabel('Name (Bangla)').fill(`ই২ই পরীক্ষা ক্যাম্প ${RUN_ID}`)
  await page.getByLabel('Capacity').fill('100')
  await page.getByLabel('Initial population').fill('10')

  await page.getByRole('button', { name: 'Submit proposal' }).click()
  await expect(page.getByText('Proposal submitted — waiting on the central authority.')).toBeVisible()

  await signOut(page)
  await signIn(page, 'central_authority')

  // The bare inbox: no shell, no DEMO badge, no map, no "Welcome," heading — just the queue.
  await expect(page.getByRole('heading', { name: 'Pending proposals' })).toBeVisible()
  await expect(page.getByText('DEMO')).toHaveCount(0)
  await expect(page.locator('.leaflet-container')).toHaveCount(0)

  const summary = `Create camp: ${campCode} (pop 10)`
  const card = page.locator('li').filter({ hasText: summary })
  await expect(card).toBeVisible()

  await card.getByRole('button', { name: 'Approve' }).click()

  // Approved proposals leave the pending queue for good.
  await expect(page.getByText(summary)).toHaveCount(0)
})

test('a signed-out visitor at the central authority path is sent to sign in', async ({ page }) => {
  await page.goto('/central-authority')
  await expect(page.getByRole('heading', { name: 'Sign in to the operation' })).toBeVisible()
})
