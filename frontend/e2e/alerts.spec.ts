import { expect, test, type Page } from '@playwright/test'
import { signIn } from './helpers'

/**
 * The alert lifecycle end to end: a Camp Manager can raise and drive a camp-scoped alert, and
 * routing actually withholds a Coordinator-only type (Security incident) from a Camp Manager who
 * has no business seeing it. Mirrors ticket-06's raise/acknowledge/escalate flow against the real
 * running stack, not a mock.
 *
 * Alerts are never reset between runs (unlike the simulation clock), so earlier runs' alerts —
 * with the same description text, if it were left constant — stay in the database. Each
 * description below is stamped with a nonce so a run can find the exact row it just made rather
 * than an identical-looking one an earlier run left behind.
 */

/** A description unique to this test run, so a re-run never collides with a leftover alert. */
function uniqueDescription(base: string): string {
  return `${base} [${Date.now()}-${Math.random().toString(36).slice(2, 8)}]`
}

/** A camp id the signed-in user is entitled to raise an alert against. */
async function ownCampId(page: Page): Promise<number> {
  const token = await page.evaluate(() => localStorage.getItem('dms.access'))
  const me = await page.request.get('http://localhost:8080/me', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(me.ok()).toBeTruthy()
  const { campIds } = (await me.json()) as { campIds: number[] }
  if (campIds.length > 0) return campIds[0]

  // Oversight roles (Coordinator/Admin) manage no camp of their own but may raise against any —
  // fall back to the first camp in the world.
  const disasters = await page.request.get('http://localhost:8080/world/disasters', {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(disasters.ok()).toBeTruthy()
  const world = (await disasters.json()) as { camps: { id: number }[] }[]
  return world[0].camps[0].id
}

/** Raises an alert through the real form and waits for it to close before handing back control. */
async function raiseAlert(page: Page, { type, description }: { type?: string; description: string }) {
  await page.getByRole('button', { name: /raise alert/i }).click()
  if (type) {
    await page.getByLabel(/alert type/i).selectOption(type)
  }
  await page.getByPlaceholder('Camp').fill(String(await ownCampId(page)))
  await page.getByRole('textbox', { name: /description/i }).fill(description)
  await page.getByRole('button', { name: /submit/i }).click()
  // The form unmounts once the create request resolves — wait for that rather than racing it,
  // since its <option>/<textarea> text would otherwise collide with the list row's own text.
  await expect(page.getByLabel(/alert type/i)).toHaveCount(0)
}

/**
 * Finds and opens the row for the alert this run just raised. Row order isn't guaranteed by the
 * API, rows only show type/status (not description), and the list's own refetch can still be in
 * flight right after the raise — so this retries a newest-to-oldest scan until the detail panel
 * shows the description this run used, or gives up.
 */
async function openJustRaised(page: Page, description: string) {
  const rows = page.getByRole('list', { name: /alerts/i }).getByRole('listitem')

  await expect(async () => {
    const count = await rows.count()
    for (let index = count - 1; index >= 0; index--) {
      await rows.nth(index).locator('button').click()
      const found = await detailPanel(page)
        .getByText(description)
        .isVisible()
        .catch(() => false)
      if (found) return
    }
    throw new Error(`No alert row found with description "${description}"`)
  }).toPass({ timeout: 10_000 })
}

/** The single alert-detail section — scoping to it avoids matching a list row's own text. */
function detailPanel(page: Page) {
  return page.locator('section')
}

test('a camp manager raises a resource-shortage alert and acknowledges it', async ({ page }) => {
  await signIn(page, 'camp_manager')

  const description = uniqueDescription('Water stock critically low')
  await raiseAlert(page, { description })
  await openJustRaised(page, description)

  await detailPanel(page).getByRole('button', { name: /acknowledge/i }).click()

  await expect(detailPanel(page).getByText('Acknowledged', { exact: true })).toBeVisible()
})

test('a coordinator sees and can act on a security incident a camp manager cannot see', async ({
  page,
  browser,
}) => {
  const coordinatorPage = page
  await signIn(coordinatorPage, 'coordinator')
  const description = uniqueDescription('Perimeter breach')
  await raiseAlert(coordinatorPage, { type: 'SECURITY_INCIDENT', description })
  await openJustRaised(coordinatorPage, description)
  // Security incident routes straight to Coordinator/Admin, so the Coordinator can act on it.
  await expect(
    detailPanel(coordinatorPage).getByRole('button', { name: /acknowledge/i }),
  ).toBeVisible()

  const campManagerContext = await browser.newContext()
  const campManagerPage = await campManagerContext.newPage()
  await signIn(campManagerPage, 'camp_manager')
  await expect(campManagerPage.getByText(description)).toHaveCount(0)
  await campManagerContext.close()
})
